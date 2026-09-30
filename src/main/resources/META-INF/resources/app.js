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
    return escapeHtml(text.trim()).split(/\n\s*\n/).map(renderBlock).join("");
}

// A block can mix plain lines (a heading like "**Dados físicos**") with list items;
// an indented item becomes a sublist of the item above it.
function renderBlock(block) {
    const html = [];
    let paragraph = [];
    let depth = 0;

    const flushParagraph = () => {
        if (paragraph.length) {
            html.push(`<p>${paragraph.join("<br>")}</p>`);
            paragraph = [];
        }
    };
    const closeLists = (to) => {
        while (depth > to) {
            html.push("</li></ul>");
            depth--;
        }
    };

    for (const line of block.split("\n")) {
        if (!line.trim()) {
            continue;
        }
        const item = line.match(/^(\s*)(?:[-*•]|\d+\.)\s(.*)$/);
        if (!item) {
            closeLists(0);
            paragraph.push(inline(line.trim().replace(/^#+\s*/, "")));
            continue;
        }
        flushParagraph();
        const level = item[1].length >= 2 ? 2 : 1;
        if (depth < level) {
            while (depth < level) {
                html.push("<ul><li>");
                depth++;
            }
        } else {
            closeLists(level);
            html.push("</li><li>");
        }
        html.push(inline(item[2].trim()));
    }
    closeLists(0);
    flushParagraph();
    return html.join("");
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
