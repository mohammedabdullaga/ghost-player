#include <string.h>
#include <jni.h>
#include <getopt.h>
#include <signal.h>
#include <setjmp.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/socket.h>

#include "byedpi/error.h"
#include "main.h"

extern int server_fd;
static int g_proxy_running = 0;

struct params default_params = {
        .await_int = 10,
        .ipv6 = 1,
        .resolve = 1,
        .udp = 1,
        .max_open = 512,
        .bfsize = 65536, // IPTV Optimized
        .baddr = { .in6 = { .sin6_family = AF_INET6 } },
        .laddr = { .in = { .sin_family = AF_INET } },
        .debug = 0
};

void reset_params(void) {
    clear_params();
    params = default_params;
}

JNIEXPORT jint JNICALL
Java_com_eliteprime_app_data_remote_proxy_SecureStreamingManager_jniStartProxy(JNIEnv *env, jobject thiz, jobjectArray args) {
    if (g_proxy_running) return -1;
    int argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc(argc, sizeof(char *));
    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        const char *arg_str = (*env)->GetStringUTFChars(env, arg, 0);
        argv[i] = arg_str ? strdup(arg_str) : NULL;
        (*env)->ReleaseStringUTFChars(env, arg, arg_str);
        (*env)->DeleteLocalRef(env, arg);
    }
    reset_params();
    g_proxy_running = 1;
    optind = 1;
    int result = main(argc, argv);
    g_proxy_running = 0;
    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_eliteprime_app_data_remote_proxy_SecureStreamingManager_jniStopProxy(JNIEnv *env, jobject thiz) {
    if (!g_proxy_running) return -1;
    shutdown(server_fd, SHUT_RDWR);
    g_proxy_running = 0;
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_eliteprime_app_data_remote_proxy_SecureStreamingManager_jniForceClose(JNIEnv *env, jobject thiz) {
    if (close(server_fd) == -1) return -1;
    g_proxy_running = 0;
    return 0;
}
