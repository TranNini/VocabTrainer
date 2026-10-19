Vocab Trainer

A web app to practice vocabulary of the languages you learn (with translations into English, German or another language),
on the Mac in the browser and on the iPhone. Started with Spanish; more languages can be added.


Feature:

Several learners (e.g. a parent and a child), each with their own languages and vocabulary; pick
    "who's learning" at the top, each device remembers it. The same menu adds, renames and deletes learners,
    and copies vocab from another learner (one language or all; words already in the list are skipped,
    the other learner's quiz results aren't copied)
Several languages, each with its own vocabulary and categories; switch at the top of the app
Every word list is a pair, e.g. Spanish → English or Spanisch → German or English → German, shown in the
    language menu. Adding a language asks for both; "Change translation…" changes the second. Both sides get
    the rules of their language (English, Spanish, German): spell check, capitals, articles, short forms like
    "he / she / it has" and "er / sie / es hat", letter buttons. Lists saved before pairs existed are recognised
    by their words (the dictionary that knows most of them)
Add new vocabulary with its translation and an optional context note (rules, usage, exceptions),
    shown with the "Note" button in the word list and after each quiz question
Spell check when saving: a word that isn't in the dictionary (Spanish, German, and American or British
    English for the English side) gets a warning
    with suggestions, e.g. 'nostoros' -> nosotros; a word only right with a capital says so ('haus' -> Haus,
    'spanish' -> Spanish), and a sentence must start with a capital (an entry ending with . ? ! or in a
    category like sentences / sätze). It can still be saved (names, rare words). Works offline,
    with the open Hunspell dictionaries in src/main/resources/dictionaries (licences next to them)
Never saves a duplicate (same word or same translation, also one of the '/' alternatives): it lists the
    entries it matches. Brackets tell entries apart, e.g. you have (singular) / you have (plural)
Save vocabulary locally (one file per language)
Quiz saved vocabulary
Randomized vocabulary order, in which the words that need practice tend to come first: the fewer of a
    word's last 5 tries were right on the first try, the earlier it usually comes (new words in between)
Practice both directions (language → English and English → language)
Up to 3 tries per answer, with two different hints per try: one after every wrong Check, and one from the
    hint button (once per try). A nearly right answer gets told what kind of mistake it is (a letter wrong,
    missing or too many, letters swapped, an accent, ü or ñ missing); otherwise the next letter of the right start
Score at the end of the quiz
Stats per learner and language (Stats tab): days in a row, answered today, right on the first try and in
    the end, an overview of today / 1 month / 6 months / 1 year, a chart of the answers for today (by hour), 1 month (by day), 6 months (by week) or 1 year (by month), words mastered (right on the first try 3 times in a row), practising
    and not practised yet, and the words missed most
