// All quiz checking, sorting and saving happens in Java (see VocabController);
// this file only shows the screens and talks to /api.

const ACCENTS = ["á", "é", "í", "ó", "ú", "ñ", "ü", "¿", "¡"];
const NEW_CATEGORY = "__new__";

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

function addAccentButtons(container, input) {
    container.replaceChildren(...ACCENTS.map((letter) => {
        const button = el("button", {type: "button", textContent: letter});
        // keep the keyboard open on the phone
        button.addEventListener("mousedown", (e) => e.preventDefault());
        button.addEventListener("click", () => {
            const start = input.selectionStart ?? input.value.length;
            const end = input.selectionEnd ?? input.value.length;
            input.setRangeText(letter, start, end, "end");
            input.focus();
        });
        return button;
    }));
}

// ---------- pronunciation ----------

// Button that shows the IPA (worked out in SpanishIpa.java) in the given element, and hides it again
function ipaButton(ipa, target) {
    const button = el("button", {type: "button", className: "ipa-toggle", textContent: "IPA", title: "Show pronunciation"});
    button.setAttribute("aria-expanded", "false");
    button.addEventListener("click", (event) => {
        event.stopPropagation();
        const show = target.hidden;
        target.textContent = ipa;
        target.hidden = !show;
        button.classList.toggle("active", show);
        button.setAttribute("aria-expanded", String(show));
    });
    return button;
}

// SpanishDict's own page for the word, with their recording, examples and conjugations
function spanishDictLink(spanish) {
    const word = spanish.split("/")[0].replace(/[¡!¿?.…]/g, "").trim();
    return el("a", {
        href: "https://www.spanishdict.com/translate/" + encodeURIComponent(word),
        target: "_blank",
        rel: "noopener",
        className: "dict-link",
        textContent: "SpanishDict ↗",
    });
}

// ---------- categories ----------

async function loadCategories() {
    categories = await api("GET", "/api/categories");
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
        $("add-spanish").focus();
    }
}

// ---------- quiz ----------

const quiz = {questions: [], index: 0, attempt: 1, correct: 0, asked: 0};

async function startQuiz() {
    quiz.questions = await api("GET", "/api/quiz?category=" + encodeURIComponent($("quiz-category").value));
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
    $("quiz-direction").textContent = question.askInSpanish ? "In Spanish:" : "In English:";
    $("quiz-prompt").textContent = question.prompt;
    // promptIpa only comes with Spanish prompts
    $("quiz-prompt-ipa").hidden = true;
    $("quiz-prompt-ipa-toggle").replaceChildren(
        ...(question.promptIpa ? [ipaButton(question.promptIpa, $("quiz-prompt-ipa"))] : []));
    $("quiz-extras").hidden = true;
    $("quiz-answer-ipa").hidden = true;
    $("quiz-accents").hidden = !question.askInSpanish;
    $("quiz-feedback").textContent = "";
    $("quiz-feedback").className = "feedback";
    $("quiz-full-answer").hidden = true;
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
    const result = await api("POST", "/api/quiz/check", {
        id: question.id, askInSpanish: question.askInSpanish, answer, attempt: quiz.attempt,
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
        const spanish = question.askInSpanish ? result.answer : question.prompt;
        $("quiz-extras").replaceChildren(
            ipaButton(result.spanishIpa, $("quiz-answer-ipa")), spanishDictLink(spanish));
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
        spanish: $("add-spanish").value,
        english: $("add-english").value,
        ...readCategory($("add-category"), $("add-new-category")),
    };
    try {
        const saved = await saveVocab("POST", "/api/vocab", vocab);
        $("add-recent").hidden = false;
        $("add-recent-list").prepend(el("li", {textContent: `${saved.spanish} = ${saved.english} [${saved.category}]`}));
        // keep the category, so a whole verb can be added in a row
        $("add-spanish").value = "";
        $("add-english").value = "";
        $("add-new-category").value = "";
        $("add-new-category").hidden = true;
        await loadCategories();
        fillCategorySelect($("add-category"), saved.category);
        $("add-spanish").focus();
    } catch (error) {
        showMessage(error.message, true);
    }
}

// ---------- words ----------

let searchTimer;

async function loadWords() {
    const category = $("words-category").value;
    const query = $("words-search").value.trim();
    const words = await api("GET", `/api/vocab?category=${encodeURIComponent(category)}&q=${encodeURIComponent(query)}`);
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
        el("span", {className: "es", textContent: word.spanish}),
        el("span", {className: "en", textContent: word.english}));
    line.addEventListener("click", () => {
        const open = row.querySelector("form");
        if (open) {
            open.remove();
        } else {
            row.append(editForm(word));
        }
    });
    const ipa = el("p", {className: "ipa", hidden: true});
    row.append(line, ipaButton(word.ipa, ipa), ipa);
    return row;
}

function editForm(word) {
    const form = $("edit-template").content.firstElementChild.cloneNode(true);
    const {spanish, english, category, newCategory} = form.elements;
    spanish.value = word.spanish;
    english.value = word.english;
    fillCategorySelect(category, word.category);
    wireCategorySelect(category, newCategory);
    addAccentButtons(form.querySelector(".accents"), spanish);
    form.querySelector(".extras").append(spanishDictLink(word.spanish));

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
            await saveVocab("PUT", "/api/vocab/" + word.id, {
                spanish: spanish.value,
                english: english.value,
                ...readCategory(category, newCategory),
            });
            showMessage(`Saved ${spanish.value.trim()}.`);
            await loadCategories();
            await loadWords();
        } catch (error) {
            showMessage(error.message, true);
        }
    });
    form.querySelector("[data-action=cancel]").addEventListener("click", () => form.remove());
    form.querySelector("[data-action=delete]").addEventListener("click", async () => {
        if (!confirm(`Delete ${word.spanish} = ${word.english}?`)) {
            return;
        }
        try {
            await api("DELETE", "/api/vocab/" + word.id);
            showMessage(`Deleted ${word.spanish}.`);
            await loadCategories();
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
addAccentButtons($("quiz-accents"), $("quiz-answer"));

$("add-form").addEventListener("submit", addVocab);
wireCategorySelect($("add-category"), $("add-new-category"));
addAccentButtons(document.querySelector("#add-form .accents"), $("add-spanish"));

$("words-category").addEventListener("change", loadWords);
$("words-search").addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(loadWords, 200);
});

window.addEventListener("hashchange", () => showView(location.hash.slice(1)));

loadCategories()
    .then(() => showView(location.hash.slice(1)))
    .catch((error) => showMessage(error.message, true));
