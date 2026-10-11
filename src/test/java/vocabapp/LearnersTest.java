package vocabapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearnersTest {

    @TempDir
    Path dir;

    private Learners learners() {
        return new Learners(dir.resolve("vocab").toString(), dir.resolve("vocab.txt").toString(), "Nini");
    }

    @Test
    void movesTheLanguagesFromBeforeLearnersToTheFirstLearner() throws IOException {
        Files.createDirectories(dir.resolve("vocab"));
        Files.writeString(dir.resolve("vocab/Spanish.txt"), "ir;to go;verbs\n");
        Files.writeString(dir.resolve("vocab/Vietnamese.txt"), "yêu;to love;verbs\n");
        Learners learners = learners();

        assertEquals(List.of("Nini"), learners.names());
        assertFalse(Files.exists(dir.resolve("vocab/Spanish.txt")));
        assertEquals("ir;to go;verbs\n", Files.readString(dir.resolve("vocab/Nini/Spanish.txt")));
        assertEquals(List.of("Spanish", "Vietnamese"), learners.library("nini").languageNames());
    }

    @Test
    void movesTheOldFileToSpanishOfTheFirstLearner() throws IOException {
        Files.writeString(dir.resolve("vocab.txt"), "hablar;to talk;verbs\n");
        Learners learners = learners();

        assertFalse(Files.exists(dir.resolve("vocab.txt")));
        assertEquals("hablar;to talk;verbs\n", Files.readString(dir.resolve("vocab/Nini/Spanish.txt")));
        assertEquals("hablar", learners.library("Nini").store("spanish").getAll().get(0).getWord());
    }

    @Test
    void leavesEverythingAloneOnceThereAreLearners() throws IOException {
        Files.createDirectories(dir.resolve("vocab/Mia"));
        Files.writeString(dir.resolve("vocab/Spanish.txt"), "ir;to go;verbs\n");
        Files.writeString(dir.resolve("vocab.txt"), "hablar;to talk;verbs\n");
        Learners learners = learners();

        assertEquals(List.of("Mia"), learners.names());
        assertTrue(Files.exists(dir.resolve("vocab/Spanish.txt")));
        assertTrue(Files.exists(dir.resolve("vocab.txt")));
    }

    @Test
    void startsWithoutLearnersWhenThereIsNoVocab() {
        assertEquals(List.of(), learners().names());
    }

    @Test
    void addsLearnersAsFoldersWithTheirOwnLanguages() {
        Learners learners = learners();
        assertEquals("Mia", learners.add(" mia "));
        assertEquals("Nini", learners.add("Nini"));
        learners.library("Mia").addLanguage("Spanish");

        assertTrue(Files.exists(dir.resolve("vocab/Mia/Spanish.txt")));
        assertEquals(List.of(), learners.library("Nini").languageNames());
        assertEquals(List.of("Mia", "Nini"), learners().names());
        assertThrows(IllegalStateException.class, () -> learners.add("MIA"));
        assertThrows(IllegalArgumentException.class, () -> learners.add("../secret"));
        assertNull(learners.library("Nobody"));
    }

    @Test
    void skipsFoldersThatAreNotLearners() throws IOException {
        Files.createDirectories(dir.resolve("vocab/@Recycle"));
        Files.createDirectories(dir.resolve("vocab/.hidden"));
        Files.createDirectories(dir.resolve("vocab/Mia"));
        assertEquals(List.of("Mia"), learners().names());
    }
}
