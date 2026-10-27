package vocabapp;

// One entry: the word or sentence in the language you learn, its translation, an optional context note (rules,
// usage, exceptions) and, for languages written in their own script, its reading (Japanese 猫 -> romaji "neko").
// An English verb can have its other two forms: "to fly" with past "flew" and participle "flown".
public class Vocab {
    public static final String DEFAULT_CATEGORY = "general";
    public static final String VERBS_CATEGORY = "verbs";
    // categories whose conjugations are grouped under their infinitive, also in German and Spanish
    private static final java.util.Set<String> VERB_CATEGORIES = java.util.Set.of("verbs", "verben", "verbformen", "verbos");

    public static boolean isVerbCategory(String category) {
        return category != null && VERB_CATEGORIES.contains(category.trim().toLowerCase());
    }

    private final String word;
    private final String english;
    private final String category;
    private final String context;
    private final String reading;
    private final String past;
    private final String participle;

    public Vocab(String word, String english, String category) {
        this(word, english, category, "");
    }

    public Vocab(String word, String english, String category, String context) {
        this(word, english, category, context, "");
    }

    public Vocab(String word, String english, String category, String context, String reading) {
        this(word, english, category, context, reading, "", "");
    }

    public Vocab(String word, String english, String category, String context, String reading, String past,
                 String participle) {
        this.word = word;
        this.english = english;
        this.category = (category == null || category.isBlank()) ? DEFAULT_CATEGORY : category;
        this.context = context == null ? "" : context.trim();
        this.reading = reading == null ? "" : reading.trim();
        this.past = past == null ? "" : past.trim();
        this.participle = participle == null ? "" : participle.trim();
    }

    public String getWord() {
        return word;
    }

    public String getEnglish() {
        return english;
    }

    public String getCategory() {
        return category;
    }

    // "" when there is no note
    public String getContext() {
        return context;
    }

    // "" when the language has no reading row (see Language.getReadingName) or none was given
    public String getReading() {
        return reading;
    }

    // "flew" for "to fly"; "" for every other entry
    public String getPast() {
        return past;
    }

    // "flown" for "to fly"; "" for every other entry
    public String getParticiple() {
        return participle;
    }

    // An English verb saved with all three forms (see getPast)
    public boolean hasForms() {
        return !past.isEmpty();
    }

    // "to fly – flew – flown"; just the word without the other forms
    public String getWordWithForms() {
        return hasForms() ? word + " – " + past + " – " + participle : word;
    }
}
