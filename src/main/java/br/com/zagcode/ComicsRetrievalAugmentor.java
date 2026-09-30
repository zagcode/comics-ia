package br.com.zagcode;

import java.util.function.Supplier;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * RAG do assistente com duas fontes: os documentos de quarkus.langchain4j.easy-rag.path, por similaridade
 * (min-score calibrado para eles), e as fichas salvas de personagens, so quando a pergunta cita o personagem.
 */
@ApplicationScoped
public class ComicsRetrievalAugmentor implements Supplier<RetrievalAugmentor> {

    private static final int MAX_HERO_SHEETS = 3;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    HeroArchive archive;

    @ConfigProperty (name = "quarkus.langchain4j.easy-rag.max-results", defaultValue = "5")
    int maxResults;

    @ConfigProperty (name = "quarkus.langchain4j.easy-rag.min-score")
    double minScore;

    private volatile RetrievalAugmentor augmentor;

    @Override
    public RetrievalAugmentor get() {
        if (augmentor == null) {
            ContentRetriever documents = EmbeddingStoreContentRetriever.builder()
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();
            ContentRetriever heroSheets = query -> archive.mentionedIn(query.text(), MAX_HERO_SHEETS).stream()
                    .map(Content::from)
                    .toList();
            augmentor = DefaultRetrievalAugmentor.builder()
                    .queryRouter(new DefaultQueryRouter(documents, heroSheets))
                    .build();
        }
        return augmentor;
    }
}
