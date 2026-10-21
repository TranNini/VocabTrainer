package vocabapp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

// All languages one learner learns: one file per language in their folder, e.g. vocab/Nini/Spanish.txt,
// and next to it the language it is translated into (Spanish.translation, e.g. "English", see Language)
// and the quiz results for the statistics (Spanish.log, see QuizLog)
public class VocabLibrary {
    static final String EXTENSION = ".txt";
    static final String TRANSLATION_EXTENSION = ".translation";
    // Before word lists had their own: one translation language for all of a learner's lists
    static final String OLD_TRANSLATION_FILE = "translation.cfg";

    private final Path dir;
    // which language a list without a .translation file is translated into, from its words; null = English
    private final Function<List<Vocab>, String> guessTranslation;
    private final Map<String, VocabStore> stores = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Language> languages = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, QuizLog> logs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public VocabLibrary(Path dir) {
        this(dir, null);
    }

    public VocabLibrary(Path dir, Function<List<Vocab>, String> guessTranslation) {
        this.dir = dir;
        this.guessTranslation = guessTranslation;
        try {
            Files.createDirectories(this.dir);
            Path oldFile = this.dir.resolve(OLD_TRANSLATION_FILE);
            String oldTranslation = Files.exists(oldFile) ? Files.readString(oldFile).trim() : null;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(this.dir, "*" + EXTENSION)) {
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    open(name.substring(0, name.length() - EXTENSION.length()), oldTranslation);
                }
            }
            // every list has its own .translation file now
            Files.deleteIfExists(oldFile);
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

    // The rules of the language, translated into the language of its list (see translatedInto)
    public synchronized Language language(String language) {
        return languages.get(language);
    }

    // The quiz results in that language; null if there is no such language
    public synchronized QuizLog log(String language) {
        return logs.get(language);
    }

    // The language this learner translates into most, for a new list: "German" for Kiki; English for a new learner
    public synchronized String usualTranslation() {
        return languages.values().stream()
                .collect(Collectors.groupingBy(l -> l.getTranslation().getName(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(Language.DEFAULT_TRANSLATION);
    }

    // Creates an empty file for a new language, translated into the usual language; returns the saved name
    public synchronized String addLanguage(String name) {
        return addLanguage(name, null);
    }

    // Creates an empty file for a new language, translated into translation (null or blank = the usual one);
    // returns the saved name ("italian" -> "Italian")
    public synchronized String addLanguage(String name, String translation) {
        String saved = cleanName(name, "language name");
        if (stores.containsKey(saved)) {
            throw new IllegalStateException(stores.keySet().stream()
                    .filter(saved::equalsIgnoreCase).findFirst().orElse(saved) + " already exists.");
        }
        String into = translation == null || translation.isBlank() ? usualTranslation()
                : cleanName(translation, "translation language");
        try {
            Files.createFile(dir.resolve(saved + EXTENSION));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the file for " + saved, e);
        }
        saveTranslation(saved, into);
        open(saved, null);
        return saved;
    }

    // Moves the list's files (words, translation language, quiz results) to
    // vocab/.deleted/<learner> <language> <date and time>/, so it can be put back; false if there is no such list
    public synchronized boolean deleteLanguage(String language) {
        String saved = stores.keySet().stream().filter(language::equalsIgnoreCase).findFirst().orElse(null);
        if (saved == null) {
            return false;
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss"));
        try {
            Path deleted = Files.createDirectories(dir.getParent().resolve(Learners.DELETED)
                    .resolve(dir.getFileName() + " " + saved + " " + stamp));
            for (String extension : List.of(EXTENSION, TRANSLATION_EXTENSION, QuizLog.EXTENSION)) {
                Path file = dir.resolve(saved + extension);
                if (Files.exists(file)) {
                    Files.move(file, deleted.resolve(file.getFileName()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete the list " + saved, e);
        }
        stores.remove(saved);
        languages.remove(saved);
        logs.remove(saved);
        return true;
    }

    // Sets the language the vocab of that list is translated into ("German"); returns the saved name
    public synchronized String setTranslation(String language, String translation) {
        String into = cleanName(translation, "translation language");
        saveTranslation(language, into);
        languages.put(language, Language.forName(language).translatedInto(Language.forName(into)));
        return into;
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

    // The translation comes from the list's .translation file; a list without one (saved before there were
    // pairs) gets the learner's old setting, else a guess from its words, else English, and keeps it from then on
    private void open(String name, String oldTranslation) {
        VocabStore store = new VocabStore(dir.resolve(name + EXTENSION));
        stores.put(name, store);
        logs.put(name, new QuizLog(dir.resolve(name + QuizLog.EXTENSION)));
        Path file = dir.resolve(name + TRANSLATION_EXTENSION);
        String translation = null;
        try {
            if (Files.exists(file)) {
                translation = Files.readString(file).trim();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        if (translation == null || translation.isEmpty()) {
            if (oldTranslation != null && !oldTranslation.isEmpty()) {
                translation = oldTranslation;
            } else if (guessTranslation != null) {
                translation = guessTranslation.apply(store.getAll());
            }
            if (translation == null) {
                translation = Language.DEFAULT_TRANSLATION;
            }
            saveTranslation(name, translation);
        }
        languages.put(name, Language.forName(name).translatedInto(Language.forName(translation)));
    }

    private void saveTranslation(String language, String translation) {
        try {
            Files.writeString(dir.resolve(language + TRANSLATION_EXTENSION), translation + System.lineSeparator());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the translation language of " + language, e);
        }
    }
}
