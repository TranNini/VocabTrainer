package vocabapp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class VocabStore {
    private static final String FILE_NAME = "vocab.txt";
    private final Path path;
    private final List<Vocab> vocabList = new ArrayList<>();
    // Ids aren't saved in vocab.txt; they only identify entries while the app is running
    private final Map<Vocab, Integer> ids = new IdentityHashMap<>();
    private int nextId = 1;

    public VocabStore() {
        this(Paths.get(FILE_NAME));
    }

    public VocabStore(Path path) {
        this.path = path;
        load();
    }

    public synchronized List<Vocab> getAll() {
        return List.copyOf(vocabList);
    }

    public synchronized int idOf(Vocab vocab) {
        return ids.getOrDefault(vocab, -1);
    }

    public synchronized Vocab findById(int id) {
        for (Vocab v : vocabList) {
            if (ids.get(v) == id) {
                return v;
            }
        }
        return null;
    }

    public synchronized void add(Vocab vocab) {
        track(vocab);
        save();
    }

    // The new vocab keeps the old one's id and place in the list
    public synchronized void replace(Vocab oldVocab, Vocab newVocab) {
        int index = vocabList.indexOf(oldVocab);
        if (index >= 0) {
            vocabList.set(index, newVocab);
            ids.put(newVocab, ids.remove(oldVocab));
            save();
        }
    }

    public synchronized void remove(Vocab vocab) {
        if (vocabList.remove(vocab)) {
            ids.remove(vocab);
            save();
        }
    }

    private void track(Vocab vocab) {
        vocabList.add(vocab);
        ids.put(vocab, nextId++);
    }

    private void load() {
        if (!Files.exists(path)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                // Older files have no category column: "spanish;english"
                String[] parts = line.split(";", 3);
                if (parts.length >= 2) {
                    String category = parts.length == 3 ? parts[2].trim() : Vocab.DEFAULT_CATEGORY;
                    track(new Vocab(parts[0].trim(), parts[1].trim(), category));
                }
            }
        } catch (IOException e) {
            System.out.println("Could not load vocab file: " + e.getMessage());
        }
    }

    private void save() {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (Vocab v : vocabList) {
                writer.write(v.getSpanish() + ";" + v.getEnglish() + ";" + v.getCategory());
                writer.newLine();
            }
        }
        catch (IOException e) {
            System.out.println("Could not save vocab file: " + e.getMessage());
        }
    }
}