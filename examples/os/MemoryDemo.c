#define _GNU_SOURCE
#include <errno.h>
#include <inttypes.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <unistd.h>

typedef struct { unsigned long rss, pss; } Stats;
typedef struct { char *reservation; volatile char *data; size_t bytes, page; } Mapping;

static void require(int ok, const char *message) {
    if (!ok) { perror(message); exit(1); }
}

static Mapping mapping(size_t pages) {
    long page = sysconf(_SC_PAGESIZE);
    require(page > 0, "page size");
    size_t bytes = pages * (size_t) page;
    char *base = mmap(NULL, bytes + 2 * page, PROT_NONE,
                     MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    require(base != MAP_FAILED, "mmap");
    char *data = base + page;
    require(mprotect(data, bytes, PROT_READ | PROT_WRITE) == 0, "mprotect");
    require(madvise(data, bytes, MADV_NOHUGEPAGE) == 0, "madvise");
    // Две защитные страницы удерживают измеряемый диапазон отдельным VMA.
    return (Mapping) {base, data, bytes, (size_t) page};
}

static void release(Mapping m) {
    require(munmap(m.reservation, m.bytes + 2 * m.page) == 0, "munmap");
}

static Stats stats(volatile char *address) {
    FILE *file = fopen("/proc/self/smaps", "r");
    require(file != NULL, "smaps");
    uintptr_t begin, end;
    unsigned long number;
    int target = 0, found = 0;
    Stats result = {0};
    char line[512];
    while (fgets(line, sizeof line, file)) {
        if (sscanf(line, "%" SCNxPTR "-%" SCNxPTR, &begin, &end) == 2)
            target = begin == (uintptr_t) address;
        else if (target && sscanf(line, "Rss: %lu kB", &number) == 1) {
            result.rss = number; found = 1;
        } else if (target && sscanf(line, "Pss: %lu kB", &number) == 1) result.pss = number;
    }
    require(fclose(file) == 0, "fclose");
    require(found, "mapping absent from smaps");
    return result;
}

static struct rusage usage(void) {
    struct rusage result;
    require(getrusage(RUSAGE_SELF, &result) == 0, "getrusage");
    return result;
}

static void resident(void) {
    Mapping m = mapping(1024);
    printf("page_bytes=%zu, mapped_bytes=%zu\n", m.page, m.bytes);
    printf("reserved rss_kib=%lu\n", stats(m.data).rss);
    struct rusage before = usage();
    unsigned long sum = 0;
    for (size_t offset = 0; offset < m.bytes; offset += m.page) sum += m.data[offset];
    struct rusage after_read = usage();
    printf("read sum=%lu, rss_kib=%lu\n", sum, stats(m.data).rss);
    printf("read minor_faults_increased=%s, major_faults_delta=%ld\n",
           after_read.ru_minflt > before.ru_minflt ? "true" : "false",
           after_read.ru_majflt - before.ru_majflt);
    before = usage();
    for (size_t offset = 0; offset < m.bytes; offset += m.page) m.data[offset] = 1;
    struct rusage after_write = usage();
    printf("write rss_kib=%lu, minor_faults_increased=%s\n", stats(m.data).rss,
           after_write.ru_minflt > before.ru_minflt ? "true" : "false");
    release(m);
}

static void reap(pid_t child, int *status) {
    pid_t result;
    do { result = waitpid(child, status, 0); } while (result == -1 && errno == EINTR);
    require(result == child, "waitpid");
}

static void cow(void) {
    Mapping m = mapping(1);
    m.data[0] = 41;
    int ends[2];
    require(pipe(ends) == 0, "pipe");
    pid_t child = fork();
    require(child != -1, "fork");
    if (child == 0) {
        close(ends[0]);
        Stats shared = stats(m.data);
        printf("child before write: rss_kib=%lu, pss_kib=%lu\n", shared.rss, shared.pss);
        m.data[0] = 99;                    // COW: этот же виртуальный адрес меняет отображение.
        Stats private = stats(m.data);
        printf("child after write: rss_kib=%lu, pss_kib=%lu, value=%d\n",
               private.rss, private.pss, m.data[0]);
        uintptr_t address = (uintptr_t) m.data;
        require(write(ends[1], &address, sizeof address) == sizeof address, "address write");
        require(fflush(stdout) == 0, "fflush");
        _exit(0);
    }
    close(ends[1]);
    int status;
    reap(child, &status);
    require(WIFEXITED(status) && WEXITSTATUS(status) == 0, "child status");
    uintptr_t child_address;
    require(read(ends[0], &child_address, sizeof child_address) == sizeof child_address, "address read");
    close(ends[0]);
    Stats private = stats(m.data);
    printf("parent after wait: rss_kib=%lu, pss_kib=%lu, value=%d\n",
           private.rss, private.pss, m.data[0]);
    printf("same virtual address=%s\n", child_address == (uintptr_t) m.data ? "true" : "false");
    release(m);
}

static void protect(void) {
    Mapping m = mapping(1);
    m.data[0] = 41;
    pid_t child = fork();
    require(child != -1, "fork");
    if (child == 0) {
        struct rlimit no_core = {0, 0};
        require(setrlimit(RLIMIT_CORE, &no_core) == 0, "no core");
        require(mprotect((void *) m.data, m.bytes, PROT_READ) == 0, "protect read only");
        m.data[0] = 99;                    // VMA запретил запись: это уже не разрешимый COW.
        _exit(2);
    }
    int status;
    reap(child, &status);
    printf("child terminated by SIGSEGV=%s\n",
           WIFSIGNALED(status) && WTERMSIG(status) == SIGSEGV ? "true" : "false");
    require(WIFSIGNALED(status) && WTERMSIG(status) == SIGSEGV, "expected SIGSEGV");
    printf("parent value=%d, alive=true\n", m.data[0]);
    release(m);
}

int main(int argc, char **argv) {
    if (argc != 2) { fprintf(stderr, "Usage: MemoryDemo resident|cow|protect\n"); return 1; }
    if (strcmp(argv[1], "resident") == 0) resident();
    else if (strcmp(argv[1], "cow") == 0) cow();
    else if (strcmp(argv[1], "protect") == 0) protect();
    else { fprintf(stderr, "Unknown mode\n"); return 1; }
    return 0;
}
