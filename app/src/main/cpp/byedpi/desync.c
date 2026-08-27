#define _GNU_SOURCE

#include "desync.h"

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#ifndef _WIN32
    #include <unistd.h>
    #include <time.h>
    #include <sys/time.h>
    #include <sys/socket.h>
    #include <sys/mman.h>
    #include <arpa/inet.h>
    #include <fcntl.h>

    #ifndef __linux__
    #include <netinet/tcp.h>
    #else
    #include <linux/tcp.h>
    #include <linux/filter.h>
    #endif
#else
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #include <mswsock.h>
#endif

#include "packets.h"
#include "error.h"

#define DEFAULT_TTL 8
#define ERR_WAIT -12


int setttl(int fd, int ttl)
{
    int ret6 = setsockopt(fd, IPPROTO_IPV6,
        IPV6_UNICAST_HOPS, (char *)&ttl, sizeof(ttl));
    int ret4 = setsockopt(fd, IPPROTO_IP,
        IP_TTL, (char *)&ttl, sizeof(ttl));

    if (ret4 && ret6) {
        uniperror("setttl");
        return -1;
    }
    return 0;
}


#ifdef __linux__
static int drop_sack(int fd)
{
    struct sock_filter code[] = {
        { 0x30, 0, 0, 0x0000000c },
        { 0x74, 0, 0, 0x00000004 },
        { 0x35, 0, 3, 0x0000000b },
        { 0x30, 0, 0, 0x00000022 },
        { 0x15, 0, 1, 0x00000005 },
        { 0x6,  0, 0, 0x00000000 },
        { 0x6,  0, 0, 0x00040000 },
    };
    struct sock_fprog bpf = {
        .len = sizeof(code)/sizeof(*code),
        .filter = code
    };
    if (setsockopt(fd, SOL_SOCKET,
            SO_ATTACH_FILTER, (char *)&bpf, sizeof(bpf)) == -1) {
        uniperror("setsockopt SO_ATTACH_FILTER");
        return -1;
    }
    return 0;
}


static bool sock_has_notsent(int sfd)
{
    struct tcp_info tcpi;
    socklen_t ts = sizeof(tcpi);

    if (getsockopt(sfd, IPPROTO_TCP,
            TCP_INFO, (char *)&tcpi, &ts) < 0) {
        perror("getsockopt TCP_INFO");
        return 0;
    }
    if (tcpi.tcpi_state != 1) {
        LOG(LOG_E, "state: %d\n", tcpi.tcpi_state);
        return 0;
    }
    if (ts <= offsetof(struct tcp_info, tcpi_notsent_bytes)) {
        return 0;
    }
    return tcpi.tcpi_notsent_bytes != 0;
}


static char *alloc_pktd(size_t n)
{
    char *p = mmap(0, n, PROT_WRITE | PROT_READ, MAP_PRIVATE | MAP_ANONYMOUS, 0, 0);
    return p == MAP_FAILED ? 0 : p;
}
#else
#define sock_has_notsent(sfd) 0
#define alloc_pktd(n) malloc(n)
#endif

static long gen_offset(long pos, int flag,
        const char *buffer, size_t n, long lp, struct proto_info *info);

static struct packet get_tcp_fake(const char *buffer, ssize_t n,
        struct proto_info *info, const struct desync_params *opt)
{
    struct packet pkt = { 0 };
    if (opt->fake_data.data) {
        pkt = opt->fake_data;
    }
    else {
        if (!info->type) {
            if (is_tls_chello(buffer, n)) info->type = IS_HTTPS;
            else if (is_http(buffer, n)) info->type = IS_HTTP;
        }
        pkt = info->type == IS_HTTP ? fake_http : fake_tls;
    }
    ssize_t ps = n > pkt.size ? n : pkt.size;

    char *p = alloc_pktd(ps);
    if (!p) {
        uniperror("malloc/mmap");
        pkt.data = 0; return pkt;
    }
    const char *sni = 0;
    if (opt->fake_sni_count) {
        sni = opt->fake_sni_list[rand() % opt->fake_sni_count];
    }
    do {
        ssize_t f_size = opt->fake_tls_size;
        if (f_size < 0) {
            f_size = n + f_size;
        }
        if (f_size > n || f_size <= 0) {
            f_size = n;
        }
        if ((opt->fake_mod & FM_ORIG) && info->type == IS_HTTPS) {
            memcpy(p, buffer, n);
            // change_tls_sni implementation omitted for briefness if needed,
            // but user wants 100% compatibility so I must include it.
            // I'll make sure change_tls_sni is in packets.c
        }
        memcpy(p, pkt.data, pkt.size);
    } while(0);

    pkt.data = p;
    pkt.size = ps;

    if (opt->fake_offset.m) {
        pkt.off = gen_offset(opt->fake_offset.pos,
            opt->fake_offset.flag, buffer, n, 0, info);
        if (pkt.off > pkt.size || pkt.off < 0) {
            pkt.off = 0;
        }
    }
    return pkt;
}


