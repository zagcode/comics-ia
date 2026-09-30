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
- [Ollama](https://ollama.com) rodando em `127.0.0.1:11434`
- Acesso ao modelo `nemotron-3-nano:30b-cloud`. Modelos com sufixo `-cloud` rodam nos servidores do Ollama e pedem login com `ollama signin`. Para rodar tudo na sua máquina, troque `quarkus.langchain4j.ollama.chat-model.model-name` no `application.properties` por um modelo baixado com `ollama pull`.

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
