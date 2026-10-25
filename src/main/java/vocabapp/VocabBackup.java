package vocabapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.stream.Stream;

// A copy of the whole vocab folder (every learner, their quiz results and .deleted) and the access code, every
// night into backups/<date>/, kept for a number of days. On the NAS the backups folder is on the same disk:
// it is against mistakes (a list deleted or saved over), not against a broken disk.
@Component
public class VocabBackup {
    private static final Logger log = LoggerFactory.getLogger(VocabBackup.class);

    private final Path vocabDir;
    private final Path accessCodeFile;
    private final Path backupDir;
    private final int keepDays;

    public VocabBackup(@Value("${vocab.dir}") String vocabDir, @Value("${vocab.access-code-file}") String accessCodeFile,
                       @Value("${vocab.backup-dir}") String backupDir, @Value("${vocab.backup-days}") int keepDays) {
        this.vocabDir = Path.of(vocabDir);
        this.accessCodeFile = Path.of(accessCodeFile);
        this.backupDir = Path.of(backupDir);
        this.keepDays = keepDays;
    }

    // At 03:30, when nobody practises (a list being saved at that moment could be copied half written)
    @Scheduled(cron = "${vocab.backup-time}")
    public void nightly() {
        try {
            backUp(LocalDate.now());
        } catch (RuntimeException e) {
            log.error("Vocab backup failed", e);
        }
    }

    // Also right after a start, when today has no copy yet: an update then shows at once whether backups work
    @EventListener(ApplicationReadyEvent.class)
    public void afterStart() {
        if (!Files.isDirectory(backupDir.resolve(LocalDate.now().toString()))) {
            nightly();
        }
    }

    // Copies everything into backups/<day>/ (replacing an older copy of that day) and removes the copies
    // older than keepDays; returns the folder
    Path backUp(LocalDate day) {
        Path target = backupDir.resolve(day.toString());
        try {
            deleteTree(target);
            Files.createDirectories(target);
            if (Files.isDirectory(vocabDir)) {
                copyTree(vocabDir, target.resolve(vocabDir.getFileName()));
            }
            if (Files.isRegularFile(accessCodeFile)) {
                Files.copy(accessCodeFile, target.resolve(accessCodeFile.getFileName()));
            }
            removeOlderThan(day.minusDays(keepDays - 1));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not back up the vocab into " + target, e);
        }
        log.info("Vocab backed up into {}", target);
        return target;
    }

    // Removes the dated folders before the first day to keep; other folders are left alone
    private void removeOlderThan(LocalDate firstKept) throws IOException {
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(backupDir, Files::isDirectory)) {
            for (Path folder : folders) {
                try {
                    if (LocalDate.parse(folder.getFileName().toString()).isBefore(firstKept)) {
                        deleteTree(folder);
                    }
                } catch (DateTimeParseException e) {
                    // not one of ours
                }
            }
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path copy = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(copy);
                } else {
                    Files.copy(path, copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        }
    }
}
