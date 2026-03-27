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
    const topicInput = document.getElementById('topicInput');
    if (!topicInput || !topicInput.value.trim()) return;

    const micStatus = document.getElementById('micStatus');
    const sendBtn   = document.getElementById('sendBtn');
    const sendLabel = document.getElementById('sendLabel');
    const spinner   = document.getElementById('spinner');
    const dots      = document.getElementById('thinkingDots');
    const ph        = document.getElementById('placeholder');

    if (micStatus) { micStatus.textContent = 'Thinking…'; micStatus.className = 'mic-status thinking'; }
    if (sendBtn)   sendBtn.disabled = true;
    if (sendLabel) sendLabel.style.display = 'none';
    if (spinner)   spinner.style.display   = 'block';
    if (dots)      dots.style.display      = 'flex';
    if (ph)        ph.style.display        = 'none';

    // Stop any active TTS before replacing the panel
    if ('speechSynthesis' in window) window.speechSynthesis.cancel();

    try {
        const formEl = document.getElementById('explainForm');
        const body   = new URLSearchParams(new FormData(formEl)).toString();

        // POST then follow the server's redirect back to /  — browser URL never changes
        const res  = await fetch('/explain', {
            method:   'POST',
            headers:  { 'Content-Type': 'application/x-www-form-urlencoded' },
            body,
            redirect: 'follow'
        });
        const html = await res.text();
        const doc  = new DOMParser().parseFromString(html, 'text/html');

        // Swap out the right panel content only
        const newPanel = doc.querySelector('.layout .panel:last-child');
        const curPanel = document.querySelector('.layout .panel:last-child');
        if (newPanel && curPanel) curPanel.innerHTML = newPanel.innerHTML;

        // Sync hidden TTS text + flag divs (they live outside .layout)
        ['ttsExplain', 'ttsExample', 'ttsKey', 'ttsRaw', 'hasVideo', 'hasWikiGif'].forEach(id => {
            const n = doc.getElementById(id);
            const c = document.getElementById(id);
            if (n && c) c.textContent = n.textContent;
        });

        applySceneAndIcon();
        if (typeof window.initLazyClassroomVideo === 'function') window.initLazyClassroomVideo();
        if (typeof window.reinitVoiceUI === 'function') window.reinitVoiceUI();

    } catch (err) {
        console.error('Request failed:', err);
    } finally {
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
            wrap.innerHTML = '<div class="yt-wrapper"><iframe src="https://www.youtube.com/embed/' + id +
                '?rel=0&modestbranding=1" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen" allowfullscreen></iframe></div>';
            wrap.dataset.loaded = '1';
        }, { once: true });
    });
};

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('explainForm');
    if (form) form.addEventListener('submit', e => { e.preventDefault(); submitForm(); });

    // Handle initial page load that already has a response (e.g. after browser back)
    applySceneAndIcon();
    window.initLazyClassroomVideo();
});
