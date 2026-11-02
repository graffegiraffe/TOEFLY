#!/bin/sh
# Registers (or updates) one Debezium outbox connector per file in connectors/; tools/new-service.sh adds the file.
# A connector is accepted only after its service ran Flyway (database, outbox table and publication exist), so a
# rejected connector is reported and skipped; run this container again after the service's first start.
set -u

: "${CONNECT_URL:?}" "${DEBEZIUM_PASSWORD:?}"

for file in /connectors/*.json; do
    [ -e "$file" ] || { echo "no connectors yet"; exit 0; }
    name=$(basename "$file" .json)
    response=$(sed "s/\${DEBEZIUM_PASSWORD}/$DEBEZIUM_PASSWORD/" "$file" \
        | curl -sS -w '\n%{http_code}' -X PUT -H 'Content-Type: application/json' --data-binary @- \
            "$CONNECT_URL/connectors/$name/config")
    status=$(printf '%s' "$response" | tail -n 1)
    case "$status" in
        200|201) echo "connector $name" ;;
        *) echo "skipped $name ($status): $(printf '%s' "$response" | sed '$d' | cut -c 1-300)" ;;
    esac
done
