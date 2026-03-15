# SarvashikshaAI — Design & theme plan

**Current theme:** [Kider](https://themewagon.github.io/kider/) (ThemeWagon preschool template) — primary `#FE5D37`, light `#FFF5F3`, dark `#103741`, Lobster Two for headings, Nunito for body. Soothing pastel rainbow strip under nav.

**Goal:** Kid-oriented, cool and cute, yet professional.  
Suitable for classroom/projector use and teacher panels. Not babyish; not corporate. Friendly, clear, and trustworthy.

---

## 1. Design direction (in one line)

**“Friendly school”** — warm, clear, and inviting, with a touch of playfulness that kids like and teachers/coordinators still take seriously.

---

## 2. Tone & vibe

| Avoid | Aim for |
|-------|--------|
| Babyish (oversized cartoons, comic sans, rainbow overload) | **Playful but clear** — rounded shapes, soft colors, one or two fun accents |
| Cold or corporate (gray-heavy, sharp edges, sterile) | **Warm and approachable** — soft backgrounds, friendly icons, readable text |
| Cluttered or noisy | **Clean and spacious** — enough whitespace, clear hierarchy, one main action per area |
| Too dark for projectors | **Readable in class** — good contrast, not pure white flash; consider light theme for main classroom view |

**Mood words:** Friendly, clear, calm, cheerful, trustworthy, simple.

---

## 3. Color palette

### Option A — Soft & warm (recommended for “cool and cute yet pro”)

- **Primary:** Soft teal / mint (`#0d9488`, `#14b8a6`) — friendly, educational, easy on the eyes.
- **Secondary:** Warm coral or peach (`#f97316`, `#fb923c`) for highlights and CTAs — energetic but not harsh.
- **Background:** Off-white or very light gray (`#f8fafc`, `#f1f5f9`) — reduces glare on projectors.
- **Surface/cards:** White or near-white (`#ffffff`, `#f8fafc`) with a soft shadow — clean and professional.
- **Text:** Dark gray, not black (`#1e293b`, `#334155`) — readable, not harsh.
- **Muted text:** Medium gray (`#64748b`, `#94a3b8`).
- **Success:** Green (`#22c55e`, `#10b981`).
- **Caution / error:** Soft red or amber (`#ef4444`, `#f59e0b`).

**Why this works:** Feels like a modern learning app: warm, clear, and suitable for both kids and adults. Works well on projectors and small screens.

### Option B — Light with a pop of color

- Same as A, but **primary = one bold accent** (e.g. indigo `#6366f1` or violet `#8b5cf6`) for buttons and key UI. Rest stays neutral (grays + white). Feels a bit more “product” and less “playful,” but still friendly.

### Option C — Keep dark for teacher-only areas

- **Classroom / student-facing screens:** Light theme (Option A) — better for projectors and kids.
- **Teacher panel (setup, reports, etc.):** Keep current dark theme or a softer dark — so teachers get a distinct, “control panel” feel without affecting the classroom vibe.

**Recommendation:** Start with **Option A** for the main classroom/assembly/reading/quiz/attendance views; use the same palette for teacher area or a slightly darker variant (Option C) if you want a clear split.

---

## 4. Typography

- **Headings:** One friendly but readable sans-serif. Examples: **Nunito** (rounded, warm), **DM Sans** (clean, modern), **Plus Jakarta Sans** (professional with a soft feel). Avoid overly decorative or comic fonts.
- **Body:** Same font or a neutral system stack (e.g. `system-ui, -apple-system, sans-serif`) for body text. Size **at least 16px** for main content so it’s readable from the back of the class.
- **Hierarchy:** Clear difference between page title (e.g. 1.5rem–1.75rem), section titles (1.1rem–1.25rem), and body (1rem). Keep line height around 1.5–1.6 for readability.

**Example (Google Fonts):**  
`Nunito` for headings + `Nunito` or system UI for body. Load via link in `index.html` / layout; set in CSS variables.

---

## 5. Shapes & spacing

- **Rounded corners:** Buttons and cards with `border-radius: 12px–16px` (or 999px for pills). Avoid sharp rectangles for a friendlier feel.
- **Shadows:** Soft, subtle shadows (`0 2px 8px rgba(0,0,0,0.06)`) so cards feel light and professional, not flat or heavy.
- **Spacing:** Generous padding (e.g. 16px–24px) inside cards and between sections so the UI doesn’t feel cramped. Consistent gaps (e.g. 12px or 16px) between related elements.
- **Icons:** Rounded style (e.g. Font Awesome “solid” or similar) — same line weight and size group so the set feels consistent.

---

## 6. Imagery & illustrations

- **Style:** Simple, flat or soft 2D illustrations (not heavy 3D or noisy textures). Friendly characters or objects (e.g. book, lightbulb, star) used sparingly — for empty states, onboarding, or section headers.
- **Sources:** Use one consistent style (e.g. unDraw, Humaaans, or a small custom set). Avoid mixing many illustration styles.
- **Icons:** One icon set across the app (e.g. Font Awesome 6). Prefer outline or solid consistently; use color only for emphasis (e.g. primary color for main actions).

---

## 7. Components (buttons, cards, inputs)

- **Buttons:**  
  - Primary: solid background (primary color), white text, rounded (e.g. 12px), clear padding.  
  - Secondary: light background or outline.  
  - Hover: slight darkening or scale (e.g. `transform: scale(1.02)`) for feedback.
- **Cards:** White (or surface) background, rounded corners, soft shadow, clear padding. Optional: a colored left border or small icon for “explain / example / key point” type cards.
- **Inputs:** Rounded borders, enough height (e.g. 44px min) for touch, visible focus state (e.g. ring in primary color). Placeholder text in muted color.

Keep the same treatment across classroom and teacher screens so the product feels one cohesive “friendly school” theme.

---

## 8. Motion & feedback

- **Subtle only:** Light transitions (e.g. 0.2s ease) on hover/focus. Optional: gentle fade-in or slide-up when content appears (e.g. answer cards).
- **Avoid:** Flashy or distracting animations. No auto-playing motion that pulls focus from the lesson.
- **Feedback:** Clear loading states (spinner or skeleton) and success/error messages so kids and teachers know what’s happening.

---

## 9. Accessibility & projector use

- **Contrast:** Text and important UI meet WCAG AA (e.g. 4.5:1 for normal text). Use your palette’s dark gray on light background for body text.
- **Projector:** Prefer light background and dark text for main classroom views to avoid glare and improve readability in a dim room.
- **Touch targets:** Buttons and tappable areas at least 44px height/width where the app is used on tablets or phones.

---

## 10. Implementation order

1. **Define tokens** — In `app.css` (or your main stylesheet), set CSS variables for the chosen palette (primary, secondary, background, surface, text, muted, success, danger) and optionally font family.
2. **Apply to classroom** — Update the main classroom/explain view: background, cards, buttons, typography using the new tokens.
3. **Apply to shared nav** — Tabs, header, and footer so the whole app feels consistent.
4. **Apply to other flows** — Assembly, Reading, Quiz, Attendance with the same components and tokens.
5. **Teacher area** — Either same light theme or a soft dark variant; keep components and spacing consistent.
6. **Polish** — Icons, empty states, loading states, and one illustration set if you use imagery.

---

## 11. Quick reference — “cool, cute, professional”

| Element | Choice |
|--------|--------|
| **Overall** | Soft, warm light theme; “friendly school” |
| **Primary color** | Teal/mint (`#0d9488` / `#14b8a6`) |
| **Accent** | Warm coral/peach for CTAs (`#f97316` / `#fb923c`) |
| **Background** | Off-white / light gray (`#f8fafc`) |
| **Font** | Nunito or DM Sans (headings + body) |
| **Shapes** | Rounded (12–16px), soft shadows |
| **Icons** | One set, consistent weight (e.g. Font Awesome) |
| **Motion** | Subtle (0.2s), no flashy animation |
| **Imagery** | Simple, flat, used sparingly |

You can lock this in as the “SarvashikshaAI theme” and then implement step by step (tokens first, then screens) so the app stays cool, cute, and professional across all flows.
