package vocabapp;

import java.util.List;

// The quiz for an English verb saved with all three forms: one question per form, always from the translation,
// "fliegen (present tense)" -> "to fly", "fliegen (past tense)" -> "flew", "fliegen (past participle)" -> "flown".
// Each form is checked like any answer (alternatives with "/", "got / gotten"; "to" is part of the infinitive).
final class VerbForms {
    static final List<String> NAMES = List.of("present tense", "past tense", "past participle");

    private VerbForms() {
    }

    // "fliegen (past tense)"
    static String prompt(Vocab vocab, int form, java.util.Random random) {
        return AnswerChecker.pickOne(vocab.getEnglish(), random) + " (" + NAMES.get(form) + ")";
    }

    // The form asked for: 0 = the infinitive ("to fly"), 1 = past ("flew"), 2 = participle ("flown")
    static String expected(Vocab vocab, int form) {
        return switch (form) {
            case 1 -> vocab.getPast();
            case 2 -> vocab.getParticiple();
            default -> vocab.getWord();
        };
    }

    // A form the request names, or null when it asks the whole entry
    static Integer form(Vocab vocab, Integer form) {
        return vocab.hasForms() && form != null && form >= 0 && form < NAMES.size() ? form : null;
    }
}
