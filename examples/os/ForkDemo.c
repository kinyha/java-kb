#define _POSIX_C_SOURCE 200809L
#include <errno.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

static void require(int condition, const char *operation) {
    if (!condition) { perror(operation); exit(1); }
}

static void reap(pid_t child) {
    int status;
    pid_t result;
    do { result = waitpid(child, &status, 0); } while (result == -1 && errno == EINTR);
    require(result == child, "waitpid");
    require(WIFEXITED(status) && WEXITSTATUS(status) == 0, "child status");
}

static void copy(void) {
    int captured = 41;
    char name[] = "/tmp/kb-fork-XXXXXX";
    int fd = mkstemp(name);
    require(fd != -1, "mkstemp");
    require(unlink(name) == 0, "unlink"); // Имя больше не нужно: открытый файл остаётся.
    require(write(fd, "ABC", 3) == 3, "write");
    require(lseek(fd, 0, SEEK_SET) == 0, "lseek");

    pid_t child = fork();
    require(child != -1, "fork");
    if (child == 0) {
        captured = 99;                    // Меняется память ребёнка, не родителя.
        char letter;
        require(read(fd, &letter, 1) == 1, "child read");
        printf("child captured=%d, file=%c\n", captured, letter);
        require(fflush(stdout) == 0, "fflush");
        _exit(0);
    }
    reap(child);                           // Порядок чтения теперь определён.
    char letter;
    require(read(fd, &letter, 1) == 1, "parent read");
    printf("parent captured=%d, file=%c\n", captured, letter);
    require(close(fd) == 0, "close");
}

static void eof(void) {
    int ends[2];
    require(pipe(ends) == 0, "pipe");
    pid_t child = fork();
    require(child != -1, "fork");
    if (child == 0) {
        require(close(ends[0]) == 0, "child close read");
        require(write(ends[1], "paid", 4) == 4, "child write");
        require(close(ends[1]) == 0, "child close write");
        _exit(0);
    }
    reap(child);
    char text[5] = {0};
    require(read(ends[0], text, 4) == 4, "parent read");
    printf("child exited=true, message=%s\n", text);

    struct pollfd ready = {.fd = ends[0], .events = POLLIN};
    int count = poll(&ready, 1, 0);          // Опрос без ожидания: данные уже прочитаны.
    require(count >= 0, "poll");
    printf("EOF while parent owns writer=%s\n", count == 0 ? "false" : "true");
    require(count == 0, "unexpected pipe event");

    require(close(ends[1]) == 0, "parent close write"); // Закрыли последнюю копию записи.
    char next;
    ssize_t bytes = read(ends[0], &next, 1);
    require(bytes == 0, "expected EOF");
    printf("EOF after closing parent writer=true\n");
    require(close(ends[0]) == 0, "parent close read");
}

int main(int argc, char **argv) {
    if (argc != 2) { fprintf(stderr, "Usage: ForkDemo copy|eof\n"); return 1; }
    if (strcmp(argv[1], "copy") == 0) copy();
    else if (strcmp(argv[1], "eof") == 0) eof();
    else { fprintf(stderr, "Unknown mode\n"); return 1; }
    return 0;
}
