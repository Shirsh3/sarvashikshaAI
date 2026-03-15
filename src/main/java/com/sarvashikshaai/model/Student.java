package com.sarvashikshaai.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A student profile read from the teacher's Google Sheet (Students tab).
 * Not a JPA entity — lives in an in-memory cache, refreshed from Sheets.
 *
 * Expected sheet columns (row 1 = header, data from row 2):
 *   A: Name | B: Grade | C: Strength | D: Weakness | E: Notes | F: Active (Yes/No)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Student {
    private String name;
    private String grade;
    private String strength;
    private String weakness;
    private String notes;
    private boolean active;

    /** Returns a short context string injected into AI prompts. */
    public String toPromptContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("Student: ").append(name).append(", ").append(grade).append(". ");
        if (strength  != null && !strength.isBlank())  sb.append("Strength: ").append(strength).append(". ");
        if (weakness  != null && !weakness.isBlank())  sb.append("Weakness: ").append(weakness).append(". ");
        if (notes     != null && !notes.isBlank())     sb.append("Note: ").append(notes).append(". ");
        return sb.toString();
    }
}
