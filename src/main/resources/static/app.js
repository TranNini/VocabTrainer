// All quiz checking, sorting and saving happens in Java (see VocabController);
// this file only shows the screens and talks to /api.

const NEW_CATEGORY = "__new__";
const ADD_LANGUAGE = "__add__";
const ADD_LEARNER = "__add__";
const RENAME_LEARNER = "__rename__";
const DELETE_LEARNER = "__delete__";

let learner = null; // whose vocab is shown: each learner has their own languages and words
let languages = [];
let language = null; // the one being learned right now: {name, count, ipa, specialLetters, dictionaryName}
let categories = [];

// ---------- helpers ----------

const $ = (id) => document.getElementById(id);

function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    Object.assign(node, props);
    node.append(...children);
    return node;
}

async function api(method, url, body) {
    const response = await fetch(url, {
        method,
        headers: body ? {"Content-Type": "application/json"} : {},
        body: body ? JSON.stringify(body) : undefined,
    });
    if (response.status === 401) {
        // another device without the access code yet
        location.href = "/login.html";
        throw new Error("Please enter the access code.");
    }
    const data = response.status === 204 ? null : await response.json();
    if (!response.ok) {
        const error = new Error(data?.error || "Something went wrong.");
        error.status = response.status;
        error.data = data;
        throw error;
    }
    return data;
}

function showMessage(text, isError = false) {
    const message = $("message");
    message.textContent = text;
    message.classList.toggle("error", isError);
    message.hidden = false;
    clearTimeout(showMessage.timer);
    showMessage.timer = setTimeout(() => (message.hidden = true), 4000);
}

// URL of something of the current learner, e.g. /api/learners/Nini/languages
function learnerUrl(path) {
    return `/api/learners/${encodeURIComponent(learner)}${path}`;
}

// URL of something in the current language, e.g. /api/learners/Nini/languages/Spanish/vocab
function langUrl(path) {
    return learnerUrl(`/languages/${encodeURIComponent(language.name)}${path}`);
}

// Vietnamese tone marks, typed after the vowel they belong to
const MARK_NAMES = {"\u0300": "huyền", "\u0301": "sắc", "\u0309": "hỏi", "\u0303": "ngã", "\u0323": "nặng"};

// Buttons for the language's letters that a normal keyboard lacks (á, ñ, ¿, ơ, tone marks …)
function addAccentButtons(container, input) {
    const letters = language?.specialLetters ?? [];
    container.hidden = letters.length === 0;
    container.replaceChildren(...letters.map((letter) => {
        const isMark = /\p{M}/u.test(letter);
        // a mark on its own is invisible, so the button shows it on an "a" (it still only adds the mark)
        const button = el("button", {type: "button", textContent: isMark ? ("a" + letter).normalize("NFC") : letter,
            title: isMark ? `${MARK_NAMES[letter] ?? "mark"}: adds this mark to the letter before` : letter});
        button.classList.toggle("mark", isMark);
        // keep the keyboard open on the phone
        button.addEventListener("mousedown", (e) => e.preventDefault());
        button.addEventListener("click", () => {
            const start = input.selectionStart ?? input.value.length;
            const end = input.selectionEnd ?? input.value.length;
            input.setRangeText(letter, start, end, "end");
            if (isMark) {
                // "e" + mark -> "é" as one letter, and keep the cursor after it
                const before = input.value.slice(0, input.selectionEnd).normalize("NFC");
                input.value = before + input.value.slice(input.selectionEnd).normalize("NFC");
                input.setSelectionRange(before.length, before.length);
            }
            input.focus();
        });
        return button;
    }));
}

// ---------- pronunciation ----------

// Button that shows the IPA (worked out in Java, e.g. SpanishIpa) in the given element, and hides it again.
// Languages without IPA rules get no button.
function ipaButtons(ipa, target) {
    return ipa ? [toggleButton("IPA", "Show pronunciation", ipa, target)] : [];
}

// Same for the context note; words without one get no button
function noteButtons(context, target) {
    return context ? [toggleButton("Note", "Show context", context, target)] : [];
}

