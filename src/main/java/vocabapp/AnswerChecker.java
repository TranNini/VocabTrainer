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
        Mistake mistake = findMistakes(cleaned, expected, articles);
        if (mistake != null) {
            return mistake.message();
        }
        return "Not quite. Hint: it starts with '" + getHint(target, articles) + "'";
    }

    // Hint button. It only looks at what is typed, so pressing it again gives the same hint:
    // nothing typed or all wrong -> the first letter ('d' for "dado"); the right start -> one
    // letter more ("deca" -> 'da'); mistakes in it ("como" for "Cómo te llamas") -> which letters
    // are right, word by word, like after Check; only the article wrong or missing -> says so.
    // A leading article (el, la, the, to …) is skipped, so "la vaca" starts with 'v'.
    public static String hint(String answer, String expected, Language language) {
        String cleaned = normalize(answer);
        if (!cleaned.isEmpty() && isCorrect(cleaned, expected)) {
            return "That's right! Press Check.";
        }
        List<String> articles = new ArrayList<>(language.getArticles());
        articles.addAll(Language.ENGLISH_ARTICLES);
        String[] typed = splitArticle(cleaned, articles);
        // Typed right so far, nearly finished or with the article ("e", "el", "to") -> one letter more.
        // Checked first, so a correct start is never mistaken for a nearly right answer.
        if (!cleaned.isEmpty()) {
            for (String option : expected.split("/")) {
                String full = normalize(option);
                String word = splitArticle(full, articles)[1];
                if (commonPrefix(cleaned, full) == cleaned.length()) {
                    return "It starts with '" + start(full, cleaned.length()) + "'";
                }
                // the whole word without its article falls through to "Don't forget the article!"
                if (typed[0] == null && cleaned.length() < word.length()
                        && commonPrefix(cleaned, word) == cleaned.length()) {
                    return "It starts with '" + start(word, cleaned.length()) + "'";
                }
            }
        }
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
        int prefix = commonPrefix(typedWord, word);
        boolean articleRight = article == null || article.equalsIgnoreCase(typed[0]);
        if (!typedWord.isEmpty() && prefix == word.length() && articleRight) {
            return "The start is right, but there are letters too many.";
        }
        String start = start(word, prefix);
        if (article != null && articleRight) {
            start = article + " " + start; // keep the article that is already typed right
        }
        // the letters that are right, unless the start tells more ("Dama" for "dado" -> 'dad')
        Mistake mistake = findMistakes(cleaned, expected, articles);
        if (mistake != null && (mistake.gaps() == 0 || mistake.known() >= start.replace(" ", "").length())) {
            return mistake.message();
        }
        return "It starts with '" + start + "'";
    }

    // The first known letters of the target plus one more
    private static String start(String target, int known) {
        int length = Math.min(known + 1, target.length());
        if (length < target.length() && target.charAt(length - 1) == ' ') {
            length++; // a space alone tells nothing, so give the next letter with it
        }
        return target.substring(0, length);
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

    // known = letters shown as right, gaps = letters shown as "_"
    private record Mistake(String message, int known, int gaps) {
    }

    // For answers with mistakes: the answer word by word, with the letters that are right and "_" for the
    // rest, e.g. "oro" for "oso" -> "o_o", "como se llamas" for "Cómo te llamas" -> "C_mo _e llamas",
    // "numero dos" for "número uno" -> "n_mero ___". Words not typed yet at the end are left out
    // ("como" -> "C_mo …"). Uses the alternative that fits best; null if nothing typed fits.
    private static Mistake findMistakes(String answer, String expected, List<String> articles) {
        if (answer.isEmpty()) {
            return null;
        }
        String[] typed = answer.split(" ");
        boolean typedArticle = splitArticle(answer, articles)[0] != null;
        Mistake best = null;
        for (String option : expected.split("/")) {
            String full = normalize(option);
            List<String> targets = new ArrayList<>(List.of(full));
            String word = splitArticle(full, articles)[1];
            if (!typedArticle && !word.equals(full)) {
                targets.add(word); // "apellidion" is compared with "apellido", not "el apellido"
            }
            for (String target : targets) {
                Mistake mistake = compareWords(typed, target.split(" "));
                if (mistake != null && (best == null || mistake.known() > best.known()
                        || mistake.known() == best.known() && mistake.gaps() < best.gaps())) {
                    best = mistake;
                }
            }
        }
        return best;
    }

    // Lines up the typed words with the target words (a word can be missing, extra or wrong),
    // then shows each target word as right, partly right or all "_"
    private static Mistake compareWords(String[] typed, String[] target) {
        int n = typed.length;
        int m = target.length;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int pair = dp[i - 1][j - 1] + wordCost(typed[i - 1], target[j - 1], i == n);
                dp[i][j] = Math.min(pair, Math.min(dp[i - 1][j], dp[i][j - 1]) + 1);
            }
        }
        String[] shown = new String[m];
        boolean onlyAccents = true;
        int extraWords = 0;
        int i = n;
        int j = m;
        while (i > 0 && j > 0) {
            int cost = wordCost(typed[i - 1], target[j - 1], i == n);
            if (dp[i][j] == dp[i - 1][j - 1] + cost) {
                shown[j - 1] = cost < 2 ? knownLetters(typed[i - 1], target[j - 1]) : "_".repeat(target[j - 1].length());
                onlyAccents &= stripAccents(typed[i - 1]).equalsIgnoreCase(stripAccents(target[j - 1]));
                i--;
                j--;
            } else if (dp[i][j] == dp[i - 1][j] + 1) {
                extraWords++;
                i--;
            } else {
                j--;
            }
        }
        extraWords += i;
        int last = m - 1;
        while (last >= 0 && shown[last] == null) {
            last--; // not typed yet
        }
        if (last < 0) {
            return null;
        }
        List<String> words = new ArrayList<>();
        int known = 0;
        int gaps = 0;
        for (int k = 0; k <= last; k++) {
            if (shown[k] == null) {
                shown[k] = "_".repeat(target[k].length()); // missing word
                onlyAccents = false;
            }
            words.add(shown[k]);
            gaps += (int) shown[k].chars().filter(c -> c == '_').count();
            known += shown[k].length() - (int) shown[k].chars().filter(c -> c == '_').count();
        }
        if (known == 0) {
            return null;
        }
        String pattern = String.join(" ", words) + (last < m - 1 ? " …" : "");
        String message;
        if (onlyAccents && extraWords == 0 && gaps > 0) {
            message = "Almost! Check the accent: " + pattern;
        } else if (gaps == 0 && last == m - 1) {
            // every letter is there, so the pattern would give the answer away
            int extraLetters = String.join(" ", typed).length() - String.join(" ", target).length();
            if (extraWords > 0) {
                message = extraWords == 1 ? "Almost! There's one word too many." : "Almost! There are a few words too many.";
            } else {
                message = extraLetters == 1 ? "Almost! There's one letter too many." : "Almost! There are a few letters too many.";
            }
        } else {
            message = "Almost! These letters are right: " + pattern;
        }
        return new Mistake(message, known, gaps);
    }

    // 0 = the same word, 1 = partly right (more than half the letters), 2 = a different word. The last typed word may be unfinished
    // ("ll" for "llamas"). Short words (el, tu, yo) are partly right only when just an accent is wrong.
    private static int wordCost(String typed, String target, boolean last) {
        if (typed.equalsIgnoreCase(target)) {
            return 0;
        }
        if (stripAccents(typed).equalsIgnoreCase(stripAccents(target))) {
            return 1;
        }
        if (target.length() <= 2) {
            return 2;
        }
        if (last && stripAccents(target).toLowerCase().startsWith(stripAccents(typed).toLowerCase())) {
            return 1;
        }
        if (sortedLetters(typed).equals(sortedLetters(target))) {
            return 1; // letters swapped ("yuo" for "you")
        }
        String pattern = knownLetters(typed, target);
        int known = pattern.length() - (int) pattern.chars().filter(c -> c == '_').count();
        return known * 2 > target.length() ? 1 : 2;
    }

    private static String sortedLetters(String word) {
        int[] letters = stripAccents(word).toLowerCase().codePoints().sorted().toArray();
        return new String(letters, 0, letters.length);
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
