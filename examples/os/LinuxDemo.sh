#!/usr/bin/env bash
set -euo pipefail

if [[ $(id -u) == 0 ]]; then
    printf '%s\n' 'Run as a regular user: root bypasses the permission experiment.' >&2
    exit 1
fi
work_dir=$(mktemp -d /tmp/java-kb-linux.XXXXXX)
child_pid=''
cleanup() {
    if [[ -n $child_pid ]]; then
        kill -TERM "$child_pid" 2>/dev/null || true
        wait "$child_pid" 2>/dev/null || true
    fi
    chmod -R u+rwX "$work_dir"
    rm -rf -- "$work_dir"
}
trap cleanup EXIT

ready() {
    for attempt in {1..100}; do
        [[ ! -s $work_dir/ready ]] || return 0
        kill -0 "$child_pid" 2>/dev/null || { printf '%s\n' 'Child exited before ready' >&2; exit 1; }
        sleep 0.02
    done
    printf '%s\n' 'Child did not become ready' >&2
    exit 1
}

permissions() {
    mkdir "$work_dir/receipts"
    printf '%s\n' paid > "$work_dir/receipts/p42.txt"
    chmod 666 "$work_dir/receipts/p42.txt"
    chmod 600 "$work_dir/receipts"             # У каталога нет x: пройти к файлу нельзя.
    printf '%s\n' 'file mode=666, directory mode=600'
    if cat "$work_dir/receipts/p42.txt" >/dev/null 2>&1; then readable=true; else readable=false; fi
    printf 'read with missing directory x=%s\n' "$readable"
    chmod u+x "$work_dir/receipts"
    printf 'read after chmod u+x=%s\n' "$(cat "$work_dir/receipts/p42.txt")"
    chmod 400 "$work_dir/receipts/p42.txt"
    if [[ -w $work_dir/receipts/p42.txt ]]; then writable=true; else writable=false; fi
    printf 'file writable=%s\n' "$writable"
    rm -- "$work_dir/receipts/p42.txt" </dev/null  # Удаление имени проверяет w+x каталога.
    printf '%s\n' 'unlink with directory wx=true'
    (
        umask 027
        : > "$work_dir/new-file"
        mkdir "$work_dir/new-directory"
        printf 'umask 027: file=%s, directory=%s\n' \
            "$(stat -c '%a' "$work_dir/new-file")" "$(stat -c '%a' "$work_dir/new-directory")"
    )
}

pipeline() {
    printf '%s\n' 'INFO payment=p42' 'ERROR payment=p43 bank declined' > "$work_dir/app.log"
    set +e
    set +o pipefail
    grep -F 'payment=p99' "$work_dir/app.log" | wc -l > "$work_dir/count"
    snapshot=("$?" "${PIPESTATUS[@]}")          # Скопировали до следующей команды оболочки.
    read -r count < "$work_dir/count"
    printf 'no matches: count=%s, pipeline=%s, grep=%s\n' "$count" "${snapshot[0]}" "${snapshot[1]}"
    grep -F 'payment=p42' "$work_dir/missing.log" 2> "$work_dir/grep.err" | wc -l > "$work_dir/count"
    snapshot=("$?" "${PIPESTATUS[@]}")
    read -r count < "$work_dir/count"
    printf 'missing file without pipefail: count=%s, pipeline=%s, grep=%s\n' \
        "$count" "${snapshot[0]}" "${snapshot[1]}"
    set -o pipefail
    grep -F 'payment=p42' "$work_dir/missing.log" 2> "$work_dir/grep.err" | wc -l > "$work_dir/count"
    snapshot=("$?" "${PIPESTATUS[@]}")
    read -r count < "$work_dir/count"
    printf 'missing file with pipefail: count=%s, pipeline=%s, grep=%s\n' \
        "$count" "${snapshot[0]}" "${snapshot[1]}"
    set -e
    bash -c 'printf "%s\n" OUT; printf "%s\n" ERR >&2' > "$work_dir/both" 2>&1
    mapfile -t both < "$work_dir/both"
    printf '>file 2>&1: %s,%s\n' "${both[0]}" "${both[1]}"
    escaped=$(bash -c 'printf "%s\n" OUT; printf "%s\n" ERR >&2' 2>&1 > "$work_dir/only-out")
    read -r output < "$work_dir/only-out"
    printf '2>&1 >file: file=%s, escaped=%s\n' "$output" "$escaped"
}

