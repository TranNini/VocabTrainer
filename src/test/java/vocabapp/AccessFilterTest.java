package vocabapp;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccessFilterTest {

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
    AccessCode accessCode;

    private static RequestPostProcessor fromIPhone() {
        return request -> {
            request.setRemoteAddr("192.168.1.42");
            return request;
        };
    }

    @Test
    void createsAndSavesACode() throws Exception {
        assertEquals(8, accessCode.getCode().length());
        assertEquals(accessCode.getCode(), Files.readString(dir.resolve("access-code.txt")).trim());
        assertTrue(accessCode.matches(" " + accessCode.getCode().toUpperCase() + " "));
    }

    @Test
    void thisMacNeedsNoCode() throws Exception {
        mvc.perform(get("/api/languages")).andExpect(status().isOk());
    }

    @Test
    void otherDevicesAreSentToTheLoginPage() throws Exception {
        mvc.perform(get("/api/languages").with(fromIPhone())).andExpect(status().isUnauthorized());
        mvc.perform(get("/").with(fromIPhone())).andExpect(redirectedUrl("/login.html"));
        mvc.perform(get("/login.html").with(fromIPhone())).andExpect(status().isOk());
        mvc.perform(get("/apple-touch-icon.png").with(fromIPhone())).andExpect(status().isOk());
    }

    @Test
    void wrongCodeIsRejected() throws Exception {
        mvc.perform(post("/login").param("code", "nope").with(fromIPhone()))
                .andExpect(redirectedUrl("/login.html?wrong"))
                .andExpect(cookie().doesNotExist(AccessFilter.COOKIE));
        mvc.perform(get("/api/languages").with(fromIPhone()).cookie(new Cookie(AccessFilter.COOKIE, "nope")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rightCodeIsRemembered() throws Exception {
        mvc.perform(post("/login").param("code", accessCode.getCode()).with(fromIPhone()))
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().value(AccessFilter.COOKIE, accessCode.getCode()))
                .andExpect(cookie().httpOnly(AccessFilter.COOKIE, true));
        mvc.perform(get("/api/languages").with(fromIPhone()).cookie(new Cookie(AccessFilter.COOKIE, accessCode.getCode())))
                .andExpect(status().isOk());
    }

    @Test
    void locksAfterTenWrongCodes() {
        AccessCode code = new AccessCode(dir.resolve("lock-test.txt").toString());
        for (int i = 0; i < 10; i++) {
            code.tryLogin("wrong");
        }
        assertEquals(false, code.tryLogin(code.getCode()));
    }
}
