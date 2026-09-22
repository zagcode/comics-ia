package br.com.zagcode;

import jakarta.ws.rs.core.MediaType;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path ("/comics")
@Consumes (MediaType.TEXT_PLAIN)
@Produces (MediaType.TEXT_PLAIN)
public class comicsResource {

    @Inject 
    comicAssistant comicAssistant;

    @POST 
    public String chat(String userMessage) {
        return comicAssistant.chat(userMessage);
    }
}
