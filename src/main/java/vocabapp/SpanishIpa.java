package vocabapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Works out the pronunciation of Spanish (Spain) in the International Phonetic Alphabet from the spelling,
// e.g. "tener" -> /teˈneɾ/. Spanish is written almost the way it is spoken, so a handful of rules cover
// nearly everything; loanwords with other spellings (e.g. "México") come out as if they were Spanish.
// Broad (phonemic) transcription: syllables separated by ".", stress mark "ˈ" before the stressed one.
public class SpanishIpa {
    // Short words that are said without stress inside a sentence, so they get no stress mark
    private static final Set<String> UNSTRESSED = Set.of(
            "el", "la", "los", "las", "lo", "un", "de", "del", "a", "al", "en", "con", "por", "y", "o", "u", "e",
            "ni", "que", "se", "me", "te", "le", "les", "nos", "os", "mi", "mis", "tu", "tus", "su", "sus");

    private SpanishIpa() {
    }

    // Alternatives ("el niño / niño") are transcribed separately and joined with " · "
    public static String transcribe(String spanish) {
        List<String> alternatives = new ArrayList<>();
        for (String alternative : spanish.split("/")) {
            List<String> words = new ArrayList<>();
            for (String word : alternative.toLowerCase().split("[\\s,;:]+")) {
                String letters = word.replaceAll("[^a-zñáéíóúü]", "");
                if (!letters.isEmpty()) {
                    words.add(transcribeWord(letters));
                }
            }
            if (!words.isEmpty()) {
                alternatives.add("/" + String.join(" ", words) + "/");
            }
        }
        return String.join(" · ", alternatives);
    }

    // One sound of the word: a consonant or a vowel
    private record Sound(String ipa, boolean vowel, boolean strong, boolean accent) {
        static Sound consonant(String ipa) {
            return new Sound(ipa, false, false, false);
        }

        static Sound vowel(String ipa, boolean strong, boolean accent) {
            return new Sound(ipa, true, strong, accent);
        }
    }

    static String transcribeWord(String word) {
        List<Sound> sounds = sounds(word);
        List<List<Sound>> syllables = syllables(sounds);
        if (syllables.isEmpty()) {
            return joinSounds(sounds);
        }
        int stressed = stressedSyllable(word, syllables);
        StringBuilder ipa = new StringBuilder();
        for (int s = 0; s < syllables.size(); s++) {
            if (s == stressed && (syllables.size() > 1 || !UNSTRESSED.contains(word))) {
                ipa.append('ˈ');
            } else if (s > 0) {
                ipa.append('.');
            }
            ipa.append(syllableIpa(syllables.get(s)));
        }
        return ipa.toString();
    }

