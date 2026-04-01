let monitorActive = false;
let monitorUserDisabled = false;
let monitorAutoStartRegistered = false;
let monitorAudioCtx = null;
let monitorAnalyser = null;
let monitorStream = null;
let monitorAnimFrame = null;
let monitorLastWarning = 0;
let monitorLastSpoken = 0;
// Medium sensitivity: higher value = less likely to warn on normal classroom voice.
// Lower value = more sensitive (warn sooner).
const THRESHOLD_MEDIUM = { noisy: 48 };

const MONITOR_MESSAGES = {
    attendance: [
        { text: 'Please keep quiet. Attendance is going on.', lang: 'en-IN' },
        { text: 'Attendance is going on. Please keep quiet and listen for your name.', lang: 'en-IN' },
        { text: 'Everyone, stay silent while attendance is in progress.', lang: 'en-IN' },
        { text: 'Listen carefully. The teacher is taking attendance.', lang: 'en-IN' },
        { text: 'कृपया शांति रखें, उपस्थिति चल रही है।', lang: 'hi-IN' },
        { text: 'कृपया शांति रखें। उपस्थिति चल रही है, अपना नाम ध्यान से सुनें।', lang: 'hi-IN' },
        { text: 'ध्यान से सुनो, टीचर उपस्थिति ले रही हैं।', lang: 'hi-IN' },
        { text: 'सब बच्चे शांति से बैठो, attendance हो रही है।', lang: 'hi-IN' },
        { text: 'क्लास में शोर नहीं, अभी attendance चल रही है।', lang: 'hi-IN' },
        { text: 'कृपया बातें बंद करें, उपस्थिति लिखी जा रही है।', lang: 'hi-IN' },
        { text: 'Stay quiet please. Names are being marked.', lang: 'en-IN' },
        { text: 'Hands still and lips closed while attendance happens.', lang: 'en-IN' }
    ],
    quiz: [
        { text: 'Please keep quiet. A quiz is in progress.', lang: 'en-IN' },
        { text: 'Quiz is going on. Please keep quiet and listen carefully.', lang: 'en-IN' },
        { text: 'Everyone, please be silent. Let {name} answer.', lang: 'en-IN' },
        { text: 'No talking now. Someone is answering a question.', lang: 'en-IN' },
        { text: 'Give your friend silence so they can think.', lang: 'en-IN' },
        { text: 'कृपया शांति रखें, क्विज़ चल रही है।', lang: 'hi-IN' },
        { text: 'कृपया शांति रखें। क्विज़ चल रही है, ध्यान से सुनो।', lang: 'hi-IN' },
        { text: 'सब बच्चे शांति रखें। {name} जवाब दे रहा/रही है।', lang: 'hi-IN' },
        { text: 'जो बच्चा जवाब दे रहा है, उसे चुपचाप सुनो।', lang: 'hi-IN' },
        { text: 'अभी सवालों के जवाब दिए जा रहे हैं, शोर मत करो।', lang: 'hi-IN' },
        { text: 'क्विज़ के समय क्लास में चुप्पी रहनी चाहिए।', lang: 'hi-IN' },
        { text: 'अपने दोस्त को सोचने के लिए शांति दो।', lang: 'hi-IN' },
        { text: 'Quiet quiz time. Use your inside voice.', lang: 'en-IN' },
        { text: 'Only the answer should be heard, not the noise.', lang: 'en-IN' }
    ],
    reading: [
        { text: 'Please keep quiet. Reading practice is going on.', lang: 'en-IN' },
        { text: 'Reading is going on. Please keep quiet and listen carefully.', lang: 'en-IN' },
        { text: 'Everyone, please be silent. Let {name} read.', lang: 'en-IN' },
        { text: 'Let your friend read aloud without noise.', lang: 'en-IN' },
        { text: 'Use your listening ears. Someone is reading.', lang: 'en-IN' },
        { text: 'कृपया शांति रखें, पढ़ने का अभ्यास चल रहा है।', lang: 'hi-IN' },
        { text: 'कृपया शांति रखें। पढ़ाई/रीडिंग चल रही है, ध्यान से सुनो।', lang: 'hi-IN' },
        { text: 'सब बच्चे शांति रखें। {name} पढ़ रहा/रही है।', lang: 'hi-IN' },
        { text: 'जो बच्चा पढ़ रहा है, उसे ध्यान से सुनो।', lang: 'hi-IN' },
        { text: 'किताब की आवाज़ सुनो, शोर की नहीं।', lang: 'hi-IN' },
        { text: 'क्लास में reading चल रही है, सब चुप रहें।', lang: 'hi-IN' },
        { text: 'शांत रहो, ताकि हर शब्द साफ़ सुनाई दे।', lang: 'hi-IN' },
        { text: 'Reading time. Let the story be louder than the classroom.', lang: 'en-IN' },
        { text: 'Quiet please, words are trying to fly.', lang: 'en-IN' }
    ],
    generic: [
        { text: 'Class is in progress. Please keep your voice low.', lang: 'en-IN' },
        { text: 'Use your indoor voice. Learning is happening.', lang: 'en-IN' },
        { text: 'Let the classroom stay calm and focused.', lang: 'en-IN' },
        { text: 'क्लास चल रही है, कृपया शांति रखें।', lang: 'hi-IN' },
        { text: 'धीरे बोलो, सबको सुनाई देना चाहिए।', lang: 'hi-IN' },
        { text: 'क्लास में पढ़ाई हो रही है, शोर मत करो।', lang: 'hi-IN' },
        { text: 'सब बच्चे अपनी सीट पर शांत बैठें।', lang: 'hi-IN' },
        { text: 'टिफिन टाइम नहीं, अभी पढ़ाई का समय है।', lang: 'hi-IN' },
        { text: 'Learning zone on. Keep the volume down.', lang: 'en-IN' },
        { text: 'Let’s keep the classroom calm so minds can work.', lang: 'en-IN' }
    ]
};

