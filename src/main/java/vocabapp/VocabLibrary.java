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

// All languages you learn: one file per language in the vocab folder, e.g. vocab/Spanish.txt
@Component
public class VocabLibrary {
    private static final String EXTENSION = ".txt";
    // Before languages existed everything was Spanish, saved in vocab.txt
    private static final String FIRST_LANGUAGE = "Spanish";

    private final Path dir;
    private final Map<String, VocabStore> stores = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Language> languages = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public VocabLibrary(@Value("${vocab.dir}") String dir, @Value("${vocab.old-file}") String oldFile) {
        this.dir = Paths.get(dir);
        try {
            Files.createDirectories(this.dir);
            moveOldFile(Paths.get(oldFile));
            try (DirectoryStream<Path> files = Files.newDirectoryStream(this.dir, "*" + EXTENSION)) {
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    open(name.substring(0, name.length() - EXTENSION.length()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the vocab folder " + this.dir, e);
        }
    }

    public synchronized List<String> languageNames() {
        return new ArrayList<>(stores.keySet());
    }

    // null if there is no such language
    public synchronized VocabStore store(String language) {
        return stores.get(language);
    }

    public synchronized Language language(String language) {
        return languages.get(language);
    }

    // Creates an empty file for a new language; returns the saved name ("italian" -> "Italian")
    public synchronized String addLanguage(String name) {
        String trimmed = name == null ? "" : name.trim().replaceAll(" +", " ");
        if (!trimmed.matches("\\p{L}[\\p{L} -]{0,29}")) {
            throw new IllegalArgumentException("Please use only letters for the language name (up to 30).");
        }
        String saved = trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1);
        if (stores.containsKey(saved)) {
            throw new IllegalStateException(stores.keySet().stream()
                    .filter(saved::equalsIgnoreCase).findFirst().orElse(saved) + " already exists.");
        }
        try {
            Files.createFile(dir.resolve(saved + EXTENSION));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the file for " + saved, e);
        }
        open(saved);
        return saved;
    }

    private void open(String name) {
        stores.put(name, new VocabStore(dir.resolve(name + EXTENSION)));
        languages.put(name, Language.forName(name));
    }

    // vocab.txt -> vocab/Spanish.txt, only when the folder has no languages yet
    private void moveOldFile(Path oldFile) throws IOException {
        if (!Files.exists(oldFile)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*" + EXTENSION)) {
            if (files.iterator().hasNext()) {
                return;
            }
        }
        Path target = dir.resolve(FIRST_LANGUAGE + EXTENSION);
        Files.move(oldFile, target);
        System.out.println("Moved " + oldFile + " to " + target);
    }
}
