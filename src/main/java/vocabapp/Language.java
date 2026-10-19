package vocabapp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

// Everything that differs between languages. A word list is a pair: the language you learn, translated
// into another one (see translatedInto); both sides use the rules of their language. A language added in the
// app gets the basics (alphabetical order for its locale, Wiktionary link); the extras (articles, conjugation
// grouping, IPA, fixed special letter buttons, spell check, its own dictionary) come from rules set up here,
// for English, Spanish, German and Vietnamese.
public class Language {
    public static final String DEFAULT_TRANSLATION = "English";
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
            Map.entry("chinese", Locale.CHINESE), Map.entry("vietnamese", new Locale("vi")));

    private final String name;
    private final Collator collator;
    private final List<String> articles;
    private final List<List<String>> subjectPronouns;
    private final boolean pronounsOptional;
    private final List<String> specialLetters;
    private final UnaryOperator<String> ipa;
    private final String dictionaryName;
    private final String dictionaryUrl;
    private final List<String> spellDictionaries;
    // the other side of the word list; null = English
    private final Language translation;

    private Language(String name, List<String> articles, List<List<String>> subjectPronouns,
                     boolean pronounsOptional, List<String> specialLetters, UnaryOperator<String> ipa,
                     String dictionaryName, String dictionaryUrl, List<String> spellDictionaries) {
        this.name = name;
        this.collator = Collator.getInstance(LOCALES.getOrDefault(englishName(name), Locale.ROOT));
        // PRIMARY ignores case and accents, so "árbol" sorts with "arbol"
        this.collator.setStrength(Collator.PRIMARY);
        this.articles = articles;
        this.subjectPronouns = subjectPronouns;
        this.pronounsOptional = pronounsOptional;
        this.specialLetters = specialLetters;
        this.ipa = ipa;
        this.dictionaryName = dictionaryName;
        this.dictionaryUrl = dictionaryUrl;
        this.spellDictionaries = spellDictionaries;
        this.translation = null;
    }

    private Language(Language language, Language translation) {
        this.name = language.name;
        this.collator = language.collator;
        this.articles = language.articles;
        this.subjectPronouns = language.subjectPronouns;
        this.pronounsOptional = language.pronounsOptional;
        this.specialLetters = language.specialLetters;
        this.ipa = language.ipa;
        this.dictionaryName = language.dictionaryName;
        this.dictionaryUrl = language.dictionaryUrl;
        this.spellDictionaries = language.spellDictionaries;
        this.translation = translation;
    }

    // The same language, with the vocab translated into another one ("Spanisch" into "German")
    public Language translatedInto(Language translation) {
        return new Language(this, translation);
    }

    // The other side of the word list, with its own rules; English unless set
    public Language getTranslation() {
        return translation != null ? translation : DefaultTranslation.ENGLISH;
    }

    // made on first use, as Language itself has to exist first
    private static final class DefaultTranslation {
        static final Language ENGLISH = forName(DEFAULT_TRANSLATION);
    }

    // Names of a language in other languages ("Spanisch"), so it gets the same rules as its English name
    private static final Map<String, String> OTHER_NAMES = Map.of(
            "spanisch", "spanish", "español", "spanish", "espanol", "spanish",
            "deutsch", "german", "alemán", "german", "englisch", "english",
            "vietnamesisch", "vietnamese", "tiếng việt", "vietnamese");

    // "Spanisch" -> "spanish": the English name, in lower case
    static String englishName(String name) {
        String lower = name.toLowerCase();
        return OTHER_NAMES.getOrDefault(lower, lower);
    }

    public static Language forName(String name) {
        String english = englishName(name);
        if (english.equals("spanish")) {
            return spanish(name);
        }
        if (english.equals("vietnamese")) {
            return vietnamese(name);
        }
        if (english.equals("german")) {
            return german(name);
        }
        if (english.equals("english")) {
            return english(name);
        }
        return new Language(name, List.of(), List.of(), false, List.of(), null,
                "Wiktionary", "https://en.wiktionary.org/wiki/%s#" + name.replace(' ', '_'), List.of());
    }

    // "to" counts as an article too, so "to have" sorts under H; the pronouns group "I have" under "to have"
    // and make the short form "he / she / it has" work; American and British spelling
    private static Language english(String name) {
        return new Language(name,
                List.of("the", "a", "an", "to"),
                List.of(List.of("I"), List.of("you"), List.of("he", "she", "it"), List.of("we"), List.of("you"),
                        List.of("they")),
                false, List.of(), null,
                "Wiktionary", "https://en.wiktionary.org/wiki/%s#English", SpellChecker.ENGLISH);
    }

    private static Language spanish(String name) {
        return new Language(name,
                List.of("el", "la", "los", "las", "un", "una", "unos", "unas"),
                // index + 1 is the order conjugations are listed in under their infinitive
                List.of(List.of("yo"), List.of("tú"), List.of("él", "ella", "usted"),
                        List.of("nosotros", "nosotras"), List.of("vosotros", "vosotras"),
                        List.of("ellos", "ellas", "ustedes")),
                // Spanish often leaves the pronoun out: "tengo" is right for "yo tengo"
                true,
                List.of("á", "é", "í", "ó", "ú", "ñ", "ü", "¿", "¡"),
                SpanishIpa::transcribe,
                "SpanishDict", "https://www.spanishdict.com/translate/%s",
                List.of("es"));
    }

    // The pronouns group the conjugations under their infinitive and make the short form
    // "er / sie / es hat" work; German doesn't leave them out, so "hat" alone stays wrong
    private static Language german(String name) {
        return new Language(name,
                // all cases, so "den Hund" also sorts under H and gets the article hints
                List.of("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "eines"),
                List.of(List.of("ich"), List.of("du"), List.of("er", "sie", "es"), List.of("wir"), List.of("ihr"),
                        List.of("sie", "Sie")),
                false,
                // capitals too, as every noun starts with one ("die Übung")
                List.of("ä", "ö", "ü", "ß", "Ä", "Ö", "Ü"),
                null,
                "Wiktionary", "https://en.wiktionary.org/wiki/%s#German", List.of("de"));
    }

    private static Language vietnamese(String name) {
        return new Language(name, List.of(), List.of(), false,
                // the letters, then the five tone marks (huyền, sắc, hỏi, ngã, nặng) to type after a vowel
                List.of("ă", "â", "đ", "ê", "ô", "ơ", "ư", "\u0300", "\u0301", "\u0309", "\u0303", "\u0323"),
                null,
                // 2 = VDict's Vietnamese - English dictionary
                "VDict", "https://vdict.com/%s,2,0,0.html", List.of());
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

    // Every subject pronoun once, e.g. for the short form "er / sie / es hat"
    public List<String> allPronouns() {
        return subjectPronouns.stream().flatMap(List::stream).distinct().toList();
    }

    // Whether a subject pronoun at the start may be left out of an answer (Spanish, not German)
    public boolean arePronounsOptional() {
        return pronounsOptional;
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

    // The folders in src/main/resources/dictionaries for the spell check of new vocab (a word is right if
    // one of them knows it); empty = no spell check
    public List<String> getSpellDictionaries() {
        return spellDictionaries;
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
