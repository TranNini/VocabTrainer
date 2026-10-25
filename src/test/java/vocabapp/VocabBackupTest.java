package vocabapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabBackupTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 25);

    @TempDir
    Path dir;

    private VocabBackup backup() {
        return new VocabBackup(dir.resolve("vocab").toString(), dir.resolve("access-code.txt").toString(),
                dir.resolve("backups").toString(), 30);
    }

    @Test
    void copiesEveryLearnerTheDeletedListsAndTheAccessCode() throws IOException {
        Files.createDirectories(dir.resolve("vocab/Nini"));
        Files.createDirectories(dir.resolve("vocab/.deleted/Kiki Korean 2026-10-24 10-00-00"));
        Files.writeString(dir.resolve("vocab/Nini/Spanish.txt"), "ir;to go;verbs\n");
        Files.writeString(dir.resolve("vocab/Nini/Spanish.log"), "2026-10-25T08:00;ir;in;1;0\n");
        Files.writeString(dir.resolve("vocab/.deleted/Kiki Korean 2026-10-24 10-00-00/Korean.txt"), "물;Wasser;\n");
        Files.writeString(dir.resolve("access-code.txt"), "abc\n");

        Path copy = backup().backUp(DAY);

        assertEquals(dir.resolve("backups/2026-10-25"), copy);
        assertEquals("ir;to go;verbs\n", Files.readString(copy.resolve("vocab/Nini/Spanish.txt")));
        assertTrue(Files.exists(copy.resolve("vocab/Nini/Spanish.log")));
        assertTrue(Files.exists(copy.resolve("vocab/.deleted/Kiki Korean 2026-10-24 10-00-00/Korean.txt")));
        assertEquals("abc\n", Files.readString(copy.resolve("access-code.txt")));
    }

    @Test
    void aSecondCopyTheSameDayReplacesTheFirst() throws IOException {
        Files.createDirectories(dir.resolve("vocab/Nini"));
        Files.writeString(dir.resolve("vocab/Nini/Spanish.txt"), "ir;to go;verbs\n");
        backup().backUp(DAY);
        Files.delete(dir.resolve("vocab/Nini/Spanish.txt"));
        Files.writeString(dir.resolve("vocab/Nini/German.txt"), "gehen;to go;verbs\n");

        Path copy = backup().backUp(DAY);

        assertFalse(Files.exists(copy.resolve("vocab/Nini/Spanish.txt")));
        assertTrue(Files.exists(copy.resolve("vocab/Nini/German.txt")));
    }

    @Test
    void keepsThirtyDaysAndLeavesOtherFoldersAlone() throws IOException {
        Files.createDirectories(dir.resolve("vocab"));
        Files.createDirectories(dir.resolve("backups/2026-09-25"));
        Files.createDirectories(dir.resolve("backups/2026-09-26"));
        Files.createDirectories(dir.resolve("backups/old stuff"));

        backup().backUp(DAY);

        assertFalse(Files.exists(dir.resolve("backups/2026-09-25")));
        assertTrue(Files.exists(dir.resolve("backups/2026-09-26")));
        assertTrue(Files.exists(dir.resolve("backups/old stuff")));
        assertTrue(Files.exists(dir.resolve("backups/2026-10-25")));
    }
}
