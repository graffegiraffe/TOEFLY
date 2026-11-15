#!/bin/sh
# Local models of docs/09-infrastructure.md §2.5: guard classifier, PII masking, embeddings and the small
# reserve chat model of ai-gateway (docs/06-ai-agent.md §2.4). gpt-oss:20b (about 13 GB) is pulled by hand.
set -eu

until ollama list > /dev/null 2>&1; do sleep 2; done
for model in llama-guard3:1b qwen2.5:1.5b nomic-embed-text llama3.2:3b; do
    ollama pull "$model"
done
