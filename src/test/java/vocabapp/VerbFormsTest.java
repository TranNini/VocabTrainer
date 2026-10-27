package vocabapp;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VerbFormsTest {
    private final Vocab get = new Vocab("to get", "bekommen", "irregular verbs", "", "", "got", "got / gotten");

    @Test
    void asksEachFormFromTheTranslationWithItsTense() {
        assertEquals("bekommen (present tense)", VerbForms.prompt(get, 0, new Random()));
        assertEquals("bekommen (past tense)", VerbForms.prompt(get, 1, new Random()));
        assertEquals("bekommen (past participle)", VerbForms.prompt(get, 2, new Random()));
        assertEquals("to get", VerbForms.expected(get, 0));
        assertEquals("got", VerbForms.expected(get, 1));
        assertEquals("got / gotten", VerbForms.expected(get, 2));
    }

    @Test
    void onlyAVerbWithItsFormsHasForms() {
        assertEquals(2, VerbForms.form(get, 2));
        assertNull(VerbForms.form(get, null));
        assertNull(VerbForms.form(get, 3));
        assertNull(VerbForms.form(new Vocab("to walk", "gehen", "verbs"), 1));
    }
}
