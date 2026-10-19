package vocabapp;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleFunction;

// The statistics of the Stats tab, worked out from a learner's quiz log (see QuizLog) and their vocab
public class QuizStats {
    static final int DAYS = 14;
    // a word counts as mastered once it was right on the first try this many times in a row (the last tries)
    static final int MASTERED_IN_A_ROW = 3;
    static final int TO_PRACTISE = 8;

    public record Day(LocalDate date, int answered, int right) {
    }

    // The overview for one period up to today: name = "Today", "1 month", "6 months", "1 year";
    // words = how many different words were asked
    public record Period(String name, int answered, int rightFirstTry, int right, int days, int words) {
    }

    // tries = how often it was asked; firstTry = how often it was right on the first try
    public record WordStat(String word, String english, int tries, int firstTry) {
    }

    // answered, rightFirstTry, right and hints count every finished question, also of words deleted since;
    // mastered + practising + notPractised = the vocab saved now
    public record Stats(int answered, int rightFirstTry, int right, int hints, int today, int streak,
                        int daysPractised, List<Period> periods, List<Day> lastDays, int mastered, int practising,
                        int notPractised, List<WordStat> toPractise) {
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
        List<Period> periods = List.of(
                period("Today", entries, today),
                period("1 month", entries, today.minusMonths(1).plusDays(1)),
                period("6 months", entries, today.minusMonths(6).plusDays(1)),
                period("1 year", entries, today.minusYears(1).plusDays(1)));
        return new Stats(entries.size(), rightFirstTry, right, hints, todayCounts[0], streak(days, today),
                days.size(), periods, lastDays, mastered, practising, notPractised,
                missed.subList(0, Math.min(TO_PRACTISE, missed.size())));
    }

    // The hidden side of the stats, for the quiz order: how much each word needs practice, from its last
    // tries. 1 = right on the first try every time; up to 1 + MISSED_WEIGHT = never. Words not asked
    // yet aren't in the map: they get NEW_WEIGHT.
    static final int RECENT_TRIES = 5;
    static final double MISSED_WEIGHT = 4;
    static final double NEW_WEIGHT = 2;

    public static Map<String, Double> practiceWeights(List<QuizLog.Entry> entries) {
        Map<String, List<QuizLog.Entry>> perWord = new HashMap<>();
        for (QuizLog.Entry e : entries) {
            perWord.computeIfAbsent(e.word(), w -> new ArrayList<>()).add(e);
        }
        Map<String, Double> weights = new HashMap<>();
        perWord.forEach((word, tries) -> {
            List<QuizLog.Entry> recent = tries.subList(Math.max(0, tries.size() - RECENT_TRIES), tries.size());
            long missed = recent.stream().filter(e -> !e.rightFirstTry()).count();
            weights.put(word, 1 + MISSED_WEIGHT * missed / recent.size());
        });
        return weights;
    }

    // A random order in which heavier items tend to come first, so a quiz that is stopped early still
    // asked the words that need practice; every item stays in it once (weighted random sampling:
    // key = random ^ (1 / weight), highest key first)
    public static <T> List<T> practiceOrder(List<T> items, ToDoubleFunction<T> weight, Random random) {
        Map<T, Double> keys = new IdentityHashMap<>(); // two equal entries are still two items
        for (T item : items) {
            keys.put(item, Math.pow(random.nextDouble(), 1 / weight.applyAsDouble(item)));
        }
        List<T> ordered = new ArrayList<>(items);
        ordered.sort(Comparator.comparing(keys::get, Comparator.reverseOrder()));
        return ordered;
    }

    // Everything answered from the day "from" on (today included), e.g. 1 month = 20 Sep to 19 Oct
    private static Period period(String name, List<QuizLog.Entry> entries, LocalDate from) {
        int answered = 0;
        int rightFirstTry = 0;
        int right = 0;
        Set<LocalDate> days = new HashSet<>();
        Set<String> words = new HashSet<>();
        for (QuizLog.Entry e : entries) {
            if (e.when().toLocalDate().isBefore(from)) {
                continue;
            }
            answered++;
            rightFirstTry += e.rightFirstTry() ? 1 : 0;
            right += e.right() ? 1 : 0;
            days.add(e.when().toLocalDate());
            words.add(e.word());
        }
        return new Period(name, answered, rightFirstTry, right, days.size(), words.size());
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
