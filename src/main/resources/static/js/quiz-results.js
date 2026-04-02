(() => {
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  function setHidden(el, hidden) {
    if (!el) return;
    if (hidden) el.setAttribute("hidden", "");
    else el.removeAttribute("hidden");
  }

  function setText(el, text) {
    if (!el) return;
    el.textContent = text == null ? "" : String(text);
  }

  function clearActive(rows) {
    rows.forEach((r) => r.classList.remove("qr-active"));
  }

  function textOf(sel, root) {
    return ($(sel, root)?.textContent || "").trim();
  }

  function ensureEmbedIdle(placeholderMsg) {
    try {
      const dots = document.getElementById("thinkingDots");
      const ph = document.getElementById("placeholder");
      if (dots) dots.style.display = "none";
      if (ph) {
        ph.style.display = "";
        if (placeholderMsg) {
          const p = ph.querySelector("p");
          if (p) p.innerHTML = String(placeholderMsg);
        }
      }
    } catch (_) {}
  }

  function setRightPanel({ showEmbed }) {
    const layout = document.getElementById("qrLayout");
    const title = document.getElementById("qrExplainTitle");
    const wrap = document.getElementById("qrEmbedWrap");
    if (layout) {
      if (showEmbed) layout.classList.add("qr-layout--show-explain");
      else layout.classList.remove("qr-layout--show-explain");
    }
    setHidden(title, !showEmbed);
    setHidden(wrap, !showEmbed);
    if (!showEmbed) return;
    ensureEmbedIdle("");
  }

  function openExplainForRow(row, quizGrade) {
    const qid = row.getAttribute("data-question-id");
    if (!qid) return;

    const question = textOf(".qr-q", row);
    const correct = textOf(".qr-correct", row);

    const pg = document.getElementById("inputPrepareGrade");
    const mode = document.getElementById("inputClassSessionMode");
    const focus = document.getElementById("inputPrepareFocus");
    const iv = document.getElementById("inputIncludeVideo");
    const qidField = document.getElementById("inputSourceQuestionId");
    const input = document.getElementById("topicInput");

    if (pg) pg.value = (quizGrade || "").trim();
    if (mode) mode.value = "quiz";
    if (focus) focus.value = "revision";
    if (iv) iv.value = "false";
    if (qidField) qidField.value = String(qid);

    const prompt = `Explain this question for a Grade ${(quizGrade || "5")} student.\n\nQuestion: ${question}\nCorrect Answer: ${correct}\n\nReturn ONLY in this JSON format:\n{\n  \"key_point\": \"1 short sentence\",\n  \"simple_explanation\": \"2 short sentences max\",\n  \"example\": \"1 simple relatable example\"\n}`;

    const shortLabel = (question || "Explanation").replace(/\s+/g, " ").trim();
    window.CLASSROOM_POST_TOPIC_LABEL = shortLabel.length > 64 ? (shortLabel.slice(0, 61) + "…") : shortLabel;
    if (input) {
      input.value = window.CLASSROOM_POST_TOPIC_LABEL;
      input.dataset.fullPrompt = prompt;
    }

    if (typeof window.submitForm === "function") {
      window.submitForm();
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    const rows = $$(".qr-row");
    const backBtn = $("#qrBackBtn");

    const pageGrade = $("#qrLayout")?.getAttribute("data-quiz-grade")?.trim() || "";
    // Initial state: AI embed hidden until 💡 is clicked (no placeholder text).
    setRightPanel({ showEmbed: false });

    if (backBtn) {
      backBtn.addEventListener("click", () => {
        if (history.length > 1) history.back();
        else window.location.href = "/quiz/teacher";
      });
    }

    rows.forEach((row) => {
      const qid = row.getAttribute("data-question-id");
      if (!qid) return;

      const selectRowOnly = () => {
        clearActive(rows);
        row.classList.add("qr-active");
        // Do not auto-load explanation on selection.
        setRightPanel({ showEmbed: false });
      };

      row.addEventListener("click", (e) => {
        const btn = e.target && e.target.closest ? e.target.closest(".qr-explain-btn") : null;
        if (btn) return;
        selectRowOnly();
      });

      row.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          selectRowOnly();
        }
      });

      const explainBtn = $(".qr-explain-btn", row);
      if (explainBtn) {
        explainBtn.addEventListener("click", (e) => {
          e.stopPropagation();
          clearActive(rows);
          row.classList.add("qr-active");
          setRightPanel({ showEmbed: true });
          openExplainForRow(row, pageGrade);
        });
        explainBtn.addEventListener("keydown", (e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            e.stopPropagation();
            clearActive(rows);
            row.classList.add("qr-active");
            setRightPanel({ showEmbed: true });
            openExplainForRow(row, pageGrade);
          }
        });
      }
    });
  });
})();

