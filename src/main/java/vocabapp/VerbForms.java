package vocabapp;

import java.util.ArrayList;
import java.util.List;

// The quiz for an English verb saved with all three forms: "fliegen" is answered with "to fly", "flew" and
// "flown", one field each, and only counts when all three are right. Each form is checked like any answer
// (alternatives with "/", "got / gotten"); the hints are for the first wrong one and say which form it is.
final class VerbForms {
    static final List<String> NAMES = List.of("Infinitive", "Simple past", "Past participle");

    private final List<String> expected;
    private final Language language;

    VerbForms(Vocab vocab, Language language) {
        this.expected = List.of(vocab.getWord(), vocab.getPast(), vocab.getParticiple());
        this.language = language;
    }

    // Which of the typed forms are right, in the order of NAMES; a missing one is wrong
    List<Boolean> right(List<String> answers) {
        List<Boolean> right = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            right.add(AnswerChecker.isCorrect(answer(answers, i), expected.get(i), language));
        }
        return right;
    }

    // "to fly – flew – flown", every alternative of each form included ("to get – got – got / gotten")
    String fullAnswer() {
        return String.join(" – ", expected.stream().map(AnswerChecker::allOptions).toList());
    }

    // The hint after Check for the first wrong form: "Simple past: Not quite. Hint: it starts with f…"
    String feedback(List<String> answers, List<String> shown) {
        int wrong = firstWrong(answers);
        return NAMES.get(wrong) + ": " + AnswerChecker.feedback(answer(answers, wrong), expected.get(wrong), language,
                shownFor(wrong, shown));
    }

    // The hint button: for the first wrong form, or "That's right! Press Check." when all are right
    String hint(List<String> answers, List<String> shown) {
        int wrong = firstWrong(answers);
        String hint = AnswerChecker.hint(answer(answers, wrong), expected.get(wrong), language, shownFor(wrong, shown));
        return wrong == 0 && AnswerChecker.isCorrect(answer(answers, 0), expected.get(0), language) ? hint
                : NAMES.get(wrong) + ": " + hint;
    }

    private int firstWrong(List<String> answers) {
        List<Boolean> right = right(answers);
        return Math.max(0, right.indexOf(false));
    }

    // The hints already shown for this form, without its name, so none is repeated
    private static List<String> shownFor(int form, List<String> shown) {
        String prefix = NAMES.get(form) + ": ";
        return shown.stream().filter(text -> text.startsWith(prefix)).map(text -> text.substring(prefix.length()))
                .toList();
    }

    private static String answer(List<String> answers, int form) {
        return answers == null || form >= answers.size() || answers.get(form) == null ? "" : answers.get(form).trim();
    }
}