function toggleButton(label, title, text, target) {
    const button = el("button", {type: "button", className: "toggle", textContent: label, title});
    button.setAttribute("aria-expanded", "false");
    button.addEventListener("click", (event) => {
        event.stopPropagation();
        const show = target.hidden;
        target.textContent = text;
        target.hidden = !show;
        button.classList.toggle("active", show);
        button.setAttribute("aria-expanded", String(show));
    });
    return button;
}

// The word in a dictionary (SpanishDict for Spanish, Wiktionary for others); the server builds the URL
function dictionaryLink(url) {
    return el("a", {
        href: url,
        target: "_blank",
        rel: "noopener",
        className: "dict-link",
        textContent: language.dictionaryName + " ↗",
    });
}

// ---------- learners ----------

// Each device remembers its learner, and the last language of each learner
function loadSetting(key) {
    try {
        return localStorage.getItem(key);
    } catch {
        return null; // private browsing: just start with the first one
    }
}

function saveSetting(key, value) {
    try {
        localStorage.setItem(key, value);
    } catch {
        // only a convenience
    }
}

let learnerNames = [];

async function loadLearners(selectName) {
    learnerNames = await api("GET", "/api/learners");
    const wanted = learnerNames.find((n) => n === selectName) || learnerNames.find((n) => n === loadSetting("learner"))
        || learnerNames[0];
    if (!wanted) {
        learner = null;
        fillLearnerSelect();
        $("learner").value = ADD_LEARNER;
        showMessage("Add your name to start.");
        return;
    }
    await switchLearner(wanted);
}

// The learners, then "Add learner…" and renaming or deleting the current one
function fillLearnerSelect() {
    const options = learnerNames.map((name) => el("option", {value: name, textContent: name}));
    options.push(el("option", {value: ADD_LEARNER, textContent: "Add learner…"}));
    if (learner) {
        options.push(el("option", {value: RENAME_LEARNER, textContent: `Rename ${learner}…`}),
            el("option", {value: DELETE_LEARNER, textContent: `Delete ${learner}…`}));
    }
    $("learner").replaceChildren(...options);
    $("learner").value = learner ?? ADD_LEARNER;
}

async function switchLearner(name) {
    learner = name;
    saveSetting("learner", name);
    fillLearnerSelect();
    await loadLanguages();
}

async function renameLearner() {
    const name = prompt(`New name for ${learner}:`, learner);
    $("learner").value = learner;
    if (!name || name.trim() === learner) {
        return;
    }
    try {
        const renamed = await api("PUT", "/api/learners/" + encodeURIComponent(learner), {name});
        // keep the last language of this learner
        saveSetting("language:" + renamed.name, loadSetting("language:" + learner) ?? "");
        await loadLearners(renamed.name);
        showMessage(`Renamed to ${renamed.name}.`);
    } catch (error) {
        showMessage(error.message, true);
    }
}

async function deleteLearner() {
    $("learner").value = learner;
    if (!confirm(`Delete ${learner} with all of ${learner}'s languages and words?`)) {
        return;
    }
    try {
        const deleted = learner;
        await api("DELETE", "/api/learners/" + encodeURIComponent(deleted));
        await loadLearners();
        showMessage(`Deleted ${deleted}.`);
    } catch (error) {
        showMessage(error.message, true);
    }
}

async function addLearner() {
    const name = prompt("What's the name of the new learner?");
    if (!name) {
        $("learner").value = learner ?? ADD_LEARNER;
        return;
    }
    try {
        const added = await api("POST", "/api/learners", {name});
        await loadLearners(added.name);
        showMessage(`Hi ${added.name}! Add the first language you want to learn.`);
    } catch (error) {
        $("learner").value = learner ?? ADD_LEARNER;
        showMessage(error.message, true);
    }
}

// ---------- languages ----------

async function loadLanguages(selectName) {
    languages = await api("GET", learnerUrl("/languages"));
    const select = $("language");
    select.replaceChildren(
        ...languages.map((l) => el("option", {value: l.name, textContent: l.name})),
        el("option", {value: ADD_LANGUAGE, textContent: "Add language…"}));
    const wanted = languages.find((l) => l.name === selectName)
        || languages.find((l) => l.name === loadSetting("language:" + learner)) || languages[0];
    if (!wanted) {
        // a new learner: nothing of the previous learner may stay on screen
        language = null;
        for (const id of ["quiz-category", "words-category", "add-category"]) {
            $(id).replaceChildren();
        }
        $("words-list").replaceChildren();
        $("words-letters").replaceChildren();
        $("add-recent").hidden = true;
        $("add-recent-list").replaceChildren();
        $("quiz-question").hidden = true;
        $("quiz-summary").hidden = true;
        $("quiz-setup").hidden = false;
        select.value = ADD_LANGUAGE;
        showMessage("Add the first language you want to learn.");
        return;
    }
    select.value = wanted.name;
    await switchLanguage(wanted.name);
}

