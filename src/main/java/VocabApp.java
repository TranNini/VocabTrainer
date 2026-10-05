import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Random;
import java.util.TreeMap;

public class VocabApp {
    private static final
    Scanner scanner = new Scanner(System.in);
    private static final VocabStore store = new VocabStore();
    private static final Random random = new Random();
    private static final Collator spanishOrder = Collator.getInstance(new Locale("es"));
    private static final String[] SPANISH_ARTICLES = {"el", "la", "los", "las", "un", "una", "unos", "unas"};
    private static final String VERBS_CATEGORY = "verbs";
    // Index + 1 is the order conjugations are listed in under their infinitive
    private static final String[][] SUBJECT_PRONOUNS = {
            {"yo"},
            {"tú"},
            {"él", "ella", "usted"},
            {"nosotros", "nosotras"},
            {"vosotros", "vosotras"},
            {"ellos", "ellas", "ustedes"}
    };

    static {
        // PRIMARY ignores case and accents, so "árbol" sorts with "arbol"
        spanishOrder.setStrength(Collator.PRIMARY);
    }

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
        String selectedCategory = chooseCategoryName(store.getAll());
        while (true) {
            List<Vocab> inCategory = filterByCategory(store.getAll(), selectedCategory);
            if (inCategory.isEmpty()) {
                System.out.println("No vocab left in this category.");
                return;
            }
            System.out.println("\nType a letter for that section, a word to search, Enter = show all, 'done' to stop:");
            String query = scanner.nextLine().trim();
            if (query.equalsIgnoreCase("done")) {
                return;
            }
            Map<Vocab, Vocab> infinitives = findInfinitives();
            List<Vocab> matches = new ArrayList<>();
            for (Vocab v : inCategory) {
                boolean match;
                if (query.length() == 1) {
                    match = spanishOrder.equals(sectionLetter(v, infinitives), query);
                } else {
                    match = v.getSpanish().toLowerCase().contains(query.toLowerCase())
                            || v.getEnglish().toLowerCase().contains(query.toLowerCase());
                }
                if (match) {
                    matches.add(v);
                }
            }
            if (matches.isEmpty()) {
                System.out.println("No vocab found for '" + query + "'.");
                continue;
            }
            matches.sort((a, b) -> {
                Vocab infinitiveA = infinitives.getOrDefault(a, a);
                Vocab infinitiveB = infinitives.getOrDefault(b, b);
                int byInfinitive = spanishOrder.compare(sortKey(infinitiveA), sortKey(infinitiveB));
                if (byInfinitive != 0) {
                    return byInfinitive;
                }
                return Integer.compare(pronounRank(a), pronounRank(b));
            });
            String currentSection = null;
            for (int i = 0; i < matches.size(); i++) {
                Vocab v = matches.get(i);
                String section = sectionLetter(v, infinitives);
                if (currentSection == null || !spanishOrder.equals(section, currentSection)) {
                    System.out.println("--- " + section + " ---");
                    currentSection = section;
                }
                String indent = infinitives.containsKey(v) ? "    " : "";
                System.out.println(indent + (i + 1) + ") " + v.getSpanish() + " = " + v.getEnglish() + " [" + v.getCategory() + "]");
            }
            System.out.println("Number to edit (Enter = new search):");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }
            int index;
            try {
                index = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                index = -1;
            }
            if (index < 1 || index > matches.size()) {
                System.out.println("Please choose a number from the list.");
                continue;
            }
            Vocab selected = matches.get(index - 1);
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

    // "la abuela" -> "abuela", "¡Hola!" -> "Hola"
    private static String sortKey(Vocab v) {
        String spanish = normalize(v.getSpanish());
        String[] words = spanish.split(" ", 2);
        if (words.length == 2) {
            for (String article : SPANISH_ARTICLES) {
                if (words[0].equalsIgnoreCase(article)) {
                    return words[1];
                }
            }
        }
        return spanish;
    }

    private static String sectionLetter(Vocab v, Map<Vocab, Vocab> infinitives) {
        String key = sortKey(infinitives.getOrDefault(v, v));
        return key.isEmpty() ? "#" : key.substring(0, 1).toUpperCase();
    }

    // Maps each conjugated verb ("yo tengo") to the infinitive saved before it ("tener"),
    // since irregular forms can't be matched to their infinitive by spelling
    private static Map<Vocab, Vocab> findInfinitives() {
        Map<Vocab, Vocab> infinitives = new HashMap<>();
        Vocab currentInfinitive = null;
        for (Vocab v : store.getAll()) {
            if (!v.getCategory().equalsIgnoreCase(VERBS_CATEGORY)) {
                continue;
            }
            if (pronounRank(v) == 0) {
                currentInfinitive = v;
            } else if (currentInfinitive != null) {
                infinitives.put(v, currentInfinitive);
            }
        }
        return infinitives;
    }

