package vocabapp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// All languages one learner learns: one file per language in their folder, e.g. vocab/Nini/Spanish.txt,
// and next to it the quiz results for the statistics (Spanish.log, see QuizLog). translation.cfg holds the
// language the learner translates into ("German"); without it, English.
public class VocabLibrary {
    static final String EXTENSION = ".txt";
    static final String TRANSLATION_FILE = "translation.cfg";

    private final Path dir;
    private final Map<String, VocabStore> stores = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Language> languages = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, QuizLog> logs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private Language.Translation translation = Language.ENGLISH;

    public VocabLibrary(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(this.dir);
            Path translationFile = this.dir.resolve(TRANSLATION_FILE);
            if (Files.exists(translationFile)) {
                Language.Translation saved = Language.translation(Files.readString(translationFile));
                translation = saved == null ? Language.ENGLISH : saved;
            }
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

    // The quiz results in that language; null if there is no such language
    public synchronized QuizLog log(String language) {
        return logs.get(language);
    }

    public synchronized Language.Translation translation() {
        return translation;
    }

    // Sets the language every vocab of this learner is translated into ("German"); returns it
    public synchronized Language.Translation setTranslation(String name) {
        Language.Translation picked = Language.translation(name);
        if (picked == null) {
            throw new IllegalArgumentException("Pick one of: " + String.join(", ",
                    Language.TRANSLATIONS.stream().map(Language.Translation::name).toList()) + ".");
        }
        try {
            Files.writeString(dir.resolve(TRANSLATION_FILE), picked.name() + System.lineSeparator());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the translation language in " + dir, e);
        }
        translation = picked;
        languages.replaceAll((language, rules) -> rules.translatedInto(picked));
        return picked;
    }

    // Creates an empty file for a new language; returns the saved name ("italian" -> "Italian")
    public synchronized String addLanguage(String name) {
        String saved = cleanName(name, "language name");
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

    // A language or learner name, also used as a file or folder name: letters, spaces and "-" only
    // (so never "../"), up to 30, starting with a capital ("italian" -> "Italian")
    static String cleanName(String name, String what) {
        String trimmed = name == null ? "" : name.trim().replaceAll(" +", " ");
        if (!trimmed.matches("\\p{L}[\\p{L} -]{0,29}")) {
            throw new IllegalArgumentException("Please use only letters for the " + what + " (up to 30).");
        }
        return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1);
    }

    private void open(String name) {
        stores.put(name, new VocabStore(dir.resolve(name + EXTENSION)));
        languages.put(name, Language.forName(name).translatedInto(translation));
        logs.put(name, new QuizLog(dir.resolve(name + QuizLog.EXTENSION)));
    }
}
