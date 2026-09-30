const thread = document.getElementById("thread");
const form = document.getElementById("composer");
const input = document.getElementById("question");
const send = document.getElementById("send");

// Uma conversa por carregamento da pagina: o servidor guarda o historico por esse id.
const conversationId = newConversationId();

form.addEventListener("submit", (event) => {
    event.preventDefault();
    ask(input.value);
});

input.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        form.requestSubmit();
    }
});

document.querySelectorAll(".suggestion").forEach((button) => {
    button.addEventListener("click", () => ask(button.textContent));
});

async function ask(rawQuestion) {
    const question = rawQuestion.trim();
    if (!question || send.disabled) {
        return;
    }

    input.value = "";
    setBusy(true);

    const panel = element("article", "panel panel--new");
    const balloon = element("div", "balloon balloon--user");
    balloon.textContent = question;
    panel.append(balloon);

    const waiting = element("p", "caption waiting");
    waiting.innerHTML = 'Enquanto isso, nos arquivos… <span class="waiting__dots" aria-hidden="true"><span></span><span></span><span></span></span>';
    panel.append(waiting);

    thread.append(panel);
    panel.scrollIntoView({ behavior: "smooth", block: "start" });

    try {
        const response = await fetch("/comics", {
            method: "POST",
            headers: {
                "Content-Type": "text/plain; charset=utf-8",
                "X-Conversation-Id": conversationId,
            },
            body: question,
        });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const answer = element("div", "balloon balloon--answer");
        answer.innerHTML = render(await response.text());
        waiting.replaceWith(answer);
    } catch (error) {
        console.error(error);
        const failure = element("p", "caption caption--error");
        failure.textContent = "A resposta não chegou. Confira se o servidor está rodando e pergunte de novo.";
        waiting.replaceWith(failure);
        input.value = question;
    } finally {
        setBusy(false);
        input.focus();
    }
}

function setBusy(busy) {
    send.disabled = busy;
    input.readOnly = busy;
}

function newConversationId() {
    if (crypto.randomUUID) {
        return crypto.randomUUID();
    }
    // randomUUID so existe em HTTPS ou localhost; getRandomValues funciona em qualquer origem.
    return Array.from(crypto.getRandomValues(new Uint8Array(16)), (b) => b.toString(16).padStart(2, "0")).join("");
}

function element(tag, className) {
    const node = document.createElement(tag);
    node.className = className;
    return node;
}

// Small Markdown subset (paragraphs, lists, bold, italic), escaped first.
function render(text) {
    const blocks = escapeHtml(text.trim()).split(/\n\s*\n/);
    return blocks.map((block) => {
        const lines = block.split("\n").map((line) => line.trim()).filter(Boolean);
        if (lines.length && lines.every((line) => /^([-*•]|\d+\.)\s+/.test(line))) {
            const items = lines.map((line) => `<li>${inline(line.replace(/^([-*•]|\d+\.)\s+/, ""))}</li>`);
            return `<ul>${items.join("")}</ul>`;
        }
        return `<p>${lines.map((line) => inline(line.replace(/^#+\s*/, ""))).join("<br>")}</p>`;
    }).join("");
}

function inline(text) {
    return text
        .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
        .replace(/(^|[^*])\*([^*\s][^*]*?)\*/g, "$1<em>$2</em>");
}

function escapeHtml(text) {
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}
