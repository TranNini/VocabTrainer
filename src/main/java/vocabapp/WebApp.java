package vocabapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Paths;

// Starts the web version on http://localhost:8080
@SpringBootApplication
public class WebApp {
    public static void main(String[] args) {
        SpringApplication.run(WebApp.class, args);
    }

    @Bean
    VocabStore vocabStore(@Value("${vocab.file}") String file) {
        return new VocabStore(Paths.get(file));
    }
}
