package vocabapp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellCheckerTest {
    private static final SpellChecker CHECKER = new SpellChecker();

    private static List<String> wrong(String text, String dictionary) {
        return wrong(text, dictionary == null ? List.of() : List.of(dictionary));
    }

    private static List<String> wrong(String text, List<String> dictionaries) {
        return CHECKER.check(text, dictionaries).stream().map(SpellChecker.Misspelling::word).toList();
    }

    @Test
    void findsSpanishTyposAndSuggestsTheWord() {
        List<SpellChecker.Misspelling> found = CHECKER.check("nostoros queremos", List.of("es"));
        assertEquals(1, found.size());
        assertEquals("nostoros", found.get(0).word());
        assertEquals("nosotros", found.get(0).suggestions().get(0));
        assertEquals(List.of("médicia"), wrong("Yo tengo una cita médicia", "es"));
        assertEquals(List.of("tambien"), wrong("tambien", "es"));
    }

    @Test
    void acceptsRightSpanishWithAccentsSignsAndAlternatives() {
        assertTrue(wrong("¿Cómo te llamas?", "es").isEmpty());
        assertTrue(wrong("él / ella / usted quiere", "es").isEmpty());
        assertTrue(wrong("el niño / la niña", "es").isEmpty());
        assertTrue(wrong("Yo soy...", "es").isEmpty());
    }

    @Test
    void checksTheEnglishSide() {
        assertEquals(List.of("appointmen"), wrong("I have an appointmen", SpellChecker.ENGLISH));
        assertTrue(wrong("you have (plural) / you all have", SpellChecker.ENGLISH).isEmpty());
        assertTrue(wrong("I don't understand", SpellChecker.ENGLISH).isEmpty());
    }

    @Test
    void englishAcceptsAmericanAndBritishSpelling() {
        assertTrue(wrong("colour / color", SpellChecker.ENGLISH).isEmpty());
        assertTrue(wrong("to organise / to organize", SpellChecker.ENGLISH).isEmpty());
        assertEquals(List.of("colur"), wrong("colur", SpellChecker.ENGLISH));
    }

    @Test
    void takesCapitalisedWordsInsideAsNames() {
        assertTrue(wrong("Yo soy Nini", "es").isEmpty());
        // a lower-case country is still a mistake
        assertEquals(List.of("alemania"), wrong("alemania", "es"));
    }

    @Test
    void checksGermanAlsoTheCapitalisedNouns() {
        List<SpellChecker.Misspelling> found = CHECKER.check("das Hauss", List.of("de"));
        assertEquals(List.of("Hauss"), found.stream().map(SpellChecker.Misspelling::word).toList());
        assertEquals("Haus", found.get(0).suggestions().get(0));
        assertTrue(wrong("das Mädchen / die Straße / Tschüss", "de").isEmpty());
        assertEquals(List.of("Strasse", "haus"), wrong("die Strasse / das haus", "de"));
        assertEquals("de", Language.forName("german").getSpellDictionary());
    }

    @Test
    void languagesWithoutADictionaryAreNotChecked() {
        assertTrue(wrong("xyzzy", (String) null).isEmpty());
    }
}
