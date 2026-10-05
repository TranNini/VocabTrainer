package vocabapp;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api")
public class VocabController {
    private final VocabStore store;
    private final Random random = new Random();

    public VocabController(VocabStore store) {
        this.store = store;
    }

    record CategoryDto(String name, int count) {
    }

    record VocabDto(int id, String spanish, String english, String category, String section, boolean conjugation,
                    String ipa) {
    }

    // newCategory = true confirms that a category which doesn't exist yet should be created
    record VocabRequest(String spanish, String english, String category, boolean newCategory) {
    }

    // promptIpa is only filled in when the prompt is Spanish, so it never gives the answer away
    record QuestionDto(int id, boolean askInSpanish, String prompt, String promptIpa) {
    }

    record CheckRequest(int id, boolean askInSpanish, String answer, int attempt) {
    }

    // finished = no more tries for this question; once finished, answer = the full answer with all
    // alternatives and spanishIpa = the pronunciation of the Spanish side
    record CheckResult(boolean correct, String feedback, String answer, boolean finished, String spanishIpa) {
    }

    static class ApiException extends RuntimeException {
        final HttpStatus status;
        final Map<String, String> body;

        ApiException(HttpStatus status, Map<String, String> body) {
            super(body.get("error"));
            this.status = status;
            this.body = body;
        }

        ApiException(HttpStatus status, String error) {
            this(status, Map.of("error", error));
        }
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> handle(ApiException e) {
        return ResponseEntity.status(e.status).body(e.body);
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        List<CategoryDto> categories = new ArrayList<>();
        new VocabIndex(store.getAll()).categoryCounts()
                .forEach((name, count) -> categories.add(new CategoryDto(name, count)));
        return categories;
    }

    // Empty category = all; q works like the console search (one letter = that section)
    @GetMapping("/vocab")
    public List<VocabDto> vocab(@RequestParam(defaultValue = "") String category,
                                @RequestParam(defaultValue = "") String q) {
        VocabIndex index = new VocabIndex(store.getAll());
        List<VocabDto> result = new ArrayList<>();
        for (Vocab v : index.search(blankToNull(category), q.trim())) {
            result.add(toDto(v, index));
        }
        return result;
    }

    @PostMapping("/vocab")
    @ResponseStatus(HttpStatus.CREATED)
    public VocabDto add(@RequestBody VocabRequest request) {
        Vocab vocab = toVocab(request);
        store.add(vocab);
        return toDto(vocab, new VocabIndex(store.getAll()));
    }

    @PutMapping("/vocab/{id}")
    public VocabDto update(@PathVariable int id, @RequestBody VocabRequest request) {
        Vocab existing = find(id);
        Vocab updated = toVocab(request);
        store.replace(existing, updated);
        return toDto(updated, new VocabIndex(store.getAll()));
    }

    @DeleteMapping("/vocab/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        store.remove(find(id));
    }

    // All vocab in the category, shuffled, each asked in a random direction
    @GetMapping("/quiz")
    public List<QuestionDto> quiz(@RequestParam(defaultValue = "") String category) {
        List<Vocab> vocab = new ArrayList<>(new VocabIndex(store.getAll()).inCategory(blankToNull(category)));
        Collections.shuffle(vocab, random);
        List<QuestionDto> questions = new ArrayList<>();
        for (Vocab v : vocab) {
            Question question = Question.from(v, random);
            String promptIpa = question.askInSpanish() ? null : SpanishIpa.transcribe(question.prompt());
            questions.add(new QuestionDto(store.idOf(v), question.askInSpanish(), question.prompt(), promptIpa));
        }
        return questions;
    }

    @PostMapping("/quiz/check")
    public CheckResult check(@RequestBody CheckRequest request) {
        Vocab vocab = find(request.id());
        String expected = Question.expectedAnswer(vocab, request.askInSpanish());
        String answer = request.answer() == null ? "" : request.answer().trim();
        String spanishIpa = SpanishIpa.transcribe(vocab.getSpanish());
        if (AnswerChecker.isCorrect(answer, expected)) {
            return new CheckResult(true, "Correct!", AnswerChecker.allOptions(expected), true, spanishIpa);
        }
        if (request.attempt() < AnswerChecker.MAX_ATTEMPTS) {
            return new CheckResult(false, AnswerChecker.feedback(answer, expected, request.attempt()), null, false, null);
        }
        return new CheckResult(false, "Not quite.", AnswerChecker.allOptions(expected), true, spanishIpa);
    }

    private Vocab find(int id) {
        Vocab vocab = store.findById(id);
        if (vocab == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This vocab doesn't exist anymore.");
        }
        return vocab;
    }

    private Vocab toVocab(VocabRequest request) {
        String spanish = request.spanish() == null ? "" : request.spanish().trim();
        String english = request.english() == null ? "" : request.english().trim();
        if (spanish.isEmpty() || english.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please fill in both Spanish and English.");
        }
        if (spanish.contains(";") || english.contains(";")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please don't use ';' — it separates the columns in vocab.txt.");
        }
        String typed = request.category() == null ? "" : request.category().trim();
        if (typed.contains(";")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please don't use ';' in a category name.");
        }
        String category = new VocabIndex(store.getAll()).resolveCategory(typed);
        if (category == null) {
            if (!request.newCategory()) {
                throw new ApiException(HttpStatus.CONFLICT, Map.of(
                        "error", "'" + typed + "' is a new category.",
                        "newCategory", typed));
            }
            category = typed;
        }
        return new Vocab(spanish, english, category);
    }

    private VocabDto toDto(Vocab v, VocabIndex index) {
        return new VocabDto(store.idOf(v), v.getSpanish(), v.getEnglish(), v.getCategory(),
                index.sectionLetter(v), index.isConjugation(v), SpanishIpa.transcribe(v.getSpanish()));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
