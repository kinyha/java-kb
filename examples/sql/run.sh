#!/usr/bin/env bash
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "$0")" && pwd)
container_id=$(docker run --detach --rm \
  -e POSTGRES_HOST_AUTH_METHOD=trust postgres:17.11-alpine)
cleanup() { docker stop --time 1 "$container_id" >/dev/null 2>&1 || true; }
trap cleanup EXIT
ready=false
for attempt in {1..60}; do
  if docker exec "$container_id" pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 0.2
done
if [[ "$ready" != true ]]; then
  docker logs "$container_id" >&2
  exit 1
fi
docker exec "$container_id" psql -X -h 127.0.0.1 -U postgres -At -c 'SHOW server_version'
docker exec -i "$container_id" psql -X -h 127.0.0.1 -U postgres -v ON_ERROR_STOP=1 \
  -q -f - < "$script_dir/Queries.sql"
