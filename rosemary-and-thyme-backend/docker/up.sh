#!/usr/bin/env sh
set -eu

compose_file="$(dirname "$0")/docker-compose.yaml"
docker compose -f "$compose_file" up -d

printf 'Postgres: %s\n' "$(docker compose -f "$compose_file" port postgres 5432)"
printf 'pgAdmin:  http://%s\n' "$(docker compose -f "$compose_file" port pgadmin 80 | sed 's/0.0.0.0/localhost/; s/\[::\]/localhost/')"
