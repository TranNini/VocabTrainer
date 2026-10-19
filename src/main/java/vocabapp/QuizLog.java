package vocabapp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

// Every quiz question a learner finished in one language, for the statistics: one line each in
// vocab/<Learner>/<Language>.log, e.g. 2026-10-19T18:05:12;nosotros queremos;in;2;1
// (when; the word; "in" = answered in the language, "en" = in English; the try it was right on,
// 0 = not right after the last try; how many hints the hint button gave)
public class QuizLog {
    static final String EXTENSION = ".log";

    private final Path path;

    public record Entry(LocalDateTime when, String word, boolean answerInLanguage, int rightOnTry, int hints) {
        public boolean right() {
            return rightOnTry > 0;
        }

        public boolean rightFirstTry() {
            return rightOnTry == 1;
        }
    }

    public QuizLog(Path path) {
        this.path = path;
    }

    public synchronized void add(Entry entry) {
        try {
            Files.writeString(path, line(entry), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the quiz result in " + path, e);
        }
    }

    // Oldest first; lines that can't be read are skipped
    public synchronized List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        if (!Files.exists(path)) {
            return entries;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String[] parts = line.split(";", -1);
                if (parts.length != 5) {
                    continue;
                }
                try {
                    entries.add(new Entry(LocalDateTime.parse(parts[0]), parts[1], parts[2].equals("in"),
                            Integer.parseInt(parts[3]), Integer.parseInt(parts[4])));
                } catch (DateTimeParseException | NumberFormatException e) {
                    // a damaged line: leave it out
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the quiz results in " + path, e);
        }
        return entries;
    }

    // After a word was edited, its results belong to the new spelling
    public synchronized void renameWord(String oldWord, String newWord) {
        if (oldWord.equals(newWord) || !Files.exists(path)) {
            return;
        }
        List<Entry> entries = entries();
        StringBuilder text = new StringBuilder();
        boolean changed = false;
        for (Entry e : entries) {
            if (e.word().equals(oldWord)) {
                e = new Entry(e.when(), newWord, e.answerInLanguage(), e.rightOnTry(), e.hints());
                changed = true;
            }
            text.append(line(e));
        }
        if (!changed) {
            return;
        }
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not update the quiz results in " + path, e);
        }
    }

    private static String line(Entry e) {
        return String.join(";", e.when().withNano(0).toString(), e.word(), e.answerInLanguage() ? "in" : "en",
                String.valueOf(e.rightOnTry()), String.valueOf(e.hints())) + System.lineSeparator();
    }
}
