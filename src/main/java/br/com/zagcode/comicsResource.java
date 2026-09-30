package br.com.zagcode;

import jakarta.ws.rs.core.MediaType;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path ("/comics")
@Consumes (MediaType.TEXT_PLAIN)
@Produces (MediaType.TEXT_PLAIN)
public class comicsResource {

    @Inject
    comicAssistant comicAssistant;

    @Inject
    Conversations conversations;

    @POST
    public String chat(@HeaderParam ("X-Conversation-Id") String conversationId, String userMessage) {
        return comicAssistant.chat(conversations.touch(conversationId), userMessage);
    }
}
