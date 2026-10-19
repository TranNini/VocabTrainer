package vocabapp;

import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.Hunspell;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Spell check for new vocab, so a typo isn't learned: the open Hunspell dictionaries (the ones LibreOffice
// uses) in src/main/resources/dictionaries/<code>/, e.g. "es" for Spanish, "de" for German; the English side has American
// and British spelling ("color" and "colour").
// It only warns; the word can still be saved (names, rare words).
@Component
public class SpellChecker {
    public static final List<String> ENGLISH = List.of("en", "en-gb");

    // a word, also with an apostrophe inside ("don't"); numbers and signs are skipped
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]+(?:'[\\p{L}\\p{M}]+)*");
    private static final int SUGGESTIONS = 3;
    // languages that write every noun with a capital, so a capital inside doesn't mean a name
    private static final Set<String> CAPITALISED_NOUNS = Set.of("de");

    // loaded on first use, as each takes a moment and a lot of memory
    private final Map<String, Hunspell> dictionaries = new HashMap<>();

    // What is wrong with a word: SPELLING = not in the dictionary; CAPITAL = only right with a capital
    // letter ("haus" -> Haus, "spanish" -> Spanish); SENTENCE = a sentence that starts with a small letter
    public static final String SPELLING = "spelling";
    public static final String CAPITAL = "capital";
    public static final String SENTENCE = "sentence";

    // A word with a mistake, with the closest right ones
    public record Misspelling(String word, List<String> suggestions, String kind) {
    }

    // Categories whose entries are all sentences, in the languages of the app
    private static final Set<String> SENTENCE_CATEGORIES = Set.of("sentences", "sentence", "sätze", "satz",
            "frases", "oraciones");

    public static boolean isSentenceCategory(String category) {
        return category != null && SENTENCE_CATEGORIES.contains(category.trim().toLowerCase());
    }

    public List<Misspelling> check(String text, List<String> dictionaries) {
        return check(text, dictionaries, false);
    }

    // The words of text that are in none of the dictionaries, each once. A capitalised word that doesn't
    // start an alternative is taken as a name ("Yo soy Nini"), except in German, where every noun has a
    // capital ("das Hauss" is checked). Also, with any or no dictionary: a sentence must start with a
    // capital letter. A sentence is an alternative that ends with . ? ! or any alternative when sentences
    // is true (the vocab is in a sentence category).
    public List<Misspelling> check(String text, List<String> dictionaries, boolean sentences) {
        List<Misspelling> misspellings = new ArrayList<>();
        if (text == null) {
            return misspellings;
        }
        List<Hunspell> loaded = dictionaries.stream().map(this::load).toList();
        boolean capitalsAreNames = dictionaries.stream().noneMatch(CAPITALISED_NOUNS::contains);
        Set<String> seen = new LinkedHashSet<>();
        for (String option : text.split("/")) {
            Matcher matcher = WORD.matcher(option);
            boolean first = true;
            while (matcher.find()) {
                String word = matcher.group();
                boolean startsSentence = first && (sentences || option.trim().matches(".*[.?!…]$"))
                        && Character.isLowerCase(word.codePointAt(0));
                boolean name = capitalsAreNames && !first && Character.isUpperCase(word.codePointAt(0));
                first = false;
                if (name || !seen.add(word)) {
                    continue;
                }
                if (!loaded.isEmpty() && loaded.stream().noneMatch(hunspell -> spell(hunspell, word))) {
                    String capitalised = capitalise(word);
                    if (!capitalised.equals(word) && loaded.stream().anyMatch(hunspell -> spell(hunspell, capitalised))) {
                        misspellings.add(new Misspelling(word, List.of(capitalised), CAPITAL));
                    } else {
                        misspellings.add(new Misspelling(word, suggest(loaded.get(0), word), SPELLING));
                    }
                } else if (startsSentence) {
                    misspellings.add(new Misspelling(word, List.of(capitalise(word)), SENTENCE));
                }
            }
        }
        return misspellings;
    }

    private static String capitalise(String word) {
        int first = word.offsetByCodePoints(0, 1);
        return word.substring(0, first).toUpperCase() + word.substring(first);
    }

    // The languages a word list's translation side is recognised as, with their dictionaries
    private static final Map<String, List<String>> RECOGNISED = Map.of(
            "English", ENGLISH, "German", List.of("de"), "Spanish", List.of("es"));
    // enough words to tell, and quick at the start
    private static final int WORDS_TO_GUESS = 300;

    // The language most words of the translation side of the vocab are in ("German" for Kiki's Spanisch list),
    // or null if none of the dictionaries knows more of them than another (e.g. no vocab yet)
    public String guessTranslation(List<Vocab> vocab) {
        Map<String, Integer> known = new HashMap<>();
        int words = 0;
        for (Vocab v : vocab) {
            Matcher matcher = WORD.matcher(v.getEnglish());
            while (matcher.find() && words < WORDS_TO_GUESS) {
                String word = matcher.group();
                words++;
                RECOGNISED.forEach((language, codes) -> {
                    if (codes.stream().map(this::load).anyMatch(hunspell -> spell(hunspell, word))) {
                        known.merge(language, 1, Integer::sum);
                    }
                });
            }
        }
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(known.entrySet());
        ranked.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        if (ranked.isEmpty() || ranked.size() > 1 && ranked.get(0).getValue().equals(ranked.get(1).getValue())) {
            return null;
        }
        return ranked.get(0).getKey();
    }

    // one Hunspell must not be used by two requests at once
    private static boolean spell(Hunspell hunspell, String word) {
        synchronized (hunspell) {
            return hunspell.spell(word);
        }
    }

    // Whole words first: "nos toros" for "nostoros" is seldom what was meant
    private static List<String> suggest(Hunspell hunspell, String word) {
        List<String> all;
        synchronized (hunspell) {
            all = hunspell.suggest(word);
        }
        List<String> suggestions = new ArrayList<>();
        for (String suggestion : all) {
            if (!suggestion.contains(" ") && !suggestion.contains("-") && suggestions.size() < SUGGESTIONS) {
                suggestions.add(suggestion);
            }
        }
        return suggestions.isEmpty() ? all.subList(0, Math.min(SUGGESTIONS, all.size())) : suggestions;
    }

    private synchronized Hunspell load(String code) {
        return dictionaries.computeIfAbsent(code, c -> {
            String path = "/dictionaries/" + c + "/index.";
            try (InputStream affix = SpellChecker.class.getResourceAsStream(path + "aff");
                 InputStream words = SpellChecker.class.getResourceAsStream(path + "dic")) {
                if (affix == null || words == null) {
                    throw new IllegalStateException("No dictionary " + c + " in src/main/resources/dictionaries");
                }
                return new Hunspell(new Dictionary(new ByteBuffersDirectory(), "spell", affix, words));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read the dictionary " + c, e);
            } catch (ParseException e) {
                throw new IllegalStateException("Could not read the dictionary " + c, e);
            }
        });
    }
}
