package vocabapp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabIndexTest {
    private static final Language SPANISH = Language.forName("Spanish");

    private static Vocab general(String spanish) {
        return new Vocab(spanish, "x", Vocab.DEFAULT_CATEGORY);
    }

    private static Vocab verb(String spanish) {
        return new Vocab(spanish, "x", Vocab.VERBS_CATEGORY);
    }

    private static List<String> spanish(List<Vocab> list) {
        List<String> words = new ArrayList<>();
        for (Vocab v : list) {
            words.add(v.getWord());
        }
        return words;
    }

    @Test
    void sortsIgnoringArticlesAccentsAndPunctuation() {
        VocabIndex index = new VocabIndex(List.of(
                general("el perro"), general("¡Hola!"), general("la abuela"), general("bien"), general("árbol")), SPANISH);
        assertEquals(List.of("la abuela", "árbol", "bien", "¡Hola!", "el perro"), spanish(index.search(null, "")));
    }

    @Test
    void singleLetterGivesSection() {
        Vocab abuela = general("la abuela");
        Vocab arbol = general("árbol");
        VocabIndex index = new VocabIndex(List.of(abuela, general("el perro"), arbol), SPANISH);
        assertEquals(List.of("la abuela", "árbol"), spanish(index.search(null, "a")));
        assertEquals("A", index.sectionLetter(arbol));
    }

    @Test
    void keepsÑAsItsOwnSection() {
        Vocab nino = general("el niño");
        Vocab nube = general("la nube");
        Vocab nandu = general("el ñandú");
        VocabIndex index = new VocabIndex(List.of(nandu, nube, nino), SPANISH);
        assertEquals(List.of("el niño", "la nube", "el ñandú"), spanish(index.search(null, "")));
        assertEquals("Ñ", index.sectionLetter(nandu));
        assertEquals(List.of("el ñandú"), spanish(index.search(null, "ñ")));
    }

    @Test
    void longerQuerySearchesBothLanguages() {
        VocabIndex index = new VocabIndex(List.of(
                new Vocab("el gato", "cat", "general"), new Vocab("el perro", "dog", "general")), SPANISH);
        assertEquals(List.of("el gato"), spanish(index.search(null, "gat")));
        assertEquals(List.of("el perro"), spanish(index.search(null, "do")));
    }

    @Test
    void groupsConjugationsUnderTheInfinitiveSavedBeforeThem() {
        Vocab yoTengo = verb("yo tengo");
        Vocab yoSoy = general("yo soy");
        VocabIndex index = new VocabIndex(List.of(
                verb("hablar"), verb("tener"), verb("tú tienes"), yoTengo, verb("ir"), verb("ellos van"), verb("yo voy"), yoSoy), SPANISH);

        assertEquals(List.of("hablar", "ir", "yo voy", "ellos van", "tener", "yo tengo", "tú tienes", "yo soy"),
                spanish(index.search(null, "")));
        assertEquals(List.of("tener", "yo tengo", "tú tienes"), spanish(index.search(null, "t")));
        assertEquals("T", index.sectionLetter(yoTengo));
        assertTrue(index.isConjugation(yoTengo));
        assertFalse(index.isConjugation(yoSoy));
    }

    @Test
    void languagesWithoutRulesSortPlainlyAndDontGroup() {
        Language italian = Language.forName("Italian");
        Vocab io = new Vocab("io ho", "I have", Vocab.VERBS_CATEGORY);
        VocabIndex index = new VocabIndex(List.of(
                new Vocab("avere", "to have", Vocab.VERBS_CATEGORY), io, new Vocab("il cane", "dog", "general"),
                new Vocab("è", "is", "general")), italian);
        assertEquals(List.of("avere", "è", "il cane", "io ho"), spanish(index.search(null, "")));
        assertFalse(index.isConjugation(io));
        assertEquals("E", index.sectionLetter(new Vocab("è", "is", "general")));
    }

    @Test
    void filtersByCategory() {
        VocabIndex index = new VocabIndex(List.of(verb("hablar"), general("bien"), new Vocab("hola", "hi", "Verbs")), SPANISH);
        assertEquals(List.of("hablar", "hola"), spanish(index.inCategory("verbs")));
        assertEquals(3, index.inCategory(null).size());
        assertEquals(List.of("bien"), spanish(index.search("general", "")));
    }

    @Test
    void countsCategoriesIgnoringCase() {
        VocabIndex index = new VocabIndex(List.of(verb("hablar"), new Vocab("ir", "to go", "Verbs"), general("bien")), SPANISH);
        assertEquals(Map.of("general", 1, "verbs", 2), Map.copyOf(index.categoryCounts()));
    }

    @Test
    void resolvesCategoryInput() {
        VocabIndex index = new VocabIndex(List.of(new Vocab("hola", "hi", "Sentences")), SPANISH);
        assertEquals("general", index.resolveCategory(""));
        assertEquals("Sentences", index.resolveCategory("sentences"));
        assertNull(index.resolveCategory("verb"));
        assertEquals("general", new VocabIndex(List.of(), SPANISH).resolveCategory("General"));
    }

    @Test
    void findsExistingCategorySpelling() {
        VocabIndex index = new VocabIndex(List.of(verb("hablar")), SPANISH);
        assertEquals("verbs", index.findCategory("VERBS"));
        assertNull(index.findCategory("verb"));
    }
}