    // 0 = not a conjugation, 1 = yo ... 6 = ellos
    private static int pronounRank(Vocab v) {
        if (!v.getCategory().equalsIgnoreCase(VERBS_CATEGORY)) {
            return 0;
        }
        String firstWord = normalize(v.getSpanish()).split("[ /,]", 2)[0];
        for (int i = 0; i < SUBJECT_PRONOUNS.length; i++) {
            for (String pronoun : SUBJECT_PRONOUNS[i]) {
                if (spanishOrder.equals(firstWord, pronoun)) {
                    return i + 1;
                }
            }
        }
        return 0;
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
            String category = expandCategory(input);
            if (!category.equals(input)) {
                return category;
            }
            for (Vocab v : store.getAll()) {
                if (v.getCategory().equalsIgnoreCase(category)) {
                    return v.getCategory();
                }
            }
            System.out.println("New category '" + category + "'? (y/n)");
            if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
                return category;
            }
            System.out.println("Category:");
        }
    }

    private static String expandCategory(String input) {
        return switch (input.toLowerCase()) {
            case "v" -> "verbs";
            case "s" -> "sentences";
            case "g" -> Vocab.DEFAULT_CATEGORY;
            default -> input;
        };
    }

    private static void quiz() {
        List<Vocab> all = store.getAll();
        if (all.isEmpty()) {
            System.out.println("No vocab saved yet. Add some first.");
            return;
        }
        List<Vocab> shuffled = new ArrayList<>(chooseCategory(all));
        Collections.shuffle(shuffled);
        System.out.println("Type 'stop' to end the quiz.");
        int correct = 0;
        int asked = 0;
        for (Vocab v : shuffled) {
            boolean askInSpanish = random.nextBoolean();
            String prompt = askInSpanish ? pickOne(v.getEnglish()) : pickOne(v.getSpanish());
            String expected = askInSpanish ? v.getSpanish() : v.getEnglish();
            System.out.println("\n" + prompt + " -> ");
            String answer = scanner.nextLine().trim();
            if (answer.equalsIgnoreCase("stop")) {
                break;
            }
            asked++;
            boolean solved = isCorrect(answer, expected);
            int attempt = 1;
            while (!solved && attempt < 3) {
                String target = closestAlternative(answer, expected);
                String cleaned = normalize(answer);
                String articleHint = missingArticleHint(cleaned, target);
                if (articleHint != null) {
                    System.out.println(articleHint);
                } else if (attempt == 1)
                    System.out.println("Not quite.");
                else if (isClose(cleaned, target)) {
                    System.out.println("Almost correct!");
                } else {
                    System.out.println("Not quite. Hint: it starts with '" + getHint(target) + "'");
                }
                System.out.print("Try again -> ");
                answer = scanner.nextLine().trim();
                solved = isCorrect(answer, expected);
                attempt++;
            }
            if (solved) {
                System.out.println("Correct!");
                correct++;
            } else {
                System.out.println("Not quite. The correct answer is: " + expected);
            }
        }
        System.out.println("\nScore: " + correct + "/" + asked);
    }

    private static List<Vocab> chooseCategory(List<Vocab> all) {
        return filterByCategory(all, chooseCategoryName(all));
    }

    private static List<Vocab> filterByCategory(List<Vocab> all, String category) {
        if (category == null) {
            return all;
        }
        List<Vocab> filtered = new ArrayList<>();
        for (Vocab v : all) {
            if (v.getCategory().equalsIgnoreCase(category)) {
                filtered.add(v);
            }
        }
        return filtered;
    }

    // Returns null for "all categories"
    private static String chooseCategoryName(List<Vocab> all) {
        Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Vocab v : all) {
            counts.merge(v.getCategory(), 1, Integer::sum);
        }
        if (counts.size() < 2) {
            return null;
        }
        List<String> categories = new ArrayList<>(counts.keySet());
        System.out.println("Choose a category:");
        System.out.println("0) All (" + all.size() + ")");
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

    private static String getHint(String expected) {
        String[] articles = {"el", "la", "los", "las", "un", "una", "unos", "unas", "to", "the", "a", "an"};
        String[] words = expected.split(" ");
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            boolean isArticle = false;
            for (String article : articles) {
                if (word.equalsIgnoreCase(article)) {
                    isArticle = true;
                }
            }
            if (!isArticle) {
                return word.substring(0, 1);
            }
        }
        return expected.substring(0, 1);
    }

    private static boolean isClose(String answer, String expected) {
        String a = answer.toLowerCase();
        String b = expected.toLowerCase();
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        int distance = dp[a.length()][b.length()];
        int allowed = b.length() <= 4 ? 1 : 2;
        return distance <= allowed;
    }


    private static String missingArticleHint(String answer, String expected) {
        String[] words = expected.split(" ", 2);
        if (words.length < 2) {
            return null;
        }
        String first = words[0].toLowerCase();
        if (!answer.equalsIgnoreCase(words[1])) {
            return null;
        }
        if (first.equals("to")) {
            return "What comes in front of an infinitive verb?";
        }
        String[] articles = {"el", "la", "los", "las", "un", "una", "unos", "unas", "the", "a", "an"};
        for (String article : articles) {
            if (first.equals(article)) {
                return "Don't forget the article!";
            }
        }
        return null;
    }


    private static String normalize(String text) {
        return text.replace("...", "")
                .replace("…", "")
                .replace("?", "")
                .replace("¿", "")
                .replace("!", "")
                .replace("¡", "")
                .replace(".", "")
                .replaceAll(" +", " ")
                .trim();
    }

    private static boolean isCorrect(String answer, String expected) {
        String cleaned = normalize(answer);
        for (String option : expected.split("/")) {
            if (cleaned.equalsIgnoreCase(normalize(option)))
            {
                return true;
            }
        }
        return false;
    }

    private static String pickOne(String text) {
        String[] options = text.split("/");
        return options[random.nextInt(options.length)].trim();
    }

    private static String closestAlternative(String answer, String expected) {
        String cleaned = normalize(answer);
        String[] options = expected.split("/");
        for (String option : options)
        {
            String target = normalize(option);
            if (missingArticleHint(cleaned, target)!=null ||
                    isClose(cleaned, target))
            {
                return target;
            }
        }
        return normalize(options[0]);
    }

}