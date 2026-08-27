#include "proxy.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <assert.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netinet/tcp.h>
#include <netdb.h>

#include "params.h"
#include "conev.h"
#include "extend.h"
#include "error.h"
#include "packets.h"

int server_fd;

static void on_cancel(int sig) {
    shutdown(server_fd, SHUT_RDWR);
}

void map_fix(union sockaddr_u *addr, char f6)
{
    if (addr->sa.sa_family == AF_INET && f6) {
        addr->sa.sa_family = AF_INET6;
        uint32_t ip = addr->in.sin_addr.s_addr;
        memset(&addr->in6.sin6_addr, 0, 10);
        addr->in6.sin6_addr.s6_addr[10] = 0xff;
        addr->in6.sin6_addr.s6_addr[11] = 0xff;
        memcpy(&addr->in6.sin6_addr.s6_addr[12], &ip, 4);
    }
}

static inline int nb_socket(int domain, int type)
{
    int fd = socket(domain, type, 0);
    if (fd < 0) return -1;
    if (fcntl(fd, F_SETFL, O_NONBLOCK) < 0) { close(fd); return -1; }

    // Increase socket buffers for high-speed catalog sync
    int sock_buf = 1024 * 1024; // 1MB
    setsockopt(fd, SOL_SOCKET, SO_RCVBUF, (char *)&sock_buf, sizeof(sock_buf));
    setsockopt(fd, SOL_SOCKET, SO_SNDBUF, (char *)&sock_buf, sizeof(sock_buf));

    return fd;
}

static int resolve(const char *chost, int len, union sockaddr_u *addr, int type)
{
    struct addrinfo hints = {0}, *res = 0;
    hints.ai_socktype = type;
    hints.ai_flags = AI_ADDRCONFIG;
    if (!params.resolve) hints.ai_flags |= AI_NUMERICHOST;
    hints.ai_family = params.ipv6 ? AF_UNSPEC : AF_INET;

    char host[len + 1]; memcpy(host, chost, len); host[len] = 0;
    if (getaddrinfo(host, 0, &hints, &res) || !res) return -1;
    memcpy(addr, res->ai_addr, SA_SIZE(res->ai_addr));
    freeaddrinfo(res);
    return 0;
}

static int auth_socks5(int fd, const char *buffer, ssize_t n)
{
    if (n <= 2) return -1;
    uint8_t a[2] = { S_VER5, S_AUTH_NONE };
    if (send(fd, (char *)a, sizeof(a), 0) < 0) return -1;
    return 0;
}

static int resp_s5_error(int fd, int e)
{
    struct s5_rep s5r = { .ver = 0x05, .code = (uint8_t )e, .atp = S_ATP_I4 };
    return send(fd, (char *)&s5r, sizeof(s5r), 0);
}

static int resp_error(int fd, int e, int flag)
{
    if (flag == FLAG_S5) return resp_s5_error(fd, e);
    return 0;
}

static int s5_get_addr(const char *buffer, size_t n, union sockaddr_u *addr, int type)
{
    if (n < S_SIZE_MIN) return -S_ER_GEN;
    const struct s5_req *r = (const struct s5_req *)buffer;
    size_t o = (r->atp == S_ATP_I4 ? S_SIZE_I4 : (r->atp == S_ATP_ID ? r->dst.id.len + S_SIZE_ID : (r->atp == S_ATP_I6 ? S_SIZE_I6 : 0)));
    if (n < o) return -S_ER_GEN;
    switch (r->atp) {
        case S_ATP_I4: addr->in.sin_family = AF_INET; addr->in.sin_addr = r->dst.i4.ip; break;
        case S_ATP_ID: if (resolve(r->dst.id.domain, r->dst.id.len, addr, type)) return -S_ER_HOST; break;
        case S_ATP_I6: addr->in6.sin6_family = AF_INET6; addr->in6.sin6_addr = r->dst.i6.ip; break;
    }
    memcpy(&addr->in.sin_port, &buffer[o - 2], sizeof(uint16_t));
    return (int)o;
}

int create_conn(struct poolhd *pool, struct eval *val, const union sockaddr_u *dst, evcb_t next)
{
    int sfd = nb_socket(dst->sa.sa_family, SOCK_STREAM);
    if (sfd < 0) return -1;
    int one = 1; setsockopt(sfd, IPPROTO_TCP, TCP_NODELAY, (char *)&one, sizeof(one));
    if (connect(sfd, &dst->sa, SA_SIZE(dst)) < 0 && errno != EINPROGRESS && errno != EAGAIN) { close(sfd); return -1; }
    struct eval *pair = add_event(pool, next, sfd, POLLOUT);
    if (!pair) { close(sfd); return -1; }
    val->pair = pair; pair->pair = val;
    pair->addr = *dst; pair->flag = FLAG_CONN;
    val->cb = &on_ignore;
    return 0;
}

