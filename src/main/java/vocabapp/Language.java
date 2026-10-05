package vocabapp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

// Everything that differs between the languages you learn. A language added in the app gets the
// basics (alphabetical order for its locale, Wiktionary link); the extras (articles, conjugation
// grouping, IPA, fixed special letter buttons, its own dictionary) come from rules set up here.
public class Language {
    // The translation side is always English
    public static final List<String> ENGLISH_ARTICLES = List.of("the", "a", "an");

    // Locales for alphabetical order, by the English name of the language
    private static final Map<String, Locale> LOCALES = Map.ofEntries(
            Map.entry("spanish", new Locale("es")), Map.entry("french", Locale.FRENCH),
            Map.entry("italian", Locale.ITALIAN), Map.entry("german", Locale.GERMAN),
            Map.entry("portuguese", new Locale("pt")), Map.entry("catalan", new Locale("ca")),
            Map.entry("dutch", new Locale("nl")), Map.entry("swedish", new Locale("sv")),
            Map.entry("norwegian", new Locale("nb")), Map.entry("danish", new Locale("da")),
            Map.entry("polish", new Locale("pl")), Map.entry("turkish", new Locale("tr")),
            Map.entry("russian", new Locale("ru")), Map.entry("greek", new Locale("el")),
            Map.entry("japanese", Locale.JAPANESE), Map.entry("korean", Locale.KOREAN),
            Map.entry("chinese", Locale.CHINESE));

    private final String name;
    private final Collator collator;
    private final List<String> articles;
    private final List<List<String>> subjectPronouns;
    private final List<String> specialLetters;
    private final UnaryOperator<String> ipa;
    private final String dictionaryName;
    private final String dictionaryUrl;

    private Language(String name, List<String> articles, List<List<String>> subjectPronouns,
                     List<String> specialLetters, UnaryOperator<String> ipa, String dictionaryName, String dictionaryUrl) {
        this.name = name;
        this.collator = Collator.getInstance(LOCALES.getOrDefault(name.toLowerCase(), Locale.ROOT));
        // PRIMARY ignores case and accents, so "árbol" sorts with "arbol"
        this.collator.setStrength(Collator.PRIMARY);
        this.articles = articles;
        this.subjectPronouns = subjectPronouns;
        this.specialLetters = specialLetters;
        this.ipa = ipa;
        this.dictionaryName = dictionaryName;
        this.dictionaryUrl = dictionaryUrl;
    }

    public static Language forName(String name) {
        if (name.equalsIgnoreCase("spanish")) {
            return spanish(name);
        }
        return new Language(name, List.of(), List.of(), List.of(), null,
                "Wiktionary", "https://en.wiktionary.org/wiki/%s#" + name.replace(' ', '_'));
    }

    private static Language spanish(String name) {
        return new Language(name,
                List.of("el", "la", "los", "las", "un", "una", "unos", "unas"),
                // index + 1 is the order conjugations are listed in under their infinitive
                List.of(List.of("yo"), List.of("tú"), List.of("él", "ella", "usted"),
                        List.of("nosotros", "nosotras"), List.of("vosotros", "vosotras"),
                        List.of("ellos", "ellas", "ustedes")),
                List.of("á", "é", "í", "ó", "ú", "ñ", "ü", "¿", "¡"),
                SpanishIpa::transcribe,
                "SpanishDict", "https://www.spanishdict.com/translate/%s");
    }

    public String getName() {
        return name;
    }

    public Collator getCollator() {
        return collator;
    }

    public List<String> getArticles() {
        return articles;
    }

    public List<List<String>> getSubjectPronouns() {
        return subjectPronouns;
    }

    // Empty = no fixed list; the app then offers the special letters found in the saved words
    public List<String> getSpecialLetters() {
        return specialLetters;
    }

    public boolean hasIpa() {
        return ipa != null;
    }

    // null when there are no IPA rules for this language yet
    public String ipa(String word) {
        return ipa == null ? null : ipa.apply(word);
    }

    public String getDictionaryName() {
        return dictionaryName;
    }

    // Link for the first alternative, without ¡!¿? and "..."
    public String dictionaryUrl(String word) {
        String first = word.split("/")[0].replaceAll("[¡!¿?.…]", "").trim();
        return dictionaryUrl.formatted(URLEncoder.encode(first, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    // "Á" -> "A", but a letter the language treats as its own (Spanish "Ñ") stays
    public String sectionLetter(String word) {
        if (word.isEmpty()) {
            return "#";
        }
        String letter = word.substring(0, word.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        String plain = Normalizer.normalize(letter, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return !plain.isEmpty() && collator.equals(plain, letter) ? plain : letter;
    }
}
