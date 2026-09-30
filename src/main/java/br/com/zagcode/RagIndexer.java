package br.com.zagcode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Monta o indice do RAG com os documentos de quarkus.langchain4j.easy-rag.path e as fichas salvas
 * pelo {@link HeroArchive}. Roda na subida e a cada comics.rag.reindex-every, e so reembeda se algum
 * arquivo mudou. O indice novo entra antes de o antigo sair, entao as perguntas nunca ficam sem RAG.
 */
@ApplicationScoped
public class RagIndexer {

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @ConfigProperty (name = "quarkus.langchain4j.easy-rag.path")
    Path documentsPath;

    @ConfigProperty (name = "comics.herois.path")
    Path heroesPath;

    @ConfigProperty (name = "quarkus.langchain4j.easy-rag.max-segment-size", defaultValue = "300")
    int maxSegmentSize;

    @ConfigProperty (name = "quarkus.langchain4j.easy-rag.max-overlap-size", defaultValue = "30")
    int maxOverlapSize;

    private List<String> indexedIds = List.of();
    private String indexedFingerprint = "";

    void onStart(@Observes StartupEvent event) {
        reindex();
    }

    @Scheduled (every = "${comics.rag.reindex-every}", delayed = "${comics.rag.reindex-every}",
                concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    synchronized void reindex() {
        try {
            List<Path> files = new ArrayList<>(listFiles(documentsPath));
            files.addAll(listFiles(heroesPath));
            String fingerprint = fingerprint(files);
            if (fingerprint.equals(indexedFingerprint)) {
                Log.debug("RAG sem mudancas desde a ultima indexacao");
                return;
            }

            List<Document> documents = new ArrayList<>(load(documentsPath));
            documents.addAll(load(heroesPath));
            List<TextSegment> segments = DocumentSplitters
                    .recursive(maxSegmentSize, maxOverlapSize, new HuggingFaceTokenCountEstimator())
                    .splitAll(documents);
            List<Embedding> embeddings = segments.isEmpty()
                    ? List.of()
                    : embeddingModel.embedAll(segments).content();

            List<String> ids = segments.isEmpty() ? List.of() : embeddingStore.addAll(embeddings, segments);
            if (!indexedIds.isEmpty()) {
                embeddingStore.removeAll(indexedIds);
            }
            indexedIds = ids;
            indexedFingerprint = fingerprint;
            Log.infof("RAG indexado: %d arquivos, %d trechos", documents.size(), segments.size());
        } catch (Exception e) {
            Log.warnf(e, "Falha ao indexar o RAG; o indice anterior continua valendo");
        }
    }

    private static List<Document> load(Path directory) {
        return Files.isDirectory(directory)
                ? FileSystemDocumentLoader.loadDocumentsRecursively(directory)
                : List.of();
    }

    private static List<Path> listFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().endsWith(".tmp"))
                    .sorted()
                    .toList();
        }
    }

    private static String fingerprint(List<Path> files) throws IOException {
        StringBuilder fingerprint = new StringBuilder();
        for (Path file : files) {
            fingerprint.append(file).append('|')
                    .append(Files.size(file)).append('|')
                    .append(Files.getLastModifiedTime(file).toMillis()).append('\n');
        }
        return fingerprint.toString();
    }
}
