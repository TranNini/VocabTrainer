package vocabapp;

import java.util.Random;

// One quiz question: shows one side of the vocab and expects the other.
// answerInLanguage = the answer is the word in the language you learn, the prompt is English.
public record Question(String prompt, String expected, boolean answerInLanguage) {
    public static Question from(Vocab vocab, Random random) {
        boolean answerInLanguage = random.nextBoolean();
        String prompt = AnswerChecker.pickOne(answerInLanguage ? vocab.getEnglish() : vocab.getWord(), random);
        return new Question(prompt, expectedAnswer(vocab, answerInLanguage), answerInLanguage);
    }

    public static String expectedAnswer(Vocab vocab, boolean answerInLanguage) {
        return answerInLanguage ? vocab.getWord() : vocab.getEnglish();
    }
}
