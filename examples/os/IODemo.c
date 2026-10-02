#define _GNU_SOURCE
#include <errno.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/sendfile.h>
#include <sys/socket.h>
#include <unistd.h>

static void require(int ok, const char *message) {
    if (!ok) { perror(message); exit(1); }
}

static void read_exact(int fd, char *data, size_t size) {
    size_t offset = 0;
    while (offset < size) {
        ssize_t count = read(fd, data + offset, size - offset);
        if (count == -1 && errno == EINTR) continue;
        require(count > 0, "read exact");
        offset += (size_t) count;
    }
    data[size] = '\0';
}

static int receipt_file(const char *text) {
    char name[] = "/tmp/java-kb-io.XXXXXX";
    int fd = mkstemp(name);
    require(fd != -1, "mkstemp");
    require(unlink(name) == 0, "unlink");
    size_t length = strlen(text), offset = 0;
    while (offset < length) {
        ssize_t count = write(fd, text + offset, length - offset);
        if (count == -1 && errno == EINTR) continue;
        require(count > 0, "write receipt");
        offset += (size_t) count;
    }
    require(lseek(fd, 0, SEEK_SET) == 0, "rewind");
    return fd;
}

static void descriptors(void) {
    int original = receipt_file("paid");
    int duplicate = dup(original);
    require(duplicate != -1, "dup");
    printf("different descriptor=%s\n", original != duplicate ? "true" : "false");
    char first[3], second[3], positioned[3] = {0};
    read_exact(original, first, 2);
    read_exact(duplicate, second, 2);       // dup ведёт к той же open file description и позиции.
    require(pread(duplicate, positioned, 2, 0) == 2, "pread");
    off_t position = lseek(original, 0, SEEK_CUR);
    require(position != -1, "position");
    printf("read original=%s\n", first);
    printf("read duplicate=%s\n", second);
    printf("pread offset 0=%s\n", positioned);
    printf("shared position=%jd\n", (intmax_t) position);
    close(duplicate);
    close(original);
}

static ssize_t read_once(int fd, char *buffer, size_t bytes) {
    ssize_t result;
    do { result = read(fd, buffer, bytes); } while (result == -1 && errno == EINTR);
    return result;
}

static const char *empty_read(int fd) {
    char byte;
    ssize_t count = read_once(fd, &byte, 1);
    require(count == -1 && (errno == EAGAIN || errno == EWOULDBLOCK), "expected EAGAIN");
    return "EAGAIN";
}

static int next_event(int watcher) {
    struct epoll_event event;
    int count;
    do { count = epoll_wait(watcher, &event, 1, 0); } while (count == -1 && errno == EINTR);
    require(count >= 0, "epoll_wait");
    return count;                          // timeout=0: пустой список не подвешивает опыт.
}

static void readiness(int edge) {
    const char *mode = edge ? "ET" : "LT";
    int sockets[2];
    require(socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, sockets) == 0, "socketpair");
    int watcher = epoll_create1(EPOLL_CLOEXEC);
    require(watcher != -1, "epoll_create1");
    struct epoll_event interest = {.events = EPOLLIN | (edge ? EPOLLET : 0), .data.fd = sockets[0]};
    require(epoll_ctl(watcher, EPOLL_CTL_ADD, sockets[0], &interest) == 0, "epoll_ctl");
    printf("%s empty read=%s\n", mode, empty_read(sockets[0]));
    require(write(sockets[1], "paid", 4) == 4, "bank reply");
    int first_event = next_event(watcher);
    char first[3], remaining[3];
    read_exact(sockets[0], first, 2);       // Намеренно забрали только половину ответа.
    printf("%s first event=%d, first read=%s\n", mode, first_event, first);
    printf("%s event with unread bytes=%d\n", mode, next_event(watcher));
    read_exact(sockets[0], remaining, 2);   // Дочитываем даже в ET, где нового события не было.
    const char *drained = empty_read(sockets[0]);
    printf("%s remaining read=%s, drained=%s\n", mode, remaining, drained);
    close(sockets[1]);
    int close_event = next_event(watcher);
    char byte;
    ssize_t final_read = read_once(sockets[0], &byte, 1);
    require(final_read == 0, "expected EOF");
    printf("%s event after close=%d, read=EOF\n", mode, close_event);
    close(watcher);
    close(sockets[0]);
}

static void transfer(void) {
    int file = receipt_file("paid-42");
    int sockets[2];
    require(socketpair(AF_UNIX, SOCK_STREAM, 0, sockets) == 0, "socketpair");
    off_t offset = 0;
    ssize_t first;
    do { first = sendfile(sockets[0], file, &offset, 4); } while (first == -1 && errno == EINTR);
    require(first > 0 && first <= 4, "first sendfile");
    off_t file_position = lseek(file, 0, SEEK_CUR);
    require(file_position != -1, "file position");
    printf("first sent=%zd, explicit offset=%jd, file position=%jd\n",
           first, (intmax_t) offset, (intmax_t) file_position);
    char head[5];
    read_exact(sockets[1], head, (size_t) first);
    printf("receiver first=%s\n", head);
    ssize_t resumed = 0;
    while (offset < 7) {
        ssize_t count = sendfile(sockets[0], file, &offset, (size_t) (7 - offset));
        if (count == -1 && errno == EINTR) continue;
        require(count > 0, "resume sendfile");
        resumed += count;                 // Продолжаем с обновлённого offset, не отправляем начало заново.
    }
    char tail[8];
    read_exact(sockets[1], tail, (size_t) resumed);
    printf("resumed sent=%zd, explicit offset=%jd\n", resumed, (intmax_t) offset);
    printf("receiver tail=%s\n", tail);
    close(sockets[0]);
    close(sockets[1]);
    close(file);
}

int main(int argc, char **argv) {
    if (argc != 2) { fprintf(stderr, "Usage: IODemo descriptors|events|sendfile\n"); return 1; }
    if (strcmp(argv[1], "descriptors") == 0) descriptors();
    else if (strcmp(argv[1], "events") == 0) { readiness(0); readiness(1); }
    else if (strcmp(argv[1], "sendfile") == 0) transfer();
    else { fprintf(stderr, "Unknown mode\n"); return 1; }
    return 0;
}
