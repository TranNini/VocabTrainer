package vocabapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabLibraryTest {

    @TempDir
    Path dir;

    private VocabLibrary library() {
        return new VocabLibrary(dir.resolve("vocab").toString(), dir.resolve("vocab.txt").toString());
    }

    @Test
    void movesTheOldFileToSpanish() throws IOException {
        Files.writeString(dir.resolve("vocab.txt"), "hablar;to talk;verbs\n");
        VocabLibrary library = library();

        assertFalse(Files.exists(dir.resolve("vocab.txt")));
        assertEquals("hablar;to talk;verbs\n", Files.readString(dir.resolve("vocab/Spanish.txt")));
        assertEquals(List.of("Spanish"), library.languageNames());
        assertEquals("hablar", library.store("spanish").getAll().get(0).getWord());
    }

    @Test
    void leavesTheOldFileAloneOnceLanguagesExist() throws IOException {
        Files.createDirectories(dir.resolve("vocab"));
        Files.writeString(dir.resolve("vocab/Spanish.txt"), "ir;to go;verbs\n");
        Files.writeString(dir.resolve("vocab.txt"), "hablar;to talk;verbs\n");
        library();
        assertTrue(Files.exists(dir.resolve("vocab.txt")));
        assertEquals("ir;to go;verbs\n", Files.readString(dir.resolve("vocab/Spanish.txt")));
    }

    @Test
    void addsLanguagesAsFiles() {
        VocabLibrary library = library();
        assertEquals("Italian", library.addLanguage("  italian "));
        assertTrue(Files.exists(dir.resolve("vocab/Italian.txt")));
        assertEquals(List.of("Italian"), library().languageNames());
        assertThrows(IllegalStateException.class, () -> library.addLanguage("ITALIAN"));
        assertThrows(IllegalArgumentException.class, () -> library.addLanguage("../secret"));
        assertThrows(IllegalArgumentException.class, () -> library.addLanguage(""));
    }
}
