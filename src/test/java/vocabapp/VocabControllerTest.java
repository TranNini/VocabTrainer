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

import java.nio.file.Path;

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
    private static final String SPANISH = "/api/languages/Spanish";

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
    VocabLibrary library;

    @BeforeEach
    void emptySpanish() {
        if (library.store("Spanish") == null) {
            library.addLanguage("Spanish");
        }
        VocabStore store = library.store("Spanish");
        for (Vocab v : store.getAll()) {
            store.remove(v);
        }
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

        int night = add("¡Buenas noches!", "Good evening/Good night", "sentences");
        send("POST", SPANISH + "/quiz/check", """
                {"id": %d, "answerInLanguage": false, "answer": "good night", "attempt": 1}
                """.formatted(night))
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.answer").value("Good evening / Good night"));
    }

    @Test
    void addsALanguageThatWorksWithTheBasics() throws Exception {
        mvc.perform(get("/api/languages"))
                .andExpect(jsonPath("$[?(@.name == 'Spanish')].ipa").value(true));
        send("POST", "/api/languages", """
                {"name": "italian"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Italian"))
                .andExpect(jsonPath("$.ipa").value(false))
                .andExpect(jsonPath("$.dictionaryName").value("Wiktionary"));
        send("POST", "/api/languages", """
                {"name": "Italian"}
                """).andExpect(status().isConflict());
        send("POST", "/api/languages", """
                {"name": "Klingon 2"}
                """).andExpect(status().isBadRequest());

        send("POST", "/api/languages/Italian/vocab", """
                {"word": "il caffè", "english": "coffee", "category": ""}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ipa").value(nullValue()))
                .andExpect(jsonPath("$.dictionaryUrl").value("https://en.wiktionary.org/wiki/il%20caff%C3%A8#Italian"));
        mvc.perform(get("/api/languages"))
                .andExpect(jsonPath("$[?(@.name == 'Italian')].specialLetters[0]").value("è"));
        // each language has its own words
        mvc.perform(get(SPANISH + "/vocab")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/languages/Klingon/vocab")).andExpect(status().isNotFound());
    }
}
