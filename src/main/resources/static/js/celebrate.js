/* global confetti */
(() => {
    // Optional sound (served from `src/main/resources/static/sounds/`).
    const AUDIO_SRC = '/sounds/clap.wav';
    const audioPool = [];
    let audioIdx = 0;

    function getAudio() {
        if (!AUDIO_SRC) return null;
        if (audioPool.length === 0) {
            for (let i = 0; i < 4; i++) {
                try {
                    const a = new Audio(AUDIO_SRC);
                    a.preload = 'auto';
                    audioPool.push(a);
                } catch (e) {
                    // ignore
                }
            }
        }
        if (audioPool.length === 0) return null;
        const a2 = audioPool[audioIdx % audioPool.length];
        audioIdx++;
        return a2;
    }

    function playClap() {
        const a = getAudio();
        if (!a) return;
        try { a.pause(); } catch (e) {}
        try { a.currentTime = 0; } catch (e) {}
        let p = null;
        try { p = a.play(); } catch (e2) { p = null; }
        if (p && typeof p.catch === 'function') {
            // Autoplay policies: call celebrate() from a user gesture (click/tap).
            p.catch(function () {});
        }
    }

    function fireCenterExplosion() {
        if (typeof confetti !== 'function') return;
        if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

        // True explosion from the exact center (no top-origin "rain").
        const ORIGIN = { x: 0.5, y: 0.5 };
        // Target total particles ~150–200 across all bursts
        const TOTAL_PARTICLES = 190;

        function fire(particleRatio, opts) {
            confetti(Object.assign({
                origin: ORIGIN,
                particleCount: Math.max(1, Math.floor(TOTAL_PARTICLES * particleRatio)),
                // Keep the effect short so it reads as a burst (not a falling animation).
                ticks: 140,
                gravity: 0.9,
                drift: 0,
                decay: 0.91,
                scalar: 1
            }, opts));
        }

        // Layered bursts: tighter core + wider halo for a strong "explosion" read.
        // Spreads vary 30..120; velocities 40..60.
        fire(0.24, { startVelocity: 60, spread: 30, scalar: 0.95 });
        fire(0.22, { startVelocity: 56, spread: 50, scalar: 1.0 });
        fire(0.20, { startVelocity: 52, spread: 78, scalar: 1.05 });
        fire(0.18, { startVelocity: 48, spread: 105, scalar: 1.0 });
        fire(0.16, { startVelocity: 44, spread: 120, scalar: 0.92, decay: 0.92, ticks: 130 });

        // Bonus: a quick second burst for richness (still center-origin).
        setTimeout(() => {
            fire(0.14, { startVelocity: 58, spread: 42, scalar: 0.95, ticks: 135 });
            fire(0.12, { startVelocity: 50, spread: 96, scalar: 1.0, ticks: 135 });
        }, 160);
    }

    // Backwards compatibility: older pages may call this name.
    function fireConfettiBurst() {
        fireCenterExplosion();
    }

    function celebrate() {
        playClap();
        fireCenterExplosion();
    }

    window.celebrate = celebrate;
    window.playClap = playClap;
    window.fireConfettiBurst = fireConfettiBurst;
    window.fireCenterExplosion = fireCenterExplosion;
})();

