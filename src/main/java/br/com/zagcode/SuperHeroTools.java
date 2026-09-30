package br.com.zagcode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class SuperHeroTools {

    private static final int MAX_RESULTS = 5;

    @Inject
    @RestClient
    SuperHeroApi superHeroApi;

    @Inject
    HeroNameTranslator translator;

    @Inject
    HeroArchive archive;

    @ConfigProperty (name = "superhero-api.token")
    String token;

    @Tool ("""
        Busca na Superhero API os dados de um personagem de quadrinhos (super-heroi ou vilao):
        nome verdadeiro, editora, primeira aparicao, alinhamento, poderes, aparencia, ocupacao,
        base, grupos e parentes.
    """)
    public String searchHero(
            @P ("nome original do personagem em ingles, como nas HQs americanas, ex.: Spider-Man;"
                + " se nao souber, repita o nome que o usuario escreveu") String englishName,
            @P ("nome do personagem como aparece na pergunta, ex.: Homem-Aranha") String name) {
        String asked = name == null ? "" : name.trim();

        // Tenta primeiro os nomes que o modelo ja mandou; o tradutor so entra se nenhum deles achar nada.
        Set<String> tried = new LinkedHashSet<>();
        boolean apiFailed = false;
        for (String candidate : new String[] { englishName, asked }) {
            if (candidate == null || candidate.isBlank() || !tried.add(normalize(candidate))) {
                continue;
            }
            try {
                Optional<String> sheet = lookup(candidate, asked);
                if (sheet.isPresent()) {
                    return forModel(sheet.get());
                }
            } catch (Exception e) {
                Log.warnf(e, "Falha ao consultar a Superhero API para '%s'", candidate);
                apiFailed = true;
            }
        }

        String translated = translate(asked);
        if (!translated.isBlank() && tried.add(translated)) {
            Log.infof("Superhero API: '%s' traduzido para '%s'", asked, translated);
            try {
                Optional<String> sheet = lookup(translated, asked);
                if (sheet.isPresent()) {
                    return forModel(sheet.get());
                }
            } catch (Exception e) {
                Log.warnf(e, "Falha ao consultar a Superhero API para '%s'", translated);
                apiFailed = true;
            }
        }

        if (apiFailed) {
            return "A Superhero API nao respondeu. Responda com o seu proprio conhecimento.";
        }
        return "Nenhum personagem encontrado com o nome '" + asked + "' (buscado como " + String.join(", ", tried)
                + "). Responda com o seu proprio conhecimento.";
    }

    /** Diz ao modelo como usar a ficha; o texto salvo em disco fica so com os dados. */
    private String forModel(String sheet) {
        return """
            Dados da Superhero API: use-os como fonte principal da resposta e nao os contradiga.
            Se vier mais de um personagem, deixe claro de qual versao voce esta falando.
            A API nao lista poderes: "Atributos" sao notas de 0 a 100. Quando perguntarem sobre poderes ou forca,
            cite essas notas e complemente com o que voce sabe sobre as habilidades do personagem.

            """ + sheet;
    }

    /** Ficha salva, se houver; senao consulta a API e salva o resultado. */
    private Optional<String> lookup(String searchName, String askedName) {
        Optional<String> saved = archive.find(searchName);
        if (saved.isPresent()) {
            Log.infof("Superhero API: '%s' encontrado nas fichas salvas", searchName);
            return saved;
        }

        JsonNode body = superHeroApi.search(token, normalize(searchName));
        if (!"success".equals(body.path("response").asText())) {
            return Optional.empty();
        }

        List<JsonNode> heroes = new ArrayList<>();
        for (JsonNode hero : body.path("results")) {
            if (heroes.size() == MAX_RESULTS) {
                break;
            }
            heroes.add(hero);
        }
        String sheet = String.join("\n\n", heroes.stream().map(this::describe).toList());
        String originalName = heroes.get(0).path("name").asText(searchName);
        Log.infof("Superhero API: '%s' consultado e salvo", searchName);
        return Optional.of(archive.save(searchName, askedName, originalName, sheet));
    }

    private String translate(String name) {
        if (name.isBlank()) {
            return "";
        }
        try {
            String translated = translator.translate(name).lines().findFirst().orElse("");
            return normalize(translated.replaceAll("[\"'`.]", ""));
        } catch (Exception e) {
            Log.warnf(e, "Falha ao traduzir '%s'", name);
            return "";
        }
    }

    private String normalize(String name) {
        return name.trim().toLowerCase();
    }

    private String describe(JsonNode hero) {
        JsonNode bio = hero.path("biography");
        JsonNode stats = hero.path("powerstats");
        JsonNode look = hero.path("appearance");
        JsonNode work = hero.path("work");
        JsonNode links = hero.path("connections");

        StringBuilder sheet = new StringBuilder();
        line(sheet, "Nome", hero.path("name").asText());
        line(sheet, "Nome verdadeiro", bio.path("full-name").asText());
        line(sheet, "Editora", bio.path("publisher").asText());
        line(sheet, "Primeira aparicao", bio.path("first-appearance").asText());
        line(sheet, "Alinhamento", bio.path("alignment").asText());
        line(sheet, "Codinomes", join(bio.path("aliases")));
        line(sheet, "Local de nascimento", bio.path("place-of-birth").asText());
        line(sheet, "Atributos (0 a 100)", attributes(stats));
        line(sheet, "Genero", look.path("gender").asText());
        line(sheet, "Raca", look.path("race").asText());
        line(sheet, "Altura", join(look.path("height")));
        line(sheet, "Peso", join(look.path("weight")));
        line(sheet, "Olhos", look.path("eye-color").asText());
        line(sheet, "Cabelo", look.path("hair-color").asText());
        line(sheet, "Ocupacao", work.path("occupation").asText());
        line(sheet, "Base", work.path("base").asText());
        line(sheet, "Grupos", links.path("group-affiliation").asText());
        line(sheet, "Parentes", links.path("relatives").asText());
        return sheet.toString().stripTrailing();
    }

    private String attributes(JsonNode stats) {
        String[][] labels = {
            { "intelligence", "inteligencia" }, { "strength", "forca" }, { "speed", "velocidade" },
            { "durability", "durabilidade" }, { "power", "poder" }, { "combat", "combate" } };
        List<String> values = new ArrayList<>();
        for (String[] label : labels) {
            String value = stats.path(label[0]).asText();
            if (isKnown(value)) {
                values.add(label[1] + " " + value);
            }
        }
        return String.join(", ", values);
    }

    private void line(StringBuilder sheet, String label, String value) {
        if (isKnown(value)) {
            sheet.append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    /** A API usa "-", "null" e medidas zeradas para campos sem informacao. */
    private boolean isKnown(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        return !v.isEmpty() && !v.equals("-") && !v.equalsIgnoreCase("null")
                && !v.matches("(-|0) ?(cm|kg|lb)?(, (-|0) ?(cm|kg|lb)?)*");
    }

    private String join(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> {
            if (isKnown(value.asText())) {
                values.add(value.asText());
            }
        });
        return String.join(", ", values);
    }
}
