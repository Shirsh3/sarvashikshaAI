/**
 * voice.js — Text-to-speech using Google हिन्दी voice exclusively.
 * Falls back to the best available Hindi voice if Google हिन्दी is not found.
 */

let isSpeaking = false;
let ttsTimer   = null;

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

function resetReadAloudBtn() {
    isSpeaking = false;
    if (ttsTimer) { clearInterval(ttsTimer); ttsTimer = null; }
    const btn = document.getElementById('readAloudBtn');
    if (btn) {
        btn.className = 'read-aloud-btn';
        btn.innerHTML = '<i class="fa-solid fa-volume-high"></i> Read Aloud';
    }
}

function doSpeak(text, voice) {
    const utter   = new SpeechSynthesisUtterance(text);
    utter.lang    = 'hi-IN';
    utter.rate    = 0.9;
    utter.pitch   = 1;
    if (voice) utter.voice = voice;
    utter.onend   = resetReadAloudBtn;
    utter.onerror = (e) => {
        if (e.error !== 'interrupted') resetReadAloudBtn();
    };

    isSpeaking = true;
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
    const text = buildTtsText();
    if (!text) return;
    if (window.monitorIsPriorityActive && window.monitorIsPriorityActive()) return;

    window.speechSynthesis.cancel();

    // Chrome sometimes returns empty list right after cancel() — retry in 50 ms
    let voices = window.speechSynthesis.getVoices();
    if (voices.length === 0) {
        setTimeout(() => {
            voices = window.speechSynthesis.getVoices();
            doSpeak(text, getGoogleHindiVoice(voices));
        }, 50);
    } else {
        doSpeak(text, getGoogleHindiVoice(voices));
    }
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
    document.getElementById('readAloudBtn').addEventListener('click', () => {
        if (isSpeaking) {
            window.speechSynthesis.cancel();
            resetReadAloudBtn();
        } else {
            startReading();
        }
    });
}

// Called by app.js after the right panel is swapped via AJAX
window.reinitVoiceUI = function () {
    isSpeaking = false;
    if (ttsTimer) { clearInterval(ttsTimer); ttsTimer = null; }
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
