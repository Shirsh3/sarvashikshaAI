/**
 * voice.js — Text-to-speech using Google हिन्दी voice exclusively.
 * Falls back to the best available Hindi voice if Google हिन्दी is not found.
 */

let isSpeaking = false;
let ttsTimer   = null;
let __ttsSequence = null;

// ── Voice selection ───────────────────────────────────────────────────────────

function getGoogleHindiVoice(voices) {
    // Exact match first
    const exact = voices.find(v => v.name === 'Google हिन्दी');
    if (exact) return exact;

    // Loose match — any Google voice for Hindi
    const loose = voices.find(v =>
        v.name.toLowerCase().includes('google') &&
        v.lang.toLowerCase().startsWith('hi')
    );
    if (loose) return loose;

    // Fallback: any hi-IN voice
    return voices.find(v => v.lang.toLowerCase().startsWith('hi')) || null;
}

// ── TTS text builder ──────────────────────────────────────────────────────────

function buildTtsText() {
    const explain = document.getElementById('ttsExplain')?.textContent.trim();
    const example = document.getElementById('ttsExample')?.textContent.trim();
    const key     = document.getElementById('ttsKey')?.textContent.trim();
    const raw     = document.getElementById('ttsRaw')?.textContent.trim();

    const parts = [];
    // Match visible classroom sequence: Key points -> Detailed explanation -> Example.
    if (key)     parts.push('Key points. ' + key);
    if (explain) parts.push('Detailed explanation. ' + explain);
    if (example) parts.push('Example. ' + example);

    const text = parts.length > 0 ? parts.join('. ') : (raw || '');
    return text.replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/gu, '').replace(/\s{2,}/g, ' ').trim();
}

// ── Read Aloud state ──────────────────────────────────────────────────────────

function setReadAloudDetailsOpen(open) {
    const det = document.getElementById('readAloudDetails');
    if (!det) return;
    det.open = !!open;
}

function resetReadAloudBtn() {
    isSpeaking = false;
    __ttsSequence = null;
    if (ttsTimer) { clearInterval(ttsTimer); ttsTimer = null; }
    const btn = document.getElementById('readAloudBtn');
    if (btn) {
        btn.className = 'read-aloud-btn';
        btn.innerHTML = '<i class="fa-solid fa-volume-high"></i> Read Aloud';
    }
    setReadAloudDetailsOpen(false);
}

function setSectionOpen(detailsEl, open) {
    if (!detailsEl) return;
    try { detailsEl.open = !!open; } catch (_) {}
}

function closeAllSpokenSections() {
    setSectionOpen(document.getElementById('keyPointsDetails'), false);
    setSectionOpen(document.getElementById('explanationDetails'), false);
    setSectionOpen(document.getElementById('exampleDetails'), false);
}

function buildTtsSectionsQueue() {
    const explain = document.getElementById('ttsExplain')?.textContent.trim();
    const example = document.getElementById('ttsExample')?.textContent.trim();
    const key     = document.getElementById('ttsKey')?.textContent.trim();
    const raw     = document.getElementById('ttsRaw')?.textContent.trim();

    const q = [];
    if (key) q.push({ label: 'Key points', text: key, details: document.getElementById('keyPointsDetails') });
    if (explain) q.push({ label: 'Detailed explanation', text: explain, details: document.getElementById('explanationDetails') });
    if (example) q.push({ label: 'Example', text: example, details: document.getElementById('exampleDetails') });
    if (q.length === 0 && raw) q.push({ label: 'Explanation', text: raw, details: null });
    return q;
}

