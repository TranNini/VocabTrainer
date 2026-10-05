package vocabapp;

// One entry: the word or sentence in the language you learn, its English translation, and an optional
// context note (rules, usage, exceptions)
public class Vocab {
    public static final String DEFAULT_CATEGORY = "general";
    public static final String VERBS_CATEGORY = "verbs";

    private final String word;
    private final String english;
    private final String category;
    private final String context;

    public Vocab(String word, String english, String category) {
        this(word, english, category, "");
    }

    public Vocab(String word, String english, String category, String context) {
        this.word = word;
        this.english = english;
        this.category = (category == null || category.isBlank()) ? DEFAULT_CATEGORY : category;
        this.context = context == null ? "" : context.trim();
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
}
