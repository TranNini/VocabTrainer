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
    void missingFileMeansEmpty() {
        assertTrue(new VocabStore(dir.resolve("nothing.txt")).getAll().isEmpty());
    }
}
