package vocabapp;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerCheckerTest {

    @Test
    void ignoresCaseAndPunctuation() {
        assertTrue(AnswerChecker.isCorrect("hola", "¡Hola!"));
        assertTrue(AnswerChecker.isCorrect("buenas  noches", "Buenas noches!"));
        assertTrue(AnswerChecker.isCorrect("Como", "Como...?"));
    }

    @Test
    void accentsStillCount() {
        assertTrue(AnswerChecker.isCorrect("qué tal", "¿Qué tal?"));
        assertFalse(AnswerChecker.isCorrect("que tal", "¿Qué tal?"));
    }

    @Test
    void acceptsAnyAlternative() {
        assertTrue(AnswerChecker.isCorrect("good night", "Good evening / Good night"));
        assertTrue(AnswerChecker.isCorrect("Good evening", "Good evening / Good night"));
        assertFalse(AnswerChecker.isCorrect("good", "Good evening / Good night"));
    }

    @Test
    void hintsAtMissingArticle() {
        assertEquals("Don't forget the article!", AnswerChecker.feedback("perro", "el perro", 1));
        assertEquals("Don't forget the article!", AnswerChecker.feedback("dog", "the dog", 2));
    }

    @Test
    void hintsAtMissingTo() {
        assertEquals("What comes in front of an infinitive verb?", AnswerChecker.feedback("talk", "to talk", 1));
    }

    @Test
    void findsArticleHintInLaterAlternative() {
        assertEquals("Don't forget the article!", AnswerChecker.feedback("speak", "to talk / the speak", 1));
    }

    @Test
    void firstWrongTryOnlySaysNotQuite() {
        assertEquals("Not quite.", AnswerChecker.feedback("gatto", "gato", 1));
    }

    @Test
    void secondTryRecognisesTypos() {
        assertEquals("Almost correct!", AnswerChecker.feedback("gatto", "gato", 2));
    }

    @Test
    void secondTryGivesFirstLetterSkippingArticles() {
        assertEquals("Not quite. Hint: it starts with 'g'", AnswerChecker.feedback("perro", "el gato", 2));
        assertEquals("Not quite. Hint: it starts with 't'", AnswerChecker.feedback("speak", "to talk", 2));
    }

    @Test
    void pickOneTrimsAlternatives() {
        Random random = new Random(1);
        for (int i = 0; i < 20; i++) {
            String picked = AnswerChecker.pickOne("Good evening / Good night", random);
            assertTrue(picked.equals("Good evening") || picked.equals("Good night"), picked);
        }
    }
}
