import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionTest {

    @Test
    void expectsTheOtherLanguage() {
        Vocab vocab = new Vocab("hablar", "to talk/to speak", Vocab.VERBS_CATEGORY);
        Random random = new Random(42);
        boolean askedSpanish = false;
        boolean askedEnglish = false;
        for (int i = 0; i < 50; i++) {
            Question question = Question.from(vocab, random);
            if (question.prompt().equals("hablar")) {
                assertEquals("to talk/to speak", question.expected());
                askedEnglish = true;
            } else {
                assertTrue(question.prompt().equals("to talk") || question.prompt().equals("to speak"), question.prompt());
                assertEquals("hablar", question.expected());
                askedSpanish = true;
            }
        }
        assertTrue(askedSpanish && askedEnglish);
    }
}