int on_tunnel(struct poolhd *pool, struct eval *val, int etype)
{
    ssize_t n = 0;
    struct eval *pair = val->pair;
    if (!pair) return -1;
    if (etype & POLLOUT || etype == POLLTIMEOUT) { val = pair; pair = val->pair; }

    if (val->buff) {
        if (etype & POLLHUP) return -1;
        n = val->buff->lock - val->buff->offset;
        bool wait = false;
        ssize_t sn = tcp_send_hook(pool, pair, val->buff, &val->buff->lock, &wait);
        if (sn < 0) return -1;
        if (sn < n || wait) { val->buff->offset += sn; return 0; }
        buff_push(pool, val->buff); val->buff = 0;
        if (mod_etype(pool, val, POLLIN) || mod_etype(pool, pair, POLLIN)) return -1;
    }

    struct buffer *buff = buff_pop(pool, params.bfsize);
    if (!buff) return -1;
    val->buff = buff;
    do {
        n = tcp_recv_hook(pool, val, buff);
        if (n == 0) break;
        if (n < 0) {
            if (errno == EAGAIN || errno == EINPROGRESS) { n = 0; break; }
            return -1;
        }
        bool wait = false;
        ssize_t sn = tcp_send_hook(pool, pair, buff, &n, &wait);
        if (sn < 0) return -1;

        if (sn < n || wait) {
            buff->lock = n;
            buff->offset = (unsigned int)sn;
            if (mod_etype(pool, val, 0) ||
                    mod_etype(pool, pair, !wait ? POLLOUT : 0)) return -1;
            return 0;
        }
    } while (n == (ssize_t)buff->size);
    buff_push(pool, val->buff); val->buff = 0;
    return 0;
}

int on_request(struct poolhd *pool, struct eval *val, int et)
{
    char buf[2048];
    ssize_t n = recv(val->fd, buf, sizeof(buf), 0);
    if (n <= 0) return -1;
    if (buf[0] == S_VER5) {
        if (val->flag != FLAG_S5) { if (auth_socks5(val->fd, buf, n)) return -1; val->flag = FLAG_S5; return 0; }
        union sockaddr_u dst = {0};
        int s5e = s5_get_addr(buf, (size_t)n, &dst, SOCK_STREAM);
        if (s5e >= 0) return connect_hook(pool, val, &dst, &on_connect);
    }
    return -1;
}

int on_connect(struct poolhd *pool, struct eval *val, int et) {
    if (et & POLLERR) return -1;
    if (mod_etype(pool, val, POLLIN) || mod_etype(pool, val->pair, POLLIN)) return -1;
    val->cb = &on_tunnel; val->pair->cb = &on_tunnel;
    uint8_t rep[] = {S_VER5, 0, 0, S_ATP_I4, 0, 0, 0, 0, 0, 0};
    send(val->pair->fd, (char*)rep, 10, 0);
    return 0;
}

int on_ignore(struct poolhd *pool, struct eval *val, int etype) {
    if (etype & POLLOUT) return on_connect(pool, val, etype);
    return (etype & (POLLHUP | POLLERR | POLLRDHUP)) ? -1 : 0;
}

static int on_accept(struct poolhd *pool, struct eval *val, int et) {
    union sockaddr_u client; socklen_t len = sizeof(client);
    int c = accept(val->fd, &client.sa, &len);
    if (c < 0) return 0;
    fcntl(c, F_SETFL, O_NONBLOCK);
    int one = 1; setsockopt(c, IPPROTO_TCP, TCP_NODELAY, (char *)&one, sizeof(one));
    struct eval *rval = add_event(pool, &on_request, c, POLLIN);
    if (!rval) close(c);
    else rval->addr = client;
    return 0;
}

int start_event_loop(int srvfd) {
    server_fd = srvfd;
    struct poolhd *pool = init_pool(params.max_open * 2 + 1);
    if (!pool) return -1;
    add_event(pool, &on_accept, srvfd, POLLIN);
    loop_event(pool);
    destroy_pool(pool);
    return 0;
}

int run(const union sockaddr_u *srv) {
    signal(SIGINT, on_cancel); signal(SIGTERM, on_cancel);
    int srvfd = nb_socket(srv->sa.sa_family, SOCK_STREAM);
    if (srvfd < 0) return -1;
    int opt = 1; setsockopt(srvfd, SOL_SOCKET, SO_REUSEADDR, (char *)&opt, sizeof(opt));
    if (bind(srvfd, &srv->sa, SA_SIZE(srv)) < 0) { close(srvfd); return -1; }
    listen(srvfd, 10);
    return start_event_loop(srvfd);
}
