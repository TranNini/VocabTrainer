package vocabapp;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnswerChecker {
    public static final int MAX_ATTEMPTS = 3;

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

    // Hint after a wrong answer; attempt is 1 after the first try. The language gives its articles
    // (for "Don't forget the article!"); English ones are always known.
    public static String feedback(String answer, String expected, int attempt, Language language) {
        List<String> articles = new ArrayList<>(language.getArticles());
        articles.addAll(Language.ENGLISH_ARTICLES);
        String target = closestAlternative(answer, expected, articles);
        String cleaned = normalize(answer);
        String articleHint = missingArticleHint(cleaned, target, articles);
        if (articleHint != null) {
            return articleHint;
        }
        if (attempt == 1) {
            return "Not quite.";
        }
        if (isClose(cleaned, target)) {
            return closeHint(cleaned, target);
        }
        return "Not quite. Hint: it starts with '" + getHint(target, articles) + "'";
    }

    // Hint button. It only looks at what is typed, so pressing it again gives the same hint:
    // nothing typed or all wrong -> the first letter ('d' for "dado"); the right start -> one
    // letter more ("deca" -> 'da'); nearly right ("apellidion" for "apellido") -> which letters
    // are right, like after Check; only the article wrong or missing -> says so.
    // A leading article (el, la, the, to …) is skipped, so "la vaca" starts with 'v'.
    public static String hint(String answer, String expected, Language language) {
        String cleaned = normalize(answer);
        if (!cleaned.isEmpty() && isCorrect(cleaned, expected)) {
            return "That's right! Press Check.";
        }
        List<String> articles = new ArrayList<>(language.getArticles());
        articles.addAll(Language.ENGLISH_ARTICLES);
        String[] typed = splitArticle(cleaned, articles);
        String[] target = null;
        int bestPrefix = -1;
        for (String option : expected.split("/")) {
            String[] candidate = splitArticle(normalize(option), articles);
            String word = candidate[0] == null ? cleaned : typed[1];
            if (!word.isEmpty() && (word.equalsIgnoreCase(candidate[1]) || isClose(word, candidate[1]))) {
                target = candidate;
                break;
            }
            int prefix = commonPrefix(word, candidate[1]);
            if (prefix > bestPrefix) {
                target = candidate;
                bestPrefix = prefix;
            }
        }
        String article = target[0];
        String word = target[1];
        String typedWord = article == null ? cleaned : typed[1];
        if (typedWord.equalsIgnoreCase(word)) {
            // the word is right, so it's the article (isCorrect caught everything else)
            if (article.equals("to")) {
                return "What comes in front of an infinitive verb?";
            }
            return typed[0] == null ? "Don't forget the article!" : "Check the article.";
        }
        if (!typedWord.isEmpty() && isClose(typedWord, word)) {
            return closeHint(typedWord, word);
        }
        int prefix = commonPrefix(typedWord, word);
        if (prefix == word.length()) {
            return "The start is right, but there are letters too many.";
        }
        int length = prefix + 1;
        if (length < word.length() && word.charAt(length - 1) == ' ') {
            length++; // a space alone tells nothing, so give the next letter with it
        }
        String start = word.substring(0, length);
        if (article != null && article.equalsIgnoreCase(typed[0])) {
            start = article + " " + start; // keep the article that is already typed right
        }
        return "It starts with '" + start + "'";
    }

    // "la vaca" -> {"la", "vaca"}, "to talk" -> {"to", "talk"}, "vaca" -> {null, "vaca"}
    private static String[] splitArticle(String text, List<String> articles) {
        String[] words = text.split(" ", 2);
        if (words.length == 2) {
            String first = words[0].toLowerCase();
            if (first.equals("to") || articles.contains(first)) {
                return new String[]{words[0], words[1]};
            }
        }
        return new String[]{null, text};
    }

    private static int commonPrefix(String a, String b) {
        int i = 0;
        while (i < a.length() && i < b.length() && Character.toLowerCase(a.charAt(i)) == Character.toLowerCase(b.charAt(i))) {
            i++;
        }
        return i;
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
        // NFC: a letter typed as "i" + accent mark becomes the same "í" that is saved
        return Normalizer.normalize(text, Normalizer.Form.NFC).replace("...", "")
                .replace("…", "")
                .replace("?", "")
                .replace("¿", "")
                .replace("!", "")
                .replace("¡", "")
                .replace(".", "")
                .replaceAll(" +", " ")
                .trim();
    }

    private static String closestAlternative(String answer, String expected, List<String> articles) {
        String cleaned = normalize(answer);
        String[] options = expected.split("/");
        for (String option : options) {
            String target = normalize(option);
            if (missingArticleHint(cleaned, target, articles) != null || isClose(cleaned, target)) {
                return target;
            }
        }
        return normalize(options[0]);
    }

    private static String missingArticleHint(String answer, String expected, List<String> articles) {
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
        for (String article : articles) {
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

    private static String getHint(String expected, List<String> articles) {
        for (String word : expected.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            boolean isArticle = word.equalsIgnoreCase("to");
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
}
