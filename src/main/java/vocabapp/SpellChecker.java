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
// uses) in src/main/resources/dictionaries/<code>/, e.g. "es" for Spanish; the English side has American
// and British spelling ("color" and "colour").
// It only warns; the word can still be saved (names, rare words).
@Component
public class SpellChecker {
    public static final List<String> ENGLISH = List.of("en", "en-gb");

    // a word, also with an apostrophe inside ("don't"); numbers and signs are skipped
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]+(?:'[\\p{L}\\p{M}]+)*");
    private static final int SUGGESTIONS = 3;

    // loaded on first use, as each takes a moment and a lot of memory
    private final Map<String, Hunspell> dictionaries = new HashMap<>();

    // A word that isn't in the dictionary, with the closest ones that are
    public record Misspelling(String word, List<String> suggestions) {
    }

    // The words of text that are in none of the dictionaries, each once. A capitalised word that doesn't
    // start an alternative is taken as a name ("Yo soy Nini"). Empty for no dictionaries.
    public List<Misspelling> check(String text, List<String> dictionaries) {
        List<Misspelling> misspellings = new ArrayList<>();
        if (text == null) {
            return misspellings;
        }
        List<Hunspell> loaded = dictionaries.stream().map(this::load).toList();
        Set<String> seen = new LinkedHashSet<>();
        for (String option : text.split("/")) {
            Matcher matcher = WORD.matcher(option);
            boolean first = true;
            while (matcher.find()) {
                String word = matcher.group();
                boolean name = !first && Character.isUpperCase(word.codePointAt(0));
                first = false;
                if (name || !seen.add(word)) {
                    continue;
                }
                if (!loaded.isEmpty() && loaded.stream().noneMatch(hunspell -> spell(hunspell, word))) {
                    misspellings.add(new Misspelling(word, suggest(loaded.get(0), word)));
                }
            }
        }
        return misspellings;
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
