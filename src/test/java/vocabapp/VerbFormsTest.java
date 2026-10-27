package vocabapp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerbFormsTest {
    private final Language english = Language.forName("English").translatedInto(Language.forName("German"));
    private final VerbForms get = new VerbForms(
            new Vocab("to get", "bekommen", "verbs", "", "", "got", "got / gotten"), english);

    @Test
    void everyFormIsCheckedOnItsOwnWithItsAlternatives() {
        assertEquals(List.of(true, true, true), get.right(List.of("to get", "got", "gotten")));
        assertEquals(List.of(true, true, true), get.right(List.of("To get ", "got", "got")));
        assertEquals(List.of(false, true, false), get.right(List.of("get", "got", "")));
        assertEquals(List.of(false, false, false), get.right(null));
        assertEquals("to get – got – got / gotten", get.fullAnswer());
    }

    @Test
    void theHintsAreForTheFirstWrongFormAndNeverRepeat() {
        String first = get.feedback(List.of("to get", "gat", "got"), List.of());
        assertTrue(first.startsWith("Simple past: "), first);
        String next = get.hint(List.of("to get", "gat", "got"), List.of(first));
        assertTrue(next.startsWith("Simple past: "), next);
        assertTrue(!next.equals(first), next);
        assertEquals("That's right! Press Check.", get.hint(List.of("to get", "got", "got"), List.of()));
    }
}