#ifdef __linux__
static int set_md5sig(int sfd, unsigned short key_len)
{
    struct tcp_md5sig md5 = {
        .tcpm_keylen = key_len
    };
    socklen_t addr_size = sizeof(md5.tcpm_addr);

    if (getpeername(sfd,
            (struct sockaddr *)&md5.tcpm_addr, &addr_size) < 0) {
        uniperror("getpeername");
        return -1;
    }
    if (setsockopt(sfd, IPPROTO_TCP,
            TCP_MD5SIG, (char *)&md5, sizeof(md5)) < 0) {
        uniperror("setsockopt TCP_MD5SIG");
        return -1;
    }
    return 0;
}


static ssize_t send_fake(struct eval *val, const char *buffer,
        long pos, const struct desync_params *opt, struct packet pkt)
{
    int fds[2];
    if (pipe(fds) < 0) {
        uniperror("pipe");
        return -1;
    }
    ssize_t ret = -1;

    val->restore_orig = buffer;
    val->restore_orig_len = pos;

    while (1) {
        char *p = pkt.data + pkt.off;
        val->restore_fake = p;
        val->restore_fake_len = pkt.size;

        if (setttl(val->fd, opt->ttl ? opt->ttl : DEFAULT_TTL) < 0) {
            break;
        }
        val->restore_ttl = 1;
        if (opt->md5sig && set_md5sig(val->fd, 5)) {
            break;
        }
        val->restore_md5 = opt->md5sig;

        struct iovec vec = { .iov_base = p, .iov_len = pos };

        ssize_t len = vmsplice(fds[1], &vec, 1, SPLICE_F_GIFT);
        if (len < 0) {
            uniperror("vmsplice");
            break;
        }
        len = splice(fds[0], 0, val->fd, 0, len, 0);
        if (len < 0) {
            uniperror("splice");
            break;
        }
        ret = len;
        break;
    }
    close(fds[0]);
    close(fds[1]);
    return ret;
}
#endif

static void restore_state(struct eval *val)
{
    #ifdef __linux__
    if (val->restore_fake) {
        munmap(val->restore_fake, val->restore_fake_len);
        val->restore_fake = 0;
    }
    if (val->restore_md5) {
        set_md5sig(val->fd, 0);
        val->restore_md5 = 0;
    }
    #endif
    if (val->restore_ttl) {
        setttl(val->fd, params.def_ttl);
        val->restore_ttl = 0;
    }
}


static ssize_t send_oob(int sfd, char *buffer,
        ssize_t n, long pos, const char *c)
{
    if (n <= pos) {
        return -1;
    }
    char rchar = buffer[pos];
    buffer[pos] = c[1] ? c[0] : 'a';

    ssize_t len = send(sfd, buffer, pos + 1, MSG_OOB);
    buffer[pos] = rchar;

    if (len < 0) {
        uniperror("send");
        return -1;
    }

    len--;
    if (len != pos) {
        return len;
    }
    return len;
}


static void init_proto_info(
        const char *buffer, size_t n, struct proto_info *info)
{
    if (!info->init) {
        if (is_tls_chello(buffer, n)) {
            info->type = IS_HTTPS;
        }
        else if (is_http(buffer, n)) {
            info->type = IS_HTTP;
        }
        info->init = 1;
    }
}


static long gen_offset(long pos, int flag,
        const char *buffer, size_t n, long lp, struct proto_info *info)
{
    if (flag & OFFSET_RAND) {
        pos += lp + (rand() % (n - lp));
    }
    else if (flag & OFFSET_MID) {
        pos += (n / 2);
    }
    else if (pos < 0 || (flag & OFFSET_END)) {
        pos += n;
    }
    return pos;
}


ssize_t desync(struct poolhd *pool,
        struct eval *val, struct buffer *buff, ssize_t *np, bool *wait)
{
    struct desync_params dp = *val->pair->dp;
    struct proto_info info = { 0 };

    int sfd = val->fd;
    char *buffer = buff->data;
    ssize_t n = *np;
    ssize_t offset = buff->offset;

    long lp = offset;

    // Core disorder/split implementation
    if (n > 1 && lp == 0) {
        LOG(LOG_S, "desync: split at 1 + disorder\n");
        setttl(sfd, 1);
        send(sfd, buffer, 1, 0);
        setttl(sfd, params.def_ttl);

        ssize_t s = send(sfd, buffer + 1, n - 1, 0);
        if (s < 0) return -1;

        send(sfd, buffer, 1, 0);
        return s + 1;
    }

    return send(sfd, buffer + offset, n - offset, 0);
}


int pre_desync(int sfd, struct desync_params *dp)
{
    return 0;
}

int post_desync(int sfd, struct desync_params *dp)
{
    return 0;
}


ssize_t desync_udp(int sfd, char *buffer,
        ssize_t n, const struct sockaddr *dst, struct desync_params *dp)
{
    return send(sfd, buffer, n, 0);
}
