/**
 * app.js — Main application logic: form submission and CSS scene injection.
 * Depends on: scenes.js (SCENES, detectCategory) — load scenes.js first.
 */

// ── Scene / icon injection ────────────────────────────────────────────────────

function applySceneAndIcon() {
    const iconEl    = document.getElementById('topicIcon');
    const topicSpan = document.querySelector('.topic-badge span:last-child');
    if (!iconEl || !topicSpan) return;

    const { cat, icon } = detectCategory(topicSpan.textContent);
    iconEl.textContent  = icon;

    const hasVideo   = document.getElementById('hasVideo')?.textContent === 'true';
    const hasWikiGif = document.getElementById('hasWikiGif')?.textContent === 'true';

    if (!hasVideo && !hasWikiGif) {
        const sceneEl = document.getElementById('cssScene');
        if (sceneEl) {
            sceneEl.innerHTML     = SCENES[cat] || SCENES.default;
            sceneEl.style.display = 'block';
        }
    }
}

// ── Form submission (AJAX — URL always stays at /) ────────────────────────────

async function submitForm() {
    if (typeof window.isQuizExplainAiAllowed === 'function' && !window.isQuizExplainAiAllowed()) {
        const ms = document.getElementById('micStatus');
        if (ms) {
            ms.textContent = 'Turn on AI explanations on this page first (button above the explain panel).';
            ms.className = 'mic-status';
        }
        return;
    }
    const topicInput = document.getElementById('topicInput');
    const fullPrompt = topicInput && topicInput.dataset && topicInput.dataset.fullPrompt;
    if (!topicInput || (!topicInput.value.trim() && !fullPrompt)) return;
    const displayLabelBefore = (topicInput.value || '').trim();

    const micStatus = document.getElementById('micStatus');
    const sendBtn   = document.getElementById('sendBtn');
    const sendLabel = document.getElementById('sendLabel');
    const spinner   = document.getElementById('spinner');
    const dots      = document.getElementById('thinkingDots');
    const ph        = document.getElementById('placeholder');

    if (typeof window.showGlobalLoader === 'function') {
        window.showGlobalLoader('Getting explanation from AI…');
    }
    if (micStatus) { micStatus.textContent = 'Thinking…'; micStatus.className = 'mic-status thinking'; }
    if (sendBtn)   sendBtn.disabled = true;
    if (sendLabel) sendLabel.style.display = 'none';
    if (spinner)   spinner.style.display   = 'block';
    if (dots)      dots.style.display      = 'flex';
    if (ph)        ph.style.display        = 'none';

    // Stop any active TTS before replacing the panel
    if ('speechSynthesis' in window) window.speechSynthesis.cancel();
    // Disable monitor during AI answer flow
    if (typeof window.stopMonitor === 'function') {
        try { window.stopMonitor(); } catch (_) {}
    }

    try {
        const formEl = document.getElementById('explainForm');
        const fd = new FormData(formEl);
        let g = '';
        if (typeof window.getPrepareGradeContext === 'function') {
            g = window.getPrepareGradeContext() || '';
        } else {
            const pg = document.getElementById('inputPrepareGrade');
            g = pg && pg.value ? pg.value.trim() : '';
        }
        if (topicInput && topicInput.dataset && topicInput.dataset.fullPrompt) {
            let full = topicInput.dataset.fullPrompt;
            if (g && !/\bGrade\s*\d+/.test(full)) {
                full = '[Grade ' + g + '] ' + full;
            }
            fd.set('topic', full);
        } else if (topicInput) {
            let topicVal = (topicInput.value || '').trim();
            if (g && topicVal) topicVal = '[Grade ' + g + '] ' + topicVal;
            fd.set('topic', topicVal);
        }
        const body = new URLSearchParams(fd).toString();

        // POST then follow the server's redirect back to /  — browser URL never changes
        const ctrl = new AbortController();
        const timeoutMs = 25000;
        const timeoutId = setTimeout(() => ctrl.abort(), timeoutMs);
        const res  = await fetch('/explain', {
            method:   'POST',
            headers:  {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'fetch'
            },
            body,
            redirect: 'follow',
            credentials: 'same-origin',
            signal: ctrl.signal
        }).finally(() => clearTimeout(timeoutId));
        if (!res.ok) {
            console.error('/explain failed', res.status, res.statusText);
            if (micStatus) {
                micStatus.textContent = 'Request failed (' + res.status + '). Try again.';
                micStatus.className = 'mic-status';
            }
            if (typeof window.onAiExplainFailed === 'function') {
                try { window.onAiExplainFailed('Request failed'); } catch (_) {}
            }
            return;
        }
        const html = await res.text();
        const doc  = new DOMParser().parseFromString(html, 'text/html');

        // Swap out the right panel content only.
        // On quiz/reading pages we use the embedded classroom fragment, not the main .layout page.
        const newPanel = doc.querySelector('.classroom-embed-layout .panel:last-child')
            || doc.querySelector('.layout .panel:last-child');
        const curPanel = document.querySelector('.classroom-embed-layout .panel:last-child')
            || document.querySelector('.layout .panel:last-child');
        if (!newPanel || !curPanel) {
            console.warn('Explain response: missing expected answer panel. Looked for .classroom-embed-layout .panel:last-child and .layout .panel:last-child.');
            if (micStatus) {
                micStatus.textContent = 'Could not display the answer here. Try refreshing, or use the home classroom page.';
                micStatus.className = 'mic-status';
            }
            if (typeof window.onAiExplainFailed === 'function') {
                try { window.onAiExplainFailed('Could not display the answer'); } catch (_) {}
            }
            return;
        }
        curPanel.innerHTML = newPanel.innerHTML;

        // Sync hidden TTS text + flag divs (they live outside .layout)
        ['ttsExplain', 'ttsExample', 'ttsKey', 'ttsRaw', 'hasVideo', 'hasWikiGif'].forEach(id => {
            const n = doc.getElementById(id);
            const c = document.getElementById(id);
            if (n && c) c.textContent = n.textContent;
        });

        // Keep the UI label in the textarea; never leave the full JSON prompt visible.
        const curTopic = document.getElementById('topicInput');
        if (curTopic) {
            curTopic.value = displayLabelBefore || (window.CLASSROOM_POST_TOPIC_LABEL || '') || 'Explain answer';
            if (curTopic.dataset) delete curTopic.dataset.fullPrompt;
        }

        if (typeof window.CLASSROOM_POST_TOPIC_LABEL === 'string' && window.CLASSROOM_POST_TOPIC_LABEL) {
            const topicSpan = document.querySelector('.classroom-embed-layout .panel:last-child .topic-badge span:last-child')
                || document.querySelector('.layout .panel:last-child .topic-badge span:last-child');
            if (topicSpan) topicSpan.textContent = window.CLASSROOM_POST_TOPIC_LABEL;
            window.CLASSROOM_POST_TOPIC_LABEL = null;
        }

        applySceneAndIcon();
        if (typeof window.initLazyClassroomVideo === 'function') window.initLazyClassroomVideo();
        if (typeof window.reinitVoiceUI === 'function') window.reinitVoiceUI();
        const ttsRaw = (document.getElementById('ttsRaw')?.textContent || '').trim();
        const hasAnswer = !!ttsRaw || !!document.querySelector('.answer-header') || !!document.querySelector('.answer-cards');
        if (hasAnswer) {
            if (typeof window.onAiExplainLoaded === 'function') {
                try { window.onAiExplainLoaded(); } catch (_) {}
            }
        } else {
            if (typeof window.onAiExplainFailed === 'function') {
                try { window.onAiExplainFailed('Empty answer'); } catch (_) {}
            }
        }
        if (window.__quizExplainAutoTts) {
            window.__quizExplainAutoTts = false;
            setTimeout(function () {
                var btn = document.getElementById('readAloudBtn');
                if (btn) btn.click();
            }, 450);
        }

    } catch (err) {
        console.error('Request failed:', err);
        if (micStatus) {
            if (err && err.name === 'AbortError') {
                micStatus.textContent = 'Taking too long. Please try again.';
            } else {
                micStatus.textContent = 'Something went wrong. Check the network and try again.';
            }
            micStatus.className = 'mic-status';
        }
        if (typeof window.onAiExplainFailed === 'function') {
            try {
                window.onAiExplainFailed(err && err.name === 'AbortError' ? 'Timeout' : 'Request failed');
            } catch (_) {}
        }
    } finally {
        if (typeof window.hideGlobalLoader === 'function') window.hideGlobalLoader();
        if (topicInput && topicInput.dataset) delete topicInput.dataset.fullPrompt;
        if (sendBtn)   sendBtn.disabled = false;
        if (sendLabel) sendLabel.style.display = '';
        if (spinner)   spinner.style.display   = 'none';
        if (micStatus) {
            micStatus.textContent = 'Tap the mic and ask your question';
            micStatus.className   = 'mic-status';
        }
    }
}

