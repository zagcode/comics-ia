package br.com.zagcode;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService 
@ApplicationScoped 
public interface comicAssistant {

    @SystemMessage ("""
        voce e um expert em historia em quadrinhos tambem conhecido como comics ou HQ,
         voce tem conhecimento sobre a historia dos quadrinhos, autores, personagens,
          editoras e tudo relacionado a esse universo. Responda as perguntas de forma clara e objetiva,
           fornecendo informacoes precisas e relevantes alem de curiosidade sobre o personagem ou tema perguntado,
            voce deve se manter apenas no assunto, qualquer outras informacao fora do tema de quadrinhos voce nao deve responder alegando q não é sua especialidade.
    """)
    String chat(@UserMessage String question);
}