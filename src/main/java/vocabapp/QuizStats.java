package vocabapp;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The statistics of the Stats tab, worked out from a learner's quiz log (see QuizLog) and their vocab
public class QuizStats {
    static final int DAYS = 14;
    // a word counts as mastered once it was right on the first try this many times in a row (the last tries)
    static final int MASTERED_IN_A_ROW = 3;
    static final int TO_PRACTISE = 8;

    public record Day(LocalDate date, int answered, int right) {
    }

    // tries = how often it was asked; firstTry = how often it was right on the first try
    public record WordStat(String word, String english, int tries, int firstTry) {
    }

    // answered, rightFirstTry, right and hints count every finished question, also of words deleted since;
    // mastered + practising + notPractised = the vocab saved now
    public record Stats(int answered, int rightFirstTry, int right, int hints, int today, int streak,
                        int daysPractised, List<Day> lastDays, int mastered, int practising, int notPractised,
                        List<WordStat> toPractise) {
    }

    private QuizStats() {
    }

    public static Stats of(List<QuizLog.Entry> entries, List<Vocab> vocab, LocalDate today) {
        int rightFirstTry = 0;
        int right = 0;
        int hints = 0;
        Set<LocalDate> days = new HashSet<>();
        Map<LocalDate, int[]> perDay = new HashMap<>();
        Map<String, List<QuizLog.Entry>> perWord = new HashMap<>();
        for (QuizLog.Entry e : entries) {
            rightFirstTry += e.rightFirstTry() ? 1 : 0;
            right += e.right() ? 1 : 0;
            hints += e.hints();
            LocalDate day = e.when().toLocalDate();
            days.add(day);
            int[] counts = perDay.computeIfAbsent(day, d -> new int[2]);
            counts[0]++;
            counts[1] += e.right() ? 1 : 0;
            perWord.computeIfAbsent(e.word(), w -> new ArrayList<>()).add(e);
        }

        List<Day> lastDays = new ArrayList<>();
        for (LocalDate day = today.minusDays(DAYS - 1); !day.isAfter(today); day = day.plusDays(1)) {
            int[] counts = perDay.getOrDefault(day, new int[2]);
            lastDays.add(new Day(day, counts[0], counts[1]));
        }

        int mastered = 0;
        int practising = 0;
        int notPractised = 0;
        List<WordStat> missed = new ArrayList<>();
        for (Vocab v : vocab) {
            List<QuizLog.Entry> tries = perWord.getOrDefault(v.getWord(), List.of());
            if (tries.isEmpty()) {
                notPractised++;
                continue;
            }
            int firstTry = (int) tries.stream().filter(QuizLog.Entry::rightFirstTry).count();
            if (isMastered(tries)) {
                mastered++;
            } else {
                practising++;
                if (firstTry < tries.size()) {
                    missed.add(new WordStat(v.getWord(), v.getEnglish(), tries.size(), firstTry));
                }
            }
        }
        // the lowest share right on the first try first; of those, the ones asked most
        missed.sort(Comparator.comparingDouble((WordStat w) -> (double) w.firstTry() / w.tries())
                .thenComparing(WordStat::tries, Comparator.reverseOrder()));

        int[] todayCounts = perDay.getOrDefault(today, new int[2]);
        return new Stats(entries.size(), rightFirstTry, right, hints, todayCounts[0], streak(days, today),
                days.size(), lastDays, mastered, practising, notPractised,
                missed.subList(0, Math.min(TO_PRACTISE, missed.size())));
    }

    private static boolean isMastered(List<QuizLog.Entry> tries) {
        if (tries.size() < MASTERED_IN_A_ROW) {
            return false;
        }
        return tries.subList(tries.size() - MASTERED_IN_A_ROW, tries.size()).stream()
                .allMatch(QuizLog.Entry::rightFirstTry);
    }

    // Days in a row with practice, up to today; a streak that ended yesterday still counts, so it
    // doesn't show 0 in the morning before the first quiz of the day
    private static int streak(Set<LocalDate> days, LocalDate today) {
        LocalDate day = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }
}
