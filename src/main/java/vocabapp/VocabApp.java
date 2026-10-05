package vocabapp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

public class VocabApp {
    private static final Scanner scanner = new Scanner(System.in);
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
                case "3" -> editVocab();
                case "4" -> {
                    running = false;
                    System.out.println("Vocab saved in vocab.txt");
                }
                default -> System.out.println(
                        "Please choose 1, 2, 3 or 4."
                );
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n1) Add new vocab");
        System.out.println("2) Quiz");
        System.out.println("3) Edit vocab");
        System.out.println("4) Exit");
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
            System.out.println("Category (v = verbs, s = sentences, Enter = " + Vocab.DEFAULT_CATEGORY + ", or type a name):");
            String category = readCategory(Vocab.DEFAULT_CATEGORY);
            Vocab vocab = new Vocab(spanish, english, category);
            store.add(vocab);
            System.out.println("Added " + spanish + " = " + english + " [" + vocab.getCategory() + "]");
        }
    }

    private static void editVocab() {
        if (store.getAll().isEmpty()) {
            System.out.println("No vocab saved yet. Add some first.");
            return;
        }
        String selectedCategory = chooseCategoryName();
        while (true) {
            VocabIndex index = new VocabIndex(store.getAll());
            if (index.inCategory(selectedCategory).isEmpty()) {
                System.out.println("No vocab left in this category.");
                return;
            }
            System.out.println("\nType a letter for that section, a word to search, Enter = show all, 'done' to stop:");
            String query = scanner.nextLine().trim();
            if (query.equalsIgnoreCase("done")) {
                return;
            }
            List<Vocab> matches = index.search(selectedCategory, query);
            if (matches.isEmpty()) {
                System.out.println("No vocab found for '" + query + "'.");
                continue;
            }
            String currentSection = null;
            for (int i = 0; i < matches.size(); i++) {
                Vocab v = matches.get(i);
                String section = index.sectionLetter(v);
                if (currentSection == null || !index.sameSection(section, currentSection)) {
                    System.out.println("--- " + section + " ---");
                    currentSection = section;
                }
                String indent = index.isConjugation(v) ? "    " : "";
                System.out.println(indent + (i + 1) + ") " + v.getSpanish() + " = " + v.getEnglish());
            }
            System.out.println("Number to edit (Enter = new search):");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }
            int number;
            try {
                number = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                number = -1;
            }
            if (number < 1 || number > matches.size()) {
                System.out.println("Please choose a number from the list.");
                continue;
            }
            Vocab selected = matches.get(number - 1);
            System.out.println("e = edit, d = delete, Enter = cancel");
            String action = scanner.nextLine().trim().toLowerCase();
            if (action.equals("d")) {
                System.out.println("Delete " + selected.getSpanish() + " = " + selected.getEnglish() + "? (y/n)");
                if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
                    store.remove(selected);
                    System.out.println("Deleted.");
                }
            } else if (action.equals("e")) {
                System.out.println("Press Enter to keep the current value.");
                System.out.println("Spanish [" + selected.getSpanish() + "]:");
                String spanish = keepIfEmpty(scanner.nextLine().trim(), selected.getSpanish());
                System.out.println("English [" + selected.getEnglish() + "]:");
                String english = keepIfEmpty(scanner.nextLine().trim(), selected.getEnglish());
                System.out.println("Category [" + selected.getCategory() + "] (v = verbs, s = sentences, g = " + Vocab.DEFAULT_CATEGORY + ", or type a name):");
                String category = readCategory(selected.getCategory());
                Vocab updated = new Vocab(spanish, english, category);
                store.replace(selected, updated);
                System.out.println("Saved " + spanish + " = " + english + " [" + updated.getCategory() + "]");
            }
        }
    }

    private static String keepIfEmpty(String input, String current) {
        return input.isEmpty() ? current : input;
    }

    // Asks before creating a category that doesn't exist yet, so typos don't become new categories
    private static String readCategory(String defaultCategory) {
        while (true) {
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return defaultCategory;
            }
            String existing = new VocabIndex(store.getAll()).resolveCategory(input);
            if (existing != null) {
                return existing;
            }
            System.out.println("New category '" + input + "'? (y/n)");
            if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
                return input;
            }
            System.out.println("Category:");
        }
    }

    private static void quiz() {
        if (store.getAll().isEmpty()) {
            System.out.println("No vocab saved yet. Add some first.");
            return;
        }
        String category = chooseCategoryName();
        List<Vocab> shuffled = new ArrayList<>(new VocabIndex(store.getAll()).inCategory(category));
        Collections.shuffle(shuffled);
        System.out.println("Type 'stop' to end the quiz.");
        int correct = 0;
        int asked = 0;
        for (Vocab v : shuffled) {
            Question question = Question.from(v, random);
            System.out.println("\n" + question.prompt() + " -> ");
            String answer = scanner.nextLine().trim();
            if (answer.equalsIgnoreCase("stop")) {
                break;
            }
            asked++;
            boolean solved = AnswerChecker.isCorrect(answer, question.expected());
            int attempt = 1;
            while (!solved && attempt < AnswerChecker.MAX_ATTEMPTS) {
                System.out.println(AnswerChecker.feedback(answer, question.expected(), attempt));
                System.out.print("Try again -> ");
                answer = scanner.nextLine().trim();
                solved = AnswerChecker.isCorrect(answer, question.expected());
                attempt++;
            }
            if (solved) {
                System.out.println("Correct! " + AnswerChecker.allOptions(question.expected()));
                correct++;
            } else {
                System.out.println("Not quite. The correct answer is: " + AnswerChecker.allOptions(question.expected()));
            }
        }
        System.out.println("\nScore: " + correct + "/" + asked);
    }

    // Returns null for "all categories"
    private static String chooseCategoryName() {
        Map<String, Integer> counts = new VocabIndex(store.getAll()).categoryCounts();
        if (counts.size() < 2) {
            return null;
        }
        List<String> categories = new ArrayList<>(counts.keySet());
        System.out.println("Choose a category:");
        System.out.println("0) All (" + store.getAll().size() + ")");
        for (int i = 0; i < categories.size(); i++) {
            String category = categories.get(i);
            System.out.println((i + 1) + ") " + category + " (" + counts.get(category) + ")");
        }
        while (true) {
            System.out.println(">");
            String input = scanner.nextLine().trim();
            if (input.isEmpty() || input.equals("0") || input.equalsIgnoreCase("all")) {
                return null;
            }
            String selected = null;
            try {
                int index = Integer.parseInt(input);
                if (index >= 1 && index <= categories.size()) {
                    selected = categories.get(index - 1);
                }
            } catch (NumberFormatException e) {
                if (counts.containsKey(input)) {
                    selected = input;
                }
            }
            if (selected == null) {
                System.out.println("Please choose a number from the list or type a category name.");
                continue;
            }
            return selected;
        }
    }
}
