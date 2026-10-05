package vocabapp;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
                        || v.getEnglish().toLowerCase().contains(query.toLowerCase());
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

    // "la abuela" -> "abuela", "¡Hola!" -> "Hola"
    private String sortKey(Vocab v) {
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

    // Maps each conjugated verb ("yo tengo") to the infinitive saved before it ("tener"),
    // since irregular forms can't be matched to their infinitive by spelling
    private Map<Vocab, Vocab> findInfinitives() {
        Map<Vocab, Vocab> infinitives = new HashMap<>();
        Vocab currentInfinitive = null;
        for (Vocab v : all) {
            if (!v.getCategory().equalsIgnoreCase(Vocab.VERBS_CATEGORY)) {
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

    // 0 = not a conjugation, 1 = first pronoun (yo) and so on; always 0 for languages without pronoun rules
    private int pronounRank(Vocab v) {
        if (!v.getCategory().equalsIgnoreCase(Vocab.VERBS_CATEGORY)) {
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
