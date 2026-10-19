package vocabapp;

import java.util.Random;

// One quiz question: shows one side of the vocab and expects the other.
// answerInLanguage = the answer is the word in the language you learn, the prompt is English.
public record Question(String prompt, String expected, boolean answerInLanguage) {
    // The prompt is one of the alternatives; a short form like "he / she / it wants" is expanded first,
    // so the prompt is "she wants", never just "she"
    public static Question from(Vocab vocab, Language language, Random random) {
        boolean answerInLanguage = random.nextBoolean();
        String side = answerInLanguage ? vocab.getEnglish() : vocab.getWord();
        String prompt = AnswerChecker.pickOne(AnswerChecker.expandPronounShortForm(side, language), random);
        return new Question(prompt, expectedAnswer(vocab, answerInLanguage), answerInLanguage);
    }

    public static String expectedAnswer(Vocab vocab, boolean answerInLanguage) {
        return answerInLanguage ? vocab.getWord() : vocab.getEnglish();
    }
}
