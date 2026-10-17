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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

@RestController
@RequestMapping("/api/learners")
public class VocabController {
    private final Learners learners;
    private final SpellChecker spellChecker;
    private final Random random = new Random();

    public VocabController(Learners learners, SpellChecker spellChecker) {
        this.learners = learners;
        this.spellChecker = spellChecker;
    }

    // ipa = whether IPA can be shown; specialLetters = buttons for letters a normal keyboard lacks
    record LanguageDto(String name, int count, boolean ipa, List<String> specialLetters, String dictionaryName) {
    }

    record NewNameRequest(String name) {
    }

    record CategoryDto(String name, int count) {
    }

    record VocabDto(int id, String word, String english, String category, String context, String section,
                    boolean conjugation, String ipa, String dictionaryUrl) {
    }

    // newCategory = true confirms that a category which doesn't exist yet should be created;
    // duplicate = true confirms saving although the same word or English is already saved;
    // spellingChecked = true confirms saving although a word isn't in the dictionary
    record VocabRequest(String word, String english, String category, String context, boolean newCategory,
                        boolean duplicate, boolean spellingChecked) {
    }

    // promptIpa is only filled in when the prompt is in the language, so it never gives the answer away
    record QuestionDto(int id, boolean answerInLanguage, String prompt, String promptIpa) {
    }

    record CheckRequest(int id, boolean answerInLanguage, String answer, int attempt, List<String> shown) {
    }

    // finished = no more tries for this question; once finished, answer = the full answer with all
    // alternatives, ipa / dictionaryUrl are for the word in the language, and context is its note
    record CheckResult(boolean correct, String feedback, String answer, boolean finished, String ipa,
                       String dictionaryUrl, String context) {
    }

    // shown = the hints already shown for this question, so the next one says more
    record HintRequest(int id, boolean answerInLanguage, String answer, List<String> shown) {
    }

    record HintResult(String text) {
    }

    static class ApiException extends RuntimeException {
        final HttpStatus status;
        final Map<String, Object> body;

        ApiException(HttpStatus status, Map<String, Object> body) {
            super((String) body.get("error"));
            this.status = status;
            this.body = body;
        }

        ApiException(HttpStatus status, String error) {
            this(status, Map.of("error", error));
        }
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> handle(ApiException e) {
        return ResponseEntity.status(e.status).body(e.body);
    }

    // Everyone who learns with the app, each with their own languages and vocab
    @GetMapping
    public List<String> learners() {
        return learners.names();
    }

    // Returns the saved name ("mia" -> "Mia")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> addLearner(@RequestBody NewNameRequest request) {
        try {
            return Map.of("name", learners.add(request.name()));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    // Renames the learner (and their folder); returns the saved new name
    @PutMapping("/{learner}")
    public Map<String, String> renameLearner(@PathVariable String learner, @RequestBody NewNameRequest request) {
        String saved;
        try {
            saved = learners.rename(learner, request.name());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, e.getMessage());
        }
        if (saved == null) {
            library(learner); // 404
        }
        return Map.of("name", saved);
    }

    // Deletes the learner with all their languages and vocab (kept in vocab/.deleted, see Learners.delete)
    @DeleteMapping("/{learner}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLearner(@PathVariable String learner) {
        if (!learners.delete(learner)) {
            library(learner); // 404
        }
    }

    @GetMapping("/{learner}/languages")
    public List<LanguageDto> languages(@PathVariable String learner) {
        List<LanguageDto> result = new ArrayList<>();
        for (String name : library(learner).languageNames()) {
            result.add(toDto(learner, name));
        }
        return result;
    }