function doSpeak(text, voice) {
    const utter   = new SpeechSynthesisUtterance(text);
    utter.lang    = 'hi-IN';
    utter.rate    = 0.9;
    utter.pitch   = 1;
    if (voice) utter.voice = voice;
    utter.onend   = () => {
        // If we are in a section-by-section sequence, proceed; otherwise reset.
        if (__ttsSequence && typeof __ttsSequence.next === 'function') {
            __ttsSequence.next();
        } else {
            resetReadAloudBtn();
        }
    };
    utter.onerror = (e) => {
        if (e.error !== 'interrupted') resetReadAloudBtn();
    };

    isSpeaking = true;
    setReadAloudDetailsOpen(true);
    const btn = document.getElementById('readAloudBtn');
    if (btn) {
        btn.className = 'read-aloud-btn speaking';
        btn.innerHTML = '<i class="fa-solid fa-circle-stop"></i> Stop';
    }

    window.speechSynthesis.speak(utter);

    // Chrome silently pauses on long text after ~15 s — keep it alive
    ttsTimer = setInterval(() => {
        if (!window.speechSynthesis.speaking) { clearInterval(ttsTimer); ttsTimer = null; return; }
        if (window.speechSynthesis.paused) window.speechSynthesis.resume();
    }, 10000);
}

function startReading() {
    const queue = buildTtsSectionsQueue();
    if (!queue || queue.length === 0) return;
    if (window.monitorIsPriorityActive && window.monitorIsPriorityActive()) return;

    window.speechSynthesis.cancel();

    closeAllSpokenSections();
    let idx = 0;
    __ttsSequence = {
        next: () => {
            if (!__ttsSequence) return;
            // Close previous section
            if (idx > 0) {
                const prev = queue[idx - 1];
                if (prev && prev.details) setSectionOpen(prev.details, false);
            }
            // Finished
            if (idx >= queue.length) {
                closeAllSpokenSections();
                resetReadAloudBtn();
                return;
            }
            const cur = queue[idx++];
            if (cur && cur.details) setSectionOpen(cur.details, true);

            const text = (cur && cur.text ? cur.text : '').trim();
            if (!text) { __ttsSequence.next(); return; }

            // Chrome sometimes returns empty list right after cancel() — retry in 50 ms
            let voices = window.speechSynthesis.getVoices();
            const voice = getGoogleHindiVoice(voices);
            if (voices.length === 0) {
                setTimeout(() => {
                    voices = window.speechSynthesis.getVoices();
                    doSpeak(text, getGoogleHindiVoice(voices));
                }, 50);
            } else {
                doSpeak(text, voice);
            }
        }
    };
    __ttsSequence.next();
}

function isReadAloudActive() {
    return isSpeaking || (window.speechSynthesis && window.speechSynthesis.speaking);
}

// ── Button wiring ─────────────────────────────────────────────────────────────

function initReadAloudBtn() {
    const btn = document.getElementById('readAloudBtn');
    if (!btn || !('speechSynthesis' in window)) return;
    // Clone to remove any stale listeners (safe after AJAX panel swap)
    const fresh = btn.cloneNode(true);
    btn.replaceWith(fresh);
    document.getElementById('readAloudBtn').addEventListener('click', (e) => {
        e.preventDefault();
        if (isSpeaking) {
            window.speechSynthesis.cancel();
            closeAllSpokenSections();
            resetReadAloudBtn();
        } else {
            startReading();
        }
    });
    const det = document.getElementById('readAloudDetails');
    if (det) {
        det.addEventListener('toggle', () => {
            if (!det.open && isSpeaking) {
                window.speechSynthesis.cancel();
                resetReadAloudBtn();
            }
        });
    }
}

// Called by app.js after the right panel is swapped via AJAX
window.reinitVoiceUI = function () {
    isSpeaking = false;
    if (ttsTimer) { clearInterval(ttsTimer); ttsTimer = null; }
    setReadAloudDetailsOpen(false);
    initReadAloudBtn();
};

document.addEventListener('DOMContentLoaded', initReadAloudBtn);

// Warn before refresh/navigation while read-aloud is active.
window.addEventListener('beforeunload', function (e) {
    if (!isReadAloudActive()) return;
    const msg = 'Read Aloud is currently running. Do you want to stop and leave this page?';
    e.preventDefault();
    e.returnValue = msg;
    return msg;
});

// If user confirms leaving, stop active speech immediately.
window.addEventListener('pagehide', function () {
    if (!isReadAloudActive()) return;
    try { window.speechSynthesis.cancel(); } catch (_) {}
    resetReadAloudBtn();
});
