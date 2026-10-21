package vocabapp;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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

    // Like isCorrect(answer, expected), but what is in brackets and a subject pronoun at the start are
    // optional: "you have" is right for "you have (singular)", "tengo" for "yo tengo"
    public static boolean isCorrect(String answer, String expected, Language language) {
        return isCorrect(answer, withOptionalParts(answer, expected, language));
    }

    private static String withOptionalParts(String answer, String expected, Language language) {
        return withOptionalPronouns(answer, expandPronounShortForm(withoutBrackets(expected), language), language);
    }

    // "él / ella / usted quiere" -> "él quiere/ella quiere/usted quiere", "he / she / it wants" -> "he wants/…":
    // pronouns on their own before the last alternative share its verb. Only when every alternative
    // before the last is a pronoun on its own, so "you / you all" stays as it is. A pronoun without its
    // accent ("el") gets it here, as "el" on its own would be the article.
    static String expandPronounShortForm(String expected, Language language) {
        String[] options = expected.split("/");
        String[] last = options[options.length - 1].trim().split(" +", 2);
        if (options.length < 2 || last.length < 2 || pronounSpelling(last[0], language) == null) {
            return expected;
        }
        List<String> expanded = new ArrayList<>();
        for (int i = 0; i < options.length - 1; i++) {
            String option = options[i].trim();
            String pronoun = option.contains(" ") ? null : pronounSpelling(option, language);
            // "you / you all" stays as it is; "sie / Sie haben" is two pronouns (they / you), so it is expanded
            if (pronoun == null || option.equals(last[0])) {
                return expected;
            }
            expanded.add(pronoun + " " + last[1]);
        }
        expanded.add(options[options.length - 1].trim());
        return String.join("/", expanded);
    }

    // "el" -> "él", "She" -> "she"; null if the word is no pronoun of the language or of its translation side
    private static String pronounSpelling(String word, Language language) {
        List<String> pronouns = new ArrayList<>(language.getTranslation().allPronouns());
        pronouns.addAll(language.allPronouns());
        for (String pronoun : pronouns) {
            if (stripAccents(pronoun).equalsIgnoreCase(stripAccents(normalize(word)))) {
                return pronoun;
            }
        }
        return null;
    }

    // "you have (singular)" -> "you have/you have (singular)": what is in brackets may be left out, or
    // typed too. The form without comes first, so the hints lead to it.
    static String withoutBrackets(String expected) {
        List<String> options = new ArrayList<>();
        List<String> withBrackets = new ArrayList<>();
        for (String option : expected.split("/")) {
            String without = option.replaceAll("\\([^()]*\\)", " ").replaceAll(" +", " ").trim();
            if (without.equals(option.trim()) || normalize(without).isEmpty()) {
                options.add(option);
            } else {
                options.add(without);
                withBrackets.add(option);
            }
        }
        options.addAll(withBrackets);
        return String.join("/", options);
    }

    // "yo tengo" -> "yo tengo/tengo", "yo tengo una pregunta" -> "… /tengo una pregunta": a subject pronoun
    // at the start, of a conjugation or a sentence, may be left out. The forms without pronoun come after all the others, so the
    // first hints still lead to the saved form. Not when the answer starts with a pronoun itself: then
    // it is compared with the pronoun ("yo tienes" for "tú tienes" -> "__ tienes", not a word too many)
    static String withOptionalPronouns(String answer, String expected, Language language) {
        if (!language.arePronounsOptional() || isPronoun(normalize(answer).split(" ")[0], language)) {
            return expected;
        }
        List<String> options = new ArrayList<>(List.of(expected.split("/")));
        for (String option : expected.split("/")) {
            String[] words = normalize(option).split(" ", 2);
            if (words.length == 2 && isPronoun(words[0], language) && !options.contains(words[1])) {
                options.add(words[1]);
            }
        }
        return String.join("/", options);
    }

    private static boolean isPronoun(String word, Language language) {
        for (List<String> pronouns : language.getSubjectPronouns()) {
            for (String pronoun : pronouns) {
                if (pronoun.equalsIgnoreCase(word)) {
                    return true;
                }
            }
        }
        return false;
    }

    // The hint after a wrong answer (Check). Every wrong try gets one, never one that was already shown
    // for this vocab (shown = the hints after Check and from the hint button so far).
    public static String feedback(String answer, String expected, Language language, List<String> shown) {
        String hint = hint(answer, expected, language, shown);
        return hint.startsWith(STARTS_WITH) ? "Not quite. Hint: it starts with" + hint.substring(STARTS_WITH.length()) : hint;
    }

    // The best hint for what is typed (see hints)
    public static String hint(String answer, String expected, Language language) {
        return hint(answer, expected, language, List.of());
    }

    // The hint button: the best hint for what is typed that wasn't shown yet for this vocab. The page
    // allows one per try, so with the hint after Check there are two hints per try.
    public static String hint(String answer, String expected, Language language, List<String> shown) {
        expected = withOptionalParts(answer, expected, language);
        Set<String> seen = new HashSet<>();
        for (String text : shown) {
            seen.add(hintKey(text));
        }
        List<String> hints = hints(answer, expected, language);
        for (String hint : hints) {
            if (!seen.contains(hintKey(hint)) || hint.equals(RIGHT)) {
                return hint;
            }
        }
        return hints.get(hints.size() - 1); // seldom: every hint was shown already
    }

    // Every hint for what is typed, the most helpful first: a nearly right answer -> what kind of
    // mistake it is ("One letter is wrong: t_ngo", "An accent is missing: c_mo"); then the first hint
    // (see firstHint), then ones with one letter more each. The whole answer is never given.
    private static List<String> hints(String answer, String expected, Language language) {
        List<String> articles = new ArrayList<>(language.getArticles());
        articles.addAll(language.getTranslation().getArticles());
        Hint first = firstHint(answer, expected, language);
        List<String> hints = new ArrayList<>();
        String kind = first.text().equals(RIGHT) ? null : mistakeKind(normalize(answer), expected, articles);
        if (kind != null) {
            hints.add(kind);
        }
        hints.add(first.text());
        hints.addAll(moreHints(answer, expected, language, first));
        if (kind != null || first.mistake() == null) {
            // after "C_mo t_ llamas" with its letters filled in, a start would tell less
            hints.addAll(longerStarts(normalize(answer), expected, articles));
        }
        // the same pattern under another name is the same hint
        Set<String> keys = new HashSet<>();
        hints.removeIf(hint -> !keys.add(hintKey(hint)));
        return hints;
    }

    private static final String RIGHT = "That's right! Press Check.";
    private static final List<String> ARTICLE_HINTS = List.of(
            "Don't forget the article!", "Check the article.", "What comes in front of an infinitive verb?");

    // text = the hint; mistake = what it was made from, if it shows which letters are right
    private record Hint(String text, Mistake mistake) {
    }

    private static final String STARTS_WITH = "It starts with";

    // One letter past the right start of what is typed, then one more each time, up to the last
    // letter but one ("tango" for "tengo" -> 'te', 'ten', 'teng'); the article only when it is typed
    private static List<String> longerStarts(String answer, String expected, List<String> articles) {
        boolean typedArticle = splitArticle(answer, articles)[0] != null;
        String target = null;
        int known = -1;
        for (String option : expected.split("/")) {
            String full = normalize(option);
            String word = splitArticle(full, articles)[1];
            for (String candidate : typedArticle ? List.of(full) : List.of(word, full)) {
                int prefix = commonPrefix(answer, candidate);
                if (prefix > known) {
                    target = candidate;
                    known = prefix;
                }
            }
        }
        List<String> starts = new ArrayList<>();
        String start = target.substring(0, known);
        while (true) {
            start = start(target, start.length());
            if (start.length() >= target.length()) {
                return starts;
            }
            starts.add(STARTS_WITH + " '" + start + "'");
        }
    }

    // "Not quite. Hint: it starts with 'd'" after Check is the same hint as "It starts with 'd'", and
    // "An accent is missing: c_mo" the same as "Check the accent: c_mo"
    private static String hintKey(String text) {
        String key = text.replaceFirst("^Not quite\\. Hint: ", "").toLowerCase();
        int colon = key.indexOf(": ");
        return colon < 0 ? key : key.substring(colon + 2);
    }

    // For a nearly right answer, what kind of mistake it is; null if it isn't nearly right or is typed
    // right so far ("habl" for "hablar").
    // Compared with every alternative, without its article when none is typed ("baca" for "la vaca").
    private static String mistakeKind(String answer, String expected, List<String> articles) {
        if (answer.isEmpty()) {
            return null;
        }
        boolean typedArticle = splitArticle(answer, articles)[0] != null;
        for (String option : expected.split("/")) {
            String full = normalize(option);
            String word = splitArticle(full, articles)[1];
            for (String target : typedArticle || word.equals(full) ? List.of(full) : List.of(full, word)) {
                if (commonPrefix(answer, target) == answer.length()) {
                    return null; // right so far, so not a mistake: the next letter helps more
                }
                String kind = mistakeKind(answer, target);
                if (kind != null) {
                    return kind;
                }
            }
        }
        return null;
    }

    private static String mistakeKind(String answer, String target) {
        String a = answer.toLowerCase();
        String b = target.toLowerCase();
        if (a.equals(b)) {
            return null;
        }
        if (a.length() == b.length() && stripAccents(a).equals(stripAccents(b))) {
            return accentMistake(a, target);
        }
        int distance = editDistances(a, b)[a.length()][b.length()];
        if (distance == 1) {
            if (a.replace(" ", "").equals(b.replace(" ", ""))) {
                return a.length() > b.length() ? "Almost! There's a space too many." : "Almost! A space is missing.";
            }
            if (a.length() > b.length()) {
                return "Almost! There's one letter too many.";
            }
            String pattern = knownLetters(answer, target);
            return (a.length() < b.length() ? "Almost! One letter is missing: " : "Almost! One letter is wrong: ") + pattern;
        }
        if (distance == 2 && a.length() == b.length()) {
            int i = 0;
            while (a.charAt(i) == b.charAt(i)) {
                i++;
            }
            if (i + 1 < a.length() && a.charAt(i) == b.charAt(i + 1) && a.charAt(i + 1) == b.charAt(i)
                    && a.substring(i + 2).equals(b.substring(i + 2))) {
                return "Almost! Two letters are swapped.";
            }
        }
        return null;
    }

    // Only accents, ü or ñ differ: says which, with "_" where they belong ("pinguino" -> "ping_ino")
    private static String accentMistake(String answer, String target) {
        StringBuilder pattern = new StringBuilder(target);
        int missing = 0;
        int extra = 0;
        int other = 0;
        boolean onlyDiaeresis = true;
        boolean onlyTilde = true;
        for (int i = 0; i < answer.length(); i++) {
            char typed = answer.charAt(i);
            char right = Character.toLowerCase(target.charAt(i));
            if (typed == right) {
                continue;
            }
            pattern.setCharAt(i, '_');
            boolean rightMarked = right != stripAccents(String.valueOf(right)).charAt(0);
            boolean typedMarked = typed != stripAccents(String.valueOf(typed)).charAt(0);
            if (rightMarked && !typedMarked) {
                missing++;
                onlyDiaeresis &= right == 'ü';
                onlyTilde &= right == 'ñ';
            } else if (typedMarked && !rightMarked) {
                extra++;
            } else {
                other++;
            }
        }
        if (missing == 0 && other == 0) {
            return extra == 1 ? "Almost! There's an accent too many." : "Almost! There are accents too many.";
        }
        if (extra == 0 && other == 0) {
            if (onlyDiaeresis) {
                return "Almost! The diaeresis (¨) is missing: " + pattern;
            }
            if (onlyTilde) {
                return "Almost! The ñ is missing: " + pattern;
            }
            return (missing == 1 ? "Almost! An accent is missing: " : "Almost! Some accents are missing: ") + pattern;
        }
        if (missing == 1 && extra == 1 && other == 0) {
            return "Almost! The accent is on the wrong letter: " + pattern;
        }
        return "Almost! Check the accents: " + pattern;
    }

    // The hints after the first one, each with one letter more: the letters that are right with the
    // missing ones filled in one by one, or a longer start. Stops before the whole answer.
    private static List<String> moreHints(String answer, String expected, Language language, Hint first) {
        List<String> more = new ArrayList<>();
        Mistake mistake = first.mistake();
        if (mistake != null) {
            char[] pattern = mistake.pattern().toCharArray();
            for (int filled = 1; filled < mistake.gaps() || filled == mistake.gaps() && mistake.more(); filled++) {
                int gap = String.valueOf(pattern).indexOf('_');
                pattern[gap] = mistake.target().charAt(gap);
                more.add("One more letter: " + String.valueOf(pattern) + (mistake.more() ? " …" : ""));
            }
            return more;
        }
        List<String> articles = new ArrayList<>(language.getArticles());
        articles.addAll(language.getTranslation().getArticles());
        String cleaned = normalize(answer);
        if (ARTICLE_HINTS.contains(first.text())) {
            // the word is right, so only the article is left: its first letter ('e' for "el", 'l' for "la")
            String word = splitArticle(cleaned, articles)[1];
            for (String option : expected.split("/")) {
                String[] split = splitArticle(normalize(option), articles);
                if (split[0] != null && split[1].equalsIgnoreCase(word)) {
                    more.add("It starts with '" + split[0].charAt(0) + "'");
                    break;
                }
            }
            return more;
        }
        // the alternative (with or without its article) that the typed text or the first hint fits best
        String fits = first.text().startsWith("It starts with '")
                ? first.text().substring("It starts with '".length(), first.text().length() - 1) : cleaned;
        String target = null;
        int known = -1;
        for (String option : expected.split("/")) {
            String full = normalize(option);
            for (String candidate : List.of(full, splitArticle(full, articles)[1])) {
                int prefix = commonPrefix(fits, candidate);
                if (prefix > known) {
                    target = candidate;
                    known = prefix;
                }
            }
        }
        String start = target.substring(0, known);
        while (true) {
            start = start(target, start.length());
            if (start.length() >= target.length()) {
                return more;
            }
            more.add("It starts with '" + start + "'");
        }
    }

    private static Hint firstHint(String answer, String expected, Language language) {
        String cleaned = normalize(answer);
        if (!cleaned.isEmpty() && isCorrect(cleaned, expected)) {
            return new Hint(RIGHT, null);
        }
        List<String> articles = new ArrayList<>(language.getArticles());
        articles.addAll(language.getTranslation().getArticles());
        String[] typed = splitArticle(cleaned, articles);
        // Typed right so far, nearly finished or with the article ("e", "el", "to") -> one letter more.
        // Checked first, so a correct start is never mistaken for a nearly right answer.
        if (!cleaned.isEmpty()) {
            for (String option : expected.split("/")) {
                String full = normalize(option);
                String word = splitArticle(full, articles)[1];
                if (commonPrefix(cleaned, full) == cleaned.length()) {
                    return startHint(full, cleaned.length());
                }
                // the whole word without its article falls through to "Don't forget the article!"
                if (typed[0] == null && cleaned.length() < word.length()
                        && commonPrefix(cleaned, word) == cleaned.length()) {
                    return startHint(word, cleaned.length());
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
                return new Hint("What comes in front of an infinitive verb?", null);
            }
            return new Hint(typed[0] == null ? "Don't forget the article!" : "Check the article.", null);
        }
        int prefix = commonPrefix(typedWord, word);
        boolean articleRight = article == null || article.equalsIgnoreCase(typed[0]);
        if (!typedWord.isEmpty() && prefix == word.length() && articleRight) {
            return new Hint("The start is right, but there are letters too many.", null);
        }
        String start = start(word, prefix);
        if (article != null && articleRight) {
            start = article + " " + start; // keep the article that is already typed right
        }
        // the letters that are right, unless the start tells more ("Dama" for "dado" -> 'dad')
        Mistake mistake = findMistakes(cleaned, expected, articles);
        if (mistake != null && (mistake.gaps() == 0 || mistake.known() >= start.replace(" ", "").length())) {
            return new Hint(mistake.message(), mistake);
        }
        return new Hint("It starts with '" + start + "'", null);
    }

    // One letter past what is typed right, but never the whole answer
    private static Hint startHint(String target, int known) {
        String start = start(target, known);
        if (start.length() >= target.length()) {
            return new Hint("Almost! Just the last letter is missing.", null);
        }
        return new Hint(STARTS_WITH + " '" + start + "'", null);
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

    // known = letters shown as right, gaps = letters shown as "_"; pattern lines up letter by letter
    // with target (the target words up to the last one typed); more = words not typed yet follow
    private record Mistake(String message, int known, int gaps, String pattern, String target, boolean more) {
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
        // words with every letter right but more typed ("quieremos" for "queremos")
        boolean lettersTooMany = false;
        int i = n;
        int j = m;
        while (i > 0 && j > 0) {
            int cost = wordCost(typed[i - 1], target[j - 1], i == n);
            if (dp[i][j] == dp[i - 1][j - 1] + cost) {
                shown[j - 1] = cost < 2 ? knownLetters(typed[i - 1], target[j - 1]) : "_".repeat(target[j - 1].length());
                lettersTooMany |= shown[j - 1].indexOf('_') < 0 && typed[i - 1].length() > target[j - 1].length();
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
        String shownWords = String.join(" ", words);
        String pattern = shownWords + (last < m - 1 ? " …" : "");
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
            message = "Almost! These letters are right: " + pattern
                    + (lettersTooMany ? ", but a word has letters too many." : "");
        }
        return new Mistake(message, known, gaps, shownWords,
                String.join(" ", List.of(target).subList(0, last + 1)), last < m - 1);
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

    static String stripAccents(String text) {
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

    private static boolean isClose(String answer, String expected) {
        String a = answer.toLowerCase();
        String b = expected.toLowerCase();
        int distance = editDistances(a, b)[a.length()][b.length()];
        int allowed = b.length() <= 4 ? 1 : 2;
        return distance <= allowed;
    }

    // How many letters to change, add or remove to turn a into b ("gato" -> "gatto": 1)
    static int editDistance(String a, String b) {
        return editDistances(a, b)[a.length()][b.length()];
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
}