    @PostMapping("/{learner}/languages")
    @ResponseStatus(HttpStatus.CREATED)
    public LanguageDto addLanguage(@PathVariable String learner, @RequestBody NewNameRequest request) {
        try {
            return toDto(learner, library(learner).addLanguage(request.name()));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/{learner}/languages/{language}/categories")
    public List<CategoryDto> categories(@PathVariable String learner, @PathVariable String language) {
        List<CategoryDto> categories = new ArrayList<>();
        index(learner, language).categoryCounts().forEach((name, count) -> categories.add(new CategoryDto(name, count)));
        return categories;
    }

    // Empty category = all; q: one letter = that section, longer = search both sides
    @GetMapping("/{learner}/languages/{language}/vocab")
    public List<VocabDto> vocab(@PathVariable String learner, @PathVariable String language,
                                @RequestParam(defaultValue = "") String category,
                                @RequestParam(defaultValue = "") String q) {
        VocabIndex index = index(learner, language);
        List<VocabDto> result = new ArrayList<>();
        for (Vocab v : index.search(blankToNull(category), q.trim())) {
            result.add(toDto(learner, language, v, index));
        }
        return result;
    }

    @PostMapping("/{learner}/languages/{language}/vocab")
    @ResponseStatus(HttpStatus.CREATED)
    public VocabDto add(@PathVariable String learner, @PathVariable String language, @RequestBody VocabRequest request) {
        Vocab vocab = toVocab(learner, language, request);
        checkSpelling(learner, language, request, vocab);
        checkDuplicates(learner, language, request, vocab, null);
        store(learner, language).add(vocab);
        return toDto(learner, language, vocab, index(learner, language));
    }

    @PutMapping("/{learner}/languages/{language}/vocab/{id}")
    public VocabDto update(@PathVariable String learner, @PathVariable String language, @PathVariable int id, @RequestBody VocabRequest request) {
        Vocab existing = find(learner, language, id);
        Vocab updated = toVocab(learner, language, request);
        checkSpelling(learner, language, request, updated);
        checkDuplicates(learner, language, request, updated, existing);
        store(learner, language).replace(existing, updated);
        return toDto(learner, language, updated, index(learner, language));
    }

    @DeleteMapping("/{learner}/languages/{language}/vocab/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String learner, @PathVariable String language, @PathVariable int id) {
        store(learner, language).remove(find(learner, language, id));
    }

    // All vocab in the category, shuffled, each asked in a random direction
    @GetMapping("/{learner}/languages/{language}/quiz")
    public List<QuestionDto> quiz(@PathVariable String learner, @PathVariable String language, @RequestParam(defaultValue = "") String category) {
        Language rules = rules(learner, language);
        List<Vocab> vocab = new ArrayList<>(index(learner, language).inCategory(blankToNull(category)));
        Collections.shuffle(vocab, random);
        List<QuestionDto> questions = new ArrayList<>();
        for (Vocab v : vocab) {
            Question question = Question.from(v, random);
            String promptIpa = question.answerInLanguage() ? null : rules.ipa(question.prompt());
            questions.add(new QuestionDto(store(learner, language).idOf(v), question.answerInLanguage(), question.prompt(), promptIpa));
        }
        return questions;
    }

    @PostMapping("/{learner}/languages/{language}/quiz/check")
    public CheckResult check(@PathVariable String learner, @PathVariable String language, @RequestBody CheckRequest request) {
        Language rules = rules(learner, language);
        Vocab vocab = find(learner, language, request.id());
        String expected = Question.expectedAnswer(vocab, request.answerInLanguage());
        String answer = request.answer() == null ? "" : request.answer().trim();
        String ipa = rules.ipa(vocab.getWord());
        String dictionaryUrl = rules.dictionaryUrl(vocab.getWord());
        String context = vocab.getContext();
        if (AnswerChecker.isCorrect(answer, expected, rules)) {
            return new CheckResult(true, "Correct!", AnswerChecker.allOptions(expected), true, ipa, dictionaryUrl, context);
        }
        if (request.attempt() < AnswerChecker.MAX_ATTEMPTS) {
            return new CheckResult(false, AnswerChecker.feedback(answer, expected, rules,
                    request.shown() == null ? List.of() : request.shown()),
                    null, false, null, null, null);
        }
        return new CheckResult(false, "Not quite.", AnswerChecker.allOptions(expected), true, ipa, dictionaryUrl, context);
    }

    // The hint button; doesn't count as a try and never repeats a hint shown for this question
    @PostMapping("/{learner}/languages/{language}/quiz/hint")
    public HintResult hint(@PathVariable String learner, @PathVariable String language, @RequestBody HintRequest request) {
        Vocab vocab = find(learner, language, request.id());
        String expected = Question.expectedAnswer(vocab, request.answerInLanguage());
        String answer = request.answer() == null ? "" : request.answer();
        List<String> shown = request.shown() == null ? List.of() : request.shown();
        return new HintResult(AnswerChecker.hint(answer, expected, rules(learner, language), shown));
    }

    private VocabLibrary library(String learner) {
        VocabLibrary library = learners.library(learner);
        if (library == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, learner + " doesn't exist.");
        }
        return library;
    }

    private VocabStore store(String learner, String language) {
        VocabStore store = library(learner).store(language);
        if (store == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, language + " Doesn't exist.");
        }
        return store;
    }

