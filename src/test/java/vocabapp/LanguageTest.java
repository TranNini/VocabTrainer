package vocabapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageTest {

    @Test
    void spanishHasItsExtras() {
        Language spanish = Language.forName("Spanish");
        assertTrue(spanish.hasIpa());
        assertEquals("/ˈo.la/", spanish.ipa("¡Hola!"));
        assertEquals("SpanishDict", spanish.getDictionaryName());
        assertEquals("https://www.spanishdict.com/translate/Buenas%20noches", spanish.dictionaryUrl("¡Buenas noches!"));
        assertEquals("https://www.spanishdict.com/translate/el%20ni%C3%B1o", spanish.dictionaryUrl("el niño / niño"));
    }

    @Test
    void otherLanguagesGetTheBasics() {
        Language italian = Language.forName("Italian");
        assertFalse(italian.hasIpa());
        assertNull(italian.ipa("ciao"));
        assertTrue(italian.getArticles().isEmpty());
        assertEquals("https://en.wiktionary.org/wiki/ciao#Italian", italian.dictionaryUrl("ciao"));
    }

    @Test
    void vietnameseUsesVDictAndHasToneMarks() {
        Language vietnamese = Language.forName("Vietnamese");
        assertEquals("VDict", vietnamese.getDictionaryName());
        assertEquals("https://vdict.com/c%E1%BA%A3m%20%C6%A1n,2,0,0.html", vietnamese.dictionaryUrl("cảm ơn!"));
        assertTrue(vietnamese.getSpecialLetters().containsAll(java.util.List.of("ă", "đ", "ư", "\u0301", "\u0323")));
        assertFalse(vietnamese.hasIpa());
    }

    @Test
    void sectionLettersFollowTheLanguage() {
        assertEquals("A", Language.forName("Spanish").sectionLetter("árbol"));
        assertEquals("Ñ", Language.forName("Spanish").sectionLetter("ñandú"));
        assertEquals("A", Language.forName("German").sectionLetter("Äpfel"));
        assertEquals("E", Language.forName("French").sectionLetter("école"));
        assertEquals("#", Language.forName("French").sectionLetter(""));
    }
}
