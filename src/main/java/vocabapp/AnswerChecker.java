package vocabapp;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnswerChecker {
    public static final int MAX_ATTEMPTS = 3;
    private static final String[] ARTICLES = {"el", "la", "los", "las", "un", "una", "unos", "unas", "the", "a", "an"};

    private AnswerChecker() {
    }

    public static boolean isCorrect(String answer, String expected) {
        String cleaned = normalize(answer);
        for (String option : expected.split("/")) {
            if (cleaned.equalsIgnoreCase(normalize(option))) {
                return true;
            }
        }
        return false;
    }

    // Hint after a wrong answer; attempt is 1 after the first try
    public static String feedback(String answer, String expected, int attempt) {
        String target = closestAlternative(answer, expected);
        String cleaned = normalize(answer);
        String articleHint = missingArticleHint(cleaned, target);
        if (articleHint != null) {
            return articleHint;
        }
        if (attempt == 1) {
            return "Not quite.";
        }
        if (isClose(cleaned, target)) {
            return closeHint(cleaned, target);
        }
        return "Not quite. Hint: it starts with '" + getHint(target) + "'";
    }

    // For answers that are nearly right: shows the letters that already fit, "_" for the rest,
    // e.g. "oro" for "oso" -> "o_o"
    private static String closeHint(String answer, String target) {
        String pattern = knownLetters(answer, target);
        if (stripAccents(answer).equalsIgnoreCase(stripAccents(target))) {
            return "Almost! Check the accent: " + pattern;
        }
        if (!pattern.contains("_")) {
            // every letter is there, so the pattern would give the answer away
            return answer.length() - target.length() == 1
                    ? "Almost! There's one letter too many."
                    : "Almost! There are a few letters too many.";
        }
        return "Almost! These letters are right: " + pattern;
    }

    // Lines the answer up with the target the same way the typo check counts changes,
    // and keeps every target letter that the answer has in the right place
    private static String knownLetters(String answer, String target) {
        String a = answer.toLowerCase();
        String b = target.toLowerCase();
        int[][] dp = editDistances(a, b);
        boolean[] known = new boolean[b.length()];
        int i = a.length();
        int j = b.length();
        while (i > 0 && j > 0) {
            int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
            if (dp[i][j] == dp[i - 1][j - 1] + cost) {
                known[j - 1] = cost == 0;
                i--;
                j--;
            } else if (dp[i][j] == dp[i - 1][j] + 1) {
                i--; // extra letter in the answer
            } else {
                j--; // letter missing from the answer
            }
        }
        StringBuilder pattern = new StringBuilder();
        for (int k = 0; k < target.length(); k++) {
            char c = target.charAt(k);
            pattern.append(known[k] || c == ' ' ? c : '_');
        }
        return pattern.toString();
    }

    private static String stripAccents(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    // The full answer with every alternative, e.g. "Good evening / Good night"
    public static String allOptions(String expected) {
        List<String> options = new ArrayList<>();
        for (String option : expected.split("/")) {
            if (!option.isBlank()) {
                options.add(option.trim());
            }
        }
        return String.join(" / ", options);
    }

    public static String pickOne(String text, Random random) {
        String[] options = text.split("/");
        return options[random.nextInt(options.length)].trim();
    }

    public static String normalize(String text) {
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

    private static String closestAlternative(String answer, String expected) {
        String cleaned = normalize(answer);
        String[] options = expected.split("/");
        for (String option : options) {
            String target = normalize(option);
            if (missingArticleHint(cleaned, target) != null || isClose(cleaned, target)) {
                return target;
            }
        }
        return normalize(options[0]);
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
        for (String article : ARTICLES) {
            if (first.equals(article)) {
                return "Don't forget the article!";
            }
        }
        return null;
    }

    private static boolean isClose(String answer, String expected) {
        String a = answer.toLowerCase();
        String b = expected.toLowerCase();
        int distance = editDistances(a, b)[a.length()][b.length()];
        int allowed = b.length() <= 4 ? 1 : 2;
        return distance <= allowed;
    }

    // dp[i][j] = how many letters to change, add or remove to turn the first i letters of a
    // into the first j letters of b (Levenshtein distance)
    private static int[][] editDistances(String a, String b) {
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
        return dp;
    }

    private static String getHint(String expected) {
        for (String word : expected.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            boolean isArticle = word.equalsIgnoreCase("to");
            for (String article : ARTICLES) {
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
}
