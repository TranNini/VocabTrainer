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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VocabControllerTest {

    @TempDir
    static Path dir;

    @DynamicPropertySource
    static void vocabFile(DynamicPropertyRegistry registry) {
        registry.add("vocab.file", () -> dir.resolve("vocab.txt").toString());
        registry.add("vocab.access-code-file", () -> dir.resolve("access-code.txt").toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    VocabStore store;

    @BeforeEach
    void emptyStore() {
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

    private int add(String spanish, String english, String category) throws Exception {
        send("POST", "/api/vocab", """
                {"spanish": "%s", "english": "%s", "category": "%s", "newCategory": true}
                """.formatted(spanish, english, category)).andExpect(status().isCreated());
        return store.idOf(store.getAll().get(store.getAll().size() - 1));
    }

    @Test
    void listsVocabSortedWithConjugationsGrouped() throws Exception {
        add("tener", "to have", "verbs");
        add("yo tengo", "I have", "verbs");
        add("el agua", "water", "general");

        mvc.perform(get("/api/vocab"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].spanish").value("el agua"))
                .andExpect(jsonPath("$[0].section").value("A"))
                .andExpect(jsonPath("$[2].spanish").value("yo tengo"))
                .andExpect(jsonPath("$[2].section").value("T"))
                .andExpect(jsonPath("$[2].conjugation").value(true));
        mvc.perform(get("/api/vocab").param("category", "verbs").param("q", "t"))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$[0].name").value("general"))
                .andExpect(jsonPath("$[1].count").value(2));
    }

    @Test
    void asksBeforeCreatingANewCategory() throws Exception {
        add("hablar", "to talk", "verbs");
        send("POST", "/api/vocab", """
                {"spanish": "ir", "english": "to go", "category": "verb"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.newCategory").value("verb"));
        send("POST", "/api/vocab", """
                {"spanish": "ir", "english": "to go", "category": "VERBS"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("verbs"));
        send("POST", "/api/vocab", """
                {"spanish": "la mesa", "english": "table", "category": ""}
                """)
                .andExpect(jsonPath("$.category").value("general"));
    }

    @Test
    void rejectsMissingWordsAndSemicolons() throws Exception {
        send("POST", "/api/vocab", """
                {"spanish": "", "english": "to go", "category": "verbs"}
                """).andExpect(status().isBadRequest());
        send("POST", "/api/vocab", """
                {"spanish": "ir;x", "english": "to go", "category": "verbs"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void updatesAndDeletes() throws Exception {
        int id = add("hablar", "to talk", "verbs");
        send("PUT", "/api/vocab/" + id, """
                {"spanish": "hablar", "english": "to talk/to speak", "category": "v"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.english").value("to talk/to speak"));

        mvc.perform(delete("/api/vocab/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/vocab/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void quizGivesHintsThenTheAnswer() throws Exception {
        int id = add("el perro", "dog", "general");
        mvc.perform(get("/api/quiz"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));

        String check = """
                {"id": %d, "askInSpanish": true, "answer": "%s", "attempt": %d}
                """;
        send("POST", "/api/quiz/check", check.formatted(id, "perro", 1))
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.finished").value(false))
                .andExpect(jsonPath("$.feedback").value("Don't forget the article!"));
        send("POST", "/api/quiz/check", check.formatted(id, "gato", 3))
                .andExpect(jsonPath("$.finished").value(true))
                .andExpect(jsonPath("$.answer").value("el perro"));
        send("POST", "/api/quiz/check", check.formatted(id, "el perro", 2))
                .andExpect(jsonPath("$.correct").value(true));
    }
}
