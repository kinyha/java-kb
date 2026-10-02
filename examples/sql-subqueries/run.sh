#!/usr/bin/env bash
set -euo pipefail
# Own ephemeral container, no host ports, no volumes, no existing database access.
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
IMAGE='postgres:17.11-alpine@sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24'
CONTAINER_ID=''
cleanup() {
  if [[ -n "$CONTAINER_ID" ]]; then docker rm -f "$CONTAINER_ID" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT
CONTAINER_ID=$(docker run --rm -d --network none --memory 256m \
  --tmpfs /var/lib/postgresql/data:rw \
  -e POSTGRES_PASSWORD=local-demo-only -e POSTGRES_DB=kb "$IMAGE")
ready=false
for attempt in {1..30}; do
  # Wait for the final server, not the temporary initdb server.
  if docker exec "$CONTAINER_ID" sh -c '[ "$(cat /proc/1/comm)" = postgres ] && pg_isready -U postgres -d kb' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  docker logs "$CONTAINER_ID" >&2
  exit 1
fi
docker exec -i "$CONTAINER_ID" psql -X -q -U postgres -d kb \
  -v ON_ERROR_STOP=1 < "$SCRIPT_DIR/demo.sql"
