// All quiz checking, sorting and saving happens in Java (see VocabController);
// this file only shows the screens and talks to /api.

const NEW_CATEGORY = "__new__";
const ADD_LANGUAGE = "__add__";

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

// URL of something in the current language, e.g. /api/languages/Spanish/vocab
function langUrl(path) {
    return `/api/languages/${encodeURIComponent(language.name)}${path}`;
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

// ---------- languages ----------

function savedLanguageName() {
    try {
        return localStorage.getItem("language");
    } catch {
        return null; // private browsing: just start with the first language
    }
}

async function loadLanguages(selectName) {
    languages = await api("GET", "/api/languages");
    const select = $("language");
    select.replaceChildren(
        ...languages.map((l) => el("option", {value: l.name, textContent: l.name})),
        el("option", {value: ADD_LANGUAGE, textContent: "Add language…"}));
    const wanted = languages.find((l) => l.name === selectName) || languages.find((l) => l.name === savedLanguageName())
        || languages[0];
    if (!wanted) {
        select.value = ADD_LANGUAGE;
        showMessage("Add the first language you want to learn.");
        return;
    }
    select.value = wanted.name;
    await switchLanguage(wanted.name);
}

async function switchLanguage(name) {
    language = languages.find((l) => l.name === name);
    try {
        localStorage.setItem("language", name);
    } catch {
        // only a convenience
    }
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
}

// Word count and special letters change when words are added, so fetch the language again
async function refreshLanguage() {
    languages = await api("GET", "/api/languages");
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
        const added = await api("POST", "/api/languages", {name});
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

// Sends the vocab; if the server says the category is new, asks first and sends again
async function saveVocab(method, url, vocab) {
    try {
        return await api(method, url, vocab);
    } catch (error) {
        if (error.status === 409 && confirm(`Create new category '${error.data.newCategory}'?`)) {
            return api(method, url, {...vocab, newCategory: true});
        }
        throw error;
    }
}

// ---------- tabs ----------

function showView(name) {
    if (!["quiz", "add", "words"].includes(name)) {
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
    if (name === "add") {
        $("add-word").focus();
    }
}

// ---------- quiz ----------

const quiz = {questions: [], index: 0, attempt: 1, correct: 0, asked: 0};

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
    $("quiz-check").hidden = false;
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
    });
    const feedback = $("quiz-feedback");
    feedback.textContent = result.feedback;
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
        $("quiz-next").hidden = false;
        $("quiz-next").focus();
    } else {
        quiz.attempt++;
        $("quiz-answer").select();
    }
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
    for (const word of words) {
        if (letters[letters.length - 1] !== word.section) {
            letters.push(word.section);
            list.append(el("h3", {className: "section-header", id: "section-" + word.section, textContent: word.section}));
        }
        list.append(wordRow(word));
    }
    $("words-letters").replaceChildren(...letters.map((letter) => {
        const button = el("button", {type: "button", textContent: letter});
        button.addEventListener("click", () => $("section-" + letter).scrollIntoView({behavior: "smooth"}));
        return button;
    }));
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
$("quiz-stop").addEventListener("click", stopQuiz);

$("add-form").addEventListener("submit", addVocab);
wireCategorySelect($("add-category"), $("add-new-category"));

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

loadLanguages()
    .then(() => showView(location.hash.slice(1)))
    .catch((error) => showMessage(error.message, true));
