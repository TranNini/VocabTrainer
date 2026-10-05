package vocabapp;

import java.util.Random;

// One quiz question: shows one side of the vocab and expects the other
public record Question(String prompt, String expected, boolean askInSpanish) {
    public static Question from(Vocab vocab, Random random) {
        boolean askInSpanish = random.nextBoolean();
        String prompt = AnswerChecker.pickOne(askInSpanish ? vocab.getEnglish() : vocab.getSpanish(), random);
        return new Question(prompt, expectedAnswer(vocab, askInSpanish), askInSpanish);
    }

    public static String expectedAnswer(Vocab vocab, boolean askInSpanish) {
        return askInSpanish ? vocab.getSpanish() : vocab.getEnglish();
    }
}