function pickMonitorMessage(kind) {
    var list = MONITOR_MESSAGES[kind] || MONITOR_MESSAGES.generic;
    if (!list.length) list = MONITOR_MESSAGES.generic;
    var idx = Math.floor(Math.random() * list.length);
    return list[idx];
}

function monitorContextMessage() {
    var p = String(window.location.pathname || '').toLowerCase();
    var kind = 'generic';
    if (p.indexOf('/attendance') === 0) kind = 'attendance';
    else if (p.indexOf('/quiz') === 0) kind = 'quiz';
    else if (p.indexOf('/reading') === 0) kind = 'reading';

    var base = pickMonitorMessage(kind);
    var name = monitorSelectedStudentName();

    var text = base.text;
    if (name && text.indexOf('{name}') !== -1) {
        text = text.replace('{name}', name);
    }

    return { text: text, lang: base.lang || 'en-IN' };
}

function monitorSelectedStudentName() {
    var rollName = document.getElementById('rollName');
    if (rollName && rollName.textContent && rollName.textContent.trim() !== '-' && rollName.textContent.trim() !== '') {
        return rollName.textContent.trim();
    }
    var roster = document.getElementById('studentRosterSelect');
    if (roster && roster.selectedIndex != null && roster.selectedIndex >= 0) {
        var opt = roster.options ? roster.options[roster.selectedIndex] : null;
        var label = opt ? (opt.textContent || '').trim() : '';
        if (label) return label;
        if (roster.value && roster.value.trim() !== '') return roster.value.trim();
    }
    var oral = document.getElementById('student-oral');
    if (oral && oral.value && oral.value.trim() !== '') return oral.value.trim();
    var genericSelect = document.querySelector('select[id^="student-"]');
    if (genericSelect && genericSelect.value && genericSelect.value.trim() !== '') return genericSelect.value.trim();
    var genericInput = document.querySelector('input[id^="student-"]');
    if (genericInput && genericInput.value && genericInput.value.trim() !== '') return genericInput.value.trim();
    var q1 = document.getElementById('studentName');
    if (q1 && q1.value && q1.value.trim() !== '') return q1.value.trim();
    var q2 = document.getElementById('studentNameInput');
    if (q2 && q2.value && q2.value.trim() !== '') return q2.value.trim();
    return '';
}

function monitorContextActive() {
    var p = String(window.location.pathname || '').toLowerCase();
    if (p.indexOf('/attendance') === 0) {
        var modal = document.getElementById('rollCallModal');
        if (!modal) return false;
        return getComputedStyle(modal).display !== 'none';
    }
    if (p.indexOf('/reading') === 0) {
        var btn = document.querySelector('.practice-btn.listening');
        var recognizer = !!window.practiceRecognition || !!window.readingPassageMicListening;
        return !!btn || recognizer;
    }
    if (p.indexOf('/quiz') === 0) {
        // Disable monitor on result/explanation views
        var scoreScreen = document.getElementById('scoreScreen');
        if (scoreScreen && scoreScreen.classList.contains('visible')) return false;
        var explainDetails = document.getElementById('quizExplainDetails');
        if (explainDetails && explainDetails.open) return false;
        // Quiz take screen renders question in #qText
        var q = document.getElementById('qText')
            || document.getElementById('questionText')
            || document.getElementById('currentQuestionText')
            || document.getElementById('quizQuestion');
        return !!q;
    }
    return false;
}

