package vocabapp;

// One entry: the word or sentence in the language you learn, and its English translation
public class Vocab {
    public static final String DEFAULT_CATEGORY = "general";
    public static final String VERBS_CATEGORY = "verbs";

    private final String word;
    private final String english;
    private final String category;

    public Vocab(String word, String english, String category) {
        this.word = word;
        this.english = english;
        this.category = (category == null || category.isBlank()) ? DEFAULT_CATEGORY : category;
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
}
