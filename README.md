# comics-ia

Chatbot especialista em histórias em quadrinhos, com interface em estilo de gibi. Você pergunta sobre heróis, vilões, autores, editoras ou a história das HQs e ele responde com uma curiosidade sobre o tema. Perguntas sobre outros assuntos ele recusa.

É feito em Java 21 com Quarkus 3.39. O LangChain4j faz a ponte com os modelos, que rodam pelo Ollama: `nemotron-3-nano:30b-cloud` para as respostas e `nomic-embed-text` para os embeddings do RAG.

## Como funciona

- A interface (`src/main/resources/META-INF/resources/`) mostra a conversa em quadros de gibi e sugere perguntas para começar. Cada carregamento da página abre uma conversa nova.
- `comicsResource` expõe `POST /comics`. A pergunta vai no corpo, em texto puro, com o id da conversa no cabeçalho `X-Conversation-Id`, e a resposta volta em texto puro.
- `comicAssistant` é o assistente (`@RegisterAiService`). A `@SystemMessage` define o papel, a regra de só falar de quadrinhos e quando chamar a ferramenta. O modelo roda com temperatura 0 e com o raciocínio ligado (`think=true`): sem ele as respostas saem duas vezes mais rápidas, mas o modelo deixa de recusar assuntos fora de HQ e de chamar a ferramenta.
- Quando a pergunta cita um personagem, o modelo chama `searchHero` (`SuperHeroTools`), que busca a ficha na [Superhero API](https://superheroapi.com): nome verdadeiro, editora, primeira aparição, atributos, grupos e parentes. A busca tenta o nome em inglês que o modelo mandou e o nome da pergunta. Se nenhum achar nada, o `HeroNameTranslator` traduz o nome (Homem-Aranha vira Spider-Man) e tenta de novo.
- Cada ficha consultada é salva em `data/herois/` (`HeroArchive`). Ela serve de cache para a próxima pergunta sobre o mesmo personagem e entra no contexto sempre que a pergunta cita o nome dele, em português ou no original.
- O RAG (`ComicsRetrievalAugmentor`) junta duas fontes. Os textos de `src/main/resources/rag/` entram por similaridade de embedding, com score mínimo de 0,81, calibrado para o `nomic-embed-text`. As fichas de personagens entram pelo nome: por embedding, uma ficha parece com qualquer outra, e a do Kang aparecia em perguntas sobre o Bane.
- O `RagIndexer` indexa os textos do RAG na subida e a cada hora, e só refaz os embeddings se algum arquivo mudou. O índice novo entra antes de o antigo sair, então as perguntas nunca ficam sem RAG.
- Cada conversa guarda as últimas 20 mensagens (`Conversations`). Conversas paradas há mais de 30 minutos têm a memória apagada.

## Requisitos

- JDK 21
- [Ollama](https://ollama.com) rodando em `127.0.0.1:11434`, instalado na máquina ou pelo Docker (veja [Ollama com Docker](#ollama-com-docker)).
- Acesso ao modelo `nemotron-3-nano:30b-cloud`. Modelos com sufixo `-cloud` rodam nos servidores do Ollama e pedem login com `ollama signin`. Para rodar tudo na sua máquina, troque `quarkus.langchain4j.ollama.chat-model.model-name` no `application.properties` por um modelo baixado com `ollama pull`.
- Um token da [Superhero API](https://superheroapi.com), gerado no próprio site.

## Configuração local

O `src/main/resources/application.properties` fica fora do git, porque guarda o token da Superhero API e ajustes da sua máquina. O repositório traz o modelo, `application.properties.example`, com todos os valores comentados. Antes de rodar pela primeira vez, copie o modelo:

```shell
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Depois troque `seu-token-aqui` pelo seu token, ou deixe o arquivo como está e passe o token pela variável de ambiente `SUPERHERO_API_TOKEN`:

```shell
SUPERHERO_API_TOKEN=seu-token ./mvnw quarkus:dev                      # bash
$env:SUPERHERO_API_TOKEN="seu-token"; ./mvnw quarkus:dev              # PowerShell
```

No mesmo arquivo dá para mudar a porta do Ollama, a porta do modo dev e os modelos. As fichas salvas em `data/` também ficam fora do git: cada máquina monta o próprio cache conforme as perguntas.

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

A interface abre em <http://localhost:8080/> e a Dev UI do Quarkus em <http://localhost:8080/q/dev/>. Para testar a API direto:

```shell
curl -X POST http://localhost:8080/comics \
  -H "Content-Type: text/plain" \
  -d "Quem criou o Homem-Aranha?"
```

## Empacotando

```shell
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Os Dockerfiles gerados pelo Quarkus (JVM, nativo e nativo micro) estão em `src/main/docker/`.
