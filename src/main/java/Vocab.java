public class Vocab {
    public static final String DEFAULT_CATEGORY = "general";

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
}
