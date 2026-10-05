Spanish Vocab Trainer

A vocab trainer to practice Spanish and English vocabulary, as a web app (Mac browser, later iPhone) and on the command line.
Both versions use the same vocab.txt.


Feature:

Add new Spanish and English vocabulary
Save vocabulary locally
Quiz saved vocabulary
Randomized vocabulary order
Practice both Spanish → English and English → Spanish
Up to 3 tries per answer with hints
Score at the end of the quiz
Add multiple answer choices using '/'
Categories (general, verbs, sentences, or your own) and a category filter for the quiz
Edit and delete saved vocabulary
Alphabetical list that ignores articles, with verb conjugations grouped under their infinitive
Pronunciation in IPA (Spain Spanish) on demand, worked out from the spelling, plus a link to SpanishDict

How to run:

Web app
    Run vocabapp.WebApp (in IntelliJ: open WebApp.java and click Run),
    or on the command line: mvn spring-boot:run
    Then open http://localhost:8080
    Tabs: Quiz, Add, Words (browse, search, edit, delete)

iPhone (same Wi-Fi as the Mac, Mac must be awake with the app running)
    1. Start the web app. The Run window shows the iPhone address and the access code, e.g.
           On your iPhone: http://192.168.178.188:8080  (same Wi-Fi)
           Access code:   abcd2345
    2. The first time, macOS may ask whether Java may accept incoming connections: click Allow.
    3. Open the address in Safari on the iPhone and enter the access code (only needed once).
    4. Share button → Add to Home Screen (keep "Open as Web App" on). It now opens like an app.
    The code is saved in access-code.txt (not in git). Delete that file to get a new code;
    devices then have to enter the new one.
    If the iPhone can't connect anymore, the Mac's address may have changed: check the Run window
    for the new one and add it to the Home Screen again.

Command line
    Run vocabapp.VocabApp
    1. Add new vocab
        Add the Spanish word or sentence first then translate in English.
        Category: v = verbs, s = sentences, Enter = general, or type a name.
        Type 'done' once finished to return to main menu.
    2. Quiz
    3. Edit vocab
    4. Exit

Adding verb conjugations:
    Add the infinitive first (e.g. tener), then its forms starting with the pronoun
    (yo tengo, tú tienes, ...) in the verbs category. They are listed under the infinitive
    saved right before them.

Tests:
    mvn test (in IntelliJ: right-click src/test/java → Run 'All Tests')

Project Structure (src/main/java/vocabapp):

Vocab.java - represents a vocabulary entry and its category
VocabStore.java - handles storing and loading vocabulary in vocab.txt
AnswerChecker.java - checks quiz answers and gives hints
Question.java - one quiz question (which side is asked)
VocabIndex.java - sorting, sections, search, verb grouping and categories
SpanishIpa.java - works out the IPA pronunciation from the Spanish spelling
VocabApp.java - the command-line version
WebApp.java - starts the web version
AccessCode.java, AccessFilter.java, LoginController.java - the access code for other devices
StartupInfo.java - prints the iPhone address and access code on start
VocabController.java - the web API the page talks to (/api/...)
src/main/resources/static - the web page (index.html, style.css, app.js)
