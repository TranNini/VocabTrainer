package vocabapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpanishIpaTest {

    private static void assertIpa(String expected, String spanish) {
        assertEquals(expected, SpanishIpa.transcribe(spanish), spanish);
    }

    @Test
    void stressFollowsTheSpellingRules() {
        assertIpa("/teˈneɾ/", "tener");          // ends in r -> last syllable
        assertIpa("/ˈkaɾ.ne/", "carne");         // ends in a vowel -> second to last
        assertIpa("/ˈko.mo/", "cómo");           // written accent wins
        assertIpa("/peɾˈdon/", "Perdón");
        assertIpa("/esˈpa.ɲa/", "España");
    }

    @Test
    void spainSounds() {
        assertIpa("/ˈθjen/", "cien");            // c before i -> θ
        assertIpa("/θaˈpa.to/", "zapato");
        assertIpa("/xiˈɾa.fa/", "jirafa");       // j -> x, single r -> ɾ
        assertIpa("/xenˈtil/", "gentil");        // g before e -> x
        assertIpa("/ˈɡa.to/", "gato");
        assertIpa("/ˈke.so/", "queso");          // silent u
        assertIpa("/ˈʝa.mo/", "llamo");
        assertIpa("/ˈʝo/", "yo");
        assertIpa("/ˈpe.ro/", "perro");          // rr -> rolled r
        assertIpa("/ˈra.na/", "rana");           // r at the start -> rolled r
        assertIpa("/ˈno.tʃe/", "noche");
        assertIpa("/ˈba.ka/", "vaca");           // v -> b
        assertIpa("/ˈwe.so/", "hueso");          // silent h
    }

    @Test
    void syllablesAndDiphthongs() {
        assertIpa("/ko.koˈdɾi.lo/", "cocodrilo"); // dr starts a syllable together
        assertIpa("/a.leˈma.nja/", "Alemania");   // ia is one syllable
        assertIpa("/ˈbwe.nas ˈno.tʃes/", "¡Buenas noches!");
        assertIpa("/leˈeɾ/", "leer");             // two strong vowels -> two syllables
        assertIpa("/ˈdi.a/", "día");              // accented í -> own syllable
        assertIpa("/teˈnei̯s/", "tenéis");
        assertIpa("/esˈtoi̯/", "estoy");
        assertIpa("/pinˈɡwi.no/", "pingüino");
        assertIpa("/θjuˈdad/", "ciudad");
        assertIpa("/ekˈsa.men/", "examen");
        assertIpa("/ˈtak.si/", "taxi");
    }

    @Test
    void sentencesAndAlternatives() {
        assertIpa("/el a.niˈmal/", "el animal");   // "el" has no stress of its own
        assertIpa("/ˈʝo ˈsoi̯ de/", "Yo soy de");
        assertIpa("/ˈel ˈtje.ne/ · /ˈe.ʝa ˈtje.ne/", "él tiene / ella tiene");
        assertIpa("/aˈsi aˈsi/", "así, así");
        assertIpa("/ˈko.mo/", "Cómo...?");
    }
}
