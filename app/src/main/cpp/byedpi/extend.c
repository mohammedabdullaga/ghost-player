#include "extend.h"

#ifdef _WIN32
    #include <ws2tcpip.h>
    #ifndef TCP_MAXRT
    #define TCP_MAXRT 5
    #endif
#else
    #include <arpa/inet.h>
    #include <netinet/tcp.h>
    #include <sys/un.h>
    #include <sys/time.h>
#endif

#include <string.h>
#include <assert.h>
#include <stdlib.h>

#include "proxy.h"
#include "error.h"
#include "params.h"
#include "desync.h"
#include "packets.h"

static int set_timeout(int fd, unsigned int s)
{
    #ifdef __linux__
    if (setsockopt(fd, IPPROTO_TCP, TCP_USER_TIMEOUT, (char *)&s, sizeof(s))) {
        uniperror("setsockopt TCP_USER_TIMEOUT");
        return -1;
    }
    #endif
    return 0;
}

static ssize_t serialize_addr(const union sockaddr_u *dst, struct cache_key *out)
{
    out->port = dst->in.sin_port;
    out->family = dst->sa.sa_family;
    static const ssize_t c = offsetof(struct cache_key, ip.v4);
    if (dst->sa.sa_family == AF_INET) {
        out->ip.v4 = dst->in.sin_addr;
        return c + sizeof(out->ip.v4);
    } else {
        out->ip.v6 = dst->in6.sin6_addr;
        return c + sizeof(out->ip.v6);
    }
}

static struct elem_i *cache_get(const union sockaddr_u *dst)
{
    struct cache_key key = { 0 };
    int len = serialize_addr(dst, &key);
    struct elem_i *val = mem_get(params.mempool, (char *)&key, len);
    if (!val) return 0;
    time_t t = time(0);
    if (t > val->time + params.cache_ttl[val->time_inc - 1]) return 0;
    return val;
}

static struct elem_i *cache_add(const union sockaddr_u *dst, char **host, int host_len)
{
    struct cache_key key = { 0 };
    int cmp_len = serialize_addr(dst, &key);
    time_t t = time(0);
    struct cache_key *data = calloc(1, cmp_len);
    if (!data) return 0;
    memcpy(data, &key, cmp_len);
    struct elem_i *val = mem_add(params.mempool, (char *)data, cmp_len, sizeof(struct elem_i));
    if (!val) { free(data); return 0; }
    val->time = t;
    if (val->time_inc < params.cache_ttl_n) val->time_inc++;
    if (!val->extra && *host) { val->extra_len = host_len; val->extra = *host; *host = 0; }
    return val;
}

int connect_hook(struct poolhd *pool, struct eval *val, const union sockaddr_u *dst, evcb_t next)
{
    struct desync_params *dp = params.dp;
    val->dp = dp;
    return create_conn(pool, val, dst, next);
}

int socket_mod(int fd)
{
    if (params.custom_ttl) setttl(fd, params.def_ttl);
    return 0;
}

static int reconnect(struct poolhd *pool, struct eval *val)
{
    assert(val->flag == FLAG_CONN);
    struct eval *client = val->pair;
    if (connect_hook(pool, client, &val->addr, client->sq_buff ? &on_tunnel : &on_connect)) return -1;
    val->pair = 0; del_event(pool, val);
    client->cb = &on_tunnel;
    if (client->sq_buff) {
        if (!client->buff) client->buff = buff_pop(pool, client->sq_buff->size);
        client->buff->lock = client->sq_buff->lock;
        memcpy(client->buff->data, client->sq_buff->data, client->buff->lock);
        client->buff->offset = 0;
    }
    client->round_sent = 0; client->part_sent = 0;
    return 0;
}

static bool check_proto_tcp(int proto, const char *buffer, ssize_t n)
{
    if (!(proto & ~IS_IPV4)) return 1;
    else if ((proto & IS_HTTP) && is_http(buffer, n)) return 1;
    else if ((proto & IS_HTTPS) && is_tls_chello(buffer, n)) return 1;
    return 0;
}

int on_torst(struct poolhd *pool, struct eval *val) { return -1; }

ssize_t tcp_send_hook(struct poolhd *pool, struct eval *remote, struct buffer *buff, ssize_t *n, bool *wait)
{
    ssize_t sn = -1;
    int skip = remote->flag != FLAG_CONN;
    size_t off = buff->offset;
    if (!skip) {
        struct eval *client = remote->pair;
        int r = client->round_count;
        sn = desync(pool, remote, buff, n, wait);
    }
    if (skip) {
        sn = send(remote->fd, buff->data + off, *n - off, 0);
        if (sn < 0 && get_e() == EAGAIN) return 0;
    }
    remote->pair->round_sent += sn;
    return sn;
}

ssize_t tcp_recv_hook(struct poolhd *pool, struct eval *val, struct buffer *buff)
{
    ssize_t n = recv(val->fd, buff->data, buff->size, 0);
    if (n < 1) {
        if (!n) return -1;
        if (get_e() == EAGAIN) return 0;
        return -1;
    }
    val->recv_count += n;
    if (val->round_sent == 0) {
        val->round_count++;
        val->pair->round_sent = 0;
        val->pair->part_sent = 0;
    }
    return n;
}

ssize_t udp_hook(struct eval *val, char *buffer, ssize_t n, const union sockaddr_u *dst)
{
    return desync_udp(val->fd, buffer, n, &dst->sa, params.dp);
}
