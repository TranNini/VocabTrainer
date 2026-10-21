package vocabapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VocabControllerTest {
    private static final String NINI = "/api/learners/Nini";
    private static final String SPANISH = NINI + "/languages/Spanish";

    @TempDir
    static Path dir;

    @DynamicPropertySource
    static void files(DynamicPropertyRegistry registry) {
        registry.add("vocab.dir", () -> dir.resolve("vocab").toString());
        registry.add("vocab.old-file", () -> dir.resolve("vocab.txt").toString());
        registry.add("vocab.access-code-file", () -> dir.resolve("access-code.txt").toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    Learners learners;

    VocabLibrary library;

    @BeforeEach
    void emptySpanish() throws Exception {
        if (learners.library("Nini") == null) {
            learners.add("Nini");
        }
        library = learners.library("Nini");
        if (library.store("Spanish") == null) {
            library.addLanguage("Spanish");
        }
        VocabStore store = library.store("Spanish");
        for (Vocab v : store.getAll()) {
            store.remove(v);
        }
        Files.deleteIfExists(dir.resolve("vocab/Nini/Spanish" + QuizLog.EXTENSION));
    }

    private ResultActions send(String method, String url, String json) throws Exception {
        var request = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> throw new IllegalArgumentException(method);
        };
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int add(String word, String english, String category) throws Exception {
        send("POST", SPANISH + "/vocab", """
                {"word": "%s", "english": "%s", "category": "%s", "newCategory": true}
                """.formatted(word, english, category)).andExpect(status().isCreated());
        VocabStore store = library.store("Spanish");
        return store.idOf(store.getAll().get(store.getAll().size() - 1));
    }

    @Test
    void listsVocabSortedWithConjugationsGrouped() throws Exception {
        add("tener", "to have", "verbs");
        add("yo tengo", "I have", "verbs");
        add("el agua", "water", "general");

        mvc.perform(get(SPANISH + "/vocab"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].word").value("el agua"))
                .andExpect(jsonPath("$[0].section").value("A"))
                .andExpect(jsonPath("$[0].ipa").value("/el ˈa.ɡwa/"))
                .andExpect(jsonPath("$[0].dictionaryUrl").value("https://www.spanishdict.com/translate/el%20agua"))
                .andExpect(jsonPath("$[2].word").value("yo tengo"))
                .andExpect(jsonPath("$[2].section").value("T"))
                .andExpect(jsonPath("$[2].conjugation").value(true));
        mvc.perform(get(SPANISH + "/vocab").param("category", "verbs").param("q", "t"))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get(SPANISH + "/categories"))
                .andExpect(jsonPath("$[0].name").value("general"))
                .andExpect(jsonPath("$[1].count").value(2));
    }

    @Test
    void asksBeforeCreatingANewCategory() throws Exception {
        add("hablar", "to talk", "verbs");
        send("POST", SPANISH + "/vocab", """
                {"word": "ir", "english": "to go", "category": "verb"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.newCategory").value("verb"));
        send("POST", SPANISH + "/vocab", """
                {"word": "ir", "english": "to go", "category": "VERBS"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("verbs"));
        send("POST", SPANISH + "/vocab", """
                {"word": "la mesa", "english": "table", "category": ""}
                """)
                .andExpect(jsonPath("$.category").value("general"));
    }

    @Test
    void rejectsMissingWordsAndSemicolons() throws Exception {
        send("POST", SPANISH + "/vocab", """
                {"word": "", "english": "to go", "category": "verbs"}
                """).andExpect(status().isBadRequest());
        send("POST", SPANISH + "/vocab", """
                {"word": "ir;x", "english": "to go", "category": "verbs"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void quizResultsShowInTheStats() throws Exception {
        int id = add("tener", "to have", "verbs");
        String wrong = """
                {"id": %d, "answerInLanguage": true, "answer": "tango", "attempt": %d, "shown": [], "hints": 1}
                """;
        send("POST", SPANISH + "/quiz/check", wrong.formatted(id, 1)).andExpect(jsonPath("$.finished").value(false));
        send("POST", SPANISH + "/quiz/check", """
                {"id": %d, "answerInLanguage": true, "answer": "tener", "attempt": 2, "shown": [], "hints": 1}
                """.formatted(id)).andExpect(jsonPath("$.correct").value(true));
        for (int attempt = 1; attempt <= AnswerChecker.MAX_ATTEMPTS; attempt++) {
            send("POST", SPANISH + "/quiz/check", wrong.formatted(id, attempt));
        }
        mvc.perform(get(SPANISH + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(2))
                .andExpect(jsonPath("$.right").value(1))
                .andExpect(jsonPath("$.rightFirstTry").value(0))
                .andExpect(jsonPath("$.hints").value(2))
                .andExpect(jsonPath("$.today").value(2))
                .andExpect(jsonPath("$.streak").value(1))
                .andExpect(jsonPath("$.charts", hasSize(4)))
                .andExpect(jsonPath("$.charts[1].bars[-1].answered").value(2))
                .andExpect(jsonPath("$.practising").value(1))
                .andExpect(jsonPath("$.toPractise[0].word").value("tener"));
        // an edited word keeps its results
        send("PUT", SPANISH + "/vocab/" + id, """
                {"word": "tener", "english": "to have / to own", "category": "verbs"}
                """).andExpect(status().isOk());
        send("PUT", SPANISH + "/vocab/" + id, """
                {"word": "tenér", "english": "to have", "category": "verbs", "spellingChecked": true}
                """).andExpect(status().isOk());
        mvc.perform(get(SPANISH + "/stats")).andExpect(jsonPath("$.toPractise[0].word").value("tenér"));
    }

    @Test
    void refreshQuizHasTheWordsNotAskedForAWhile() throws Exception {
        add("tener", "to have", "verbs");
        add("ir", "to go", "verbs");
        QuizLog log = library.log("Spanish");
        log.add(new QuizLog.Entry(java.time.LocalDateTime.now().minusDays(20), "tener", true, 1, 0));
        log.add(new QuizLog.Entry(java.time.LocalDateTime.now().minusDays(1), "ir", true, 1, 0));
        mvc.perform(get(SPANISH + "/quiz/refresh"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.days").value(QuizStats.REFRESH_AFTER_DAYS));
        mvc.perform(get(SPANISH + "/quiz?refresh=true"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].prompt").value(org.hamcrest.Matchers.oneOf("tener", "to have")));
        mvc.perform(get(SPANISH + "/quiz")).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void asksBeforeSavingAMisspelledWord() throws Exception {
        send("POST", SPANISH + "/vocab", """
                {"word": "nostoros queremos", "english": "we wnat", "category": "verbs", "newCategory": true}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.spelling", hasSize(2)))
                .andExpect(jsonPath("$.spelling[0].word").value("nostoros"))
                .andExpect(jsonPath("$.spelling[0].language").value("Spanish"))
                .andExpect(jsonPath("$.spelling[0].suggestions[0]").value("nosotros"))
                .andExpect(jsonPath("$.spelling[1].word").value("wnat"))
                .andExpect(jsonPath("$.spelling[1].language").value("English"));
        send("POST", SPANISH + "/vocab", """
                {"word": "nostoros queremos", "english": "we wnat", "category": "verbs", "newCategory": true, "spellingChecked": true}
                """)
                .andExpect(status().isCreated());
    }

    @Test
    void languagesWithoutADictionaryOnlyCheckTheEnglish() throws Exception {
        send("POST", NINI + "/languages", """
                {"name": "Swedish"}
                """);
        send("POST", NINI + "/languages/Swedish/vocab", """
                {"word": "katten", "english": "the catt", "category": "general", "newCategory": true}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.spelling", hasSize(1)))
                .andExpect(jsonPath("$.spelling[0].word").value("catt"));
    }

    @Test
    void aCommaIsNoSeparatorForAlternatives() throws Exception {
        send("POST", SPANISH + "/vocab", """
                {"word": "bonito, lindo", "english": "pretty", "category": "general"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Use / instead of a comma for alternatives: 'bonito / lindo'. A comma is only for sentences."));
        send("POST", SPANISH + "/vocab", """
                {"word": "bonito", "english": "pretty,nice", "category": "general"}
                """)
                .andExpect(status().isBadRequest());
        // in sentences a comma belongs: a sentence category, or an entry ending with . ? !
        send("POST", SPANISH + "/vocab", """
                {"word": "Oye, ¿qué haces?", "english": "Hey, what are you doing?", "category": "general", "spellingChecked": true}
                """)
                .andExpect(status().isCreated());
        send("POST", SPANISH + "/vocab", """
                {"word": "Sí, claro", "english": "Yes, of course", "category": "sentences", "newCategory": true}
                """)
                .andExpect(status().isCreated());
    }

    @Test
    void twoQuickPressesOnAddSaveTheWordOnce() throws Exception {
        add("tener", "to have", "verbs"); // the category exists
        String body = """
                {"word": "comer", "english": "to eat", "category": "verbs"}
                """;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return send("POST", SPANISH + "/vocab", body).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new java.util.ArrayList<>();
        for (var result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();
        assertEquals(1, statuses.stream().filter(status -> status == 201).count(), statuses.toString());
        assertEquals(3, statuses.stream().filter(status -> status == 409).count(), statuses.toString());
        mvc.perform(get(SPANISH + "/vocab?q=comer")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void neverSavesADuplicate() throws Exception {
        add("tú tienes", "you have", "verbs");
        int id = add("ir", "to go", "verbs");
        send("POST", SPANISH + "/vocab", """
                {"word": "Tú tienes", "english": "You have", "category": "general"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Not saved: this is already in the list."))
                .andExpect(jsonPath("$.duplicates", hasSize(1)))
                .andExpect(jsonPath("$.duplicates[0].word").value("tú tienes"));
        // no way around it
        send("POST", SPANISH + "/vocab", """
                {"word": "tú tienes", "english": "you have", "category": "verbs", "duplicate": true, "spellingChecked": true}
                """)
                .andExpect(status().isConflict());
        send("PUT", SPANISH + "/vocab/" + id, """
                {"word": "tú tienes", "english": "you have", "category": "verbs"}
                """)
                .andExpect(status().isConflict());
        // another word with the same translation is no duplicate, nor is the same word with another meaning
        send("POST", SPANISH + "/vocab", """
                {"word": "vosotros tenéis", "english": "you have", "category": "verbs"}
                """)
                .andExpect(status().isCreated());
        send("PUT", SPANISH + "/vocab/" + id, """
                {"word": "ir", "english": "to go", "category": "verbs", "context": "irregular"}
                """)
                .andExpect(status().isOk());
        mvc.perform(get(SPANISH + "/vocab")).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void updatesAndDeletes() throws Exception {
        int id = add("hablar", "to talk", "verbs");
        send("PUT", SPANISH + "/vocab/" + id, """
                {"word": "hablar", "english": "to talk/to speak", "category": "verbs"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.english").value("to talk/to speak"));

        mvc.perform(delete(SPANISH + "/vocab/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete(SPANISH + "/vocab/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void quizGivesHintsThenTheAnswer() throws Exception {
        int id = add("el perro", "dog", "general");
        mvc.perform(get(SPANISH + "/quiz"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));

        String check = """
                {"id": %d, "answerInLanguage": true, "answer": "%s", "attempt": %d}
                """;
        send("POST", SPANISH + "/quiz/check", check.formatted(id, "perro", 1))
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.finished").value(false))
                .andExpect(jsonPath("$.feedback").value("Don't forget the article!"))
                .andExpect(jsonPath("$.ipa").value(nullValue()));
        send("POST", SPANISH + "/quiz/check", check.formatted(id, "gato", 3))
                .andExpect(jsonPath("$.finished").value(true))
                .andExpect(jsonPath("$.answer").value("el perro"))
                .andExpect(jsonPath("$.ipa").value("/el ˈpe.ro/"));
        send("POST", SPANISH + "/quiz/check", check.formatted(id, "el perro", 2))
                .andExpect(jsonPath("$.correct").value(true));

        send("POST", SPANISH + "/quiz/hint", """
                {"id": %d, "answerInLanguage": true, "answer": "el g"}
                """.formatted(id))
                .andExpect(jsonPath("$.text").value("It starts with 'el p'"));
        send("POST", SPANISH + "/quiz/hint", """
                {"id": %d, "answerInLanguage": true, "answer": "el g", "shown": ["It starts with 'el p'"]}
                """.formatted(id))
                .andExpect(jsonPath("$.text").value("It starts with 'el pe'"));

        int night = add("¡Buenas noches!", "Good evening/Good night", "sentences");
        send("POST", SPANISH + "/quiz/check", """
                {"id": %d, "answerInLanguage": false, "answer": "good night", "attempt": 1}
                """.formatted(night))
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.answer").value("Good evening / Good night"));
    }

    @Test
    void keepsAContextNoteAndShowsItAfterTheQuestion() throws Exception {
        send("POST", SPANISH + "/vocab", """
                {"word": "ser", "english": "to be", "category": "", "context": "permanent things;\\nlike origin"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.context").value("permanent things; like origin"));
        VocabStore store = library.store("Spanish");
        int id = store.idOf(store.getAll().get(0));

        String check = """
                {"id": %d, "answerInLanguage": true, "answer": "%s", "attempt": %d}
                """;
        send("POST", SPANISH + "/quiz/check", check.formatted(id, "estar", 1))
                .andExpect(jsonPath("$.context").value(nullValue()));
        send("POST", SPANISH + "/quiz/check", check.formatted(id, "ser", 1))
                .andExpect(jsonPath("$.context").value("permanent things; like origin"));

        send("PUT", SPANISH + "/vocab/" + id, """
                {"word": "ser", "english": "to be", "category": "general", "context": ""}
                """).andExpect(jsonPath("$.context").value(""));
    }

    @Test
    void addsALanguageThatWorksWithTheBasics() throws Exception {
        mvc.perform(get(NINI + "/languages"))
                .andExpect(jsonPath("$[?(@.name == 'Spanish')].ipa").value(true));
        send("POST", NINI + "/languages", """
                {"name": "italian"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Italian"))
                .andExpect(jsonPath("$.ipa").value(false))
                .andExpect(jsonPath("$.dictionaryName").value("Wiktionary"));
        send("POST", NINI + "/languages", """
                {"name": "Italian"}
                """).andExpect(status().isConflict());
        send("POST", NINI + "/languages", """
                {"name": "Klingon 2"}
                """).andExpect(status().isBadRequest());

        send("POST", NINI + "/languages/Italian/vocab", """
                {"word": "il caffè", "english": "coffee", "category": ""}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ipa").value(nullValue()))
                .andExpect(jsonPath("$.dictionaryUrl").value("https://en.wiktionary.org/wiki/il%20caff%C3%A8#Italian"));
        mvc.perform(get(NINI + "/languages"))
                .andExpect(jsonPath("$[?(@.name == 'Italian')].specialLetters[0]").value("è"));
        // each language has its own words
        mvc.perform(get(SPANISH + "/vocab")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(NINI + "/languages/Klingon/vocab")).andExpect(status().isNotFound());
    }

    @Test
    void everyLearnerHasTheirOwnLanguagesAndVocab() throws Exception {
        add("hablar", "to talk", "verbs");
        send("POST", "/api/learners", """
                {"name": "  mia "}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mia"));
        send("POST", "/api/learners", """
                {"name": "MIA"}
                """).andExpect(status().isConflict());
        send("POST", "/api/learners", """
                {"name": "../Nini"}
                """).andExpect(status().isBadRequest());
        mvc.perform(get("/api/learners")).andExpect(jsonPath("$[?(@ == 'Mia')]", hasSize(1)));

        mvc.perform(get("/api/learners/Mia/languages")).andExpect(jsonPath("$", hasSize(0)));
        send("POST", "/api/learners/Mia/languages", """
                {"name": "Spanish"}
                """).andExpect(status().isCreated());
        mvc.perform(get("/api/learners/Mia/languages/Spanish/vocab")).andExpect(jsonPath("$", hasSize(0)));
        send("POST", "/api/learners/Mia/languages/Spanish/vocab", """
                {"word": "hablar", "english": "to talk", "category": "verbs", "newCategory": true}
                """).andExpect(status().isCreated());
        mvc.perform(get(SPANISH + "/vocab")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/learners/Nobody/languages")).andExpect(status().isNotFound());
    }

    @Test
    void renamesAndDeletesLearners() throws Exception {
        send("POST", "/api/learners", """
                {"name": "Lena"}
                """).andExpect(status().isCreated());
        send("PUT", "/api/learners/lena", """
                {"name": "lea"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lea"));
        mvc.perform(get("/api/learners/Lea/languages")).andExpect(status().isOk());
        mvc.perform(get("/api/learners/Lena/languages")).andExpect(status().isNotFound());
        send("PUT", "/api/learners/Lea", """
                {"name": "Nini"}
                """).andExpect(status().isConflict());
        send("PUT", "/api/learners/Nobody", """
                {"name": "Somebody"}
                """).andExpect(status().isNotFound());

        mvc.perform(delete("/api/learners/Lea")).andExpect(status().isNoContent());
        mvc.perform(get("/api/learners")).andExpect(jsonPath("$[?(@ == 'Lea')]", hasSize(0)));
        mvc.perform(delete("/api/learners/Lea")).andExpect(status().isNotFound());
    }

    @Test
    void copiesVocabFromAnotherLearnerWithoutReplacingAny() throws Exception {
        add("hablar", "to talk", "verbs");
        add("tener", "to have", "verbs");
        if (library.store("Vietnamese") == null) {
            library.addLanguage("Vietnamese");
        }
        for (Vocab v : library.store("Vietnamese").getAll()) {
            library.store("Vietnamese").remove(v);
        }
        library.store("Vietnamese").add(new Vocab("xin chào", "hello", "general"));
        send("POST", "/api/learners", """
                {"name": "Copytest"}
                """).andExpect(status().isCreated());
        String copy = "/api/learners/Copytest/copy";

        // one language, into a learner who doesn't have it yet
        send("POST", copy, """
                {"from": "nini", "language": "Spanish"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copied").value(2))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.languages[0]").value("Spanish"));
        mvc.perform(get("/api/learners/Copytest/languages")).andExpect(jsonPath("$", hasSize(1)));

        // the learner's own words stay; what they already have is skipped
        send("POST", "/api/learners/Copytest/languages/Spanish/vocab", """
                {"word": "ir", "english": "to go", "category": "verbs"}
                """).andExpect(status().isCreated());
        add("comer", "to eat", "verbs");
        send("POST", copy, """
                {"from": "Nini", "language": ""}
                """)
                .andExpect(jsonPath("$.copied").value(2)) // comer and xin chào
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.languages", hasSize(2)));
        mvc.perform(get("/api/learners/Copytest/languages/Spanish/vocab")).andExpect(jsonPath("$", hasSize(4)));
        mvc.perform(get("/api/learners/Copytest/languages/Vietnamese/vocab")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/learners/Copytest/languages/Spanish/stats")).andExpect(jsonPath("$.answered").value(0));

        send("POST", copy, """
                {"from": "Copytest", "language": ""}
                """).andExpect(status().isBadRequest());
        send("POST", copy, """
                {"from": "Nobody", "language": ""}
                """).andExpect(status().isNotFound());
        send("POST", copy, """
                {"from": "Nini", "language": "French"}
                """).andExpect(status().isNotFound());
        mvc.perform(delete("/api/learners/Copytest")).andExpect(status().isNoContent());
    }

    @Test
    void everyWordListIsAPairOfLanguages() throws Exception {
        send("POST", "/api/learners", """
                {"name": "Pairtest"}
                """).andExpect(status().isCreated());
        // a new learner's first list: English unless asked for another
        send("POST", "/api/learners/Pairtest/languages", """
                {"name": "Spanisch", "translation": "deutsch"}
                """).andExpect(jsonPath("$.translation").value("Deutsch"))
                .andExpect(jsonPath("$.translationLetters[0]").value("ä"));
        // the next list: the learner's usual translation language
        send("POST", "/api/learners/Pairtest/languages", """
                {"name": "English"}
                """).andExpect(jsonPath("$.translation").value("Deutsch"));
        String spanisch = "/api/learners/Pairtest/languages/Spanisch/vocab";
        send("POST", spanisch, """
                {"word": "la casa", "english": "das haus", "category": "general"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.spelling", hasSize(1)))
                .andExpect(jsonPath("$.spelling[0].language").value("Deutsch"))
                .andExpect(jsonPath("$.spelling[0].kind").value("capital"));
        // English as the language learned: English spell check on that side, German on the other
        send("POST", "/api/learners/Pairtest/languages/English/vocab", """
                {"word": "the hous", "english": "das Haus", "category": "general"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.spelling", hasSize(1)))
                .andExpect(jsonPath("$.spelling[0].word").value("hous"))
                .andExpect(jsonPath("$.spelling[0].language").value("English"));

        send("PUT", "/api/learners/Pairtest/languages/Spanisch/translation", """
                {"name": "english"}
                """).andExpect(jsonPath("$.translation").value("English"));
        send("PUT", "/api/learners/Pairtest/languages/Spanisch/translation", """
                {"name": "../x"}
                """).andExpect(status().isBadRequest());
        // kept after a restart: each list has its .translation file
        VocabLibrary reloaded = new VocabLibrary(dir.resolve("vocab/Pairtest"));
        assertEquals("English", reloaded.language("Spanisch").getTranslation().getName());
        assertEquals("Deutsch", reloaded.language("English").getTranslation().getName());
        mvc.perform(delete("/api/learners/Pairtest")).andExpect(status().isNoContent());
    }

    @Test
    void deletesAListButKeepsItInDeleted() throws Exception {
        send("POST", "/api/learners", """
                {"name": "Listtest"}
                """).andExpect(status().isCreated());
        send("POST", "/api/learners/Listtest/languages", """
                {"name": "Spanish", "translation": "English"}
                """).andExpect(jsonPath("$.englishName").value("Spanish"));
        send("POST", "/api/learners/Listtest/languages", """
                {"name": "Spanisch", "translation": "Deutsch"}
                """).andExpect(jsonPath("$.englishName").value("Spanish"));
        send("POST", "/api/learners/Listtest/languages/Spanish/vocab", """
                {"word": "hablar", "english": "to talk", "category": "verbs", "newCategory": true}
                """).andExpect(status().isCreated());

        mvc.perform(delete("/api/learners/Listtest/languages/spanish")).andExpect(status().isNoContent());
        mvc.perform(get("/api/learners/Listtest/languages"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Spanisch"));
        mvc.perform(delete("/api/learners/Listtest/languages/Spanish")).andExpect(status().isNotFound());
        // the files are kept, to put back by hand
        try (var kept = Files.list(dir.resolve("vocab/" + Learners.DELETED))) {
            Path folder = kept.filter(p -> p.getFileName().toString().startsWith("Listtest Spanish ")).findFirst().orElseThrow();
            assertTrue(Files.readString(folder.resolve("Spanish.txt")).contains("hablar;to talk"));
            assertTrue(Files.exists(folder.resolve("Spanish.translation")));
        }
        mvc.perform(delete("/api/learners/Listtest")).andExpect(status().isNoContent());
    }
}
