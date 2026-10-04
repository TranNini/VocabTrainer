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