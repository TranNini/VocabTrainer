import java.util.Random;

// One quiz question: shows one side of the vocab and expects the other
public record Question(String prompt, String expected) {
    public static Question from(Vocab vocab, Random random) {
        boolean askInSpanish = random.nextBoolean();
        String prompt = AnswerChecker.pickOne(askInSpanish ? vocab.getEnglish() : vocab.getSpanish(), random);
        String expected = askInSpanish ? vocab.getSpanish() : vocab.getEnglish();
        return new Question(prompt, expected);
    }
}
