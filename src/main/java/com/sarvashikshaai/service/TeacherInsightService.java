package com.sarvashikshaai.service;

import com.sarvashikshaai.model.dto.ReadingLevel;
import com.sarvashikshaai.model.dto.StudentMetrics;
import com.sarvashikshaai.model.dto.TeacherInsightResponse;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TeacherInsightService {

    public enum PerformanceTag {
        EXCELLENT,
        AVERAGE,
        NEEDS_ATTENTION
    }

    private enum AttendanceLevel {
        LOW_ATTENDANCE,
        MODERATE_ATTENDANCE,
        GOOD_ATTENDANCE
    }

    private enum QuizLevel {
        STRUGGLING,
        AVERAGE,
        GOOD
    }

    private static final EnumMap<AttendanceLevel, List<String>> EN_ATTENDANCE = new EnumMap<>(AttendanceLevel.class);
    private static final EnumMap<AttendanceLevel, List<String>> HI_ATTENDANCE = new EnumMap<>(AttendanceLevel.class);

    private static final EnumMap<QuizLevel, List<String>> EN_QUIZ = new EnumMap<>(QuizLevel.class);
    private static final EnumMap<QuizLevel, List<String>> HI_QUIZ = new EnumMap<>(QuizLevel.class);

    private static final EnumMap<ReadingLevel, List<String>> EN_READING = new EnumMap<>(ReadingLevel.class);
    private static final EnumMap<ReadingLevel, List<String>> HI_READING = new EnumMap<>(ReadingLevel.class);

    static {
        EN_ATTENDANCE.put(AttendanceLevel.LOW_ATTENDANCE, List.of(
                "Low attendance is affecting academic progress.",
                "Student is frequently absent, impacting learning continuity.",
                "Attendance is insufficient and needs improvement."
        ));
        EN_ATTENDANCE.put(AttendanceLevel.MODERATE_ATTENDANCE, List.of(
                "Attendance is moderate, with some gaps in learning.",
                "Student attendance is okay, but consistency can improve.",
                "Some classes are missed; focus on better regularity."
        ));
        EN_ATTENDANCE.put(AttendanceLevel.GOOD_ATTENDANCE, List.of(
                "Attendance is good and supports steady learning.",
                "Student is coming regularly, which is helping performance.",
                "Good attendance means the student can build continuous understanding."
        ));

        HI_ATTENDANCE.put(AttendanceLevel.LOW_ATTENDANCE, List.of(
                "कम उपस्थिति की वजह से पढ़ाई का असर पड़ रहा है।",
                "छात्र अक्सर अनुपस्थित रहता है, इसलिए सीखने की कड़ी टूटती है।",
                "उपस्थिति कम है और इसमें सुधार की जरूरत है।"
        ));
        HI_ATTENDANCE.put(AttendanceLevel.MODERATE_ATTENDANCE, List.of(
                "उपस्थिति ठीक-ठाक है, लेकिन बीच में कुछ कमी रह जाती है।",
                "उपस्थिति अच्छी है, फिर भी नियमितता बढ़ाई जा सकती है।",
                "कुछ कक्षाएं छूटती हैं; नियमित रहने पर ध्यान दें।"
        ));
        HI_ATTENDANCE.put(AttendanceLevel.GOOD_ATTENDANCE, List.of(
                "उपस्थिति अच्छी है और पढ़ाई में लगातार प्रगति में मदद कर रही है।",
                "छात्र नियमित आ रहा है, इसलिए प्रदर्शन बेहतर हो रहा है।",
                "अच्छी उपस्थिति से समझ बनी रहती है।"
        ));

        EN_QUIZ.put(QuizLevel.STRUGGLING, List.of(
                "Quiz scores are low, so core concepts need reinforcement.",
                "Student is struggling with question basics; revise step-by-step.",
                "Quiz performance shows difficulty—practice targeted questions."
        ));
        EN_QUIZ.put(QuizLevel.AVERAGE, List.of(
                "Quiz scores are average; the student needs consistent practice.",
                "Student understands many topics, but accuracy can improve.",
                "With more practice, the quiz score can move into the good range."
        ));
        EN_QUIZ.put(QuizLevel.GOOD, List.of(
                "Quiz performance is good; continue challenging practice.",
                "Student is answering correctly and building confidence.",
                "Good quiz scores show strong grasp—use higher-level questions next."
        ));

        HI_QUIZ.put(QuizLevel.STRUGGLING, List.of(
                "क्विज़ स्कोर कम है, इसलिए बुनियादी अवधारणाओं को फिर से मजबूत करना होगा।",
                "छात्र को शुरुआती सवालों में दिक्कत हो रही है; कदम-दर-कदम दोहराएं।",
                "क्विज़ प्रदर्शन से पता चलता है कि अभ्यास की जरूरत है—लक्ष्य वाले सवालों पर काम करें।"
        ));
        HI_QUIZ.put(QuizLevel.AVERAGE, List.of(
                "क्विज़ स्कोर औसत है; लगातार अभ्यास की जरूरत है।",
                "छात्र कई टॉपिक समझता है, लेकिन सटीकता बढ़ाई जा सकती है।",
                "थोड़ा और अभ्यास करने पर स्कोर अच्छे स्तर तक जा सकता है।"
        ));
        HI_QUIZ.put(QuizLevel.GOOD, List.of(
                "क्विज़ प्रदर्शन अच्छा है; आगे चुनौतीपूर्ण अभ्यास जारी रखें।",
                "छात्र सही उत्तर दे रहा है और आत्मविश्वास बढ़ रहा है।",
                "अच्छे स्कोर से समझ मजबूत है—अब ऊंचे स्तर के सवाल कराएं।"
        ));

        EN_READING.put(ReadingLevel.LOW, List.of(
                "Reading level is LOW; focus on fundamentals and daily reading.",
                "Student needs help with word recognition and fluency.",
                "Reading requires improvement—start with short passages and regular practice."
        ));
        EN_READING.put(ReadingLevel.MEDIUM, List.of(
                "Reading level is MEDIUM; guided practice will improve fluency.",
                "Student can read, but comprehension and speed need work.",
                "Keep reading practice steady to reach a stronger reading level."
        ));
        EN_READING.put(ReadingLevel.HIGH, List.of(
                "Reading level is HIGH; build comprehension and expression.",
                "Student reads strongly—now focus on reasoning from the text.",
                "Strong reading is a good base; use longer passages and discussions."
        ));

        HI_READING.put(ReadingLevel.LOW, List.of(
                "पढ़ने का स्तर LOW है; बुनियाद मजबूत करें और रोज़ पढ़ने की आदत डालें।",
                "शब्द पहचान और पढ़ने की गति (फ्लुएंसी) में मदद चाहिए।",
                "पढ़ाई में सुधार जरूरी है—छोटे टेक्स्ट से शुरू करें और रोज़ अभ्यास करें।"
        ));
        HI_READING.put(ReadingLevel.MEDIUM, List.of(
                "पढ़ने का स्तर MEDIUM है; मार्गदर्शन के साथ अभ्यास से फ्लुएंसी बढ़ेगी।",
                "छात्र पढ़ सकता है, लेकिन समझ और गति पर काम करना होगा।",
                "नियमित पढ़ाई अभ्यास जारी रखें ताकि स्तर मजबूत हो।"
        ));
        HI_READING.put(ReadingLevel.HIGH, List.of(
                "पढ़ने का स्तर HIGH है; समझ और बोलने/अभिव्यक्ति पर फोकस करें।",
                "छात्र अच्छी तरह पढ़ता है—अब टेक्स्ट से कारण/उत्तर निकालने पर काम करें।",
                "पढ़ने की मजबूत नींव है; लंबे अंश और छोटी बातचीत कराएं।"
        ));
    }

    public TeacherInsightResponse generateTeacherInsight(StudentMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics must not be null");

        AttendanceLevel attendanceLevel = resolveAttendanceLevel(metrics.attendancePercentage());
        QuizLevel quizLevel = resolveQuizLevel(metrics.quizScore());
        ReadingLevel readingLevel = metrics.readingLevel();

        PerformanceTag tag = resolvePerformanceTag(metrics, attendanceLevel, quizLevel, readingLevel);

        String en = combine(
                englishTagSentence(tag),
                pickOne(EN_ATTENDANCE.get(attendanceLevel)),
                pickOne(EN_READING.get(readingLevel)),
                pickOne(EN_QUIZ.get(quizLevel))
        );

        String hi = combine(
                hindiTagSentence(tag),
                pickOne(HI_ATTENDANCE.get(attendanceLevel)),
                pickOne(HI_READING.get(readingLevel)),
                pickOne(HI_QUIZ.get(quizLevel))
        );

        return new TeacherInsightResponse(en, hi);
    }

    /**
     * Example usage method (no external dependencies, purely rule-based).
     */
    public TeacherInsightResponse exampleUsage() {
        StudentMetrics example = new StudentMetrics(58, 48, ReadingLevel.LOW);
        return generateTeacherInsight(example);
    }

    private static AttendanceLevel resolveAttendanceLevel(int attendancePercentage) {
        if (attendancePercentage < 60) return AttendanceLevel.LOW_ATTENDANCE;
        if (attendancePercentage <= 85) return AttendanceLevel.MODERATE_ATTENDANCE;
        return AttendanceLevel.GOOD_ATTENDANCE;
    }

    private static QuizLevel resolveQuizLevel(int quizScore) {
        if (quizScore < 50) return QuizLevel.STRUGGLING;
        if (quizScore <= 75) return QuizLevel.AVERAGE;
        return QuizLevel.GOOD;
    }

    private static PerformanceTag resolvePerformanceTag(StudentMetrics metrics,
                                                          AttendanceLevel attendanceLevel,
                                                          QuizLevel quizLevel,
                                                          ReadingLevel readingLevel) {
        // Combined score is based on the three signals.
        int attendanceScore = clamp01to100(metrics.attendancePercentage());
        int quizScore = clamp01to100(metrics.quizScore());
        int readingScore = readingToScore(readingLevel);

        double avg = (attendanceScore + quizScore + readingScore) / 3.0;
        if (avg >= 85) return PerformanceTag.EXCELLENT;
        if (avg >= 60) return PerformanceTag.AVERAGE;
        return PerformanceTag.NEEDS_ATTENTION;
    }

    private static int clamp01to100(int v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private static int readingToScore(ReadingLevel readingLevel) {
        return switch (readingLevel) {
            case LOW -> 40;
            case MEDIUM -> 70;
            case HIGH -> 95;
        };
    }

    private static String englishTagSentence(PerformanceTag tag) {
        return switch (tag) {
            case EXCELLENT -> "Overall performance: EXCELLENT.";
            case AVERAGE -> "Overall performance: AVERAGE.";
            case NEEDS_ATTENTION -> "Overall performance: NEEDS_ATTENTION.";
        };
    }

    private static String hindiTagSentence(PerformanceTag tag) {
        return switch (tag) {
            case EXCELLENT -> "कुल प्रदर्शन: अच्छा (EXCELLENT).";
            case AVERAGE -> "कुल प्रदर्शन: ठीक-ठाक (AVERAGE).";
            case NEEDS_ATTENTION -> "कुल प्रदर्शन: ध्यान चाहिए (NEEDS_ATTENTION).";
        };
    }

    private static String pickOne(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        int idx = ThreadLocalRandom.current().nextInt(messages.size());
        return messages.get(idx);
    }

    private static String combine(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(p.trim());
        }
        return sb.toString();
    }
}

