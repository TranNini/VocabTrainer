package vocabapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizStatsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final List<Vocab> VOCAB = List.of(
            new Vocab("tener", "to have", "verbs", ""),
            new Vocab("yo tengo", "I have", "verbs", ""),
            new Vocab("el gato", "the cat", "general", ""),
            new Vocab("la vaca", "the cow", "general", ""));

    private static QuizLog.Entry entry(int daysAgo, String word, int rightOnTry) {
        return new QuizLog.Entry(TODAY.minusDays(daysAgo).atTime(18, 0), word, true, rightOnTry, 0);
    }

    @Test
    void countsAnswersAndShares() {
        List<QuizLog.Entry> log = List.of(entry(0, "tener", 1), entry(0, "yo tengo", 2), entry(0, "el gato", 0),
                new QuizLog.Entry(TODAY.atTime(18, 5), "tener", false, 1, 2));
        QuizStats.Stats stats = QuizStats.of(log, VOCAB, TODAY);
        assertEquals(4, stats.answered());
        assertEquals(2, stats.rightFirstTry());
        assertEquals(3, stats.right());
        assertEquals(2, stats.hints());
        assertEquals(4, stats.today());
    }

    @Test
    void streakCountsDaysInARowAndStillShowsYesterdaysInTheMorning() {
        List<QuizLog.Entry> log = List.of(entry(5, "tener", 1), entry(3, "tener", 1), entry(2, "tener", 1),
                entry(1, "tener", 1));
        QuizStats.Stats stats = QuizStats.of(log, VOCAB, TODAY);
        assertEquals(3, stats.streak()); // 3 days ago to yesterday, no quiz yet today
        assertEquals(4, stats.daysPractised());
        assertEquals(0, stats.today());
        assertEquals(1, QuizStats.of(List.of(entry(0, "tener", 1)), VOCAB, TODAY).streak());
        assertEquals(0, QuizStats.of(List.of(entry(2, "tener", 1)), VOCAB, TODAY).streak());
    }

    @Test
    void periodsCountTodayOneMonthSixMonthsAndOneYear() {
        List<QuizLog.Entry> log = List.of(
                entry(400, "tener", 1),                          // older than a year: only in the totals
                entry(200, "tener", 0), entry(200, "el gato", 1), // within the year
                entry(40, "la vaca", 2),                          // within 6 months
                entry(29, "tener", 1), entry(29, "tener", 0),     // within the month (30 days ago is not)
                entry(0, "el gato", 1));                          // today
        List<QuizStats.Period> periods = QuizStats.of(log, VOCAB, TODAY).periods();
        assertEquals(List.of("Today", "1 month", "6 months", "1 year"),
                periods.stream().map(QuizStats.Period::name).toList());
        assertEquals(new QuizStats.Period("Today", 1, 1, 1, 1, 1), periods.get(0));
        assertEquals(new QuizStats.Period("1 month", 3, 2, 2, 2, 2), periods.get(1));
        assertEquals(new QuizStats.Period("6 months", 4, 2, 3, 3, 3), periods.get(2));
        assertEquals(new QuizStats.Period("1 year", 6, 3, 4, 4, 3), periods.get(3));
    }

    @Test
    void chartsHaveABarPerHourDayWeekAndMonth() {
        List<QuizLog.Entry> log = List.of(
                new QuizLog.Entry(TODAY.atTime(9, 15), "tener", true, 1, 0),
                new QuizLog.Entry(TODAY.atTime(9, 50), "el gato", true, 0, 0),
                new QuizLog.Entry(TODAY.atTime(18, 0), "tener", true, 1, 0),
                entry(29, "tener", 1), entry(30, "tener", 1), // 30 days ago is before the month
                entry(181, "la vaca", 1), entry(182, "la vaca", 1), // the 26 weeks start 181 days ago
                entry(300, "el gato", 1));
        List<QuizStats.Chart> charts = QuizStats.of(log, VOCAB, TODAY).charts();
        assertEquals(List.of("hour", "day", "week", "month"), charts.stream().map(QuizStats.Chart::unit).toList());

        List<QuizStats.Bar> hours = charts.get(0).bars();
        assertEquals(24, hours.size());
        assertEquals(new QuizStats.Bar(TODAY.atTime(9, 0), 2, 1), hours.get(9));
        assertEquals(1, hours.get(18).answered());

        List<QuizStats.Bar> days = charts.get(1).bars();
        assertEquals(30, days.size()); // 20 Sep to 19 Oct
        assertEquals(TODAY.minusDays(29).atStartOfDay(), days.get(0).start());
        assertEquals(1, days.get(0).answered());
        assertEquals(3, days.get(29).answered());

        List<QuizStats.Bar> weeks = charts.get(2).bars();
        assertEquals(26, weeks.size());
        assertEquals(TODAY.minusDays(181).atStartOfDay(), weeks.get(0).start());
        assertEquals(1, weeks.get(0).answered()); // la vaca 181 days ago, not 182
        assertEquals(TODAY.minusDays(6).atStartOfDay(), weeks.get(25).start());
        assertEquals(3, weeks.get(25).answered());

        List<QuizStats.Bar> months = charts.get(3).bars();
        assertEquals(12, months.size());
        assertEquals(LocalDate.of(2025, 11, 1).atStartOfDay(), months.get(0).start());
        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(), months.get(11).start());
        assertEquals(3, months.get(11).answered()); // today; 29 and 30 days ago are in September
        assertEquals(2, months.get(10).answered());
        assertEquals(0, months.get(0).answered());
        assertEquals(1, months.get(1).answered()); // 300 days ago = 23 Dec 2025
        assertEquals(2, months.get(5).answered()); // la vaca, both in April
    }

    @Test
    void aWordIsMasteredAfterThreeFirstTriesInARow() {
        List<QuizLog.Entry> log = List.of(
                entry(4, "tener", 0), entry(3, "tener", 1), entry(2, "tener", 1), entry(1, "tener", 1),
                entry(2, "yo tengo", 1), entry(1, "yo tengo", 2), entry(0, "yo tengo", 1),
                entry(0, "el gato", 1));
        QuizStats.Stats stats = QuizStats.of(log, VOCAB, TODAY);
        assertEquals(1, stats.mastered());
        assertEquals(2, stats.practising());
        assertEquals(1, stats.notPractised()); // la vaca
    }

    @Test
    void wordsToPractiseAreTheOnesMissedMost() {
        List<QuizLog.Entry> log = new ArrayList<>(List.of(
                entry(2, "el gato", 0), entry(1, "el gato", 0), entry(0, "el gato", 1),
                entry(1, "yo tengo", 2), entry(0, "yo tengo", 1),
                entry(0, "tener", 1),
                entry(0, "deleted word", 0)));
        List<QuizStats.WordStat> words = QuizStats.of(log, VOCAB, TODAY).toPractise();
        assertEquals(List.of("el gato", "yo tengo"), words.stream().map(QuizStats.WordStat::word).toList());
        assertEquals(new QuizStats.WordStat("el gato", "the cat", 3, 1), words.get(0));
    }

    @Test
    void wordsMissedLatelyWeighMore() {
        List<QuizLog.Entry> log = List.of(
                entry(9, "tener", 0), entry(8, "tener", 1), entry(7, "tener", 1), entry(6, "tener", 1),
                entry(5, "tener", 1), entry(4, "tener", 1), // the miss is older than the last 5 tries
                entry(1, "el gato", 0), entry(0, "el gato", 2),
                entry(1, "yo tengo", 1), entry(0, "yo tengo", 0));
        var weights = QuizStats.practiceWeights(log);
        assertEquals(1.0, weights.get("tener"));
        assertEquals(5.0, weights.get("el gato"));
        assertEquals(3.0, weights.get("yo tengo"));
        assertEquals(null, weights.get("la vaca")); // not asked yet: NEW_WEIGHT
    }

    @Test
    void practiceOrderPutsHeavyWordsFirstMoreOftenButKeepsEveryWord() {
        List<String> words = List.of("easy1", "easy2", "easy3", "easy4", "hard");
        java.util.Random random = new java.util.Random(7);
        int hardFirst = 0;
        for (int i = 0; i < 2000; i++) {
            List<String> order = QuizStats.practiceOrder(words, w -> w.equals("hard") ? 5 : 1, random);
            assertEquals(5, order.size());
            assertEquals(new java.util.HashSet<>(words), new java.util.HashSet<>(order));
            hardFirst += order.get(0).equals("hard") ? 1 : 0;
        }
        // 5 / (5 + 4) of the time in theory, instead of 1 in 5 with a plain shuffle
        assertTrue(hardFirst > 1000 && hardFirst < 1250, "hard first " + hardFirst + " times");
    }

    @Test
    void logSavesReadsAndRenames(@TempDir Path dir) throws Exception {
        QuizLog log = new QuizLog(dir.resolve("Spanish.log"));
        assertTrue(log.entries().isEmpty());
        LocalDateTime when = LocalDateTime.of(2026, 10, 19, 18, 5, 12, 345);
        log.add(new QuizLog.Entry(when, "nostoros queremos", true, 2, 1));
        log.add(new QuizLog.Entry(when, "tener", false, 0, 0));
        Files.writeString(dir.resolve("Spanish.log"), "broken line\n", java.nio.file.StandardOpenOption.APPEND);
        assertEquals(List.of(new QuizLog.Entry(when.withNano(0), "nostoros queremos", true, 2, 1),
                new QuizLog.Entry(when.withNano(0), "tener", false, 0, 0)), log.entries());
        log.renameWord("nostoros queremos", "nosotros queremos");
        assertEquals("nosotros queremos", log.entries().get(0).word());
        assertEquals("tener", log.entries().get(1).word());
    }
}
