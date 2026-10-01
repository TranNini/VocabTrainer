import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.Random;

public class VocabApp {
    private static final
    Scanner scanner = new Scanner(System.in);
    private static final VocabStore store = new VocabStore();
    private static final Random random = new Random();

    public static void main(String[] args) {
        System.out.println("===Spanish Vocab Trainer===");
        boolean running = true;
        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> addVocab();
                case "2" -> quiz();
                case "3" -> {
                    running = false;
                    System.out.println("Vocab saved in vocab.txt");
                }
                default -> System.out.println(
                        "Please choose 1,2 or 3."
                );
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n1) Add new vocab");
        System.out.println("2) Quiz");
        System.out.println("3) Exit");
        System.out.println(">");
    }

    private static void addVocab() {
        System.out.println("Enter vocab pairs. Type 'done' as the Spanish word to stop.");
        while (true) {
            System.out.println("Spanish:");
            String spanish = scanner.nextLine().trim();
            if (spanish.equalsIgnoreCase("done")) {
                break;
            }
            System.out.println("English:");
            String english = scanner.nextLine().trim();
            store.add(new Vocab(spanish, english));
            System.out.println("Added " + spanish + " = " + english);
        }
    }

    private static void quiz() {
        List<Vocab> all = store.getAll();
        if (all.isEmpty()) {
            System.out.println("No vocab saved yet. Add some first.");
            return;
        }
        List<Vocab> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled);
        int correct = 0;
        for (Vocab v : shuffled) {
            boolean askInSpanish = random.nextBoolean();
            String prompt = askInSpanish ? v.getEnglish() : v.getSpanish();
            String expected = askInSpanish ? v.getSpanish() : v.getEnglish();
            System.out.println("\n " + prompt + " -> ");
            String answer = scanner.nextLine().trim();
            if (answer.equalsIgnoreCase(expected)) {
                System.out.println("Correct :D");
                correct++;
            } else {
                System.out.println("Not quite. Correct answer: " + expected);
            }
        }
        System.out.println("\nScore: " + correct + "/" + shuffled.size());
    }
}