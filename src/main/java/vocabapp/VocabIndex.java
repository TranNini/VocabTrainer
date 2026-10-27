package vocabapp;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiPredicate;

// Sorting, searching and grouping over a snapshot of the saved vocab of one language
public class VocabIndex {
    private final List<Vocab> all;
    private final Language language;
    private final Collator order;
    private final Map<Vocab, Vocab> infinitives;

    public VocabIndex(List<Vocab> all, Language language) {
        this.all = List.copyOf(all);
        this.language = language;
        this.order = language.getCollator();
        this.infinitives = findInfinitives();
    }

    public Map<String, Integer> categoryCounts() {
        Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Vocab v : all) {
            counts.merge(v.getCategory(), 1, Integer::sum);
        }
        return counts;
    }

    // The saved spelling of an existing category, or null if it doesn't exist yet
    public String findCategory(String name) {
        for (Vocab v : all) {
            if (v.getCategory().equalsIgnoreCase(name)) {
                return v.getCategory();
            }
        }
        return null;
    }

    // Turns typed category input into a saved category name: blank or "general" = general (it always
    // exists, even in a new language), or any spelling of an existing category.
    // Returns null if it would be a new category.
    public String resolveCategory(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase(Vocab.DEFAULT_CATEGORY)) {
            return Vocab.DEFAULT_CATEGORY;
        }
        return findCategory(trimmed);
    }

    // Saved vocab with the same word and the same translation, in any category: never saved twice ("nhân vật =
    // character" is no duplicate of "tính cách = character / personality"). One alternative per side is enough;
    // case and punctuation don't matter, accents do. except = the vocab being edited, which isn't a duplicate
    // of itself.
    public List<Vocab> duplicatesOf(String word, String english, Vocab except) {
        return matching(word, english, except, String::equals);
    }

    // Saved vocab that is the same apart from a missing accent or a typo ("tinh cach = personalty" for "tính cách
    // = character / personality"): asked about before saving, as it may be a mistake. Duplicates aren't in it.
    public List<Vocab> similarTo(String word, String english, Vocab except) {
        List<Vocab> similar = matching(word, english, except, VocabIndex::isAlmostSame);
        similar.removeAll(duplicatesOf(word, english, except));
        return similar;
    }

    private List<Vocab> matching(String word, String english, Vocab except, BiPredicate<String, String> same) {
        List<Vocab> found = new ArrayList<>();
        for (Vocab v : all) {
            if (v != except && sharesOption(full(v.getWord()), full(word), same)
                    && sharesOption(full(v.getEnglish()), full(english), same)) {
                found.add(v);
            }
        }
        return found;
    }

    // The short form written out, as the quiz reads it: "el / ella / usted quiere" -> "él quiere / ella quiere /
    // usted quiere", so a pronoun on its own never matches ("el / ella / usted corre" is no duplicate)
    private String full(String text) {
        return AnswerChecker.expandPronounShortForm(text, language);
    }

    // Whether one alternative of a is the same as one of b (after normalising: case and punctuation). The start of
    // a sentence ("Yo soy... = I am...") is something else than the words alone ("yo soy = I am"), so "..." has
    // to match too.
    private static boolean sharesOption(String a, String b, BiPredicate<String, String> same) {
        for (String optionA : a.split("/")) {
            for (String optionB : b.split("/")) {
                if (isSentenceStart(optionA) != isSentenceStart(optionB)) {
                    continue;
                }
                String cleanA = AnswerChecker.normalize(optionA).toLowerCase();
                String cleanB = AnswerChecker.normalize(optionB).toLowerCase();
                if (!cleanA.isEmpty() && same.test(cleanA, cleanB)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSentenceStart(String option) {
        String trimmed = option.trim();
        return trimmed.endsWith("...") || trimmed.endsWith("…");
    }

    // The same apart from accents ("tinh cach" / "tính cách"), or also one letter apart in words of 5 letters or
    // more ("personality" / "personalty"). As both sides of an entry must match, "ma = ghost" and "má = mother"
    // stay apart.
    private static boolean isAlmostSame(String a, String b) {
        if (b.isEmpty()) {
            return false;
        }
        String plainA = AnswerChecker.stripAccents(a);
        String plainB = AnswerChecker.stripAccents(b);
        return plainA.equals(plainB)
                || Math.min(plainA.length(), plainB.length()) >= 5 && AnswerChecker.editDistance(plainA, plainB) <= 1;
    }

    // category null = all categories
    public List<Vocab> inCategory(String category) {
        List<Vocab> filtered = new ArrayList<>();
        for (Vocab v : all) {
            if (category == null || v.getCategory().equalsIgnoreCase(category)) {
                filtered.add(v);
            }
        }
        return filtered;
    }

    // A single letter gives that section, anything longer searches both sides.
    // Sorted alphabetically, with conjugations right after their infinitive.
    public List<Vocab> search(String category, String query) {
        List<Vocab> matches = new ArrayList<>();
        for (Vocab v : inCategory(category)) {
            boolean match;
            if (query.length() == 1) {
                match = order.equals(sectionLetter(v), query);
            } else {
                match = v.getWord().toLowerCase().contains(query.toLowerCase())
                        || v.getEnglish().toLowerCase().contains(query.toLowerCase())
                        || v.getReading().toLowerCase().contains(query.toLowerCase())
                        || v.getPast().toLowerCase().contains(query.toLowerCase())
                        || v.getParticiple().toLowerCase().contains(query.toLowerCase());
            }
            if (match) {
                matches.add(v);
            }
        }
        matches.sort((a, b) -> {
            int byInfinitive = order.compare(sortKey(infinitiveOf(a)), sortKey(infinitiveOf(b)));
            if (byInfinitive != 0) {
                return byInfinitive;
            }
            return Integer.compare(pronounRank(a), pronounRank(b));
        });
        return matches;
    }

    public String sectionLetter(Vocab v) {
        return language.sectionLetter(sortKey(infinitiveOf(v)));
    }

    public boolean isConjugation(Vocab v) {
        return infinitives.containsKey(v);
    }

    private Vocab infinitiveOf(Vocab v) {
        return infinitives.getOrDefault(v, v);
    }

    // "la abuela" -> "abuela", "¡Hola!" -> "Hola"; with a reading the reading ("猫" -> "neko"), so a Japanese
    // list sorts and has sections A-Z
    private String sortKey(Vocab v) {
        if (!v.getReading().isEmpty()) {
            return AnswerChecker.normalize(v.getReading());
        }
        String word = AnswerChecker.normalize(v.getWord());
        String[] words = word.split(" ", 2);
        if (words.length == 2) {
            for (String article : language.getArticles()) {
                if (words[0].equalsIgnoreCase(article)) {
                    return words[1];
                }
            }
        }
        return word;
    }

    // Maps each conjugated verb ("yo tengo") to its infinitive ("tener"), see Conjugations
    private Map<Vocab, Vocab> findInfinitives() {
        List<Vocab> verbs = all.stream().filter(v -> Vocab.isVerbCategory(v.getCategory())).toList();
        List<Vocab> infinitives = verbs.stream().filter(v -> pronounRank(v) == 0).toList();
        Map<Vocab, Vocab> infinitiveOf = new HashMap<>();
        Vocab previous = null;
        for (Vocab v : verbs) {
            if (pronounRank(v) == 0) {
                previous = v;
                continue;
            }
            // the one that fits, wherever it was saved (see Conjugations); else the one saved before
            Vocab infinitive = Conjugations.infinitiveOf(v, infinitives, previous, language);
            if (infinitive != null) {
                infinitiveOf.put(v, infinitive);
            }
        }
        return infinitiveOf;
    }

    // 0 = not a conjugation, 1 = first pronoun (yo) and so on; always 0 for languages without pronoun rules
    private int pronounRank(Vocab v) {
        if (!Vocab.isVerbCategory(v.getCategory())) {
            return 0;
        }
        String firstWord = AnswerChecker.normalize(v.getWord()).split("[ /,]", 2)[0];
        List<List<String>> pronouns = language.getSubjectPronouns();
        for (int i = 0; i < pronouns.size(); i++) {
            for (String pronoun : pronouns.get(i)) {
                if (order.equals(firstWord, pronoun)) {
                    return i + 1;
                }
            }
        }
        return 0;
    }
}