async function switchLanguage(name) {
    language = languages.find((l) => l.name === name);
    saveSetting("language:" + learner, name);
    for (const label of document.querySelectorAll(".lang-name")) {
        label.textContent = name;
    }
    addAccentButtons($("quiz-accents"), $("quiz-answer"));
    addAccentButtons(document.querySelector("#add-form .accents"), $("add-word"));
    $("add-recent").hidden = true;
    $("add-recent-list").replaceChildren();
    $("quiz-question").hidden = true;
    $("quiz-summary").hidden = true;
    $("quiz-setup").hidden = false;
    await loadCategories();
    if (!$("view-words").hidden) {
        await loadWords();
    }
    if (!$("view-stats").hidden) {
        await loadStats();
    }
}

// Word count and special letters change when words are added, so fetch the language again
async function refreshLanguage() {
    languages = await api("GET", learnerUrl("/languages"));
    language = languages.find((l) => l.name === language.name) || language;
    addAccentButtons($("quiz-accents"), $("quiz-answer"));
    addAccentButtons(document.querySelector("#add-form .accents"), $("add-word"));
}

async function addLanguage() {
    const name = prompt("Which language do you want to learn? (its English name, e.g. Italian)");
    if (!name) {
        $("language").value = language?.name ?? ADD_LANGUAGE;
        return;
    }
    try {
        const added = await api("POST", learnerUrl("/languages"), {name});
        await loadLanguages(added.name);
        showMessage(`${added.name} added. Start by adding some vocab.`);
    } catch (error) {
        $("language").value = language?.name ?? ADD_LANGUAGE;
        showMessage(error.message, true);
    }
}

// ---------- categories ----------

async function loadCategories() {
    categories = await api("GET", langUrl("/categories"));
    const total = categories.reduce((sum, c) => sum + c.count, 0);
    for (const id of ["quiz-category", "words-category"]) {
        const select = $(id);
        const previous = select.value;
        select.replaceChildren(
            el("option", {value: "", textContent: `All (${total})`}),
            ...categories.map((c) => el("option", {value: c.name, textContent: `${c.name} (${c.count})`})));
        select.value = categories.some((c) => c.name === previous) ? previous : "";
    }
    fillCategorySelect($("add-category"), $("add-category").value || "general");
}

// The select offers the existing categories plus "New category…", which shows a text field
function fillCategorySelect(select, selected) {
    const names = categories.map((c) => c.name);
    if (!names.some((n) => n.toLowerCase() === "general")) {
        names.unshift("general");
    }
    select.replaceChildren(
        ...names.map((name) => el("option", {value: name, textContent: name})),
        el("option", {value: NEW_CATEGORY, textContent: "New category…"}));
    select.value = names.includes(selected) ? selected : names[0];
}

function readCategory(select, newInput) {
    if (select.value === NEW_CATEGORY) {
        return {category: newInput.value.trim(), newCategory: true};
    }
    return {category: select.value, newCategory: false};
}

function wireCategorySelect(select, newInput) {
    select.addEventListener("change", () => {
        newInput.hidden = select.value !== NEW_CATEGORY;
        if (!newInput.hidden) {
            newInput.focus();
        }
    });
}

