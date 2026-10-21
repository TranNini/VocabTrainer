package vocabapp;

import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertEquals(java.util.List.of("\u0300", "\u0301", "\u0309", "\u0303", "\u0323"), vietnamese.getSpecialLetters());
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

    @Test
    void knowsLanguagesByTheirGermanAndOwnNames() {
        Language spanisch = Language.forName("Spanisch");
        assertEquals("Spanisch", spanisch.getName());
        assertEquals(List.of("es"), spanisch.getSpellDictionaries());
        assertTrue(spanisch.hasIpa());
        assertTrue(spanisch.arePronounsOptional());
        assertEquals("Ñ", spanisch.sectionLetter("ñandú"));
        assertEquals(List.of("de"), Language.forName("Deutsch").getSpellDictionaries());
        assertEquals(List.of("es"), Language.forName("Español").getSpellDictionaries());
    }

    @Test
    void spanishLettersOnBothSides() {
        List<String> letters = List.of("á", "é", "í", "ó", "ú", "ñ", "ü", "¿", "¡");
        assertEquals(letters, Language.forName("Spanish").getSpecialLetters());
        // Spanish as the translation: German into Spanisch
        assertEquals(letters, Language.forName("German").translatedInto(Language.forName("Spanisch"))
                .getTranslation().getSpecialLetters());
    }

    @Test
    void knowsLanguagesByManyNamesWithOrWithoutAccents() {
        for (String name : List.of("Vietnamese", "Vietnamesisch", "Tiếng Việt", "Tieng Viet", "tiếng việt", "Vietnamita")) {
            assertEquals("Vietnamese", Language.forName(name).getEnglishName(), name);
            assertEquals("VDict", Language.forName(name).getDictionaryName(), name);
        }
        for (String name : List.of("Deutsch", "Tiếng Đức", "Tieng Duc", "Alemán", "Aleman")) {
            assertEquals("German", Language.forName(name).getEnglishName(), name);
            assertEquals(List.of("de"), Language.forName(name).getSpellDictionaries(), name);
        }
        for (String name : List.of("Englisch", "Inglés", "Tiếng Anh")) {
            assertEquals("English", Language.forName(name).getEnglishName(), name);
        }
        assertEquals("Spanish", Language.forName("Tiếng Tây Ban Nha").getEnglishName());
        // a language the app has no rules for keeps its name
        assertEquals("Brazilian Portuguese", Language.forName("Brazilian Portuguese").getEnglishName());
    }
}
