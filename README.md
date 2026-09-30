# comics-ia

Chatbot especialista em histórias em quadrinhos. Você pergunta sobre personagens, autores, editoras ou a história das HQs e ele responde, com alguma curiosidade sobre o tema. Perguntas sobre outros assuntos ele recusa.

É uma API REST em Java 21 com Quarkus 3.39. O LangChain4j faz a ponte com o modelo de linguagem, que roda pelo Ollama.

> Projeto em andamento. O RAG (respostas apoiadas em documentos próprios) já está configurado, mas ainda faltam os documentos sobre quadrinhos. Veja [Estado do RAG](#estado-do-rag).

## Como funciona

- `comicAssistant` é uma interface anotada com `@RegisterAiService`, e o Quarkus gera a implementação. A `@SystemMessage` define o papel do assistente e a regra de só falar de quadrinhos.
- `comicsResource` expõe `POST /comics`. A pergunta vai no corpo, em texto puro, e a resposta volta em texto puro.
- O modelo é o `nemotron-3-nano:30b-cloud`, com temperatura 0 e timeout de 120 s. Requisições e respostas do modelo aparecem no log.

## Requisitos

- JDK 21
- [Ollama](https://ollama.com) rodando em `127.0.0.1:11434`, instalado na máquina ou pelo Docker (veja [Ollama com Docker](#ollama-com-docker)).
- Acesso ao modelo `nemotron-3-nano:30b-cloud`. Modelos com sufixo `-cloud` rodam nos servidores do Ollama e pedem login com `ollama signin`. Para rodar tudo na sua máquina, troque `quarkus.langchain4j.ollama.chat-model.model-name` no `application.properties` por um modelo baixado com `ollama pull`.

## Ollama com Docker

O `compose.yaml` sobe um Ollama com os dois modelos que o projeto usa: `nomic-embed-text`, que gera os embeddings do RAG, e `nemotron-3-nano:30b-cloud`, que responde às perguntas. O container baixa os modelos na primeira subida e os guarda no volume `ollama-data`, junto com o login, então as subidas seguintes não baixam nada.

```shell
docker compose up -d --build
docker compose exec ollama ollama signin
```

O `signin` só precisa ser feito uma vez. Ele mostra um link; abra no navegador para ligar o container à sua conta do ollama.com. Sem login, o modelo `-cloud` responde `Unauthorized`. O embedding roda no próprio container e funciona sem login.

Para acompanhar os downloads, use `docker compose logs -f ollama`.

O `compose.yaml` aceita estas variáveis:

| Variável | Padrão | Uso |
|---|---|---|
| `OLLAMA_PORT` | `11434` | porta do Ollama no host |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | modelo de embedding |
| `OLLAMA_CHAT_MODEL` | `nemotron-3-nano:30b-cloud` | modelo de chat |

Se trocar um modelo, troque também `quarkus.langchain4j.ollama.embedding-model.model-name` ou `quarkus.langchain4j.ollama.chat-model.model-name` no `application.properties`.

Se a porta 11434 já estiver ocupada por outro Ollama, suba o container em outra porta e aponte o Quarkus para ela:

```shell
OLLAMA_PORT=11435 docker compose up -d --build          # bash
$env:OLLAMA_PORT=11435; docker compose up -d --build    # PowerShell
```

```properties
quarkus.langchain4j.ollama.base-url=http://localhost:11435
```

A imagem tem cerca de 9 GB, quase tudo da imagem oficial `ollama/ollama` e das bibliotecas de GPU que vêm nela. O bloco de GPU do `compose.yaml` está comentado porque só faz diferença para modelos locais; o `-cloud` roda nos servidores do Ollama.

## Rodando

```shell
./mvnw quarkus:dev
```

A Dev UI do Quarkus fica em <http://localhost:8080/q/dev/>. Para testar a API:

```shell
curl -X POST http://localhost:8080/comics \
  -H "Content-Type: text/plain" \
  -d "Quem criou o Homem-Aranha?"
```

## Estado do RAG

- O `application.properties` aponta o Easy RAG para `src/main/resources/rag`, e essa pasta ainda não existe no repositório. Crie a pasta com os documentos sobre quadrinhos (TXT, PDF, ODT e afins) antes de rodar, ou mude o caminho em `quarkus.langchain4j.easy-rag.path`.
- `easy-rag-catalog/` tem quatro documentos sobre contas bancárias, do exemplo de Easy RAG do Quarkus LangChain4j, e `easy-rag-embeddings.json` guarda os embeddings gerados a partir deles. Nada disso trata de quadrinhos, e os dois podem sair quando a base de HQs entrar.

## Empacotando

```shell
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Os Dockerfiles gerados pelo Quarkus (JVM, nativo e nativo micro) estão em `src/main/docker/`.