// Sends the vocab; if the server says the category is new, a word may be misspelled or the vocab is
// already saved, asks first and sends again (each can still be followed by the next)
async function saveVocab(method, url, vocab) {
    try {
        return await api(method, url, vocab);
    } catch (error) {
        if (error.status !== 409) {
            throw error;
        }
        if (error.data.newCategory !== undefined) {
            if (confirm(`Create new category '${error.data.newCategory}'?`)) {
                return saveVocab(method, url, {...vocab, newCategory: true});
            }
        } else if (error.data.spelling) {
            // learning a typo is worse than a moment's checking
            const list = error.data.spelling.map((m) => `• '${m.word}' isn't in the ${m.language} dictionary` +
                (m.suggestions.length ? `. Did you mean: ${m.suggestions.join(", ")}?` : ".")).join("\n");
            if (confirm(`${error.message}\n\n${list}\n\nSave anyway?`)) {
                return saveVocab(method, url, {...vocab, spellingChecked: true});
            }
        } else if (error.data.duplicates) {
            const list = error.data.duplicates.map((d) => `• ${d.word} = ${d.english} [${d.category}]`).join("\n");
            if (confirm(`${error.message}\n\n${list}\n\nSave anyway?`)) {
                return saveVocab(method, url, {...vocab, duplicate: true});
            }
        }
        throw new Error("Not saved.");
    }
}

// ---------- tabs ----------

function showView(name) {
    if (!["quiz", "add", "words", "stats"].includes(name)) {
        name = "quiz";
    }
    history.replaceState(null, "", "#" + name);
    for (const button of document.querySelectorAll(".tabs button")) {
        button.classList.toggle("active", button.dataset.view === name);
    }
    for (const view of document.querySelectorAll(".view")) {
        view.hidden = view.id !== `view-${name}`;
    }
    if (name === "words") {
        loadWords();
    }
    if (name === "stats") {
        loadStats().catch((error) => showMessage(error.message, true));
    }
    if (name === "add") {
        $("add-word").focus();
    }
}

// ---------- quiz ----------

// shownHints = the hints shown for the current question, so the hint button never repeats one
// hints = how often the hint button was pressed for this question, for the statistics
const quiz = {questions: [], index: 0, attempt: 1, correct: 0, asked: 0, shownHints: [], hints: 0};

async function startQuiz() {
    quiz.questions = await api("GET", langUrl("/quiz?category=" + encodeURIComponent($("quiz-category").value)));
    if (quiz.questions.length === 0) {
        showMessage("No vocab saved yet. Add some first.");
        return;
    }
    Object.assign(quiz, {index: 0, correct: 0, asked: 0});
    $("quiz-setup").hidden = true;
    $("quiz-summary").hidden = true;
    $("quiz-question").hidden = false;
    showQuestion();
}

function showQuestion() {
    const question = quiz.questions[quiz.index];
    quiz.attempt = 1;
    quiz.shownHints = [];
    quiz.hints = 0;
    $("quiz-progress").textContent = `${quiz.index + 1} / ${quiz.questions.length}`;
    $("quiz-score").textContent = `${quiz.correct} correct`;
    $("quiz-direction").textContent = question.answerInLanguage ? `In ${language.name}:` : "In English:";
    $("quiz-prompt").textContent = question.prompt;
    // promptIpa only comes with prompts in the language
    $("quiz-prompt-ipa").hidden = true;
    $("quiz-prompt-ipa-toggle").replaceChildren(...ipaButtons(question.promptIpa, $("quiz-prompt-ipa")));
    $("quiz-extras").hidden = true;
    $("quiz-answer-ipa").hidden = true;
    $("quiz-accents").hidden = !question.answerInLanguage || language.specialLetters.length === 0;
    $("quiz-feedback").textContent = "";
    $("quiz-feedback").className = "feedback";
    $("quiz-full-answer").hidden = true;
    $("quiz-context").hidden = true;
    $("quiz-answer").value = "";
    $("quiz-answer").disabled = false;
    $("quiz-hint").hidden = true;
    $("quiz-check").hidden = false;
    $("quiz-hint-button").hidden = false;
    setHintButton(true);
    $("quiz-next").hidden = true;
    $("quiz-answer").focus();
}

