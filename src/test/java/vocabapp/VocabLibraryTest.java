package vocabapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabLibraryTest {

    @TempDir
    Path dir;

    private VocabLibrary library() {
        return new VocabLibrary(dir.resolve("vocab"));
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
