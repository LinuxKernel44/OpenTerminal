// PTY helpers used by the Shizuku user service (runs as the shell user).
#define _GNU_SOURCE
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

static void throw_io(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/io/IOException");
    if (cls) (*env)->ThrowNew(env, cls, msg);
}

static void set_size(int fd, int rows, int cols) {
    struct winsize ws = {.ws_row = (unsigned short) rows, .ws_col = (unsigned short) cols};
    ioctl(fd, TIOCSWINSZ, &ws);
}

// Returns {masterFd, pid}.
JNIEXPORT jintArray JNICALL
Java_com_openterminal_shell_Pty_nativeOpen(JNIEnv *env, jclass cls, jstring jshell, jstring jcwd,
                                           jobjectArray jenv, jint rows, jint cols) {
    int master = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (master < 0) { throw_io(env, "open /dev/ptmx failed"); return NULL; }
    char slave[64];
    if (grantpt(master) || unlockpt(master) || ptsname_r(master, slave, sizeof(slave))) {
        close(master);
        throw_io(env, "pty setup failed");
        return NULL;
    }
    struct termios tio;
    tcgetattr(master, &tio);
    tio.c_iflag |= IUTF8;
    tio.c_iflag &= ~(IXON | IXOFF);
    tcsetattr(master, TCSANOW, &tio);
    set_size(master, rows, cols);

    const char *shell = (*env)->GetStringUTFChars(env, jshell, NULL);
    const char *cwd = (*env)->GetStringUTFChars(env, jcwd, NULL);
    jsize n = (*env)->GetArrayLength(env, jenv);
    char **envp = calloc((size_t) n + 1, sizeof(char *));
    for (jsize i = 0; i < n; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, jenv, i);
        const char *c = (*env)->GetStringUTFChars(env, s, NULL);
        envp[i] = strdup(c);
        (*env)->ReleaseStringUTFChars(env, s, c);
    }

    pid_t pid = fork();
    if (pid < 0) {
        close(master);
        throw_io(env, "fork failed");
        return NULL;
    }
    if (pid == 0) {
        setsid();
        int s = open(slave, O_RDWR);
        ioctl(s, TIOCSCTTY, 0);
        dup2(s, 0); dup2(s, 1); dup2(s, 2);
        if (s > 2) close(s);
        // Close every inherited descriptor except stdio.
        for (int fd = 3; fd < 1024; fd++) close(fd);
        if (chdir(cwd) != 0) chdir("/");
        char *argv[] = {(char *) shell, "-l", NULL};
        // `sh -l` is not supported everywhere; fall back to plain invocation.
        argv[1] = NULL;
        execve(shell, argv, envp);
        _exit(127);
    }

    for (jsize i = 0; i < n; i++) free(envp[i]);
    free(envp);
    (*env)->ReleaseStringUTFChars(env, jshell, shell);
    (*env)->ReleaseStringUTFChars(env, jcwd, cwd);

    jintArray out = (*env)->NewIntArray(env, 2);
    jint v[2] = {master, pid};
    (*env)->SetIntArrayRegion(env, out, 0, 2, v);
    return out;
}

JNIEXPORT void JNICALL
Java_com_openterminal_shell_Pty_nativeSetSize(JNIEnv *env, jclass cls, jint fd, jint rows, jint cols) {
    set_size(fd, rows, cols);
}

// Blocks until the child exits; returns its exit status (or 128+signal).
JNIEXPORT jint JNICALL
Java_com_openterminal_shell_Pty_nativeWaitFor(JNIEnv *env, jclass cls, jint pid) {
    int status = 0;
    while (waitpid(pid, &status, 0) < 0) {
        if (errno != EINTR) return -1;
    }
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_com_openterminal_shell_Pty_nativeKill(JNIEnv *env, jclass cls, jint pid) {
    kill(-pid, SIGHUP);
    kill(pid, SIGHUP);
}
