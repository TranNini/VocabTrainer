package vocabapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            throw new IllegalStateException(libraries.keySet().stream()
                    .filter(saved::equalsIgnoreCase).findFirst().orElse(saved) + " already exists.");
        }
        libraries.put(saved, new VocabLibrary(dir.resolve(saved)));
        return saved;
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
