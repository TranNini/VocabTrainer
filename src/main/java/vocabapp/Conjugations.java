package vocabapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Which infinitive a conjugated form belongs to ("yo soy = I am" -> "ser = to be"), worked out from the form and
// from its translation, so the order they were saved in doesn't matter. Only when nothing fits does the form go
// to the infinitive saved before it, as it used to.
final class Conjugations {
    // Forms that can't be told from the infinitive's spelling, written without accents and in lower case
    private static final Map<String, List<String>> IRREGULAR = Map.ofEntries(
            // Spanish
            irregular("ser", "soy eres es somos sois son"),
            irregular("ir", "voy vas va vamos vais van"),
            irregular("haber", "he has ha hemos habeis han"),
            irregular("dar", "doy das da damos dais dan"),
            irregular("ver", "veo ves ve vemos veis ven"),
            // English (the infinitive without "to")
            irregular("be", "am are is was were"),
            irregular("have", "has had"),
            irregular("do", "does did"),
            irregular("go", "goes went"),
            // German
            irregular("sein", "bin bist ist sind seid"),
            irregular("haben", "habe hast hat habt"),
            irregular("werden", "werde wirst wird werdet"),
            irregular("wissen", "weiss weisst"),
            irregular("konnen", "kann kannst"),
            irregular("wollen", "will willst"),
            irregular("mussen", "muss musst"),
            irregular("durfen", "darf darfst"),
            irregular("mogen", "mag magst"),
            irregular("sollen", "soll sollst"));

    private Conjugations() {
    }

    private static Map.Entry<String, List<String>> irregular(String infinitive, String forms) {
        return Map.entry(infinitive, List.of(forms.split(" ")));
    }

    // The infinitive the form fits best: two points for the learned word ("corro" -> "correr"), one for the
    // translation ("I am" -> "to be"), as a translation is more often loose or mistyped ("nosotros somos = we
    // want" is still ser); of equally good ones the one saved before it. previous = the infinitive
    // saved right before the form, taken when nothing fits.
    static Vocab infinitiveOf(Vocab form, List<Vocab> infinitives, Vocab previous, Language language) {
        Language translation = language.getTranslation();
        String formVerb = verbOf(form.getWord(), language);
        String formTranslation = verbOf(form.getEnglish(), translation);
        Vocab best = null;
        int bestScore = 0;
        for (Vocab infinitive : infinitives) {
            int score = (fits(formVerb, verbsOf(infinitive.getWord(), language)) ? 2 : 0)
                    + (fits(formTranslation, verbsOf(infinitive.getEnglish(), translation)) ? 1 : 0);
            if (score > bestScore || score == bestScore && score > 0 && infinitive == previous) {
                best = infinitive;
                bestScore = score;
            }
        }
        return best != null ? best : previous;
    }

    // The verb of a conjugated form: the word after the pronoun ("yo soy" -> "soy", "él / ella / usted corre"
    // -> "corre", "I am" -> "am")
    private static String verbOf(String text, Language language) {
        String first = AnswerChecker.expandPronounShortForm(text, language).split("/")[0];
        String[] words = plain(AnswerChecker.normalize(first)).split(" ");
        List<String> pronouns = language.allPronouns().stream().map(Conjugations::plain).toList();
        return words.length > 1 && pronouns.contains(words[0]) ? words[1] : words[0];
    }

    // The infinitives of an entry, one per alternative, without "to" ("to talk / to speak" -> talk, speak)
    private static List<String> verbsOf(String text, Language language) {
        List<String> verbs = new ArrayList<>();
        for (String option : text.split("/")) {
            String[] words = plain(AnswerChecker.normalize(option)).split(" ");
            String verb = words.length > 1 && words[0].equals("to") ? words[1] : words[0];
            if (!verb.isEmpty()) {
                verbs.add(verb);
            }
        }
        return verbs;
    }

    private static boolean fits(String form, List<String> infinitives) {
        if (form.isEmpty()) {
            return false;
        }
        for (String infinitive : infinitives) {
            if (form.equals(infinitive) || IRREGULAR.getOrDefault(infinitive, List.of()).contains(form)
                    || form.equals(infinitive + "s") || form.equals(infinitive + "es")) {
                return true;
            }
            for (String stem : stems(infinitive)) {
                if (stem.length() >= 3 && form.startsWith(stem)) {
                    return true;
                }
            }
        }
        return false;
    }

    // The stems a form may start with: without its ending ("correr" -> corr, "laufen" -> lauf, "wandern" ->
    // wander), and with the Spanish stem changes e -> ie / i, o -> ue ("tener" -> tien, "poder" -> pued)
    private static List<String> stems(String infinitive) {
        List<String> stems = new ArrayList<>();
        for (int cut = 1; cut <= 2 && cut < infinitive.length(); cut++) {
            String stem = infinitive.substring(0, infinitive.length() - cut);
            stems.add(stem);
            int vowel = Math.max(stem.lastIndexOf('e'), stem.lastIndexOf('o'));
            if (vowel > 0) {
                String before = stem.substring(0, vowel);
                String after = stem.substring(vowel + 1);
                if (stem.charAt(vowel) == 'e') {
                    stems.add(before + "ie" + after);
                    stems.add(before + "i" + after);
                } else {
                    stems.add(before + "ue" + after);
                }
            }
        }
        return stems;
    }

    // lower case, without accents and umlauts ("läuft" -> "lauft", "Él" -> "el"), ß as ss
    private static String plain(String word) {
        return AnswerChecker.stripAccents(word.toLowerCase()).replace("ß", "ss");
    }
}
