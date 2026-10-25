package vocabapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Starts the app on http://localhost:8080; scheduling for the nightly backup (VocabBackup)
@SpringBootApplication
@EnableScheduling
public class WebApp {
    public static void main(String[] args) {
        SpringApplication.run(WebApp.class, args);
    }
}
