// All quiz checking, sorting and saving happens in Java (see VocabController);
// this file only shows the screens and talks to /api.

const NEW_CATEGORY = "__new__";
const ADD_LANGUAGE = "__add__";
const ADD_LEARNER = "__add__";
const RENAME_LEARNER = "__rename__";
const DELETE_LEARNER = "__delete__";
const COPY_VOCAB = "__copy__";
// "Change translation…" and "Delete list…" in the language menu
const CHANGE_TRANSLATION = "__translation__";
const DELETE_LIST = "__delete-list__";
// "Refresh vocab" in the quiz's category menu
const REFRESH = "__refresh__";
let refreshDays = 14;

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

// Buttons for the language's letters that a normal keyboard lacks (á, ñ, ¿, ơ, tone marks …), small letters
// only: a letter is typed as a capital where one is certain (see needsCapital), or once after ⇧.
// letters: by default the ones of the language you learn. slash: also a "/" button, for the alternatives when
// saving vocab ("hola / buenas"). isSentence: whether the field holds sentences (its category), so its start
// is a sentence start too.
function addAccentButtons(container, input, letters = language?.specialLetters ?? [], slash = false,
                          isSentence = () => false) {
    container.hidden = letters.length === 0 && !slash;
    let shift = false;
    const letterButtons = [];
    const setShift = (on) => {
        shift = on;
        shiftButton?.classList.toggle("active", on);
        for (const [button, letter] of letterButtons) {
            button.textContent = on ? capital(letter) : letter;
        }
    };
    container.replaceChildren(...letters.map((letter) => {
        const isMark = /\p{M}/u.test(letter);
        // a mark on its own is invisible, so the button shows it on an "a" (it still only adds the mark)
        const button = el("button", {type: "button", textContent: isMark ? ("a" + letter).normalize("NFC") : letter,
            title: isMark ? `${MARK_NAMES[letter] ?? "mark"}: adds this mark to the letter before` : letter});
        button.classList.toggle("mark", isMark);
        if (!isMark && capital(letter) !== letter) {
            letterButtons.push([button, letter]);
        }
        // keep the keyboard open on the phone
        button.addEventListener("mousedown", (e) => e.preventDefault());
        button.addEventListener("click", () => {
            const start = input.selectionStart ?? input.value.length;
            const end = input.selectionEnd ?? input.value.length;
            const big = !isMark && (shift || needsCapital(input.value.slice(0, start), isSentence()));
            input.setRangeText(big ? capital(letter) : letter, start, end, "end");
            setShift(false);
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
    // ⇧ for a row with letters or tone marks (not for ¿ ¡ alone); it also works for the next letter typed on the
    // keyboard, so Vietnamese, with only tone buttons, has it too
    const shiftButton = letterButtons.length === 0 && !letters.some((l) => /\p{M}/u.test(l)) ? null
        : el("button", {type: "button", textContent: "⇧", className: "shift", title: "The next letter as a capital"});
    if (shiftButton) {
        // the row is built again for every language: replace the keyboard listener of the last one
        input.removeEventListener("input", shiftListeners.get(input));
        const keyboard = (event) => {
            if (shift && event.inputType === "insertText" && /^\p{L}$/u.test(event.data ?? "")) {
                const end = input.selectionStart;
                input.setRangeText(capital(event.data), end - 1, end, "end");
                setShift(false);
            }
        };
        shiftListeners.set(input, keyboard);
        input.addEventListener("input", keyboard);
        shiftButton.addEventListener("mousedown", (e) => e.preventDefault());
        shiftButton.addEventListener("click", () => {
            setShift(!shift);
            input.focus();
        });
        container.append(shiftButton);
    }
    if (slash) {
        const button = el("button", {type: "button", textContent: "/", title: "Another alternative: adds \" / \""});
        button.addEventListener("mousedown", (e) => e.preventDefault());
        button.addEventListener("click", () => {
            const start = input.selectionStart ?? input.value.length;
            const end = input.selectionEnd ?? input.value.length;
            // " / " with a space on each side, but no second space after one already typed
            const spaceBefore = start === 0 || input.value[start - 1] === " ";
            input.setRangeText(spaceBefore ? "/ " : " / ", start, end, "end");
            input.focus();
        });
        container.append(button);
    }
}

// each field's keyboard listener for ⇧, so a new row replaces the old one
const shiftListeners = new WeakMap();

// "é" -> "É"; ß stays ß, as its capital is two letters
function capital(letter) {
    const big = letter.toUpperCase();
    return big.length === letter.length ? big : letter;
}

// Whether the letter typed after this text starts a sentence, so it is a capital for sure: after . ? ! and a
// space, right after an opening ¿ or ¡ there, or at the start of a sentence field (or of an alternative in it).
// A single word at the start ("árbol" or "África"?) is left to ⇧.
function needsCapital(before, isSentence) {
    const start = isSentence ? /(^|[.?!…]\s+|\/\s*)[¿¡]?$/ : /(^[¿¡]|[.?!…]\s+[¿¡]?|\/\s*[¿¡])$/;
    return start.test(before);
}

const addFormHoldsSentences = () => isSentenceCategory(readCategory($("add-category"), $("add-new-category")).category);

// The categories that hold sentences (as in SpellChecker)
const SENTENCE_CATEGORIES = ["sentences", "sentence", "sätze", "satz", "frases", "oraciones"];
const isSentenceCategory = (name) => SENTENCE_CATEGORIES.includes((name ?? "").trim().toLowerCase());

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
        if (learnerNames.length > 1) {
            options.push(el("option", {value: COPY_VOCAB, textContent: `Copy vocab to ${learner}…`}));
        }
        options.push(el("option", {value: RENAME_LEARNER, textContent: `Rename ${learner}…`}),
            el("option", {value: DELETE_LEARNER, textContent: `Delete ${learner}…`}));
    }
    if (!learner) {
        // as for languages: "Add learner…" mustn't be selected, or picking it does nothing
        options.unshift(el("option", {value: "", textContent: "Who's learning?", disabled: true}));
    }
    $("learner").replaceChildren(...options);
    $("learner").value = learner ?? "";
}

async function switchLearner(name) {
    learner = name;
    saveSetting("learner", name);
    fillLearnerSelect();
    await loadLanguages();
}

// Copies another learner's words into this learner's list (see VocabController.copyVocab)
async function copyVocab() {
    $("learner").value = learner;
    const others = learnerNames.filter((name) => name !== learner);
    const fromAnswer = prompt(`Copy vocab to ${learner} from which learner?\n\n${others.join(", ")}`,
        others.length === 1 ? others[0] : "");
    if (!fromAnswer) {
        return;
    }
    const from = others.find((name) => name.toLowerCase() === fromAnswer.trim().toLowerCase());
    if (!from) {
        showMessage(`There's no other learner called '${fromAnswer.trim()}'.`, true);
        return;
    }
    const theirs = await api("GET", "/api/learners/" + encodeURIComponent(from) + "/languages");
    if (theirs.length === 0) {
        showMessage(`${from} has no vocab yet.`, true);
        return;
    }
    let chosen = theirs;
    if (theirs.length > 1) {
        const list = theirs.map((l) => `${l.name} (${l.count})`).join(", ");
        const answer = prompt(`Which language? ${list}\n\nOr "all" for every language.`, "all");
        if (!answer) {
            return;
        }
        if (answer.trim().toLowerCase() !== "all") {
            chosen = theirs.filter((l) => l.name.toLowerCase() === answer.trim().toLowerCase());
            if (chosen.length === 0) {
                showMessage(`${from} has no '${answer.trim()}'.`, true);
                return;
            }
        }
    }
    // which list each one goes into: the one with the same name, or a new one; and whether this learner
    // already learns that language as another pair ("Spanisch → Deutsch" next to a copied "Spanish → English")
    const pair = (l) => `"${l.name} → ${l.translation}"`;
    const lines = chosen.map((l) => {
        const target = languages.find((t) => t.name.toLowerCase() === l.name.toLowerCase());
        if (target) {
            return `• ${l.count} words of ${pair(l)} into ${learner}'s list ${pair(target)}`;
        }
        const other = languages.find((t) => t.englishName === l.englishName);
        return `• ${l.count} words of ${pair(l)} into a NEW list ${pair(l)} for ${learner}` + (other
            ? `\n   (${learner} already learns ${l.englishName} as ${pair(other)}. These words are translated into `
              + `${l.translation}, so they get their own list.)` : "");
    });
    if (!confirm(`Copy from ${from} to ${learner}:\n\n${lines.join("\n")}\n\nWords already in the list are skipped, `
        + `so nothing is replaced. ${from}'s quiz results are not copied.`)) {
        return;
    }
    const result = await api("POST", learnerUrl("/copy"),
        {from, language: chosen.length === theirs.length && theirs.length > 1 ? "" : chosen[0].name});
    // show the list the words went into
    await loadLanguages(result.languages[0]);
    const into = result.languages.map((name) => pair(languages.find((l) => l.name === name))).join(" and ");
    showMessage(`Copied ${result.copied} ${result.copied === 1 ? "word" : "words"} from ${from} into ${into}` +
        (result.skipped ? `. ${result.skipped} skipped: already in the list.` : "."));
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
        $("learner").value = learner ?? "";
        return;
    }
    try {
        const added = await api("POST", "/api/learners", {name});
        await loadLearners(added.name);
        showMessage(`Hi ${added.name}! Add the first language you want to learn.`);
    } catch (error) {
        $("learner").value = learner ?? "";
        showMessage(error.message, true);
    }
}

// ---------- (s and (p ----------

// Typing "(s" fills in "(singular)" and "(p" "(plural)", e.g. "you have (s" -> "you have (singular)".
// Only in English, where "you have" is both; Spanish and German forms differ anyway ("tienes", "tenéis").
// Backspace right after it goes back to what was typed ("(sth)"). sideOf(input) = the language of that field.
function addBracketShortcuts(input, sideOf) {
    let filled = null;
    input.addEventListener("input", (event) => {
        filled = null;
        const letter = event.data?.toLowerCase();
        if (event.inputType !== "insertText" || (letter !== "s" && letter !== "p") || !isEnglish(sideOf(input))) {
            return;
        }
        const end = input.selectionStart;
        const before = input.value.slice(0, end);
        // only at the start of a bracket: "(s" after a space, a "/" or nothing
        if (!/(^|[\s/])\([sp]$/i.test(before)) {
            return;
        }
        const full = {s: "(singular)", p: "(plural)"}[letter];
        input.setRangeText(full, end - 2, end, "end");
        filled = {typed: before.slice(-2), full, at: end - 2};
    });
    input.addEventListener("keydown", (event) => {
        if (event.key === "Backspace" && filled && input.selectionStart === filled.at + filled.full.length
            && input.value.slice(filled.at, input.selectionStart) === filled.full) {
            event.preventDefault();
            input.setRangeText(filled.typed, filled.at, input.selectionStart, "end");
            filled = null;
        }
    });
}

const isEnglish = (name) => ["english", "englisch", "inglés", "ingles"].includes((name ?? "").toLowerCase());

// ---------- languages ----------

async function loadLanguages(selectName) {
    languages = await api("GET", learnerUrl("/languages"));
    const select = $("language");
    // each word list is a pair: "Spanisch → German"
    select.replaceChildren(
        ...languages.map((l) => el("option", {value: l.name, textContent: `${l.name} → ${l.translation}`})),
        el("option", {value: ADD_LANGUAGE, textContent: "Add language…"}));
    if (languages.length > 0) {
        select.append(el("option", {value: CHANGE_TRANSLATION, textContent: "Change translation…"}),
            el("option", {value: DELETE_LIST, textContent: "Delete list…"}));
    }
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
        // "Add language…" mustn't be the selected option, or picking it is no change and does nothing
        select.prepend(el("option", {value: "", textContent: "No language yet", disabled: true}));
        select.value = "";
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
    showTranslationLanguage();
    addAccentButtons($("quiz-accents"), $("quiz-answer"));
    addAccentButtons(document.querySelector("#add-form .accents"), $("add-word"), language.specialLetters, true,
        addFormHoldsSentences);
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

// The other side is English or, per learner, German: its name on the labels and its letter buttons
function showTranslationLanguage() {
    for (const label of document.querySelectorAll(".translation-name")) {
        label.textContent = language.translation;
    }
    $("bracket-hint").hidden = !isEnglish(language.translation);
    addAccentButtons(document.querySelector("#add-form .translation-accents"), $("add-english"),
        language.translationLetters, true, addFormHoldsSentences);
}

// Word count and special letters change when words are added, so fetch the language again
async function refreshLanguage() {
    languages = await api("GET", learnerUrl("/languages"));
    language = languages.find((l) => l.name === language.name) || language;
    showTranslationLanguage();
    addAccentButtons($("quiz-accents"), $("quiz-answer"));
    addAccentButtons(document.querySelector("#add-form .accents"), $("add-word"), language.specialLetters, true,
        addFormHoldsSentences);
}

// The other side of the current list: spell check, letters, articles and short forms follow it
async function changeTranslation() {
    $("language").value = language.name;
    const name = prompt(`Into which language do you translate ${language.name}? (e.g. English, German)`,
        language.translation);
    if (!name || name.trim().toLowerCase() === language.translation.toLowerCase()) {
        return;
    }
    const changed = await api("PUT", langUrl("/translation"), {name});
    await loadLanguages(language.name);
    showMessage(`${changed.name} is now translated into ${changed.translation}.`);
}

// Deletes the current list; the NAS keeps it in vocab/.deleted, so it can be put back
async function deleteList() {
    $("language").value = language.name;
    if (!confirm(`Delete ${learner}'s list "${language.name} → ${language.translation}" with its ${language.count} `
        + `words and quiz results?\n\nIt's kept in the .deleted folder on the NAS and can be put back.`)) {
        return;
    }
    const deleted = `${language.name} → ${language.translation}`;
    await api("DELETE", langUrl(""));
    await loadLanguages();
    showMessage(`Deleted "${deleted}".`);
}

// The language the learner translates into most, offered for a new list
function usualTranslation() {
    const counts = {};
    for (const l of languages) {
        counts[l.translation] = (counts[l.translation] ?? 0) + 1;
    }
    return Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] ?? "English";
}

async function addLanguage() {
    const name = prompt("Which language do you want to learn? (e.g. Spanish, English, German)");
    if (!name) {
        $("language").value = language?.name ?? "";
        return;
    }
    const translation = prompt(`Into which language do you translate ${name.trim()}? (e.g. English, German)`,
        usualTranslation());
    if (!translation) {
        $("language").value = language?.name ?? "";
        return;
    }
    try {
        const added = await api("POST", learnerUrl("/languages"), {name, translation});
        await loadLanguages(added.name);
        showMessage(`${added.name} → ${added.translation} added. Start by adding some vocab.`);
    } catch (error) {
        $("language").value = language?.name ?? "";
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
    // the quiz also offers the words to refresh (see QuizStats.toRefresh)
    const refresh = await api("GET", langUrl("/quiz/refresh"));
    const previous = $("quiz-category").dataset.picked;
    $("quiz-category").options[0].after(el("option", {value: REFRESH, textContent: `Refresh vocab (${refresh.count})`,
        title: `Words right on the first try lately that weren't asked for ${refresh.days} days`}));
    refreshDays = refresh.days;
    if (previous === REFRESH) {
        $("quiz-category").value = REFRESH;
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

// A comma instead of "/" ("gut, schön") is never saved (the server refuses it too): asks whether to make it
// "gut / schön", else nothing is saved. Not in sentences ("Oye, ¿qué haces?"), where a comma belongs.
function commasAsAlternatives(vocab) {
    const sentence = (text) => isSentenceCategory(vocab.category) || /[.?!…]\s*$/.test(text.trim());
    const fixed = {...vocab};
    for (const [field, side] of [["word", language.name], ["english", language.translation]]) {
        const text = vocab[field] ?? "";
        if (!text.includes(",") || sentence(text)) {
            continue;
        }
        const withSlash = text.split(/\s*,\s*/).filter((part) => part.trim()).join(" / ");
        if (!confirm(`'${text.trim()}' (${side}) has a comma. Alternatives are separated with / in the app.\n\n`
            + `OK: save as '${withSlash}'\nCancel: don't save, to change it`)) {
            throw new Error("Not saved: use / instead of a comma for alternatives.");
        }
        fixed[field] = withSlash;
    }
    return fixed;
}

// Sends the vocab; if the server says the category is new, it is like a saved entry apart from a typo, or a word
// may be misspelled, asks first and sends again (each can still be followed by the next). Vocab that is already
// saved is never saved again.
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
            const line = {
                spelling: (m) => `'${m.word}' isn't in the ${m.language} dictionary` +
                    (m.suggestions.length ? `. Did you mean: ${m.suggestions.join(", ")}?` : "."),
                capital: (m) => `'${m.word}' is written with a capital letter in ${m.language}: ${m.suggestions[0]}`,
                sentence: (m) => `A sentence starts with a capital letter (${m.language}): ${m.suggestions[0]} …`,
            };
            const list = error.data.spelling.map((m) => "• " + (line[m.kind] ?? line.spelling)(m)).join("\n");
            if (confirm(`${error.message}\n\n${list}\n\nSave anyway?`)) {
                return saveVocab(method, url, {...vocab, spellingChecked: true});
            }
        } else if (error.data.similar) {
            // the same apart from a typo or a missing accent counts as a duplicate: never both, so which one stays?
            const saved = error.data.similar[0];
            if (confirm(`${error.message}\nWhich one do you want to keep?\n\n`
                + `Saved: ${saved.word} = ${saved.english}\nNew:   ${vocab.word.trim()} = ${vocab.english.trim()}\n\n`
                + `OK: keep the new one (it replaces the saved one)\nCancel: keep the saved one`)) {
                return saveVocab(method, url, {...vocab, replaces: saved.id});
            }
            throw new Error(`Not saved: kept "${saved.word} = ${saved.english}".`);
        } else if (error.data.duplicates) {
            // never saved twice: say which entry it is, and keep the input for a change
            const list = error.data.duplicates.map((d) => `• ${d.word} = ${d.english} [${d.category}]`).join("\n");
            alert(`${error.message}\n\n${list}\n\nIt has the same word and translation. Change one of them if you meant something else.`);
            throw new Error(error.message);
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
    const refresh = $("quiz-category").value === REFRESH;
    quiz.questions = await api("GET", langUrl(refresh ? "/quiz?refresh=true"
        : "/quiz?category=" + encodeURIComponent($("quiz-category").value)));
    if (quiz.questions.length === 0) {
        showMessage(refresh ? `Nothing to refresh right now. Words come here once they were right on the first try `
            + `lately and weren't asked for ${refreshDays} days.` : "No vocab saved yet. Add some first.");
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
    $("quiz-direction").textContent = `In ${question.answerInLanguage ? language.name : language.translation}:`;
    $("quiz-prompt").textContent = question.prompt;
    // promptIpa only comes with prompts in the language
    $("quiz-prompt-ipa").hidden = true;
    $("quiz-prompt-ipa-toggle").replaceChildren(...ipaButtons(question.promptIpa, $("quiz-prompt-ipa")));
    $("quiz-extras").hidden = true;
    $("quiz-answer-ipa").hidden = true;
    // the letter buttons of the side to answer in (hidden when it has none)
    addAccentButtons($("quiz-accents"), $("quiz-answer"),
        question.answerInLanguage ? language.specialLetters : language.translationLetters);
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

// One save at a time per form: its button is locked and says "Saving…" until the app answers, so a second
// press (or Enter) while it waits does nothing and the word isn't added twice
async function whileSaving(form, save) {
    if (form.dataset.saving) {
        return;
    }
    const button = form.querySelector("button[type=submit]");
    const label = button.textContent;
    form.dataset.saving = "yes";
    button.disabled = true;
    button.textContent = "Saving…";
    try {
        await save();
    } finally {
        delete form.dataset.saving;
        button.disabled = false;
        button.textContent = label;
    }
}

async function addVocab(event) {
    event.preventDefault();
    await whileSaving($("add-form"), saveNewVocab);
}

async function saveNewVocab() {
    const vocab = {
        word: $("add-word").value,
        english: $("add-english").value,
        context: $("add-context").value,
        ...readCategory($("add-category"), $("add-new-category")),
    };
    try {
        const saved = await saveVocab("POST", langUrl("/vocab"), commasAsAlternatives(vocab));
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
    form.querySelector(".translation-name").textContent = language.translation;
    wordInput.value = word.word;
    english.value = word.english;
    context.value = word.context;
    fillCategorySelect(category, word.category);
    wireCategorySelect(category, newCategory);
    const holdsSentences = () => isSentenceCategory(readCategory(category, newCategory).category);
    addAccentButtons(form.querySelector(".accents"), wordInput, language.specialLetters, true, holdsSentences);
    addAccentButtons(form.querySelector(".translation-accents"), english, language.translationLetters, true,
        holdsSentences);
    addBracketShortcuts(wordInput, () => language.name);
    addBracketShortcuts(english, () => language.translation);
    form.querySelector(".extras").append(dictionaryLink(word.dictionaryUrl));

    form.addEventListener("submit", (event) => {
        event.preventDefault();
        whileSaving(form, saveEdit);
    });
    async function saveEdit() {
        try {
            await saveVocab("PUT", langUrl("/vocab/" + word.id), commasAsAlternatives({
                word: wordInput.value,
                english: english.value,
                context: context.value,
                ...readCategory(category, newCategory),
            }));
            showMessage(`Saved ${wordInput.value.trim()}.`);
            await loadCategories();
            await refreshLanguage();
            await loadWords();
        } catch (error) {
            showMessage(error.message, true);
        }
    }
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

// the stats on screen, so picking another chart period needs no new request
let statsShown = null;

const percent = (part, whole) => whole ? Math.round(100 * part / whole) + "%" : "–";

async function loadStats() {
    renderStats(await api("GET", langUrl("/stats")));
}

function renderStats(stats) {
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

    // Today, 1 month, 6 months and 1 year side by side
    const rows = [["Answered", (p) => p.answered], ["Right on the first try", (p) => percent(p.rightFirstTry, p.answered)],
        ["Right in the end", (p) => percent(p.right, p.answered)], ["Days practised", (p) => p.days],
        ["Words practised", (p) => p.words]];
    $("stats-periods").replaceChildren(
        el("thead", {}, el("tr", {}, el("th"), ...stats.periods.map((p) => el("th", {scope: "col", textContent: p.name})))),
        el("tbody", {}, ...rows.map(([label, value]) => el("tr", {},
            el("th", {scope: "row", textContent: label}),
            ...stats.periods.map((p) => el("td", {textContent: value(p)}))))));

    // the chart of the period picked above it (remembered on this device)
    statsShown = stats;
    const picked = stats.charts.find((c) => c.name === loadSetting("chart")) ?? stats.charts[1];
    $("stats-chart-periods").replaceChildren(...stats.charts.map((chart) => {
        const button = el("button", {type: "button", textContent: chart.name, className: chart === picked ? "active" : ""});
        button.setAttribute("aria-pressed", chart === picked);
        button.addEventListener("click", () => {
            saveSetting("chart", chart.name);
            renderStats(statsShown);
        });
        return button;
    }));
    drawChart(picked);

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

// One column per hour, day, week or month: the right answers at the bottom, the others on top.
// Only some columns get a label, so they never run into each other.
function drawChart(chart) {
    const bars = chart.bars;
    const most = Math.max(1, ...bars.map((b) => b.answered));
    const date = (bar) => new Date(bar.start);
    const every = {hour: 6, day: 7, week: 4, month: 1}[chart.unit];
    const label = {
        hour: (d) => String(d.getHours()),
        day: (d) => String(d.getDate()),
        week: (d) => d.toLocaleDateString(undefined, {day: "numeric", month: "short"}),
        month: (d) => d.toLocaleDateString(undefined, {month: "narrow"}),
    }[chart.unit];
    const when = {
        hour: (d) => `${d.getHours()}:00–${d.getHours() + 1}:00`,
        day: (d) => d.toLocaleDateString(undefined, {weekday: "short", day: "numeric", month: "short"}),
        week: (d) => `Week from ${d.toLocaleDateString(undefined, {day: "numeric", month: "short"})}`,
        month: (d) => d.toLocaleDateString(undefined, {month: "long", year: "numeric"}),
    }[chart.unit];
    const chartBox = $("stats-days");
    // minmax(0, 1fr): every column equally wide, also the ones with a label
    chartBox.style.gridTemplateColumns = `repeat(${bars.length}, minmax(0, 1fr))`;
    chartBox.classList.toggle("many", bars.length > 14);
    chartBox.replaceChildren(...bars.map((bar, i) => {
        const d = date(bar);
        // hours from midnight (0, 6, 12, 18); the others back from the newest column, so it always has one
        const labelled = (chart.unit === "hour" ? i : bars.length - 1 - i) % every === 0;
        return el("div", {className: "day", title: `${when(d)}: ${bar.right} of ${bar.answered} right`},
            el("div", {className: "bar"},
                el("span", {className: "missed", style: `height: ${100 * (bar.answered - bar.right) / most}%`}),
                el("span", {className: "right", style: `height: ${100 * bar.right / most}%`})),
            el("small", {className: "muted", textContent: labelled ? label(d) : ""}));
    }));
    const total = bars.reduce((sum, b) => sum + b.answered, 0);
    chartBox.setAttribute("aria-label", `${total} answers, ${chart.name}, one column per ${chart.unit}`);
}

// ---------- start ----------

for (const button of document.querySelectorAll(".tabs button")) {
    button.addEventListener("click", () => showView(button.dataset.view));
}

$("quiz-start").addEventListener("click", startQuiz);
$("quiz-again").addEventListener("click", () => {
    $("quiz-summary").hidden = true;
    $("quiz-setup").hidden = false;
    // the quiz just played changes what there is to refresh
    loadCategories().catch((error) => showMessage(error.message, true));
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
addBracketShortcuts($("add-word"), () => language?.name);
addBracketShortcuts($("add-english"), () => language?.translation);

$("learner").addEventListener("change", () => {
    const actions = {[ADD_LEARNER]: addLearner, [COPY_VOCAB]: copyVocab, [RENAME_LEARNER]: renameLearner,
        [DELETE_LEARNER]: deleteLearner};
    const name = $("learner").value;
    (actions[name] ? actions[name]() : switchLearner(name)).catch((error) => showMessage(error.message, true));
});

$("language").addEventListener("change", () => {
    const name = $("language").value;
    const actions = {[ADD_LANGUAGE]: addLanguage, [CHANGE_TRANSLATION]: changeTranslation, [DELETE_LIST]: deleteList};
    const action = actions[name];
    (action ? action() : switchLanguage(name)).catch((error) => showMessage(error.message, true));
});

$("words-category").addEventListener("change", loadWords);
// kept, so reloading the menu (a new count) keeps "Refresh vocab" picked
$("quiz-category").addEventListener("change", () => $("quiz-category").dataset.picked = $("quiz-category").value);
$("words-search").addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(loadWords, 200);
});

window.addEventListener("hashchange", () => showView(location.hash.slice(1)));

loadLearners()
    .then(() => showView(location.hash.slice(1)))
    .catch((error) => showMessage(error.message, true));