async function checkAnswer() {
    const question = quiz.questions[quiz.index];
    const answer = $("quiz-answer").value.trim();
    if (!answer) {
        return;
    }
    if (quiz.attempt === 1) {
        quiz.asked++;
    }
    const result = await api("POST", langUrl("/quiz/check"), {
        id: question.id, answerInLanguage: question.answerInLanguage, answer, attempt: quiz.attempt,
        shown: quiz.shownHints, hints: quiz.hints,
    });
    const feedback = $("quiz-feedback");
    feedback.textContent = result.feedback;
    quiz.shownHints.push(result.feedback);
    feedback.className = "feedback " + (result.correct ? "good" : "bad");
    if (result.correct) {
        quiz.correct++;
        $("quiz-score").textContent = `${quiz.correct} correct`;
    }
    if (result.finished) {
        // always show the complete answer, even after a correct one, so all alternatives are seen
        $("quiz-full-answer").replaceChildren(
            el("span", {className: "muted", textContent: "Answer: "}),
            el("strong", {textContent: result.answer}));
        $("quiz-full-answer").hidden = false;
        // the note only shows once the question is over, so it never gives the answer away
        $("quiz-context").textContent = result.context;
        $("quiz-context").hidden = !result.context;
        $("quiz-extras").replaceChildren(
            ...ipaButtons(result.ipa, $("quiz-answer-ipa")), dictionaryLink(result.dictionaryUrl));
        $("quiz-extras").hidden = false;
        $("quiz-answer").disabled = true;
        $("quiz-check").hidden = true;
        $("quiz-hint-button").hidden = true;
        $("quiz-next").hidden = false;
        $("quiz-next").focus();
    } else {
        quiz.attempt++;
        setHintButton(true); // a new try, so one more hint
        $("quiz-answer").select();
    }
}

// One hint per try: the button works again after the next Check
function setHintButton(enabled) {
    $("quiz-hint-button").disabled = !enabled;
    $("quiz-hint-button").title = enabled ? "Hint" : "One hint per try. Check your answer for the next one.";
}

// A hint that fits what is typed so far and wasn't shown yet (see AnswerChecker.hint); doesn't use up a try
async function showHint() {
    setHintButton(false);
    quiz.hints++;
    const question = quiz.questions[quiz.index];
    const hint = await api("POST", langUrl("/quiz/hint"), {
        id: question.id, answerInLanguage: question.answerInLanguage, answer: $("quiz-answer").value,
        shown: quiz.shownHints,
    });
    quiz.shownHints.push(hint.text);
    $("quiz-hint").textContent = hint.text;
    $("quiz-hint").hidden = false;
    $("quiz-answer").focus();
}

function nextQuestion() {
    quiz.index++;
    if (quiz.index >= quiz.questions.length) {
        stopQuiz();
    } else {
        showQuestion();
    }
}

function stopQuiz() {
    $("quiz-question").hidden = true;
    $("quiz-summary").hidden = false;
    $("quiz-result").textContent = `Score: ${quiz.correct}/${quiz.asked}`;
}

// ---------- add ----------

async function addVocab(event) {
    event.preventDefault();
    const vocab = {
        word: $("add-word").value,
        english: $("add-english").value,
        context: $("add-context").value,
        ...readCategory($("add-category"), $("add-new-category")),
    };
    try {
        const saved = await saveVocab("POST", langUrl("/vocab"), vocab);
        $("add-recent").hidden = false;
        $("add-recent-list").prepend(el("li", {textContent: `${saved.word} = ${saved.english} [${saved.category}]`}));
        // keep the category, so a whole verb can be added in a row
        $("add-word").value = "";
        $("add-english").value = "";
        $("add-context").value = "";
        $("add-new-category").value = "";
        $("add-new-category").hidden = true;
        await loadCategories();
        await refreshLanguage();
        fillCategorySelect($("add-category"), saved.category);
        $("add-word").focus();
    } catch (error) {
        showMessage(error.message, true);
    }
}

// ---------- words ----------

let searchTimer;

async function loadWords() {
    const category = $("words-category").value;
    const query = $("words-search").value.trim();
    const words = await api("GET", langUrl(`/vocab?category=${encodeURIComponent(category)}&q=${encodeURIComponent(query)}`));
    const list = $("words-list");
    const letters = [];
    list.replaceChildren();
    if (words.length === 0) {
        list.append(el("p", {className: "muted", textContent: query ? `No vocab found for '${query}'.` : "No vocab here yet."}));
    }
    let verb = null;
    for (const word of words) {
        // conjugations come right after their infinitive: fold them under it (a search shows them open)
        if (word.conjugation && verb) {
            addConjugation(verb, wordRow(word), query !== "");
            continue;
        }
        if (letters[letters.length - 1] !== word.section) {
            letters.push(word.section);
            list.append(el("h3", {className: "section-header", id: "section-" + word.section, textContent: word.section}));
        }
        const row = wordRow(word);
        list.append(row);
        verb = word.conjugation ? null : {word, row, forms: null};
    }
    $("words-letters").replaceChildren(...letters.map((letter) => {
        const button = el("button", {type: "button", textContent: letter});
        button.addEventListener("click", () => $("section-" + letter).scrollIntoView({behavior: "smooth"}));
        return button;
    }));
}

