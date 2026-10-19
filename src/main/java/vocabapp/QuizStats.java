package vocabapp;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    static final int WEEKS = 26;
    static final int MONTHS = 12;
    // a word counts as mastered once it was right on the first try this many times in a row (the last tries)
    static final int MASTERED_IN_A_ROW = 3;
    static final int TO_PRACTISE = 8;

    // One bar of a chart: start = when its hour, day, week or month starts (the week ending today, the month
    // on its 1st), e.g. "2026-10-19T14:00"
    public record Bar(LocalDateTime start, int answered, int right) {
    }

    // name = "Today", "1 month", "6 months", "1 year"; unit = what one bar is: "hour", "day", "week", "month"
    public record Chart(String name, String unit, List<Bar> bars) {
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
                        int daysPractised, List<Period> periods, List<Chart> charts, int mastered, int practising,
                        int notPractised, List<WordStat> toPractise) {
    }

    private QuizStats() {
    }

    public static Stats of(List<QuizLog.Entry> entries, List<Vocab> vocab, LocalDate today) {
        int rightFirstTry = 0;
        int right = 0;
        int hints = 0;
        Set<LocalDate> days = new HashSet<>();
        Map<String, List<QuizLog.Entry>> perWord = new HashMap<>();
        for (QuizLog.Entry e : entries) {
            rightFirstTry += e.rightFirstTry() ? 1 : 0;
            right += e.right() ? 1 : 0;
            hints += e.hints();
            LocalDate day = e.when().toLocalDate();
            days.add(day);
            perWord.computeIfAbsent(e.word(), w -> new ArrayList<>()).add(e);
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

        List<Period> periods = List.of(
                period("Today", entries, today),
                period("1 month", entries, today.minusMonths(1).plusDays(1)),
                period("6 months", entries, today.minusMonths(6).plusDays(1)),
                period("1 year", entries, today.minusYears(1).plusDays(1)));
        return new Stats(entries.size(), rightFirstTry, right, hints, periods.get(0).answered(), streak(days, today),
                days.size(), periods, charts(entries, today), mastered, practising, notPractised,
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

    // A chart for each period of the overview: today by hour, the month by day, 6 months by week (26 weeks,
    // the last one ending today), the year by month (this month and the 11 before)
    private static List<Chart> charts(List<QuizLog.Entry> entries, LocalDate today) {
        LocalDateTime midnight = today.atStartOfDay();
        List<LocalDateTime> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hours.add(midnight.plusHours(h));
        }
        List<LocalDateTime> days = new ArrayList<>();
        for (LocalDate day = today.minusMonths(1).plusDays(1); !day.isAfter(today); day = day.plusDays(1)) {
            days.add(day.atStartOfDay());
        }
        List<LocalDateTime> weeks = new ArrayList<>();
        for (int w = WEEKS - 1; w >= 0; w--) {
            weeks.add(midnight.minusDays(7L * w + 6));
        }
        List<LocalDateTime> months = new ArrayList<>();
        for (int m = MONTHS - 1; m >= 0; m--) {
            months.add(today.withDayOfMonth(1).minusMonths(m).atStartOfDay());
        }
        LocalDateTime end = midnight.plusDays(1);
        return List.of(chart("Today", "hour", hours, end, entries), chart("1 month", "day", days, end, entries),
                chart("6 months", "week", weeks, end, entries), chart("1 year", "month", months, end, entries));
    }

    // Each entry goes to the last bar that starts before it; entries before the first bar or from "end" on are left out
    private static Chart chart(String name, String unit, List<LocalDateTime> starts, LocalDateTime end,
                               List<QuizLog.Entry> entries) {
        int[] answered = new int[starts.size()];
        int[] right = new int[starts.size()];
        for (QuizLog.Entry e : entries) {
            if (e.when().isBefore(starts.get(0)) || !e.when().isBefore(end)) {
                continue;
            }
            int bar = starts.size() - 1;
            while (e.when().isBefore(starts.get(bar))) {
                bar--;
            }
            answered[bar]++;
            right[bar] += e.right() ? 1 : 0;
        }
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            bars.add(new Bar(starts.get(i), answered[i], right[i]));
        }
        return new Chart(name, unit, bars);
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
