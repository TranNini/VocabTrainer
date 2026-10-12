package vocabapp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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
    void subjectPronounIsOptional() {
        assertTrue(AnswerChecker.isCorrect("tengo", "yo tengo", SPANISH));
        assertTrue(AnswerChecker.isCorrect("Yo tengo", "yo tengo", SPANISH));
        assertTrue(AnswerChecker.isCorrect("tiene", "él tiene / ella tiene", SPANISH));
        assertTrue(AnswerChecker.isCorrect("ella tiene", "él tiene / ella tiene", SPANISH));
        assertFalse(AnswerChecker.isCorrect("tienes", "yo tengo", SPANISH));
        // only for languages with pronoun rules, and only a pronoun at the start
        assertFalse(AnswerChecker.isCorrect("tengo", "yo tengo", Language.forName("Italian")));
        assertFalse(AnswerChecker.isCorrect("vaca", "la vaca", SPANISH));
    }

    @Test
    void pronounAtTheStartOfASentenceIsOptional() {
        assertTrue(AnswerChecker.isCorrect("tengo una pregunta", "yo tengo una pregunta", SPANISH));
        assertTrue(AnswerChecker.isCorrect("¿Tienes hambre?", "¿Tú tienes hambre?", SPANISH));
        assertTrue(AnswerChecker.isCorrect("Somos amigos", "Nosotros somos amigos", SPANISH));
        assertFalse(AnswerChecker.isCorrect("tú tengo una pregunta", "yo tengo una pregunta", SPANISH));
        // only at the start: a pronoun later in the sentence still counts
        assertFalse(AnswerChecker.isCorrect("no entiendo", "no entiendo yo", SPANISH));
    }

    @Test
    void hintsWorkWithoutThePronoun() {
        assertEquals("That's right! Press Check.", AnswerChecker.hint("tengo", "yo tengo", SPANISH));
        assertEquals("It starts with 'y'", AnswerChecker.hint("", "yo tengo", SPANISH));
        assertEquals("It starts with 'ten'", AnswerChecker.hint("te", "yo tengo", SPANISH));
    }

    @Test
    void allOptionsListsEveryAlternativeTidily() {
        assertEquals("Good evening / Good night", AnswerChecker.allOptions("Good evening /Good night"));
        assertEquals("to talk / to speak", AnswerChecker.allOptions("to talk/to speak"));
        assertEquals("¡Hola!", AnswerChecker.allOptions("¡Hola!"));
    }

    @Test
    void hintsAtMissingArticle() {
        assertEquals("Don't forget the article!", AnswerChecker.feedback("perro", "el perro", SPANISH, List.of()));
        assertEquals("Don't forget the article!", AnswerChecker.feedback("dog", "the dog", SPANISH, List.of()));
    }

    @Test
    void hintsAtMissingTo() {
        assertEquals("What comes in front of an infinitive verb?", AnswerChecker.feedback("talk", "to talk", SPANISH, List.of()));
    }

    @Test
    void findsArticleHintInLaterAlternative() {
        assertEquals("Don't forget the article!", AnswerChecker.feedback("speak", "to talk / the speak", SPANISH, List.of()));
    }

    @Test
    void otherLanguagesStillGetEnglishArticleHints() {
        Language italian = Language.forName("Italian");
        assertEquals("Don't forget the article!", AnswerChecker.feedback("dog", "the dog", italian, List.of()));
        assertEquals("Almost! These letters are right: __ cane", AnswerChecker.feedback("cane", "il cane", italian, List.of()));
    }

    @Test
    void everyWrongTryGetsAHint() {
        assertEquals("Almost! There's one letter too many.", AnswerChecker.feedback("gatto", "gato", SPANISH, List.of()));
    }

    @Test
    void checkNamesTheKindOfMistake() {
        assertEquals("Almost! One letter is wrong: o_o", AnswerChecker.feedback("oro", "oso", SPANISH, List.of()));
        assertEquals("Almost! One letter is wrong: _ato", AnswerChecker.feedback("pato", "gato", SPANISH, List.of()));
        assertEquals("Almost! One letter is missing: g_to", AnswerChecker.feedback("gto", "gato", SPANISH, List.of()));
        assertEquals("Almost! One letter is wrong: el o_o", AnswerChecker.feedback("el oro", "el oso", SPANISH, List.of()));
        assertEquals("Almost! Two letters are swapped.", AnswerChecker.feedback("How are yuo", "How are you? / What's up?", SPANISH, List.of()));
        assertEquals("Almost! A space is missing.", AnswerChecker.feedback("buenasnoches", "buenas noches", SPANISH, List.of()));
        assertEquals("Almost! There's one word too many.", AnswerChecker.feedback("yo soy soy Nini", "Yo soy Nini", SPANISH, List.of()));
    }

    @Test
    void checkShowsWhichLettersAreRightWhenMoreIsWrong() {
        assertEquals("Almost! These letters are right: Good n__ht", AnswerChecker.feedback("good nacht", "Good evening / Good night", SPANISH, List.of()));
    }

    @Test
    void checkPointsAtTheAccent() {
        assertEquals("Almost! An accent is missing: tambi_n", AnswerChecker.feedback("tambien", "también", SPANISH, List.of()));
        assertEquals("Almost! The ñ is missing: ni_o", AnswerChecker.feedback("nino", "el niño / niño", SPANISH, List.of()));
        assertEquals("Almost! The diaeresis (¨) is missing: ping_ino", AnswerChecker.feedback("pinguino", "pingüino", SPANISH, List.of()));
        assertEquals("Almost! The accent is on the wrong letter: _di_s", AnswerChecker.feedback("ádios", "adiós", SPANISH, List.of()));
        assertEquals("Almost! There's an accent too many.", AnswerChecker.feedback("adiós", "adios", SPANISH, List.of()));
    }

    @Test
    void checkNamesExtraLettersInsteadOfGivingTheAnswerAway() {
        assertEquals("Almost! There's one letter too many.", AnswerChecker.feedback("gatto", "gato", SPANISH, List.of()));
        assertEquals("The start is right, but there are letters too many.", AnswerChecker.feedback("elefantee", "elefant", SPANISH, List.of()));
    }

    @Test
    void checkGivesFirstLetterSkippingArticles() {
        assertEquals("Not quite. Hint: it starts with 'g'", AnswerChecker.feedback("perro", "el gato", SPANISH, List.of()));
        assertEquals("Not quite. Hint: it starts with 't'", AnswerChecker.feedback("speak", "to talk", SPANISH, List.of()));
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
    void hintButtonGivesTheNextLetterButNeverTheLast() {
        // nearly finished is not the same as nearly right
        assertEquals("Almost! Just the last letter is missing.", hint("habla", "hablar"));
        assertEquals("It starts with 'escribi'", hint("escrib", "escribir"));
        assertEquals("Almost! Just the last letter is missing.", hint("i", "ir"));
        assertEquals("Almost! Just the last letter is missing.", hint("to writ", "to write"));
    }

    @Test
    void hintButtonFollowsAnArticleThatIsBeingTyped() {
        assertEquals("It starts with 'el'", hint("e", "el examen"));
        assertEquals("It starts with 'el e'", hint("el", "el examen"));
        assertEquals("It starts with 'el b'", hint("el ", "el bolígrafo"));
        assertEquals("It starts with 'to t'", hint("to", "to talk / to speak"));
        assertEquals("It starts with 'to sp'", hint("to s", "to talk / to speak"));
        assertEquals("It starts with 'to g'", hint("to", "to go"));
    }

    @Test
    void hintButtonGivesTheFirstLetterForACompletelyWrongAnswer() {
        assertEquals("It starts with 'd'", hint("gato", "dado"));
    }

    @Test
    void eachTryGetsTwoDifferentHints() {
        // per try: the hint after Check, then the hint button
        assertEquals(List.of("Almost! One letter is wrong: t_ngo", "It starts with 'te'",
                "Not quite. Hint: it starts with 'ten'", "It starts with 'teng'"), tries("tango", "tengo"));
        assertEquals(List.of("Not quite. Hint: it starts with 'g'", "It starts with 'ga'",
                "Not quite. Hint: it starts with 'gat'"), tries("perro", "el gato").subList(0, 3));
        assertEquals(List.of("Almost! These letters are right: C_mo __ llamas", "One more letter: Cómo __ llamas",
                "One more letter: Cómo t_ llamas"), tries("como se llamas", "Cómo te llamas?").subList(0, 3));
        assertEquals(List.of("Don't forget the article!", "It starts with 'e'"), tries("apellido", "el apellido").subList(0, 2));
        assertEquals(List.of("Almost! The ñ is missing: ni_o", "It starts with 'niñ'"), tries("nino", "niño").subList(0, 2));
    }

    @Test
    void hintButtonDoesNotRepeatTheHintFromCheck() {
        String afterCheck = AnswerChecker.feedback("perro", "el gato", SPANISH, List.of());
        assertEquals("Not quite. Hint: it starts with 'g'", afterCheck);
        assertEquals("It starts with 'ga'", AnswerChecker.hint("perro", "el gato", SPANISH, List.of(afterCheck)));
        // the same letters under another name count as the same hint
        assertEquals("It starts with 'Có'", AnswerChecker.hint("como", "Cómo", SPANISH, List.of("Almost! Check the accent: C_mo")));
    }

    @Test
    void hintButtonNeverGivesTheWholeAnswer() {
        // when every hint was shown, the last one comes again, never the answer
        assertEquals("It starts with 'dad'", AnswerChecker.hint("da", "dado", SPANISH, List.of("It starts with 'dad'")));
        assertEquals("Almost! Just the last letter is missing.", AnswerChecker.hint("habla", "hablar", SPANISH,
                List.of("Almost! Just the last letter is missing.")));
    }

    // Check, hint button, Check, hint button with the same answer each time
    private static List<String> tries(String answer, String expected) {
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            shown.add(AnswerChecker.feedback(answer, expected, SPANISH, shown));
            shown.add(AnswerChecker.hint(answer, expected, SPANISH, shown));
        }
        return shown;
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
    void hintButtonShowsTheMistakeInsteadOfTheStart() {
        assertEquals("Almost! An accent is missing: C_mo", hint("como", "Cómo...?"));
        assertEquals("Almost! Check the accent: C_mo …", hint("como", "Cómo te llamas?"));
        assertEquals("Almost! An accent is missing: De d_nde eres", hint("de donde eres", "De dónde eres?"));
    }

    @Test
    void hintButtonShowsWhichWordsOfASentenceAreWrong() {
        assertEquals("Almost! These letters are right: C_mo __ llamas", hint("como se llamas", "Cómo te llamas?"));
        assertEquals("Almost! These letters are right: Es la foto n_mero ___", hint("Es la foto numero dos", "Es la foto número uno."));
        assertEquals("Almost! One letter is missing: No es un_ isla", hint("No es un isla", "No es una isla."));
        assertEquals("Almost! These letters are right: __ tienes", hint("yo tienes", "tú tienes"));
        assertEquals("Almost! These letters are right: __ n_mero", hint("la numero", "el número"));
    }

    @Test
    void checkShowsTheSameMistakesOnTheSecondTry() {
        assertEquals("Almost! These letters are right: Es la foto n_mero ___",
                AnswerChecker.feedback("Es la foto numero dos", "Es la foto número uno.", SPANISH, List.of()));
        assertEquals("Almost! There's one word too many.",
                AnswerChecker.feedback("yo soy soy Nini", "Yo soy Nini", SPANISH, List.of()));
    }

    @Test
    void hintButtonShowsWhichLettersAreRightForNearlyRightAnswers() {
        assertEquals("Almost! These letters are right: __ apellido", hint("la apellidone", "el apellido"));
        assertEquals("Almost! There are a few letters too many.", hint("apellidion", "el apellido"));
        assertEquals("Almost! One letter is missing: da_o", hint("dao", "dado"));
        assertEquals("Almost! An accent is missing: adi_s", hint("adios", "adiós"));
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
