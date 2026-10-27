package vocabapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabStoreTest {

    @TempDir
    Path dir;

    @Test
    void loadsOldFilesWithoutCategory() throws IOException {
        Path file = dir.resolve("vocab.txt");
        Files.writeString(file, "hola;hello\n\nir;to go;verbs\n");
        VocabStore store = new VocabStore(file);
        assertEquals(2, store.getAll().size());
        assertEquals(Vocab.DEFAULT_CATEGORY, store.getAll().get(0).getCategory());
        assertEquals("verbs", store.getAll().get(1).getCategory());
    }

    @Test
    void savesAddReplaceAndRemove() throws IOException {
        Path file = dir.resolve("vocab.txt");
        VocabStore store = new VocabStore(file);
        Vocab hablar = new Vocab("hablar", "to talk", "verbs");
        Vocab bien = new Vocab("bien", "good", "");
        store.add(hablar);
        store.add(bien);
        store.replace(hablar, new Vocab("hablar", "to talk/to speak", "verbs"));
        store.remove(bien);

        assertEquals(List.of("hablar;to talk/to speak;verbs"), Files.readAllLines(file));
        assertEquals("to talk/to speak", new VocabStore(file).getAll().get(0).getEnglish());
    }

    @Test
    void editedVocabKeepsItsId() {
        VocabStore store = new VocabStore(dir.resolve("vocab.txt"));
        Vocab hablar = new Vocab("hablar", "to talk", "verbs");
        Vocab ir = new Vocab("ir", "to go", "verbs");
        store.add(hablar);
        store.add(ir);
        int id = store.idOf(ir);
        Vocab updated = new Vocab("ir", "to go/to leave", "verbs");
        store.replace(ir, updated);

        assertEquals(id, store.idOf(updated));
        assertEquals(updated, store.findById(id));
        assertTrue(store.idOf(hablar) != id);
        store.remove(updated);
        assertEquals(null, store.findById(id));
    }

    @Test
    void savesContextAndReadingAsFourthAndFifthColumnOnlyWhenThereAreSome() throws IOException {
        Path file = dir.resolve("vocab.txt");
        Files.writeString(file, "hablar;to talk;verbs\nser;to be;verbs;permanent things, e.g. origin\n猫;cat;general;;neko\n");
        VocabStore store = new VocabStore(file);
        assertEquals("", store.getAll().get(0).getContext());
        assertEquals("permanent things, e.g. origin", store.getAll().get(1).getContext());
        assertEquals("", store.getAll().get(1).getReading());
        assertEquals("neko", store.getAll().get(2).getReading());
        assertEquals("", store.getAll().get(2).getContext());

        store.add(new Vocab("estar", "to be", "verbs", "  temporary states "));
        store.add(new Vocab("犬", "dog", "general", "a pet", "inu"));
        assertEquals(List.of("hablar;to talk;verbs", "ser;to be;verbs;permanent things, e.g. origin",
                "猫;cat;general;;neko", "estar;to be;verbs;temporary states", "犬;dog;general;a pet;inu"),
                Files.readAllLines(file));
    }

    @Test
    void missingFileMeansEmpty() {
        assertTrue(new VocabStore(dir.resolve("nothing.txt")).getAll().isEmpty());
    }

    @Test
    void keepsTheOtherFormsOfAnEnglishVerb() throws IOException {
        Path file = dir.resolve("English.txt");
        VocabStore store = new VocabStore(file);
        store.add(new Vocab("to fly", "fliegen", "verbs", "", "", "flew", "flown"));
        store.add(new Vocab("to get", "bekommen", "verbs", "irregular", "", "got", "got / gotten"));
        store.add(new Vocab("to walk", "gehen", "verbs"));

        assertEquals(List.of("to fly;fliegen;verbs;;;flew;flown", "to get;bekommen;verbs;irregular;;got;got / gotten",
                "to walk;gehen;verbs"), Files.readAllLines(file));
        Vocab get = new VocabStore(file).getAll().get(1);
        assertEquals("got / gotten", get.getParticiple());
        assertEquals("to get – got – got / gotten", get.getWordWithForms());
        assertTrue(new VocabStore(file).getAll().get(0).hasForms());
    }
}
