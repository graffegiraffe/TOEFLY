#!/bin/sh
# Local replacement of the R2 buckets (docs/00-README.md §9.3).
set -eu

: "${MASTER:?}"

for bucket in toefly-recordings toefly-audio toefly-materials toefly-content toefly-exports toefly-seed; do
    echo "s3.bucket.create -name $bucket" | weed shell -master="$MASTER" > /dev/null
    echo "bucket $bucket"
done