    // Spelling -> sounds, e.g. "queso" -> k e s o, "llamo" -> ʝ a m o
    private static List<Sound> sounds(String w) {
        List<Sound> sounds = new ArrayList<>();
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            char next = i + 1 < w.length() ? w.charAt(i + 1) : 0;
            boolean frontVowelNext = "eéií".indexOf(next) >= 0 && next != 0;
            switch (c) {
                case 'a' -> sounds.add(Sound.vowel("a", true, false));
                case 'e' -> sounds.add(Sound.vowel("e", true, false));
                case 'o' -> sounds.add(Sound.vowel("o", true, false));
                case 'á' -> sounds.add(Sound.vowel("a", true, true));
                case 'é' -> sounds.add(Sound.vowel("e", true, true));
                case 'ó' -> sounds.add(Sound.vowel("o", true, true));
                case 'i' -> sounds.add(Sound.vowel("i", false, false));
                case 'u', 'ü' -> sounds.add(Sound.vowel("u", false, false));
                // an accent on i/u makes it its own syllable ("día", "país")
                case 'í' -> sounds.add(Sound.vowel("i", true, true));
                case 'ú' -> sounds.add(Sound.vowel("u", true, true));
                case 'c' -> {
                    if (next == 'h') {
                        sounds.add(Sound.consonant("tʃ"));
                        i++;
                    } else {
                        sounds.add(Sound.consonant(frontVowelNext ? "θ" : "k"));
                    }
                }
                case 'q' -> {
                    sounds.add(Sound.consonant("k"));
                    if (next == 'u') {
                        i++; // "que", "qui": the u is silent
                    }
                }
                case 'g' -> {
                    if (frontVowelNext) {
                        sounds.add(Sound.consonant("x"));
                    } else {
                        sounds.add(Sound.consonant("ɡ"));
                        char afterU = i + 2 < w.length() ? w.charAt(i + 2) : 0;
                        if (next == 'u' && "eéií".indexOf(afterU) >= 0 && afterU != 0) {
                            i++; // "gue", "gui": the u is silent (but not in "güe")
                        }
                    }
                }
                case 'l' -> {
                    if (next == 'l') {
                        sounds.add(Sound.consonant("ʝ"));
                        i++;
                    } else {
                        sounds.add(Sound.consonant("l"));
                    }
                }
                case 'r' -> {
                    if (next == 'r') {
                        sounds.add(Sound.consonant("r"));
                        i++;
                    } else {
                        // rolled at the start of a word and after n, l, s; a single tap otherwise
                        char previous = i > 0 ? w.charAt(i - 1) : 0;
                        sounds.add(Sound.consonant(i == 0 || "nls".indexOf(previous) >= 0 ? "r" : "ɾ"));
                    }
                }
                case 'y' -> {
                    boolean vowelNext = next != 0 && "aeiouáéíóúü".indexOf(next) >= 0;
                    // "yo" -> ʝo, but "hoy", "muy" and the word "y" -> vowel i
                    sounds.add(vowelNext ? Sound.consonant("ʝ") : Sound.vowel("i", false, false));
                }
                case 'x' -> {
                    if (i == 0) {
                        sounds.add(Sound.consonant("s"));
                    } else {
                        sounds.add(Sound.consonant("k"));
                        sounds.add(Sound.consonant("s"));
                    }
                }
                case 'h' -> {
                    // silent
                }
                case 'j' -> sounds.add(Sound.consonant("x"));
                case 'z' -> sounds.add(Sound.consonant("θ"));
                case 'ñ' -> sounds.add(Sound.consonant("ɲ"));
                case 'v' -> sounds.add(Sound.consonant("b"));
                default -> sounds.add(Sound.consonant(String.valueOf(c)));
            }
        }
        return sounds;
    }

    // Groups sounds into syllables: each needs a vowel (or a diphthong like "ie", "ue", "ai");
    // a single consonant between vowels starts the next syllable, two are split unless they are
    // a pair like "pr", "bl", "tr" that can start a syllable together
    private static List<List<Sound>> syllables(List<Sound> sounds) {
        List<int[]> nuclei = new ArrayList<>(); // [first vowel index, last vowel index]
        for (int i = 0; i < sounds.size(); i++) {
            if (!sounds.get(i).vowel()) {
                continue;
            }
            boolean continuesNucleus = !nuclei.isEmpty() && nuclei.get(nuclei.size() - 1)[1] == i - 1
                    && !(sounds.get(i - 1).strong() && sounds.get(i).strong());
            if (continuesNucleus) {
                nuclei.get(nuclei.size() - 1)[1] = i;
            } else {
                nuclei.add(new int[]{i, i});
            }
        }
        List<List<Sound>> syllables = new ArrayList<>();
        if (nuclei.isEmpty()) {
            return syllables;
        }
        int start = 0;
        for (int n = 0; n < nuclei.size(); n++) {
            int end;
            if (n == nuclei.size() - 1) {
                end = sounds.size();
            } else {
                int consonantsStart = nuclei.get(n)[1] + 1;
                int consonantsEnd = nuclei.get(n + 1)[0];
                end = consonantsStart + consonantsInCoda(sounds.subList(consonantsStart, consonantsEnd));
            }
            syllables.add(new ArrayList<>(sounds.subList(start, end)));
            start = end;
        }
        return syllables;
    }

    // How many of the consonants between two vowels stay with the first syllable
    private static int consonantsInCoda(List<Sound> consonants) {
        int count = consonants.size();
        if (count <= 1) {
            return 0;
        }
        boolean lastTwoTogether = canStartTogether(consonants.get(count - 2).ipa(), consonants.get(count - 1).ipa());
        if (count == 2) {
            return lastTwoTogether ? 0 : 1;
        }
        return lastTwoTogether ? count - 2 : count - 1;
    }

    private static boolean canStartTogether(String first, String second) {
        return (Set.of("p", "b", "f", "k", "ɡ").contains(first) && (second.equals("l") || second.equals("ɾ")))
                || (Set.of("t", "d").contains(first) && second.equals("ɾ"));
    }

    // A written accent marks the stress; otherwise words ending in a vowel, n or s are stressed
    // on the second-to-last syllable, all others on the last
    private static int stressedSyllable(String word, List<List<Sound>> syllables) {
        for (int s = 0; s < syllables.size(); s++) {
            for (Sound sound : syllables.get(s)) {
                if (sound.accent()) {
                    return s;
                }
            }
        }
        char last = word.charAt(word.length() - 1);
        boolean penultimate = "aeiouns".indexOf(last) >= 0;
        return penultimate && syllables.size() > 1 ? syllables.size() - 2 : syllables.size() - 1;
    }

    // In a diphthong the unstressed i/u become glides: "bien" -> bjen, "aire" -> ai̯ɾe
    private static String syllableIpa(List<Sound> syllable) {
        int peak = -1;
        for (int i = 0; i < syllable.size(); i++) {
            if (syllable.get(i).vowel() && syllable.get(i).strong()) {
                peak = i;
                break;
            }
        }
        if (peak < 0) {
            for (int i = syllable.size() - 1; i >= 0; i--) {
                if (syllable.get(i).vowel()) {
                    peak = i;
                    break;
                }
            }
        }
        StringBuilder ipa = new StringBuilder();
        for (int i = 0; i < syllable.size(); i++) {
            Sound sound = syllable.get(i);
            if (sound.vowel() && i != peak) {
                boolean beforePeak = i < peak;
                if (sound.ipa().equals("i")) {
                    ipa.append(beforePeak ? "j" : "i̯");
                } else if (sound.ipa().equals("u")) {
                    ipa.append(beforePeak ? "w" : "u̯");
                } else {
                    ipa.append(sound.ipa());
                }
            } else {
                ipa.append(sound.ipa());
            }
        }
        return ipa.toString();
    }

    private static String joinSounds(List<Sound> sounds) {
        StringBuilder ipa = new StringBuilder();
        for (Sound sound : sounds) {
            ipa.append(sound.ipa());
        }
        return ipa.toString();
    }
}
