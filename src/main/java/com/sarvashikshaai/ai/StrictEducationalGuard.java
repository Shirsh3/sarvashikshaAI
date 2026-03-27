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

    private static final Pattern GOSSIP = Pattern.compile(
            "(\\bgossip\\b|\\baffair\\b|\\bbreakup\\b|\\bdating\\b|\\bscandal\\b|\\bcontroversy\\b|\\bcelebrity\\s+news\\b|\\bhollywood\\s+gossip\\b|\\bbollywood\\s+gossip\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ROMANTIC_ATTRACTION = Pattern.compile(
            "(\\bcrush\\b|\\bfalling\\s+in\\s+love\\b|\\bin\\s+love\\b|\\bromantic\\b|\\bromance\\b|\\bhow\\s+to\\s+impress\\s+(a|my)\\s+(boy|girl)\\b|\\brelationship\\s+advice\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EDUCATIONAL_HINTS = Pattern.compile(
            "(\\bgrade\\b|\\bclass\\b|\\bchapter\\b|\\blesson\\b|\\bquestion\\b|\\bexplain\\b|\\bquiz\\b|\\bmcq\\b|\\btrue\\s*/?\\s*false\\b|\\bshort\\s+answer\\b|\\bscience\\b|\\bmath\\b|\\bmathematics\\b|\\bbiology\\b|\\bchemistry\\b|\\bphysics\\b|\\bhistory\\b|\\bgeography\\b|\\bgrammar\\b|\\bvocabulary\\b|\\bncert\\b|\\bpassage\\b|\\breading\\b)",
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
                || ROMANTIC_ATTRACTION.matcher(t).find();
        boolean looksEducational = EDUCATIONAL_HINTS.matcher(t).find();
        if (looksNonEducational && !looksEducational) {
            return IntentType.NON_EDUCATIONAL;
        }
        return looksNonEducational ? IntentType.NON_EDUCATIONAL : IntentType.EDUCATIONAL;
    }

    public static boolean isBlocked(String input) {
        return classifyIntent(input) == IntentType.NON_EDUCATIONAL;
    }

    public static String refusalMessage() {
        return "Only educational classroom content is allowed. Please ask a school-related question or topic.";
    }
}
