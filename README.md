Vocab Trainer

A web app to practice vocabulary of the languages you learn (with English translations),
on the Mac in the browser and on the iPhone. Started with Spanish; more languages can be added.


Feature:

Several languages, each with its own vocabulary and categories; switch at the top of the app
Add new vocabulary with its English translation and an optional context note (rules, usage, exceptions),
    shown with the "Note" button in the word list and after each quiz question
Save vocabulary locally (one file per language)
Quiz saved vocabulary
Randomized vocabulary order
Practice both directions (language → English and English → language)
Up to 3 tries per answer with hints
Score at the end of the quiz
Add multiple answer choices using '/'
Categories (general, verbs, sentences, or your own) and a category filter for the quiz
Edit and delete saved vocabulary
Alphabetical list in the order of each language, with letter sections
Buttons for special letters (á, ñ, è, …)
Link to a dictionary for every word (SpanishDict for Spanish, VDict for Vietnamese, Wiktionary for others)

Vietnamese extras:
    Buttons for ă â đ ê ô ơ ư and the five tone marks (huyền, sắc, hỏi, ngã, nặng),
    which add the mark to the letter typed before

Spanish extras:
    Articles (el, la, …) are ignored when sorting and give "Don't forget the article!" hints
    Verb conjugations are grouped under their infinitive
    Pronunciation in IPA (Spain Spanish) on demand, worked out from the spelling

How to run:

    Run vocabapp.WebApp (in IntelliJ: open WebApp.java and click Run),
    or on the command line: mvn spring-boot:run
    Then open http://localhost:8080
    Tabs: Quiz, Add, Words (browse, search, edit, delete)
    Language picker at the top; "Add language…" adds a new one

iPhone (same Wi-Fi as the Mac, Mac must be awake with the app running)
    1. Start the app. The Run window shows the iPhone address and the access code, e.g.
           On your iPhone: http://192.168.178.188:8080  (same Wi-Fi)
           Access code:   abcd2345
    2. The first time, macOS may ask whether Java may accept incoming connections: click Allow.
    3. Open the address in Safari on the iPhone and enter the access code (only needed once).
    4. Share button → Add to Home Screen (keep "Open as Web App" on). It now opens like an app.
    The code is saved in access-code.txt (not in git). Delete that file to get a new code;
    devices then have to enter the new one.
    If the iPhone can't connect anymore, the Mac's address may have changed: check the Run window
    for the new one and add it to the Home Screen again.

Where the vocabulary is saved:
    vocab/<Language>.txt, e.g. vocab/Spanish.txt (not in git), one line per entry: word;english;category
    or word;english;category;context when the entry has a context note
    The old vocab.txt is moved to vocab/Spanish.txt automatically on the first start.

Adding verb conjugations (Spanish):
    Add the infinitive first (e.g. tener), then its forms starting with the pronoun
    (yo tengo, tú tienes, ...) in the verbs category. They are listed under the infinitive
    saved right before them.

Giving a new language its extras:
    A language added in the app works right away. Articles, conjugation grouping, IPA,
    fixed special letter buttons and its own dictionary are set up in Language.java
    (see how Spanish is done there).

Tests:
    mvn test (in IntelliJ: right-click src/test/java → Run 'All Tests')

Project Structure (src/main/java/vocabapp):

Vocab.java - represents a vocabulary entry and its category
VocabStore.java - handles storing and loading the vocabulary of one language
VocabLibrary.java - all languages, one file each in the vocab folder
Language.java - everything that differs between languages (Spanish rules, defaults for others)
AnswerChecker.java - checks quiz answers and gives hints
Question.java - one quiz question (which side is asked)
VocabIndex.java - sorting, sections, search, verb grouping and categories
SpanishIpa.java - works out the IPA pronunciation from the Spanish spelling
WebApp.java - starts the app
AccessCode.java, AccessFilter.java, LoginController.java - the access code for other devices
StartupInfo.java - prints the iPhone address and access code on start
VocabController.java - the web API the page talks to (/api/languages/...)
src/main/resources/static - the web page (index.html, style.css, app.js)
