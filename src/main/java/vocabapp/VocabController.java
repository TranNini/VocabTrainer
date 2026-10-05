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
import java.util.TreeSet;

@RestController
@RequestMapping("/api/languages")
public class VocabController {
    private final VocabLibrary library;
    private final Random random = new Random();

    public VocabController(VocabLibrary library) {
        this.library = library;
    }

    // ipa = whether IPA can be shown; specialLetters = buttons for letters a normal keyboard lacks
    record LanguageDto(String name, int count, boolean ipa, List<String> specialLetters, String dictionaryName) {
    }

    record NewLanguageRequest(String name) {
    }

    record CategoryDto(String name, int count) {
    }

    record VocabDto(int id, String word, String english, String category, String section, boolean conjugation,
                    String ipa, String dictionaryUrl) {
    }

    // newCategory = true confirms that a category which doesn't exist yet should be created
    record VocabRequest(String word, String english, String category, boolean newCategory) {
    }

    // promptIpa is only filled in when the prompt is in the language, so it never gives the answer away
    record QuestionDto(int id, boolean answerInLanguage, String prompt, String promptIpa) {
    }

    record CheckRequest(int id, boolean answerInLanguage, String answer, int attempt) {
    }

    // finished = no more tries for this question; once finished, answer = the full answer with all
    // alternatives, and ipa / dictionaryUrl are for the word in the language
    record CheckResult(boolean correct, String feedback, String answer, boolean finished, String ipa,
                       String dictionaryUrl) {
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

    @GetMapping
    public List<LanguageDto> languages() {
        List<LanguageDto> result = new ArrayList<>();
        for (String name : library.languageNames()) {
            result.add(toDto(name));
        }
        return result;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LanguageDto addLanguage(@RequestBody NewLanguageRequest request) {
        try {
            return toDto(library.addLanguage(request.name()));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/{language}/categories")
    public List<CategoryDto> categories(@PathVariable String language) {
        List<CategoryDto> categories = new ArrayList<>();
        index(language).categoryCounts().forEach((name, count) -> categories.add(new CategoryDto(name, count)));
        return categories;
    }

    // Empty category = all; q: one letter = that section, longer = search both sides
    @GetMapping("/{language}/vocab")
    public List<VocabDto> vocab(@PathVariable String language,
                                @RequestParam(defaultValue = "") String category,
                                @RequestParam(defaultValue = "") String q) {
        VocabIndex index = index(language);
        List<VocabDto> result = new ArrayList<>();
        for (Vocab v : index.search(blankToNull(category), q.trim())) {
            result.add(toDto(language, v, index));
        }
        return result;
    }

    @PostMapping("/{language}/vocab")
    @ResponseStatus(HttpStatus.CREATED)
    public VocabDto add(@PathVariable String language, @RequestBody VocabRequest request) {
        Vocab vocab = toVocab(language, request);
        store(language).add(vocab);
        return toDto(language, vocab, index(language));
    }

    @PutMapping("/{language}/vocab/{id}")
    public VocabDto update(@PathVariable String language, @PathVariable int id, @RequestBody VocabRequest request) {
        Vocab existing = find(language, id);
        Vocab updated = toVocab(language, request);
        store(language).replace(existing, updated);
        return toDto(language, updated, index(language));
    }

    @DeleteMapping("/{language}/vocab/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String language, @PathVariable int id) {
        store(language).remove(find(language, id));
    }

    // All vocab in the category, shuffled, each asked in a random direction
    @GetMapping("/{language}/quiz")
    public List<QuestionDto> quiz(@PathVariable String language, @RequestParam(defaultValue = "") String category) {
        Language rules = rules(language);
        List<Vocab> vocab = new ArrayList<>(index(language).inCategory(blankToNull(category)));
        Collections.shuffle(vocab, random);
        List<QuestionDto> questions = new ArrayList<>();
        for (Vocab v : vocab) {
            Question question = Question.from(v, random);
            String promptIpa = question.answerInLanguage() ? null : rules.ipa(question.prompt());
            questions.add(new QuestionDto(store(language).idOf(v), question.answerInLanguage(), question.prompt(), promptIpa));
        }
        return questions;
    }

    @PostMapping("/{language}/quiz/check")
    public CheckResult check(@PathVariable String language, @RequestBody CheckRequest request) {
        Language rules = rules(language);
        Vocab vocab = find(language, request.id());
        String expected = Question.expectedAnswer(vocab, request.answerInLanguage());
        String answer = request.answer() == null ? "" : request.answer().trim();
        String ipa = rules.ipa(vocab.getWord());
        String dictionaryUrl = rules.dictionaryUrl(vocab.getWord());
        if (AnswerChecker.isCorrect(answer, expected)) {
            return new CheckResult(true, "Correct!", AnswerChecker.allOptions(expected), true, ipa, dictionaryUrl);
        }
        if (request.attempt() < AnswerChecker.MAX_ATTEMPTS) {
            return new CheckResult(false, AnswerChecker.feedback(answer, expected, request.attempt(), rules),
                    null, false, null, null);
        }
        return new CheckResult(false, "Not quite.", AnswerChecker.allOptions(expected), true, ipa, dictionaryUrl);
    }

    private VocabStore store(String language) {
        VocabStore store = library.store(language);
        if (store == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "There is no language called '" + language + "'.");
        }
        return store;
    }

    private Language rules(String language) {
        store(language); // 404 for unknown languages
        return library.language(language);
    }

    private VocabIndex index(String language) {
        return new VocabIndex(store(language).getAll(), rules(language));
    }

    private Vocab find(String language, int id) {
        Vocab vocab = store(language).findById(id);
        if (vocab == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This vocab doesn't exist anymore.");
        }
        return vocab;
    }

    private Vocab toVocab(String language, VocabRequest request) {
        String word = request.word() == null ? "" : request.word().trim();
        String english = request.english() == null ? "" : request.english().trim();
        if (word.isEmpty() || english.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please fill in both the word and the English translation.");
        }
        if (word.contains(";") || english.contains(";")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please don't use ';' — it separates the columns in the vocab file.");
        }
        String typed = request.category() == null ? "" : request.category().trim();
        if (typed.contains(";")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please don't use ';' in a category name.");
        }
        String category = index(language).resolveCategory(typed);
        if (category == null) {
            if (!request.newCategory()) {
                throw new ApiException(HttpStatus.CONFLICT, Map.of(
                        "error", "'" + typed + "' is a new category.",
                        "newCategory", typed));
            }
            category = typed;
        }
        return new Vocab(word, english, category);
    }

    private LanguageDto toDto(String name) {
        Language rules = library.language(name);
        List<Vocab> all = library.store(name).getAll();
        List<String> letters = rules.getSpecialLetters();
        if (letters.isEmpty()) {
            letters = specialLettersIn(all);
        }
        return new LanguageDto(rules.getName(), all.size(), rules.hasIpa(), letters, rules.getDictionaryName());
    }

    // Letters beyond a–z used in the saved words, e.g. "à", "è", "ç" for a new language
    private static List<String> specialLettersIn(List<Vocab> all) {
        TreeSet<String> letters = new TreeSet<>();
        for (Vocab v : all) {
            v.getWord().toLowerCase().codePoints()
                    .filter(c -> Character.isLetter(c) && c > 127)
                    .forEach(c -> letters.add(Character.toString(c)));
        }
        return new ArrayList<>(letters);
    }

    private VocabDto toDto(String language, Vocab v, VocabIndex index) {
        Language rules = library.language(language);
        return new VocabDto(store(language).idOf(v), v.getWord(), v.getEnglish(), v.getCategory(),
                index.sectionLetter(v), index.isConjugation(v), rules.ipa(v.getWord()), rules.dictionaryUrl(v.getWord()));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