Add multiple answer choices using '/' (a / button under every field when adding or editing types " / ")
Typing (s or (p in an English field (Add and edit) fills in (singular) or (plural), as "you have" is both;
    Spanish and German forms differ anyway. Backspace right after it goes back to what was typed
What is written in brackets can be left out in quiz answers: "you have" is right for "you have (singular)"
Categories (general, verbs, sentences, or your own) and a category filter for the quiz
"Refresh vocab" in the quiz menu: the words that were right on the first try lately (so the quiz asks them
    seldom) and weren't asked for 14 days, the longest not asked first
Edit and delete saved vocabulary
Alphabetical list in the order of each language, with letter sections
Buttons for special letters under every field, for the language of that field (also on the translation side):
    Spanish á é í ó ú ñ ü ¿ ¡, German ä ö ü ß, Vietnamese letters and tone marks. Small letters only: a letter
    becomes a capital by itself at a sentence start (after . ? ! and a space, after an opening ¿ or ¡, or at
    the start of an entry in a sentence category), else ⇧ makes the next one a capital
Link to a dictionary for every word (SpanishDict for Spanish, VDict for Vietnamese, Wiktionary for others)

Vietnamese extras:
    Buttons for ă â đ ê ô ơ ư and the five tone marks (huyền, sắc, hỏi, ngã, nặng),
    which add the mark to the letter typed before

Spanish extras:
    Articles (el, la, …) are ignored when sorting and give "Don't forget the article!" hints
    Verb conjugations (in the category verbs, verben, verbformen or verbos) are grouped under their infinitive and folded away; the button on the infinitive
        (e.g. "6 ▾") shows them. A search shows the matching forms open
    A subject pronoun at the start (yo, tú, él …) is optional in quiz answers, for conjugations and
        sentences: "tengo" is right for "yo tengo", "tengo una pregunta" for "yo tengo una pregunta".
        Save the pronoun without brackets
    Short form for conjugations: "él / ella / usted quiere" means "él quiere / ella quiere / usted quiere",
        and "he / she / it wants" means "he wants / she wants / it wants"
    Pronunciation in IPA (Spain Spanish) on demand, worked out from the spelling

German extras:
    Articles (der, die, das, den, dem, des, ein, eine, …) are ignored when sorting and give "Don't forget
        the article!" and "Check the article." hints
    Buttons for ä ö ü ß Ä Ö Ü
    Short form for conjugations, as in Spanish: "er / sie / es hat" means "er hat / sie hat / es hat", so
        the quiz asks "sie hat", never just "sie". The pronoun is required in German answers ("hat" alone is wrong)
    Conjugations (ich, du, er/sie/es, wir, ihr, sie/Sie) are grouped under their infinitive
    Spell check with the German dictionary, also of capitalised nouns

How to run:

    Run vocabapp.WebApp (in IntelliJ: open WebApp.java and click Run),
    or on the command line: mvn spring-boot:run
    Then open http://localhost:8080
    Tabs: Quiz, Add, Words (browse, search, edit, delete), Stats
    Language picker at the top; "Add language…" adds a new one

iPhone (same Wi-Fi as the Mac, Mac must be awake with the app running)
    1. Start the app. The Run window shows the iPhone address and the access code, e.g.
           On your iPhone: http://192.168.178.188:8080  (same Wi-Fi)
           Access code:   abcd2345
    2. The first time, macOS may ask whether Java may accept incoming connections: click Allow.
    3. Open the address in Safari on the iPhone and enter the access code (only needed once).
    4. Share button → Add to Home Screen (keep "Open as Web App" on). It now opens like an app.
    The code is saved in access-code.txt (not in git), on the first line; the second line is the key the
    login cookie holds (a long random one, so codes can't be tried in a cookie). Delete that file to get
    a new code and key; devices then have to enter the new code.
    If the iPhone can't connect anymore, the Mac's address may have changed: check the Run window
    for the new one and add it to the Home Screen again.

Where the vocabulary is saved:
    vocab/<Learner>/<Language>.txt, e.g. vocab/Nini/Spanish.txt (not in git), one line per entry:
    word;english;category or word;english;category;context when the entry has a context note
    Quiz results for the stats: vocab/<Learner>/<Language>.log, one line per finished question
    (when;word;in/en;the try it was right on, 0 = not right;hints)
    Vocab saved before there were learners (vocab/Spanish.txt …, or the even older vocab.txt) is moved
    to the first learner (vocab.first-learner in application.properties) automatically on the first start.
    vocab/<Learner>/<Language>.translation holds the language that list is translated into, e.g. German.
    A deleted learner's folder is moved to vocab/.deleted/<name> <date time>; to restore it, move it back
    to vocab/ (named like before, without the date) and restart the app.

Adding verb conjugations (Spanish):
    Add the infinitive first (e.g. tener), then its forms starting with the pronoun
    (yo tengo, tú tienes, ...) in the verbs category. They are listed under the infinitive
    saved right before them.

Giving a new language its extras:
    A language named in German or in itself (Spanisch, Español, Deutsch, Vietnamesisch) gets the same
    extras as its English name.
    A language added in the app works right away. Articles, conjugation grouping, IPA,
    fixed special letter buttons and its own dictionary are set up in Language.java
    (see how Spanish is done there).

Tests:
    mvn test (in IntelliJ: right-click src/test/java → Run 'All Tests')

Project Structure (src/main/java/vocabapp):

Vocab.java - represents a vocabulary entry and its category
VocabStore.java - handles storing and loading the vocabulary of one language
Learners.java - everyone who learns with the app, one folder each in the vocab folder
VocabLibrary.java - all languages of one learner, one file each in their folder
Language.java - everything that differs between languages (Spanish rules, defaults for others)
AnswerChecker.java - checks quiz answers and gives hints
SpellChecker.java - the spell check for new vocab
QuizLog.java, QuizStats.java - the quiz results and the stats worked out from them
Question.java - one quiz question (which side is asked)
VocabIndex.java - sorting, sections, search, verb grouping and categories
SpanishIpa.java - works out the IPA pronunciation from the Spanish spelling
WebApp.java - starts the app
AccessCode.java, AccessFilter.java, LoginController.java - the access code for other devices
StartupInfo.java - prints the iPhone address and access code on start
VocabController.java - the web API the page talks to (/api/languages/...)
src/main/resources/static - the web page (index.html, style.css, app.js)


Running in Docker

Build the jar and the image, then create the container once:

    mvn package
    docker build -t vocab-learning-app .
    docker run -d --name vocab-learning-app --restart unless-stopped -p 8080:8080 \
      -v /Users/nini/IdeaProjects/VocabLearningApp/vocab:/app/vocab \
      -v /Users/nini/IdeaProjects/VocabLearningApp/access-code.txt:/app/access-code.txt \
      vocab-learning-app

--restart unless-stopped starts the app again whenever Docker Desktop starts (turn on
"Start Docker Desktop when you sign in" in its settings), unless you stopped it with
docker stop. To start it by hand: docker start vocab-learning-app
For a new version: mvn package, docker build, docker rm -f vocab-learning-app, then docker run again.
