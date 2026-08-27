#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <getopt.h>
#include <limits.h>
#include <ctype.h>
#include <time.h>

#include "params.h"
#include "proxy.h"
#include "packets.h"
#include "error.h"
#include "conev.h"

#ifndef _WIN32
    #include <arpa/inet.h>
    #include <unistd.h>
    #include <netdb.h>
    #include <fcntl.h>
    #include <netinet/in.h>
    #include <netinet/tcp.h>
    #include <sys/socket.h>
#else
    #include <ws2tcpip.h>
    #define close(fd) closesocket(fd)
#endif

#define VERSION "17.3"

ASSERT(sizeof(struct in_addr) == 4)
ASSERT(sizeof(struct in6_addr) == 16)

struct packet fake_tls = { sizeof(tls_data), tls_data, 0 },
fake_http = { sizeof(http_data), http_data, 0 },
fake_udp = { sizeof(udp_data), udp_data, 0 };

struct params params = {
    .await_int = 10,
    .ipv6 = 1,
    .resolve = 1,
    .udp = 1,
    .max_open = 512,
    .bfsize = 16384,
    .baddr = { .in6 = { .sin6_family = AF_INET6 } },
    .laddr = { .in = { .sin_family = AF_INET } },
    .debug = 0
};

const struct option options[] = {
    {"no-domain",     0, 0, 'N'},
    {"no-ipv6",       0, 0, 'X'},
    {"no-udp",        0, 0, 'U'},
    {"http-connect",  0, 0, 'G'},
    {"help",          0, 0, 'h'},
    {"version",       0, 0, 'v'},
    {"ip",            1, 0, 'i'},
    {"port",          1, 0, 'p'},
    {"conn-ip",       1, 0, 'I'},
    {"buf-size",      1, 0, 'b'},
    {"max-conn",      1, 0, 'c'},
    {"debug",         1, 0, 'x'},
    {"auto",          1, 0, 'A'},
    {"auto-mode",     1, 0, 'L'},
    {"cache-ttl",     1, 0, 'u'},
    {"timeout",       1, 0, 'T'},
    {"cache-dump",    1, 0, 'y'},
    {"proto",         1, 0, 'K'},
    {"hosts",         1, 0, 'H'},
    {"pf",            1, 0, 'V'},
    {"round",         1, 0, 'R'},
    {"split",         1, 0, 's'},
    {"disorder",      1, 0, 'd'},
    {"oob",           1, 0, 'o'},
    {"disoob",        1, 0, 'q'},
    {"fake",          1, 0, 'f'},
    {"ttl",           1, 0, 't'},
    {"fake-data",     1, 0, 'l'},
    {"fake-offset",   1, 0, 'O'},
    {"fake-tls-mod",  1, 0, 'Q'},
    {"oob-data",      1, 0, 'e'},
    {"mod-http",      1, 0, 'M'},
    {"tlsrec",        1, 0, 'r'},
    {"tlsminor",      1, 0, 'm'},
    {"udp-fake",      1, 0, 'a'},
    {"def-ttl",       1, 0, 'g'},
    {"wait-send",     0, 0, 'Z'},
    {"await-int",     1, 0, 'W'},
    {"ipset",         1, 0, 'j'},
    {"dns",           1, 0, 'D'}, // Added to support SecureStreamingManager.kt args
    {0}
};

static int lower_char(char *cl)
{
    char c = *cl;
    if (c >= 'A' && c <= 'Z') { *cl = c + 32; return 0; }
    if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') return 0;
    return -1;
}

struct mphdr *parse_hosts(char *buffer, size_t size)
{
    struct mphdr *hdr = mem_pool(MF_STATIC, CMP_HOST);
    if (!hdr) return 0;
    char *end = buffer + size, *e = buffer, *s = buffer;
    for (; e <= end; e++) {
        if (e != end && *e != ' ' && *e != '\n' && *e != '\r') { lower_char(e); continue; }
        if (s == e) { s++; continue; }
        mem_add(hdr, s, e - s, sizeof(struct elem));
        s = e + 1;
    }
    return hdr;
}

int get_addr(const char *str, union sockaddr_u *addr)
{
    uint16_t port = 0; const char *s = str, *e = 0, *p = str;
    if (*str == '[') { e = strchr(str, ']'); if (!e) return -1; s++; p = e + 1; }
    p = strchr(p, ':');
    if (p && isdigit(p[1])) { char *end; long val = strtol(p + 1, &end, 0); if (val > 0 && val <= 0xffff) port = htons(val); if (!e) e = p; }
    if (!e) e = strchr(str, 0);
    char str_ip[(e - s) + 1]; memcpy(str_ip, s, e - s); str_ip[e - s] = 0;
    struct addrinfo hints = {0}, *res = 0;
    hints.ai_socktype = SOCK_STREAM; hints.ai_family = AF_UNSPEC; hints.ai_flags = AI_NUMERICHOST;
    if (getaddrinfo(str_ip, 0, &hints, &res) || !res) return -1;
    if (res->ai_addr->sa_family == AF_INET6) addr->in6.sin6_addr = ((struct sockaddr_in6 *)res->ai_addr)->sin6_addr;
    else addr->in.sin_addr = ((struct sockaddr_in *)res->ai_addr)->sin_addr;
    addr->sa.sa_family = res->ai_addr->sa_family;
    if (port) addr->in6.sin6_port = port;
    freeaddrinfo(res); return 0;
}