// Infinitives whose conjugations are open; kept while the list reloads, e.g. after an edit
const openVerbs = new Set();

function addConjugation(verb, row, open) {
    if (!verb.forms) {
        verb.forms = el("div", {className: "conjugations"});
        const toggle = el("button", {type: "button", className: "toggle forms-toggle"});
        const show = (visible) => {
            verb.forms.hidden = !visible;
            toggle.classList.toggle("active", visible);
            toggle.textContent = `${verb.forms.children.length} ${visible ? "▴" : "▾"}`;
            toggle.title = visible ? "Hide the conjugation" : "Show the conjugation";
        };
        toggle.addEventListener("click", () => {
            const visible = verb.forms.hidden;
            visible ? openVerbs.add(verb.word.id) : openVerbs.delete(verb.word.id);
            show(visible);
        });
        verb.show = show;
        verb.row.querySelector(".row-buttons").prepend(toggle);
        verb.row.after(verb.forms);
    }
    verb.forms.append(row);
    verb.show(open || openVerbs.has(verb.word.id));
}

function wordRow(word) {
    const row = el("div", {className: "word" + (word.conjugation ? " conjugation" : "")});
    const line = el("button", {type: "button", className: "word-line"},
        el("span", {className: "es", textContent: word.word}),
        el("span", {className: "en", textContent: word.english}));
    line.addEventListener("click", () => {
        const open = row.querySelector("form");
        if (open) {
            open.remove();
        } else {
            row.append(editForm(word));
        }
    });
    const buttons = el("div", {className: "row-buttons"});
    const note = el("p", {className: "note", hidden: true});
    const ipa = el("p", {className: "ipa", hidden: true});
    buttons.append(...noteButtons(word.context, note), ...ipaButtons(word.ipa, ipa));
    row.append(line, buttons, note, ipa);
    return row;
}

function editForm(word) {
    const form = $("edit-template").content.firstElementChild.cloneNode(true);
    const wordInput = form.elements.word;
    const {english, context, category, newCategory} = form.elements;
    form.querySelector(".lang-name").textContent = language.name;
    wordInput.value = word.word;
    english.value = word.english;
    context.value = word.context;
    fillCategorySelect(category, word.category);
    wireCategorySelect(category, newCategory);
    addAccentButtons(form.querySelector(".accents"), wordInput);
    form.querySelector(".extras").append(dictionaryLink(word.dictionaryUrl));

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
            await saveVocab("PUT", langUrl("/vocab/" + word.id), {
                word: wordInput.value,
                english: english.value,
                context: context.value,
                ...readCategory(category, newCategory),
            });
            showMessage(`Saved ${wordInput.value.trim()}.`);
            await loadCategories();
            await refreshLanguage();
            await loadWords();
        } catch (error) {
            showMessage(error.message, true);
        }
    });
    form.querySelector("[data-action=cancel]").addEventListener("click", () => form.remove());
    form.querySelector("[data-action=delete]").addEventListener("click", async () => {
        if (!confirm(`Delete ${word.word} = ${word.english}?`)) {
            return;
        }
        try {
            await api("DELETE", langUrl("/vocab/" + word.id));
            showMessage(`Deleted ${word.word}.`);
            await loadCategories();
            await refreshLanguage();
            await loadWords();
        } catch (error) {
            showMessage(error.message, true);
        }
    });
    return form;
}

// ---------- stats ----------

const percent = (part, whole) => whole ? Math.round(100 * part / whole) + "%" : "–";

