/**
 * mic.js — Web Speech API microphone / speech-recognition handling.
 * Depends on: app.js (submitForm function) — load mic.js after app.js.
 */

(function () {
    const micBtn     = document.getElementById('micBtn');
    const micIcon    = document.getElementById('micIcon');
    const waveBars   = document.getElementById('waveBars');
    const micStatus  = document.getElementById('micStatus');
    const topicInput = document.getElementById('topicInput');

    if (!micBtn) return;

    const SpeechRecognitionAPI = window.SpeechRecognition || window.webkitSpeechRecognition;
    let recognition  = null;
    let isListening  = false;

    if (!SpeechRecognitionAPI) {
        micBtn.style.opacity = '0.5';
        if (micStatus) micStatus.textContent = 'Voice not supported — please use Chrome';
        return;
    }

    recognition = new SpeechRecognitionAPI();
    recognition.continuous      = false;
    recognition.interimResults  = true;
    recognition.lang            = '';  // blank → Chrome auto-detects language from speech

    recognition.onstart = () => {
        isListening = true;
        micBtn.classList.add('listening');
        micIcon.className = 'fa-solid fa-stop';
        waveBars.classList.add('active');
        micStatus.textContent = 'Listening… speak now';
        micStatus.className   = 'mic-status listening';
    };

    recognition.onresult = (e) => {
        let interim = '', final = '';
        for (let i = e.resultIndex; i < e.results.length; i++) {
            if (e.results[i].isFinal) final   += e.results[i][0].transcript;
            else                      interim  += e.results[i][0].transcript;
        }
        topicInput.value = (final || interim).trim();
    };

    recognition.onspeechend = () => recognition.stop();

    recognition.onend = () => {
        isListening = false;
        micBtn.classList.remove('listening');
        micIcon.className   = 'fa-solid fa-microphone';
        waveBars.classList.remove('active');
        micStatus.textContent = 'Tap the mic and ask your question';
        micStatus.className   = 'mic-status';

        if (topicInput.value.trim().length > 0) {
            if (typeof window.isQuizExplainAiAllowed === 'function' && !window.isQuizExplainAiAllowed()) {
                if (micStatus) {
                    micStatus.textContent = 'Enable AI explanations first (button above the explain panel).';
                    micStatus.className = 'mic-status';
                }
                return;
            }
            submitForm();
        }
    };

    recognition.onerror = () => {
        isListening = false;
        micBtn.classList.remove('listening');
        micIcon.className   = 'fa-solid fa-microphone';
        waveBars.classList.remove('active');
        micStatus.textContent = 'Could not hear — please try again';
        micStatus.className   = 'mic-status';
    };

    micBtn.addEventListener('click', () => {
        if (typeof window.isQuizExplainAiAllowed === 'function' && !window.isQuizExplainAiAllowed()) {
            if (micStatus) {
                micStatus.textContent = 'Enable AI explanations first (button above the explain panel).';
                micStatus.className = 'mic-status';
            }
            return;
        }
        if (isListening) {
            recognition.stop();
        } else {
            topicInput.value = '';
            recognition.start();
        }
    });

    window.addEventListener('beforeunload', function (e) {
        if (!isListening) return;
        e.preventDefault();
        e.returnValue = '';
    });
}());
