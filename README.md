Vocab Trainer

A web app to practice vocabulary of the languages you learn (with English translations),
on the Mac in the browser and on the iPhone. Started with Spanish; more languages can be added.


Feature:

Several learners (e.g. a parent and a child), each with their own languages and vocabulary; pick
    "who's learning" at the top, each device remembers it. The same menu adds, renames and deletes learners
Several languages, each with its own vocabulary and categories; switch at the top of the app
Add new vocabulary with its English translation and an optional context note (rules, usage, exceptions),
    shown with the "Note" button in the word list and after each quiz question
Warns before saving a duplicate (same word or same English, also one of the '/' alternatives) and lists
    every entry it matches
Save vocabulary locally (one file per language)
Quiz saved vocabulary
Randomized vocabulary order
Practice both directions (language → English and English → language)
Up to 3 tries per answer, with two different hints per try: one after every wrong Check, and one from the
    hint button (once per try). A nearly right answer gets told what kind of mistake it is (a letter wrong,
    missing or too many, letters swapped, an accent, ü or ñ missing); otherwise the next letter of the right start
Score at the end of the quiz
Add multiple answer choices using '/'
What is written in brackets can be left out in quiz answers: "you have" is right for "you have (singular)"
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
    Verb conjugations are grouped under their infinitive and folded away; the button on the infinitive
        (e.g. "6 ▾") shows them. A search shows the matching forms open
    A subject pronoun at the start (yo, tú, él …) is optional in quiz answers, for conjugations and
        sentences: "tengo" is right for "yo tengo", "tengo una pregunta" for "yo tengo una pregunta".
        Save the pronoun without brackets
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
    The code is saved in access-code.txt (not in git), on the first line; the second line is the key the
    login cookie holds (a long random one, so codes can't be tried in a cookie). Delete that file to get
    a new code and key; devices then have to enter the new code.
    If the iPhone can't connect anymore, the Mac's address may have changed: check the Run window
    for the new one and add it to the Home Screen again.

Where the vocabulary is saved:
    vocab/<Learner>/<Language>.txt, e.g. vocab/Nini/Spanish.txt (not in git), one line per entry:
    word;english;category or word;english;category;context when the entry has a context note
    Vocab saved before there were learners (vocab/Spanish.txt …, or the even older vocab.txt) is moved
    to the first learner (vocab.first-learner in application.properties) automatically on the first start.
    A deleted learner's folder is moved to vocab/.deleted/<name> <date time>; to restore it, move it back
    to vocab/ (named like before, without the date) and restart the app.

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
Learners.java - everyone who learns with the app, one folder each in the vocab folder
VocabLibrary.java - all languages of one learner, one file each in their folder
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