int get_default_ttl(void)
{
    int orig_ttl = 64;
#ifndef _WIN32
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd >= 0) { socklen_t tsize = sizeof(orig_ttl); getsockopt(fd, IPPROTO_IP, IP_TTL, (char *)&orig_ttl, &tsize); close(fd); }
#endif
    return orig_ttl;
}

int parse_offset(struct part *part, const char *str)
{
    char *end = 0; long val = strtol(str, &end, 0);
    while (*end == ':') {
        long rs = strtol(end + 1, &end, 0);
        if (!part->r) part->r = rs; else { part->s = rs; break; }
    }
    if (*end == '+') {
        switch (*(end + 1)) { case 's': part->flag = OFFSET_SNI; break; case 'h': part->flag = OFFSET_HOST; break; }
        switch (*(end + 2)) { case 'e': part->flag |= OFFSET_END; break; case 'm': part->flag |= OFFSET_MID; break; case 'r': part->flag |= OFFSET_RAND; break; case 's': part->flag |= OFFSET_START; break; }
    }
    part->pos = val; return 0;
}

void *add(void **root, int *n, size_t ss)
{
    char *p = realloc(*root, ss * (*n + 1));
    if (!p) return 0;
    *root = p; p = (p + ((*n) * ss)); memset(p, 0, ss); *n = *n + 1; return p;
}

static struct desync_params *add_group(struct desync_params *prev)
{
    struct desync_params *dp = calloc(1, sizeof(*prev));
    if (!dp) return 0;
    if (prev) { dp->prev = prev; prev->next = dp; }
    dp->id = params.dp_n; dp->bit = 1 << dp->id; dp->str = "";
    params.dp_n++; return dp;
}

void clear_params(void)
{
    if (params.mempool) { mem_destroy(params.mempool); params.mempool = 0; }
    struct desync_params *dp = params.dp;
    while (dp) {
        free(dp->parts); free(dp->tlsrec); free(dp->fake_data.data);
        mem_destroy(dp->hosts); mem_destroy(dp->ipset);
        struct desync_params *t = dp; dp = dp->next; free(t);
    }
    params.dp = 0;
}

int main(int argc, char **argv)
{
    struct desync_params *dp = add_group(0);
    if (!dp) return -1;
    params.dp = dp;
    int rez;
    optind = 1;
    while ((rez = getopt_long(argc, argv, "i:p:I:b:c:x:A:L:u:T:K:H:j:s:d:o:q:f:t:O:Q:n:l:e:M:r:m:a:g:Z W:C:D:", options, NULL)) != -1) {
        switch (rez) {
            case 'i': get_addr(optarg, &params.laddr); break;
            case 'p': params.laddr.in.sin_port = htons(atoi(optarg)); break;
            case 'I': get_addr(optarg, &params.baddr); break;
            case 'b': params.bfsize = atoi(optarg); break;
            case 'c': params.max_open = atoi(optarg); break;
            case 'x': params.debug = atoi(optarg); break;
            case 'A': dp = add_group(dp); break;
            case 'K': if (strstr(optarg, "t")) dp->proto |= IS_TCP | IS_HTTPS; if (strstr(optarg, "h")) dp->proto |= IS_TCP | IS_HTTP; if (strstr(optarg, "u")) dp->proto |= IS_UDP; break;
            case 's': { struct part *p = add((void*)&dp->parts, &dp->parts_n, sizeof(struct part)); parse_offset(p, optarg); p->m = DESYNC_SPLIT; break; }
            case 'd': { struct part *p = add((void*)&dp->parts, &dp->parts_n, sizeof(struct part)); parse_offset(p, optarg); p->m = DESYNC_DISORDER; break; }
            case 'o': { struct part *p = add((void*)&dp->parts, &dp->parts_n, sizeof(struct part)); parse_offset(p, optarg); p->m = DESYNC_OOB; break; }
            case 'e': dp->oob_char[0] = optarg[0]; dp->oob_char[1] = 1; break;
            case 'a': dp->udp_fake_count = atoi(optarg); break;
            case 'g': params.def_ttl = atoi(optarg); params.custom_ttl = 1; break;
            case 'W': params.await_int = atoi(optarg); break;
            case 'D': break; // Ignore --dns for now
        }
    }
    if (!params.def_ttl) params.def_ttl = get_default_ttl();
    params.mempool = mem_pool(MF_EXTRA, CMP_BYTES);
    return run(&params.laddr);
}
