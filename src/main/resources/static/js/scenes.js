/**
 * scenes.js — CSS animated scenes and topic-to-category mapping.
 * No dependencies. Must be loaded before app.js.
 */

const SCENES = {
    math: `<div class="css-scene scene-math">
        <span class="symbol">+</span><span class="symbol">÷</span>
        <span class="symbol">∑</span><span class="symbol">π</span><span class="symbol">=</span>
    </div>`,
    science: `<div class="css-scene scene-atom">
        <div class="atom-core"></div>
        <div class="orbit"><div class="electron"></div></div>
        <div class="orbit"><div class="electron"></div></div>
        <div class="orbit"><div class="electron"></div></div>
    </div>`,
    space: `<div class="css-scene scene-space">
        <div class="star" style="top:10%;left:8%;animation-delay:.3s"></div>
        <div class="star" style="top:20%;left:80%;animation-delay:.9s"></div>
        <div class="star" style="top:80%;left:15%;animation-delay:1.5s"></div>
        <div class="star" style="top:70%;left:90%;animation-delay:.6s"></div>
        <div class="sun"></div>
        <div class="planet-orbit"><div class="planet planet-1"></div></div>
        <div class="planet-orbit"><div class="planet planet-2"></div></div>
    </div>`,
    history: `<div class="css-scene scene-history"><div class="hourglass">⏳</div></div>`,
    geography: `<div class="css-scene scene-geo">
        <div class="globe-dot"></div>
        <div class="globe-ring"></div>
        <div class="globe-ring"></div>
        <div class="globe-ring"></div>
    </div>`,
    computer: `<div class="css-scene scene-computer">
        <div class="code-line">&gt; function learn() { return knowledge; }</div>
        <div class="code-line">&gt; const answer = AI.solve(question);</div>
        <div class="code-line">&gt; console.log("Hello, World!");</div>
        <div class="code-line">&gt; while(curious) { explore(); }</div>
    </div>`,
    language: `<div class="css-scene scene-language">
        <div class="word-float">नमस्ते</div>
        <div class="word-float">Hello</div>
        <div class="word-float">வணக்கம்</div>
    </div>`,
    music: `<div class="css-scene scene-music">
        <div class="music-note">♩</div>
        <div class="music-note">♫</div>
        <div class="music-note">♬</div>
    </div>`,
    health: `<div class="css-scene scene-health">
        <svg class="ecg-svg" viewBox="0 0 300 60">
          <polyline
            points="0,30 40,30 60,30 70,5 80,55 90,30 130,30 150,30 160,10 170,50 180,30 220,30 240,30 250,8 260,52 270,30 300,30"
            fill="none" stroke="#ef4444" stroke-width="2"
            stroke-dasharray="400" stroke-dashoffset="400"
            style="animation:ecg-move 2s linear infinite"/>
        </svg>
    </div>`,
    default: `<div class="css-scene scene-default">
        <div class="sparkle">✨</div><div class="sparkle">⭐</div>
        <div class="sparkle">💫</div><div class="sparkle">🌟</div>
    </div>`
};

const ICON_MAP = [
    { keys:['math','calculus','algebra','geometry','number','fraction','equation','trigonometry'], cat:'math',      icon:'🔢' },
    { keys:['science','physics','chemistry','biology','atom','cell','energy','force','molecule'],  cat:'science',   icon:'🔬' },
    { keys:['space','planet','star','solar','moon','galaxy','universe','orbit','comet'],            cat:'space',     icon:'🌌' },
    { keys:['history','war','ancient','civilization','empire','king','queen','revolution'],         cat:'history',   icon:'🏛️' },
    { keys:['geography','country','continent','map','ocean','river','mountain','capital','climate'],cat:'geography', icon:'🌍' },
    { keys:['computer','programming','code','software','internet','algorithm','data','network'],    cat:'computer',  icon:'💻' },
    { keys:['language','grammar','word','poem','story','literature','english','hindi','writing'],   cat:'language',  icon:'📚' },
    { keys:['art','music','painting','drawing','song','dance','creative','instrument'],             cat:'music',     icon:'🎨' },
    { keys:['body','health','heart','brain','lungs','exercise','nutrition','blood','bone'],         cat:'health',    icon:'🫀' },
];

/**
 * Returns { cat, icon } for a given topic string.
 */
function detectCategory(topic) {
    if (!topic) return { cat: 'default', icon: '💡' };
    const lower = topic.toLowerCase();
    for (const entry of ICON_MAP) {
        if (entry.keys.some(k => lower.includes(k))) return entry;
    }
    return { cat: 'default', icon: '💡' };
}
