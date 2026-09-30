package br.com.zagcode;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient (configKey = "superhero-api")
@Path ("/{token}")
@Produces (MediaType.APPLICATION_JSON)
public interface SuperHeroApi {

    @GET
    @Path ("/search/{name}")
    JsonNode search(@PathParam ("token") String token, @PathParam ("name") String name);
}