async function loadStats() {
    const stats = await api("GET", langUrl("/stats"));
    $("stats-who").textContent = `· ${learner} · ${language.name}`;
    $("stats-empty").hidden = stats.answered > 0;
    $("stats-empty").textContent = `No quiz answers yet. Your stats start with your first quiz.`;

    const tile = (value, label) => el("div", {className: "stat"},
        el("strong", {textContent: value}), el("span", {className: "muted", textContent: label}));
    $("stats-numbers").replaceChildren(
        tile(stats.streak, stats.streak === 1 ? "day in a row" : "days in a row"),
        tile(stats.today, "answered today"),
        tile(percent(stats.rightFirstTry, stats.answered), "right on the first try"),
        tile(percent(stats.right, stats.answered), "right in the end"),
        tile(stats.answered, "answered in total"),
        tile(stats.daysPractised, stats.daysPractised === 1 ? "day practised" : "days practised"));

    // one column per day: the right answers at the bottom, the missed ones on top
    const most = Math.max(1, ...stats.lastDays.map((d) => d.answered));
    $("stats-days").replaceChildren(...stats.lastDays.map((d) => {
        const [y, m, day] = d.date.split("-").map(Number);
        const date = new Date(y, m - 1, day);
        const column = el("div", {className: "day", title: `${date.toLocaleDateString()}: ${d.right} of ${d.answered} right`},
            el("div", {className: "bar"},
                el("span", {className: "missed", style: `height: ${100 * (d.answered - d.right) / most}%`}),
                el("span", {className: "right", style: `height: ${100 * d.right / most}%`})),
            el("small", {className: "muted", textContent: date.toLocaleDateString(undefined, {weekday: "narrow"})}));
        return column;
    }));
    $("stats-days").setAttribute("aria-label", `Answers per day, the last ${stats.lastDays.length} days`);

    const total = stats.mastered + stats.practising + stats.notPractised;
    const parts = [["mastered", stats.mastered, "mastered (right on the first try 3 times in a row)"],
        ["practising", stats.practising, "practising"], ["new", stats.notPractised, "not practised yet"]];
    $("stats-progress").replaceChildren(...parts.filter(([, n]) => n > 0).map(([kind, n]) =>
        el("span", {className: kind, style: `flex: ${n}`})));
    $("stats-legend").replaceChildren(...parts.map(([kind, n, label]) =>
        el("li", {}, el("span", {className: "swatch " + kind}), `${n} ${label}`)));
    $("stats-progress").setAttribute("aria-label", `${stats.mastered} of ${total} words mastered`);

    $("stats-practise-card").hidden = stats.toPractise.length === 0;
    $("stats-practise").replaceChildren(...stats.toPractise.map((w) => el("li", {},
        el("strong", {textContent: w.word}), ` = ${w.english} `,
        el("span", {className: "muted", textContent: `· ${w.firstTry} of ${w.tries} right on the first try`}))));
}

// ---------- start ----------

for (const button of document.querySelectorAll(".tabs button")) {
    button.addEventListener("click", () => showView(button.dataset.view));
}

$("quiz-start").addEventListener("click", startQuiz);
$("quiz-again").addEventListener("click", () => {
    $("quiz-summary").hidden = true;
    $("quiz-setup").hidden = false;
});
$("quiz-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (!$("quiz-check").hidden) {
        checkAnswer().catch((error) => showMessage(error.message, true));
    }
});
$("quiz-next").addEventListener("click", nextQuestion);
// keep the keyboard open on the phone
$("quiz-hint-button").addEventListener("mousedown", (e) => e.preventDefault());
$("quiz-hint-button").addEventListener("click", () => showHint().catch((error) => showMessage(error.message, true)));
$("quiz-stop").addEventListener("click", stopQuiz);

$("add-form").addEventListener("submit", addVocab);
wireCategorySelect($("add-category"), $("add-new-category"));

$("learner").addEventListener("change", () => {
    const actions = {[ADD_LEARNER]: addLearner, [RENAME_LEARNER]: renameLearner, [DELETE_LEARNER]: deleteLearner};
    const name = $("learner").value;
    (actions[name] ? actions[name]() : switchLearner(name)).catch((error) => showMessage(error.message, true));
});

$("language").addEventListener("change", () => {
    const name = $("language").value;
    (name === ADD_LANGUAGE ? addLanguage() : switchLanguage(name)).catch((error) => showMessage(error.message, true));
});

$("words-category").addEventListener("change", loadWords);
$("words-search").addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(loadWords, 200);
});

window.addEventListener("hashchange", () => showView(location.hash.slice(1)));

loadLearners()
    .then(() => showView(location.hash.slice(1)))
    .catch((error) => showMessage(error.message, true));
