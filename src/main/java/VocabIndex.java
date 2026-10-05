import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

// Sorting, searching and grouping over a snapshot of the saved vocab
public class VocabIndex {
    private static final Collator spanishOrder = Collator.getInstance(new Locale("es"));
    private static final String[] SPANISH_ARTICLES = {"el", "la", "los", "las", "un", "una", "unos", "unas"};
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

    private final List<Vocab> all;
    private final Map<Vocab, Vocab> infinitives;

    public VocabIndex(List<Vocab> all) {
        this.all = List.copyOf(all);
        this.infinitives = findInfinitives(this.all);
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

    // A single letter gives that section, anything longer searches Spanish and English.
    // Sorted alphabetically, with conjugations right after their infinitive.
    public List<Vocab> search(String category, String query) {
        List<Vocab> matches = new ArrayList<>();
        for (Vocab v : inCategory(category)) {
            boolean match;
            if (query.length() == 1) {
                match = spanishOrder.equals(sectionLetter(v), query);
            } else {
                match = v.getSpanish().toLowerCase().contains(query.toLowerCase())
                        || v.getEnglish().toLowerCase().contains(query.toLowerCase());
            }
            if (match) {
                matches.add(v);
            }
        }
        matches.sort((a, b) -> {
            int byInfinitive = spanishOrder.compare(sortKey(infinitiveOf(a)), sortKey(infinitiveOf(b)));
            if (byInfinitive != 0) {
                return byInfinitive;
            }
            return Integer.compare(pronounRank(a), pronounRank(b));
        });
        return matches;
    }

    public String sectionLetter(Vocab v) {
        String key = sortKey(infinitiveOf(v));
        if (key.isEmpty()) {
            return "#";
        }
        String letter = key.substring(0, 1).toUpperCase();
        if (letter.equals("Ñ")) {
            return letter;
        }
        // "Á" -> "A", so the section header doesn't depend on which word comes first
        return Normalizer.normalize(letter, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    public boolean sameSection(String a, String b) {
        return spanishOrder.equals(a, b);
    }

    public boolean isConjugation(Vocab v) {
        return infinitives.containsKey(v);
    }

    private Vocab infinitiveOf(Vocab v) {
        return infinitives.getOrDefault(v, v);
    }

    // "la abuela" -> "abuela", "¡Hola!" -> "Hola"
    private static String sortKey(Vocab v) {
        String spanish = AnswerChecker.normalize(v.getSpanish());
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

    // Maps each conjugated verb ("yo tengo") to the infinitive saved before it ("tener"),
    // since irregular forms can't be matched to their infinitive by spelling
    private static Map<Vocab, Vocab> findInfinitives(List<Vocab> all) {
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

    // 0 = not a conjugation, 1 = yo ... 6 = ellos
    private static int pronounRank(Vocab v) {
        if (!v.getCategory().equalsIgnoreCase(Vocab.VERBS_CATEGORY)) {
            return 0;
        }
        String firstWord = AnswerChecker.normalize(v.getSpanish()).split("[ /,]", 2)[0];
        for (int i = 0; i < SUBJECT_PRONOUNS.length; i++) {
            for (String pronoun : SUBJECT_PRONOUNS[i]) {
                if (spanishOrder.equals(firstWord, pronoun)) {
                    return i + 1;
                }
            }
        }
        return 0;
    }
}
