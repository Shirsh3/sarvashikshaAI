package com.sarvashikshaai.ai;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic pre-LLM guard for clearly non-educational requests.
 * Used as a hard safety rail independent of model behavior.
 */
public final class StrictEducationalGuard {

    private StrictEducationalGuard() {}

    private static final Pattern RATE_PEOPLE = Pattern.compile(
            "(\\brate\\b|\\brating\\b|\\brate\\s+indian\\s+actress\\b|\\bhottest\\b|\\bhotter\\b|\\bhot\\b|\\bsexiest\\b|\\bbeautiful\\b|\\bprettiest\\b|\\bcompare\\b|\\bwho\\s+is\\s+better\\s+looking\\b)",
            Pattern.CASE_INSENSITIVE
    );

    /** Looks / body / figure questions about people (incl. typos like "figurre"). */
    private static final Pattern LOOKS_OR_FIGURE = Pattern.compile(
            "(\\bfigur+e?\\b|\\blooks\\b|\\bappearance\\b|\\bphysique\\b|\\bbody\\s+shape\\b|\\battractiveness\\b|\\bbeauty\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PERSON_OR_CELEBRITY = Pattern.compile(
            "(\\bactress\\b|\\bactor\\b|\\bcelebrity\\b|\\bstar\\b|\\bbollywood\\b|\\bhollywood\\b|\\bgirl\\b|\\bboy\\b|\\bwoman\\b|\\bman\\b|\\brai\\b|\\bkapoor\\b|\\bkhan\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GOSSIP = Pattern.compile(
            "(\\bgossip\\b|\\baffair\\b|\\bbreakup\\b|\\bdating\\b|\\bscandal\\b|\\bcontroversy\\b|\\bcelebrity\\s+news\\b|\\bfilm\\s+gossip\\b|\\bbollywood\\s+gossip\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ROMANTIC_ATTRACTION = Pattern.compile(
            "(\\bcrush\\b|\\bfalling\\s+in\\s+love\\b|\\bin\\s+love\\b|\\bromantic\\b|\\bromance\\b|\\bhow\\s+to\\s+impress\\s+(a|my)\\s+(boy|girl)\\b|\\brelationship\\s+advice\\b)",
            Pattern.CASE_INSENSITIVE
    );

    public enum IntentType {
        EDUCATIONAL,
        NON_EDUCATIONAL
    }

    public static IntentType classifyIntent(String input) {
        if (input == null || input.trim().isBlank()) {
            return IntentType.EDUCATIONAL;
        }
        String t = input.trim().toLowerCase(Locale.ROOT);
        boolean looksNonEducational = RATE_PEOPLE.matcher(t).find()
                || GOSSIP.matcher(t).find()
                || ROMANTIC_ATTRACTION.matcher(t).find()
                || (LOOKS_OR_FIGURE.matcher(t).find() && PERSON_OR_CELEBRITY.matcher(t).find())
                || (LOOKS_OR_FIGURE.matcher(t).find() && t.matches("(?s).*\\b(aishwarya|katrina|deepika|priyanka|alia|shraddha|anushka)\\b.*"));
        if (looksNonEducational) {
            return IntentType.NON_EDUCATIONAL;
        }
        return IntentType.EDUCATIONAL;
    }

    public static boolean isBlocked(String input) {
        return classifyIntent(input) == IntentType.NON_EDUCATIONAL;
    }

    public static String refusalMessage() {
        return "Only educational classroom content is allowed. Please ask a school-related question or topic.";
    }

    /** Same UX gate when Learning has a selected chapter but the ask is outside that PDF. */
    public static String notInSelectedChapterMessage() {
        return "Only educational classroom content from the selected chapter is allowed. "
                + "This topic was not found in your selected textbook. "
                + "Please ask about something from this chapter.";
    }
}
