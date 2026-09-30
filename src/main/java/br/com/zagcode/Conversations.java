package br.com.zagcode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import io.quarkiverse.langchain4j.ChatMemoryRemover;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Identifica cada conversa pelo id que o frontend manda e apaga a memoria das conversas paradas,
 * para que o historico guardado em memoria nao cresca para sempre.
 */
@ApplicationScoped
public class Conversations {

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Inject
    comicAssistant assistant;

    @ConfigProperty (name = "comics.conversation.idle-timeout", defaultValue = "30m")
    Duration idleTimeout;

    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    /** Devolve o id da conversa; sem um id valido, a pergunta vira uma conversa nova e avulsa. */
    public String touch(String conversationId) {
        String id = conversationId != null && VALID_ID.matcher(conversationId).matches()
                ? conversationId
                : UUID.randomUUID().toString();
        lastSeen.put(id, Instant.now());
        return id;
    }

    @Scheduled (every = "10m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void forgetIdleConversations() {
        Instant limit = Instant.now().minus(idleTimeout);
        lastSeen.forEach((id, seen) -> {
            if (seen.isBefore(limit) && lastSeen.remove(id, seen)) {
                ChatMemoryRemover.remove(assistant, id);
                Log.debugf("Memoria da conversa %s apagada por inatividade", id);
            }
        });
    }
}