/**
 * Injects the YouTube iframe into a .video-lazy wrapper (student-safe, one-time).
 */
window.injectLazyYouTubeIframe = function injectLazyYouTubeIframe(wrap) {
    if (!wrap || wrap.dataset.loaded === '1') return;
    const id = (wrap.getAttribute('data-videoid') || '').trim();
    if (!id) return;
    wrap.innerHTML = '<div class="yt-wrapper"><iframe src="https://www.youtube.com/embed/' + id +
        '?rel=0&modestbranding=1" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen" allowfullscreen></iframe></div>';
    wrap.dataset.loaded = '1';
};

/**
 * YouTube embed loads only after explicit click (student-safe).
 */
window.initLazyClassroomVideo = function initLazyClassroomVideo() {
    document.querySelectorAll('.video-lazy').forEach(function (wrap) {
        if (wrap.dataset.loaded === '1') return;
        const btn = wrap.querySelector('.btn-load-video');
        if (!btn) return;
        btn.addEventListener('click', function () {
            const id = wrap.getAttribute('data-videoid');
            if (!id) return;
            if (typeof window.injectLazyYouTubeIframe === 'function') window.injectLazyYouTubeIframe(wrap);
        }, { once: true });
    });
};

// Quiz explain: fetch videoId only after clicking "Load video"
window.loadYouTubeForCurrentAnswer = async function loadYouTubeForCurrentAnswer(e) {
    try {
        if (e && typeof e.preventDefault === 'function') e.preventDefault();
    } catch (_) {}

    const wrap = (e && e.target && e.target.closest) ? e.target.closest('.video-lazy') : document.querySelector('.video-lazy');
    if (!wrap) return;

    const existing = (wrap.getAttribute('data-videoid') || '').trim();
    if (existing) {
        // Do not call btn.click() — it re-runs this handler via inline onclick and can recurse until the button vanishes.
        if (typeof window.injectLazyYouTubeIframe === 'function') window.injectLazyYouTubeIframe(wrap);
        return;
    }

    const pre = (wrap.getAttribute('data-youtube-query') || '').trim();
    const topicSpan = document.querySelector('.classroom-embed-layout .panel:last-child .topic-badge span:last-child')
        || document.querySelector('.layout .panel:last-child .topic-badge span:last-child')
        || document.querySelector('.topic-badge span:last-child');
    const fallback = (topicSpan && topicSpan.textContent) ? topicSpan.textContent.trim() : '';
    const base = pre || fallback || 'classroom concept';
    const query = base.toLowerCase().includes('explained') ? base : (base + ' explained for students');

    const btn = wrap.querySelector('.btn-load-video');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<i class="fa-brands fa-youtube"></i> Loading…';
    }

    const res = await fetch('/api/youtube/search?q=' + encodeURIComponent(query), { method: 'GET', credentials: 'same-origin' });
    const body = await res.json().catch(() => ({}));
    const vid = body && body.videoId ? String(body.videoId).trim() : '';
    if (!res.ok || body.error || !vid) {
        if (res.status === 401 && btn) {
            btn.disabled = false;
            btn.innerHTML = '<i class="fa-brands fa-youtube"></i> Sign in required';
        } else if (btn) {
            btn.disabled = false;
            btn.innerHTML = '<i class="fa-brands fa-youtube"></i> Video not found';
        }
        if (!res.ok) {
            try { console.warn('YouTube search failed', res.status, body); } catch (_) {}
        }
        return;
    }

    wrap.setAttribute('data-videoid', vid);
    if (typeof window.injectLazyYouTubeIframe === 'function') window.injectLazyYouTubeIframe(wrap);
};

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('explainForm');
    if (form) form.addEventListener('submit', e => { e.preventDefault(); submitForm(); });

    // Handle initial page load that already has a response (e.g. after browser back)
    applySceneAndIcon();
    window.initLazyClassroomVideo();
});

// Global keybindings (work across pages)
document.addEventListener('keydown', function (e) {
    if (e.key !== 'Escape') return;

    // Close any open <dialog>
    try {
        document.querySelectorAll('dialog[open]').forEach(function (d) {
            try { d.close(); } catch (_) {}
        });
    } catch (_) {}

    // Close teacher quiz results modal (quiz/teacher.html)
    const ro = document.getElementById('resultsOverlay');
    if (ro && ro.classList && ro.classList.contains('visible')) {
        if (typeof window.closeResultsModal === 'function') {
            try { window.closeResultsModal(); } catch (_) {}
        } else {
            try { ro.classList.remove('visible'); } catch (_) {}
        }
    }
});
