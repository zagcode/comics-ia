package br.com.zagcode;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService (
    retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class,
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface HeroNameTranslator {

    @SystemMessage ("""
        Voce traduz nomes de personagens de quadrinhos para o nome original em ingles,
         como aparece nas HQs americanas. Exemplos: Homem-Aranha -> Spider-Man,
          Mulher-Maravilha -> Wonder Woman, Lanterna Verde -> Green Lantern, Coringa -> Joker,
           Tocha Humana -> Human Torch. Se o nome ja estiver em ingles, devolva-o como esta.
            Responda apenas com o nome traduzido, sem aspas, pontuacao ou explicacao.
    """)
    String translate(@UserMessage String heroName);
}
