package vocabapp;

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
            return "Almost correct!";
        }
        return "Not quite. Hint: it starts with '" + getHint(target) + "'";
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
