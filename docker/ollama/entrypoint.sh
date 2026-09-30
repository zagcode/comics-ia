#!/bin/sh
# Sobe o servidor do Ollama e baixa os modelos de PULL_MODELS que ainda nao estao no volume.
set -eu

ollama serve &
server=$!
trap 'kill -TERM "$server" 2>/dev/null' TERM INT

until ollama list >/dev/null 2>&1; do
    sleep 1
done

# "ollama show" responde para modelos -cloud mesmo sem baixar; "ollama list" so mostra o que esta no volume.
installed() {
    case "$1" in
        *:*) name="$1" ;;
        *) name="$1:latest" ;;
    esac
    ollama list | awk 'NR > 1 { print $1 }' | grep -qxF "$name"
}

for model in ${PULL_MODELS:-}; do
    if installed "$model"; then
        echo "modelo $model ja esta no volume"
    elif ollama pull "$model" >/tmp/pull.log 2>&1; then
        echo "modelo $model baixado"
    else
        echo "falha ao baixar $model:" >&2
        tail -n 3 /tmp/pull.log >&2
        echo "modelos -cloud exigem login: docker compose exec ollama ollama signin" >&2
    fi
done

wait "$server"
