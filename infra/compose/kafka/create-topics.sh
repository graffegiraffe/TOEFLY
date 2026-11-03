#!/bin/sh
# Creates every topic of infra/k8s/kafka/topics.yaml with its .retry and .dlq pair (docs/09-infrastructure.md §5.3).
# One kafka-topics.sh call per topic starts a JVM, so the calls run eight at a time.
set -eu

: "${BOOTSTRAP_SERVER:?}" "${TOPICS_FILE:?}"
KT=/opt/kafka/bin/kafka-topics.sh

value() { # value <line> <key> <default>
    v=$(printf '%s' "$1" | sed -n "s/.*$2: \([0-9][0-9]*\).*/\1/p")
    printf '%s' "${v:-$3}"
}

grep -E '^ *- \{ *name: ' "$TOPICS_FILE" | while read -r line; do
    name=$(printf '%s' "$line" | sed -n 's/.*name: \([a-z0-9.-]*\).*/\1/p')
    echo "$name 3 $(value "$line" retentionMs 604800000)"
    echo "$name.retry 3 259200000"
    echo "$name.dlq 1 $(value "$line" dlqRetentionMs 2592000000)"
done | xargs -P 8 -n 3 sh -c '
    "$0" --bootstrap-server "$1" --create --if-not-exists --topic "$2" --partitions "$3" --replication-factor 1 \
        --config "retention.ms=$4" --config cleanup.policy=delete --config min.insync.replicas=1 > /dev/null \
        && echo "topic $2"' "$KT" "$BOOTSTRAP_SERVER"
