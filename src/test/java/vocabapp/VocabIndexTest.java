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
    void sortsGermanIgnoringArticlesWithUmlautsUnderTheirLetter() {
        Language german = Language.forName("German");
        Vocab apfel = general("der Apfel");
        Vocab aerger = general("der Ärger");
        VocabIndex index = new VocabIndex(List.of(general("die Zeit"), aerger, general("das Haus"), apfel,
                general("ein Buch")), german);
        assertEquals(List.of("der Apfel", "der Ärger", "ein Buch", "das Haus", "die Zeit"), spanish(index.search(null, "")));
        assertEquals("A", index.sectionLetter(aerger));
    }

    @Test
    void groupsConjugationsInGermanVerbCategoriesForSpanischToo() {
        Language spanisch = Language.forName("Spanisch");
        Vocab tener = new Vocab("tener", "haben", "verbformen");
        Vocab tengo = new Vocab("yo tengo", "ich habe", "verbformen");
        Vocab tienes = new Vocab("tú tienes", "du hast", "verbformen");
        Vocab leer = new Vocab("leer", "lesen", "verben");
        VocabIndex index = new VocabIndex(List.of(leer, tener, tienes, tengo), spanisch);
        assertEquals(List.of("leer", "tener", "yo tengo", "tú tienes"), spanish(index.search(null, "")));
        assertTrue(index.isConjugation(tengo));
        assertTrue(Vocab.isVerbCategory("Verben"));
        assertTrue(!Vocab.isVerbCategory("sätze"));
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
    void aDuplicateHasTheSameWordAndTranslation() {
        Language vietnamese = Language.forName("Vietnamese");
        Vocab tinhCach = new Vocab("tính cách", "character / personality", "general");
        Vocab hola = new Vocab("¡Hola!", "hello/hi", "Sentences");
        VocabIndex index = new VocabIndex(List.of(tinhCach, hola), vietnamese);

        // another word with the same translation, or the same word with another translation: not a duplicate
        assertEquals(List.of(), index.duplicatesOf("nhân vật", "character", null));
        assertEquals(List.of(), index.duplicatesOf("tính cách", "temper", null));
        // the same meaning: one alternative of each side is enough, case and punctuation don't matter
        assertEquals(List.of(tinhCach), index.duplicatesOf("Tính cách!", "personality", null));
        assertEquals(List.of(hola), index.duplicatesOf("hola / buenas", "Hi", null));
        // a missing accent or a typo is no duplicate, but similar: the app asks before saving it
        assertEquals(List.of(), index.duplicatesOf("tính cách", "personalty", null));
        assertEquals(List.of(tinhCach), index.similarTo("tính cách", "personalty", null));
        assertEquals(List.of(tinhCach), index.similarTo("tinh cach", "personality", null));
        assertEquals(List.of(hola), index.similarTo("hóla", "hi", null));
        // a duplicate isn't also similar; the same word with another translation is neither
        assertEquals(List.of(), index.similarTo("tính cách", "personality", null));
        assertEquals(List.of(), index.similarTo("tính cách", "temper", null));
        // other meanings stay apart: "ma = ghost" next to "má = mother", short words need more than a typo
        VocabIndex ma = new VocabIndex(List.of(new Vocab("má", "mother", "general")), vietnamese);
        assertEquals(List.of(), ma.similarTo("ma", "ghost", null));
        assertEquals(List.of(), index.similarTo("hola", "hey", null));
        // sentences that only start alike are neither
        VocabIndex soyDe = new VocabIndex(List.of(new Vocab("Yo soy de Alemania", "I am from Germany", "sentences")),
                SPANISH);
        assertEquals(List.of(), soyDe.duplicatesOf("Yo soy de Austria", "I am from Austria", null));
        assertEquals(List.of(), soyDe.similarTo("Yo soy de Austria", "I am from Austria", null));
        // a short form is read out in full: other verbs aren't duplicates, the same one is
        VocabIndex verbs = new VocabIndex(List.of(new Vocab("el / ella / usted quiere", "he / she / it wants", "verbs")),
                SPANISH);
        assertEquals(List.of(), verbs.duplicatesOf("el / ella / usted corre", "he / she / it runs", null));
        assertEquals(1, verbs.duplicatesOf("ella quiere", "she wants", null).size());
        // the same in German, as the language learned or as the translation
        VocabIndex german = new VocabIndex(List.of(new Vocab("er / sie / es hat", "he / she / it has", "verben"),
                new Vocab("sie / Sie haben", "they / you have", "verben")), Language.forName("German"));
        assertEquals(List.of(), german.duplicatesOf("er / sie / es läuft", "he / she / it runs", null));
        assertEquals(List.of(), german.duplicatesOf("sie / Sie sind", "they / you are", null));
        assertEquals(1, german.duplicatesOf("sie hat", "she has", null).size());
        VocabIndex kiki = new VocabIndex(List.of(new Vocab("él / ella / usted tiene", "er / sie / es hat", "verbformen")),
                Language.forName("Spanisch").translatedInto(Language.forName("Deutsch")));
        assertEquals(List.of(), kiki.duplicatesOf("él / ella / usted corre", "er / sie / es läuft", null));
        assertEquals(1, kiki.duplicatesOf("ella tiene", "sie hat", null).size());
        // the start of a sentence isn't the words alone; two starts are the same
        VocabIndex soy = new VocabIndex(List.of(new Vocab("Yo soy...", "I am...", "sentences")), SPANISH);
        assertEquals(List.of(), soy.duplicatesOf("yo soy", "I am", null));
        assertEquals(1, soy.duplicatesOf("yo soy…", "I am…", null).size());
        // but "!" is no difference: "Buenos días" is "¡Buenos días!"
        VocabIndex dias = new VocabIndex(List.of(new Vocab("¡Buenos días!", "Good morning", "sentences")), SPANISH);
        assertEquals(1, dias.duplicatesOf("Buenos días", "good morning", null).size());
        // editing an entry isn't a duplicate of itself
        assertEquals(List.of(), index.duplicatesOf("tính cách", "character", tinhCach));
    }

    @Test
    void findsExistingCategorySpelling() {
        VocabIndex index = new VocabIndex(List.of(verb("hablar")), SPANISH);
        assertEquals("verbs", index.findCategory("VERBS"));
        assertNull(index.findCategory("verb"));
    }
}
