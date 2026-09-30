# mesma versao do Ollama usado no desenvolvimento; atualize de proposito, nao por acaso
FROM ollama/ollama:0.34.2

# Modelos baixados na primeira subida; mantenha em sincronia com o application.properties
# (quarkus.langchain4j.ollama.embedding-model.model-name e chat-model.model-name).
# Nao use OLLAMA_MODELS para isso: no Ollama essa variavel e a pasta onde os modelos ficam.
ENV OLLAMA_HOST=0.0.0.0:11434 \
    PULL_MODELS="nomic-embed-text nemotron-3-nano:30b-cloud"

COPY entrypoint.sh /usr/local/bin/comics-ollama-entrypoint.sh
# Remove CR caso o script tenha sido salvo com fim de linha do Windows
RUN sed -i 's/\r$//' /usr/local/bin/comics-ollama-entrypoint.sh \
    && chmod +x /usr/local/bin/comics-ollama-entrypoint.sh

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=6 CMD ollama list >/dev/null 2>&1 || exit 1

ENTRYPOINT ["/usr/local/bin/comics-ollama-entrypoint.sh"]
