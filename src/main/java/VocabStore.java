import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class VocabStore {
    private static final String FILE_NAME = "vocab.txt";
    private final List<Vocab> vocabList = new ArrayList<>();

    public VocabStore() {
        load();
    }

    public List<Vocab> getAll() {
        return vocabList;
    }

    public void add(Vocab vocab) {
        vocabList.add(vocab);
        save();
    }

    private void load() {
        Path path = Paths.get(FILE_NAME);
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
                    vocabList.add(new Vocab(parts[0].trim(), parts[1].trim(), category));
                }
            }
        } catch (IOException e) {
            System.out.println("Could not load vocab file: " + e.getMessage());
        }
    }

    private void save() {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(FILE_NAME))) {
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