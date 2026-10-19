package vocabapp;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionTest {
    private static final Language SPANISH = Language.forName("Spanish");

    @Test
    void expectsTheOtherLanguage() {
        Vocab vocab = new Vocab("hablar", "to talk/to speak", Vocab.VERBS_CATEGORY);
        Random random = new Random(42);
        boolean askedSpanish = false;
        boolean askedEnglish = false;
        for (int i = 0; i < 50; i++) {
            Question question = Question.from(vocab, SPANISH, random);
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

    @Test
    void neverAsksAPronounOnItsOwn() {
        Vocab vocab = new Vocab("él / ella / usted quiere", "he / she / it wants", Vocab.VERBS_CATEGORY);
        Random random = new Random(7);
        for (int i = 0; i < 50; i++) {
            String prompt = Question.from(vocab, SPANISH, random).prompt();
            assertTrue(prompt.matches("(he|she|it) wants|(él|ella|usted) quiere"), prompt);
        }
    }

    @Test
    void germanShortFormAsksThePronounWithTheVerb() {
        Language german = Language.forName("German");
        Vocab vocab = new Vocab("er / sie / es hat", "he / she / it has", Vocab.VERBS_CATEGORY);
        Random random = new Random(3);
        for (int i = 0; i < 50; i++) {
            String prompt = Question.from(vocab, german, random).prompt();
            assertTrue(prompt.matches("(er|sie|es) hat|(he|she|it) has"), prompt);
        }
    }

    @Test
    void neverAsksAGermanPronounOnItsOwnOnTheTranslationSide() {
        Language spanisch = Language.forName("Spanisch").translatedInto(Language.forName("German"));
        Vocab vocab = new Vocab("él / ella / usted tiene", "er / sie / es hat", "verbformen");
        Random random = new Random(5);
        for (int i = 0; i < 50; i++) {
            String prompt = Question.from(vocab, spanisch, random).prompt();
            assertTrue(prompt.matches("(er|sie|es) hat|(él|ella|usted) tiene"), prompt);
        }
    }
}
