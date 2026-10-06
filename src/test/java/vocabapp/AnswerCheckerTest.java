package vocabapp;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerCheckerTest {
    private static final Language SPANISH = Language.forName("Spanish");

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
    void accentTypedAsASeparateMarkStillCounts() {
        // "thi" + tone mark (sắc) + "ch", as the tone buttons or some keyboards type it
        assertTrue(AnswerChecker.isCorrect("thi\u0301ch", "thích"));
        assertTrue(AnswerChecker.isCorrect("thích", "thi\u0301ch"));
    }

    @Test
    void acceptsAnyAlternative() {
        assertTrue(AnswerChecker.isCorrect("good night", "Good evening / Good night"));
        assertTrue(AnswerChecker.isCorrect("Good evening", "Good evening / Good night"));
        assertFalse(AnswerChecker.isCorrect("good", "Good evening / Good night"));
    }

    @Test
    void allOptionsListsEveryAlternativeTidily() {
        assertEquals("Good evening / Good night", AnswerChecker.allOptions("Good evening /Good night"));
        assertEquals("to talk / to speak", AnswerChecker.allOptions("to talk/to speak"));
        assertEquals("¡Hola!", AnswerChecker.allOptions("¡Hola!"));
    }

    @Test
    void hintsAtMissingArticle() {
        assertEquals("Don't forget the article!", AnswerChecker.feedback("perro", "el perro", 1, SPANISH));
        assertEquals("Don't forget the article!", AnswerChecker.feedback("dog", "the dog", 2, SPANISH));
    }

    @Test
    void hintsAtMissingTo() {
        assertEquals("What comes in front of an infinitive verb?", AnswerChecker.feedback("talk", "to talk", 1, SPANISH));
    }

    @Test
    void findsArticleHintInLaterAlternative() {
        assertEquals("Don't forget the article!", AnswerChecker.feedback("speak", "to talk / the speak", 1, SPANISH));
    }

    @Test
    void otherLanguagesStillGetEnglishArticleHints() {
        Language italian = Language.forName("Italian");
        assertEquals("Don't forget the article!", AnswerChecker.feedback("dog", "the dog", 1, italian));
        assertEquals("Not quite.", AnswerChecker.feedback("cane", "il cane", 1, italian));
    }

    @Test
    void firstWrongTryOnlySaysNotQuite() {
        assertEquals("Not quite.", AnswerChecker.feedback("gatto", "gato", 1, SPANISH));
    }

    @Test
    void secondTryShowsWhichLettersAreRight() {
        assertEquals("Almost! These letters are right: o_o", AnswerChecker.feedback("oro", "oso", 2, SPANISH));
        assertEquals("Almost! These letters are right: _ato", AnswerChecker.feedback("pato", "gato", 2, SPANISH));
        assertEquals("Almost! These letters are right: g_to", AnswerChecker.feedback("gto", "gato", 2, SPANISH));
        assertEquals("Almost! These letters are right: el o_o", AnswerChecker.feedback("el oro", "el oso", 2, SPANISH));
        assertEquals("Almost! These letters are right: Good n__ht", AnswerChecker.feedback("good nacht", "Good evening / Good night", 2, SPANISH));
    }

    @Test
    void secondTryPointsAtTheAccent() {
        assertEquals("Almost! Check the accent: tambi_n", AnswerChecker.feedback("tambien", "también", 2, SPANISH));
        assertEquals("Almost! Check the accent: ni_o", AnswerChecker.feedback("nino", "el niño / niño", 2, SPANISH));
    }

    @Test
    void secondTryNamesExtraLettersInsteadOfGivingTheAnswerAway() {
        assertEquals("Almost! There's one letter too many.", AnswerChecker.feedback("gatto", "gato", 2, SPANISH));
        assertEquals("Almost! There are a few letters too many.", AnswerChecker.feedback("elefantee", "elefant", 2, SPANISH));
    }

    @Test
    void secondTryGivesFirstLetterSkippingArticles() {
        assertEquals("Not quite. Hint: it starts with 'g'", AnswerChecker.feedback("perro", "el gato", 2, SPANISH));
        assertEquals("Not quite. Hint: it starts with 't'", AnswerChecker.feedback("speak", "to talk", 2, SPANISH));
    }

    @Test
    void pickOneTrimsAlternatives() {
        Random random = new Random(1);
        for (int i = 0; i < 20; i++) {
            String picked = AnswerChecker.pickOne("Good evening / Good night", random);
            assertTrue(picked.equals("Good evening") || picked.equals("Good night"), picked);
        }
    }

    private static String hint(String answer, String expected) {
        return AnswerChecker.hint(answer, expected, SPANISH);
    }

    @Test
    void hintButtonGivesTheFirstLetterBeforeAnyInput() {
        assertEquals("It starts with 'd'", hint("", "dado"));
    }

    @Test
    void hintButtonGivesOneLetterPastWhatIsTypedRight() {
        assertEquals("It starts with 'da'", hint("deca", "dado"));
        assertEquals("It starts with 'dad'", hint("Dama", "dado"));
    }

    @Test
    void hintButtonGivesTheFirstLetterForACompletelyWrongAnswer() {
        assertEquals("It starts with 'd'", hint("gato", "dado"));
    }

    @Test
    void pressingTheHintButtonAgainWithoutTypingGivesTheSameHint() {
        assertEquals(hint("", "dado"), hint("", "dado"));
        assertEquals(hint("deca", "dado"), hint("deca", "dado"));
    }

    @Test
    void hintButtonFollowsTheAlternativeThatIsBeingTyped() {
        assertEquals("It starts with 'Good e'", hint("good x", "Good evening / Good night"));
        assertEquals("It starts with 'Good nig'", hint("good ni", "Good evening / Good night"));
    }

    @Test
    void hintButtonSkipsTheArticleUnlessItIsBeingTyped() {
        assertEquals("It starts with 'v'", hint("", "la vaca"));
        assertEquals("It starts with 'v'", hint("gato", "la vaca"));
        assertEquals("It starts with 'vac'", hint("va", "la vaca"));
        assertEquals("It starts with 'la va'", hint("la v", "la vaca"));
        assertEquals("It starts with 'el apelli'", hint("el apell", "el apellido"));
        assertEquals("It starts with 'c'", hint("", "the cow"));
        assertEquals("It starts with 's'", hint("", "to speak"));
    }

    @Test
    void hintButtonShowsWhichLettersAreRightForNearlyRightAnswers() {
        assertEquals("Almost! There are a few letters too many.", hint("la apellidone", "el apellido"));
        assertEquals("Almost! There are a few letters too many.", hint("apellidion", "el apellido"));
        assertEquals("Almost! These letters are right: da_o", hint("dao", "dado"));
        assertEquals("Almost! Check the accent: adi_s", hint("adios", "adiós"));
    }

    @Test
    void hintButtonPointsAtTheArticleWhenOnlyThatIsWrong() {
        assertEquals("Don't forget the article!", hint("apellido", "el apellido"));
        assertEquals("Check the article.", hint("la apellido", "el apellido"));
        assertEquals("What comes in front of an infinitive verb?", hint("speak", "to speak"));
    }

    @Test
    void hintButtonSaysWhenTheAnswerIsAlreadyRightOrTooLong() {
        assertEquals("That's right! Press Check.", hint("Dado", "dado"));
        assertEquals("The start is right, but there are letters too many.", hint("dadoxyz", "dado"));
    }
}
