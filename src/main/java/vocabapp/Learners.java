package vocabapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Everyone who learns with the app, each with their own languages: one folder per learner in the
// vocab folder, e.g. vocab/Nini/Spanish.txt
@Component
public class Learners {
    // Before languages existed everything was Spanish, saved in vocab.txt
    private static final String FIRST_LANGUAGE = "Spanish";
    // Deleted learners are kept here (not a learner itself, it starts with "."), so they can be restored
    static final String DELETED = ".deleted";

    private final Path dir;
    private final Map<String, VocabLibrary> libraries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // firstLearner gets the vocab saved before there were learners
    public Learners(@Value("${vocab.dir}") String dir, @Value("${vocab.old-file}") String oldFile,
                    @Value("${vocab.first-learner}") String firstLearner) {
        this.dir = Paths.get(dir);
        try {
            Files.createDirectories(this.dir);
            moveOldFiles(Paths.get(oldFile), VocabLibrary.cleanName(firstLearner, "learner name"));
            try (DirectoryStream<Path> folders = Files.newDirectoryStream(this.dir, Files::isDirectory)) {
                for (Path folder : folders) {
                    String name = folder.getFileName().toString();
                    // skips folders the NAS or the Mac add, like @Recycle or .DS_Store
                    if (isName(name)) {
                        libraries.put(name, new VocabLibrary(folder));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the vocab folder " + this.dir, e);
        }
    }

    public synchronized List<String> names() {
        return new ArrayList<>(libraries.keySet());
    }

    // null if there is no such learner
    public synchronized VocabLibrary library(String learner) {
        return libraries.get(learner);
    }

    // Creates an empty folder for a new learner; returns the saved name ("mia" -> "Mia")
    public synchronized String add(String name) {
        String saved = VocabLibrary.cleanName(name, "name");
        if (libraries.containsKey(saved)) {
            throw new IllegalStateException(savedName(saved) + " already exists.");
        }
        libraries.put(saved, new VocabLibrary(dir.resolve(saved)));
        return saved;
    }

    // Renames the learner's folder; returns the saved new name. Only the case may change ("mia" -> "Mia").
    // null if there is no such learner.
    public synchronized String rename(String learner, String newName) {
        String old = savedName(learner);
        if (old == null) {
            return null;
        }
        String saved = VocabLibrary.cleanName(newName, "name");
        if (libraries.containsKey(saved) && !saved.equalsIgnoreCase(old)) {
            throw new IllegalStateException(savedName(saved) + " already exists.");
        }
        try {
            // through a temporary name, so a change of case also works on the Mac, where "mia" and "Mia" are one folder
            Path temporary = dir.resolve("." + old + "-renaming");
            Files.move(dir.resolve(old), temporary);
            Files.move(temporary, dir.resolve(saved));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not rename the folder of " + old, e);
        }
        libraries.remove(old);
        libraries.put(saved, new VocabLibrary(dir.resolve(saved)));
        return saved;
    }

    // Moves the learner's folder to vocab/.deleted/<name> <date and time>, so nothing is lost for good;
    // false if there is no such learner
    public synchronized boolean delete(String learner) {
        String saved = savedName(learner);
        if (saved == null) {
            return false;
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss"));
        try {
            Path deleted = Files.createDirectories(dir.resolve(DELETED));
            Files.move(dir.resolve(saved), deleted.resolve(saved + " " + stamp));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete the folder of " + saved, e);
        }
        libraries.remove(saved);
        return true;
    }

    // The saved spelling of a learner's name ("nini" -> "Nini"), or null
    private String savedName(String learner) {
        return libraries.keySet().stream().filter(name -> name.equalsIgnoreCase(learner)).findFirst().orElse(null);
    }

    private static boolean isName(String name) {
        try {
            return VocabLibrary.cleanName(name, "name").equals(name);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // Only while there are no learner folders yet: vocab/Spanish.txt … -> vocab/<first learner>/,
    // or the even older vocab.txt -> vocab/<first learner>/Spanish.txt
    private void moveOldFiles(Path oldFile, String firstLearner) throws IOException {
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(dir, Files::isDirectory)) {
            for (Path folder : folders) {
                if (isName(folder.getFileName().toString())) {
                    return;
                }
            }
        }
        List<Path> languageFiles = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*" + VocabLibrary.EXTENSION)) {
            files.forEach(languageFiles::add);
        }
        Path target = dir.resolve(firstLearner);
        if (languageFiles.isEmpty() && Files.exists(oldFile)) {
            Files.createDirectories(target);
            Files.move(oldFile, target.resolve(FIRST_LANGUAGE + VocabLibrary.EXTENSION));
            System.out.println("Moved " + oldFile + " to " + target);
        }
        for (Path file : languageFiles) {
            Files.createDirectories(target);
            Files.move(file, target.resolve(file.getFileName()));
            System.out.println("Moved " + file + " to " + target);
        }
    }
}