    private Language rules(String learner, String language) {
        store(learner, language); // 404 for unknown languages
        return library(learner).language(language);
    }

    private VocabIndex index(String learner, String language) {
        return new VocabIndex(store(learner, language).getAll(), rules(learner, language));
    }

    private Vocab find(String learner, String language, int id) {
        Vocab vocab = store(learner, language).findById(id);
        if (vocab == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This vocab doesn't exist anymore.");
        }
        return vocab;
    }

    private Vocab toVocab(String learner, String language, VocabRequest request) {
        String word = request.word() == null ? "" : Normalizer.normalize(request.word(), Normalizer.Form.NFC).trim();
        String english = request.english() == null ? "" : Normalizer.normalize(request.english(), Normalizer.Form.NFC).trim();
        if (word.isEmpty() || english.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Fill in both the word and the English translation.");
        }
        if (word.contains(";") || english.contains(";")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Don't use ';' — it separates the columns in the vocab file.");
        }
        // one line in the file: line breaks in the note become spaces
        String context = request.context() == null ? "" : request.context().replaceAll("\\s*[\\r\\n]+\\s*", " ").trim();
        if (context.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Keep the context under 500 characters.");
        }
        String typed = request.category() == null ? "" : request.category().trim();
        if (typed.contains(";")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Don't use ';' in a category name.");
        }
        String category = index(learner, language).resolveCategory(typed);
        if (category == null) {
            if (!request.newCategory()) {
                throw new ApiException(HttpStatus.CONFLICT, Map.of(
                        "error", "'" + typed + "' is a new category.",
                        "newCategory", typed));
            }
            category = typed;
        }
        return new Vocab(word, english, category, context);
    }

    record MisspellingDto(String word, String language, List<String> suggestions) {
    }

    // 409 listing the words that aren't in the dictionary of their side, unless the request already confirmed them
    private void checkSpelling(String learner, String language, VocabRequest request, Vocab vocab) {
        if (request.spellingChecked()) {
            return;
        }
        Language rules = rules(learner, language);
        List<MisspellingDto> misspellings = new ArrayList<>();
        for (SpellChecker.Misspelling m : spellChecker.check(vocab.getWord(),
                rules.getSpellDictionary() == null ? List.of() : List.of(rules.getSpellDictionary()))) {
            misspellings.add(new MisspellingDto(m.word(), rules.getName(), m.suggestions()));
        }
        for (SpellChecker.Misspelling m : spellChecker.check(vocab.getEnglish(), SpellChecker.ENGLISH)) {
            misspellings.add(new MisspellingDto(m.word(), "English", m.suggestions()));
        }
        if (!misspellings.isEmpty()) {
            String error = misspellings.size() == 1 ? "Check the spelling:" : "Check the spelling of these words:";
            throw new ApiException(HttpStatus.CONFLICT, Map.of("error", error, "spelling", misspellings));
        }
    }

    // 409 listing every saved vocab with the same word or English, unless the request already confirmed it
    private void checkDuplicates(String learner, String language, VocabRequest request, Vocab vocab, Vocab except) {
        if (request.duplicate()) {
            return;
        }
        VocabIndex index = index(learner, language);
        List<VocabDto> duplicates = new ArrayList<>();
        for (Vocab v : index.duplicatesOf(vocab.getWord(), vocab.getEnglish(), except)) {
            duplicates.add(toDto(learner, language, v, index));
        }
        if (!duplicates.isEmpty()) {
            String error = duplicates.size() == 1 ? "This is already saved." : "This is already saved " + duplicates.size() + " times.";
            throw new ApiException(HttpStatus.CONFLICT, Map.of("error", error, "duplicates", duplicates));
        }
    }

    private LanguageDto toDto(String learner, String name) {
        VocabLibrary library = library(learner);
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

    private VocabDto toDto(String learner, String language, Vocab v, VocabIndex index) {
        Language rules = library(learner).language(language);
        return new VocabDto(store(learner, language).idOf(v), v.getWord(), v.getEnglish(), v.getCategory(), v.getContext(),
                index.sectionLetter(v), index.isConjugation(v), rules.ipa(v.getWord()), rules.dictionaryUrl(v.getWord()));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
