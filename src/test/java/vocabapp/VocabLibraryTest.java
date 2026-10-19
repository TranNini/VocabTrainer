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

    @Test
    void recognisesTheTranslationOfAnOldListFromItsWords(@TempDir Path dir) throws Exception {
        Path learner = Files.createDirectories(dir.resolve("Kiki"));
        Files.writeString(learner.resolve("Spanisch.txt"),
                "la casa;das Haus;general\nel perro;der Hund;general\nhablar;sprechen;verben\n");
        Files.writeString(learner.resolve("Spanish.txt"), "la casa;the house;general\nhablar;to speak;verbs\n");
        Files.writeString(learner.resolve("Italian.txt"), "");
        VocabLibrary library = new VocabLibrary(learner, new SpellChecker()::guessTranslation);
        assertEquals("German", library.language("Spanisch").getTranslation().getName());
        assertEquals("English", library.language("Spanish").getTranslation().getName());
        assertEquals("English", library.language("Italian").getTranslation().getName()); // no words: English
        // saved, so it is never guessed again
        assertEquals("German", Files.readString(learner.resolve("Spanisch.translation")).trim());
    }

    @Test
    void takesTheOldTranslationSettingOfTheLearner(@TempDir Path dir) throws Exception {
        Path learner = Files.createDirectories(dir.resolve("Kiki"));
        Files.writeString(learner.resolve("Spanisch.txt"), "");
        Files.writeString(learner.resolve(VocabLibrary.OLD_TRANSLATION_FILE), "German\n");
        VocabLibrary library = new VocabLibrary(learner);
        assertEquals("German", library.language("Spanisch").getTranslation().getName());
        assertTrue(Files.notExists(learner.resolve(VocabLibrary.OLD_TRANSLATION_FILE)));
    }
}
