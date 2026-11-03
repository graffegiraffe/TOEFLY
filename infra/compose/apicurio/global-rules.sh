#!/bin/sh
# Global registry rules: BACKWARD_TRANSITIVE compatibility and full validity (docs/05-api-contracts.md §8.3).
set -eu

: "${REGISTRY_URL:?}"

rule() { # rule <type> <config>
    if curl -fs "$REGISTRY_URL/admin/rules/$1" > /dev/null; then
        curl -fsS -X PUT -H 'Content-Type: application/json' \
            -d "{\"ruleType\":\"$1\",\"config\":\"$2\"}" "$REGISTRY_URL/admin/rules/$1" > /dev/null
    else
        curl -fsS -X POST -H 'Content-Type: application/json' \
            -d "{\"ruleType\":\"$1\",\"config\":\"$2\"}" "$REGISTRY_URL/admin/rules" > /dev/null
    fi
    echo "rule $1=$2"
}

rule COMPATIBILITY BACKWARD_TRANSITIVE
rule VALIDITY FULL
