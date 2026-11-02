#!/bin/sh
# Local models of docs/09-infrastructure.md §2.5: guard classifier, PII masking and embeddings.
set -eu

until ollama list > /dev/null 2>&1; do sleep 2; done
for model in llama-guard3:1b qwen2.5:1.5b nomic-embed-text; do
    ollama pull "$model"
done