async function toggleMonitor() {
    if (monitorActive) {
        monitorUserDisabled = true;
        stopMonitor();
    } else {
        monitorUserDisabled = false;
        await startMonitor();
    }
}

async function startMonitor() {
    const badge = document.getElementById('monitorBadge');
    const btn = document.getElementById('monitorToggleBtn');
    if (!badge || !btn) return;
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) return;

    if (!monitorAudioCtx || monitorAudioCtx.state === 'closed') {
        monitorAudioCtx = new (window.AudioContext || window.webkitAudioContext)();
    }
    if (monitorAudioCtx.state === 'suspended') await monitorAudioCtx.resume();

    try {
        monitorStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
        monitorAnalyser = monitorAudioCtx.createAnalyser();
        monitorAnalyser.fftSize = 256;
        monitorAnalyser.smoothingTimeConstant = 0.7;
        monitorAudioCtx.createMediaStreamSource(monitorStream).connect(monitorAnalyser);
    } catch (e) {
        console.warn('Monitor microphone access denied/unavailable.');
        monitorActive = false;
        if (badge) badge.textContent = 'OFF';
        if (btn) btn.setAttribute('aria-label', 'Monitor OFF');
        return;
    }

    monitorActive = true;
    badge.textContent = 'ON';
    btn.setAttribute('aria-label', 'Monitor ON');
    readMonitor();
}

function stopMonitor() {
    monitorActive = false;
    if (monitorAnimFrame) cancelAnimationFrame(monitorAnimFrame);
    if (monitorStream) monitorStream.getTracks().forEach(function (t) { t.stop(); });
    if (monitorAudioCtx) monitorAudioCtx.close();
    monitorAudioCtx = null;
    monitorAnalyser = null;
    monitorStream = null;
    const badge = document.getElementById('monitorBadge');
    const btn = document.getElementById('monitorToggleBtn');
    if (badge) badge.textContent = 'OFF';
    if (btn) btn.setAttribute('aria-label', 'Monitor OFF');
}

function readMonitor() {
    if (!monitorActive || !monitorAnalyser) return;
    const data = new Uint8Array(monitorAnalyser.frequencyBinCount);
    monitorAnalyser.getByteFrequencyData(data);
    const rms = Math.sqrt(data.reduce(function (s, v) { return s + v * v; }, 0) / data.length);
    const db = rms > 0 ? (20 * Math.log10(rms / 128) + 60) : 0;
    evaluateNoise(Math.max(0, Math.min(100, db)));
    monitorAnimFrame = requestAnimationFrame(readMonitor);
}

function evaluateNoise(db) {
    const now = Date.now();
    if (!monitorContextActive()) return;
    if (window.__monitorSuppressUntil && now < window.__monitorSuppressUntil) return;
    if (db > THRESHOLD_MEDIUM.noisy &&
        (now - monitorLastWarning) >= 8000 &&
        (now - monitorLastSpoken) >= 6000) {
        monitorLastWarning = now;
        monitorLastSpoken = now;
        var ctx = monitorContextMessage();
        monitorClassroomSpeak(ctx.text, ctx.lang, 0.9, 1);
    }
}

function monitorClassroomSpeak(text, lang, rate, pitch) {
    if (!text || !('speechSynthesis' in window)) return;
    window.speechSynthesis.cancel();
    var u = new SpeechSynthesisUtterance(text);
    u.lang = lang || 'en-IN';
    u.rate = rate || 0.9;
    u.pitch = pitch || 1;
    u.onstart = function () {
        window.__monitorTtsActive = true;
    };
    u.onend = function () {
        window.__monitorTtsActive = false;
    };
    u.onerror = function () {
        window.__monitorTtsActive = false;
    };
    window.speechSynthesis.speak(u);
}

window.toggleMonitor = toggleMonitor;
window.monitorClassroomSpeak = monitorClassroomSpeak;
window.__monitorTtsActive = false;
window.startMonitor = startMonitor;
window.stopMonitor = stopMonitor;

window.monitorIsPriorityActive = function () {
    return !!window.__monitorTtsActive;
};

// Monitor is OFF by default. Only start when teacher manually toggles it.