service() {
    python3 - "$work_dir" <<'PY' &
import pathlib, signal, socket, sys
work = pathlib.Path(sys.argv[1])
log = (work / 'service.log').open('w')
listener = socket.socket()
listener.bind(('127.0.0.1', 0))
listener.listen(1)
def stop(signum, frame):
    print('INFO stopped by SIGTERM', file=log, flush=True)
    listener.close()
    raise SystemExit(0)
signal.signal(signal.SIGTERM, stop)
(work / 'ready').write_text(str(listener.getsockname()[1]) + '\n')
signal.pause()
PY
    child_pid=$!
    ready
    read -r port < "$work_dir/ready"
    if kill -0 "$child_pid"; then exists=true; else exists=false; fi
    printf 'kill -0 before TERM=%s\n' "$exists"
    command=$(ps -p "$child_pid" -o comm= | tr -d '[:space:]')
    printf 'ps command=%s\n' "$command"
    socket=$(ss -H -ltnp "( sport = :$port )" | awk '{print $1, $4}')
    socket=${socket//:$port/:<port>}             # Номер выбирает ОС; на странице показан маркер.
    printf 'ss=%s\n' "$socket"
    owner=$(lsof -nP -a -p "$child_pid" -iTCP -sTCP:LISTEN -F c | sed -n 's/^c//p')
    printf 'lsof command=%s\n' "$owner"
    kill -TERM "$child_pid"                      # Сигнал только собственному дочернему процессу.
    if wait "$child_pid"; then status=0; else status=$?; fi
    child_pid=''
    printf 'TERM exit=%s\n' "$status"
    printf 'log=%s\n' "$(cat "$work_dir/service.log")"
}

deleted_log() {
    python3 - "$work_dir" <<'PY' &
import pathlib, signal, sys
work = pathlib.Path(sys.argv[1])
with (work / 'app.log').open('wb') as log:
    log.write(b'x' * (4 * 1024 * 1024))
    log.flush()
    (work / 'ready').write_text(str(log.fileno()) + '\n')
    signal.pause()
PY
    child_pid=$!
    ready
    read -r held_fd < "$work_dir/ready"
    rm -- "$work_dir/app.log"
    if [[ -e $work_dir/app.log ]]; then exists=true; else exists=false; fi
    printf 'path after unlink exists=%s\n' "$exists"
    if lsof -nP -a -p "$child_pid" +L1 -F n | grep -Fq 'app.log (deleted)'; then held=true; else held=false; fi
    printf 'lsof reports deleted=%s\n' "$held"
    printf 'open descriptor bytes=%s\n' "$(stat -Lc '%s' "/proc/$child_pid/fd/$held_fd")"
    held_path=/proc/$child_pid/fd/$held_fd
    kill -TERM "$child_pid"
    wait "$child_pid" || true                   # Здесь стандартное действие SIGTERM, статус не 0.
    child_pid=''
    if [[ -e $held_path ]]; then exists=true; else exists=false; fi
    printf 'descriptor after process exit exists=%s\n' "$exists"
}

case ${1-} in
    permissions) permissions ;;
    pipeline) pipeline ;;
    service) service ;;
    deleted-log) deleted_log ;;
    *) printf '%s\n' 'Usage: bash LinuxDemo.sh permissions|pipeline|service|deleted-log' >&2; exit 1 ;;
esac
