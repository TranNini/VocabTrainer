public class Vocab {
    public static final String DEFAULT_CATEGORY = "general";
    public static final String VERBS_CATEGORY = "verbs";
    public static final String SENTENCES_CATEGORY = "sentences";

    private final String spanish;
    private final String english;
    private final String category;

    public Vocab(String spanish, String english, String category) {
        this.spanish = spanish;
        this.english = english;
        this.category = (category == null || category.isBlank()) ? DEFAULT_CATEGORY : category;
    }

    public String getSpanish() {
        return spanish;
    }

    public String getEnglish() {
        return english;
    }

    public String getCategory() {
        return category;
    }

    // v = verbs, s = sentences, g = general; anything else is returned unchanged
    public static String expandCategoryShortcut(String input) {
        return switch (input.toLowerCase()) {
            case "v" -> VERBS_CATEGORY;
            case "s" -> SENTENCES_CATEGORY;
            case "g" -> DEFAULT_CATEGORY;
            default -> input;
        };
    }
}
