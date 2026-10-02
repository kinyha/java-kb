#!/usr/bin/env bash
set -euo pipefail

demo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
sql_join_container=$(docker run --rm -d --network none \
  -e POSTGRES_HOST_AUTH_METHOD=trust \
  -e POSTGRES_DB=joins_demo \
  postgres:17.11-alpine)

cleanup() {
  docker stop "$sql_join_container" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# TCP готов только у основного сервера, не у временного сервера initdb.
ready=false
for attempt in {1..30}; do
  if docker exec "$sql_join_container" pg_isready -h 127.0.0.1 -U postgres -d joins_demo >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  docker logs "$sql_join_container" >&2
  exit 1
fi

# psql из того же образа; порты и постоянные volume не создаются.
docker exec -i "$sql_join_container" \
  psql -X -q -v ON_ERROR_STOP=1 -U postgres -d joins_demo < "$demo_dir/demo.sql"
