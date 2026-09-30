package br.com.zagcode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Fichas de personagens ja consultadas na Superhero API, uma por arquivo .md.
 * Servem de cache para a ferramenta e entram no RAG pelo nome do personagem (ver {@link ComicsRetrievalAugmentor}):
 * por similaridade de embedding uma ficha parece com qualquer outra, entao a do Kang aparecia em pergunta sobre o Bane.
 */
@ApplicationScoped
public class HeroArchive {

    @ConfigProperty (name = "comics.herois.path")
    Path directory;

    public Optional<String> find(String searchName) {
        Path file = fileFor(searchName);
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.warnf(e, "Nao foi possivel ler a ficha %s", file);
            return Optional.empty();
        }
    }

    /**
     * Grava a ficha e devolve o texto gravado. O titulo leva o nome que o usuario usou e o nome
     * original, para que perguntas em portugues encontrem a ficha no RAG.
     */
    public String save(String searchName, String askedName, String originalName, String sheet) {
        String title = sameName(askedName, originalName)
                ? originalName
                : askedName + " (" + originalName + ")";
        String content = """
            # %s

            Ficha do personagem %s, consultada na Superhero API.

            %s
            """.formatted(title, title, sheet);

        Path file = fileFor(searchName);
        if (file == null) {
            return content;
        }
        try {
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, ".ficha-", ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Log.warnf(e, "Nao foi possivel salvar a ficha %s", file);
        }
        return content;
    }

    /** Fichas cujo personagem e citado no texto, pelo nome em portugues ou no original (titulo "# Nome (Original)"). */
    public List<String> mentionedIn(String text, int limit) {
        String haystack = " " + slug(text).replace('-', ' ') + " ";
        List<String> sheets = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return sheets;
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".md")).sorted().toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (namesIn(content).stream().anyMatch(name -> haystack.contains(" " + name + " "))) {
                    sheets.add(content);
                    if (sheets.size() == limit) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            Log.warnf(e, "Nao foi possivel ler as fichas em %s", directory);
        }
        return sheets;
    }

    private static List<String> namesIn(String content) {
        String title = content.lines().findFirst().orElse("").replaceFirst("^#\\s*", "");
        List<String> names = new ArrayList<>();
        for (String name : title.split("[()]")) {
            String normalized = slug(name).replace('-', ' ');
            if (normalized.length() >= 3) {
                names.add(normalized);
            }
        }
        return names;
    }

    private Path fileFor(String name) {
        String slug = slug(name);
        return slug.isEmpty() ? null : directory.resolve(slug + ".md");
    }

    private static boolean sameName(String a, String b) {
        return slug(a).equals(slug(b));
    }

    private static String slug(String name) {
        if (name == null) {
            return "";
        }
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
