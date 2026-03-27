package com.sarvashikshaai.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.sarvashikshaai.model.Student;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Wraps Google Sheets and Drive API calls.
 * All methods accept the teacher's OAuth2 access token so each call
 * is made on behalf of the logged-in teacher.
 */
@Service
@Slf4j
public class GoogleSheetsService {

    private static final String APP_NAME = "SarvashikshaAI";

    // ── Internal DTO ─────────────────────────────────────────────────────────

    public record SheetInfo(String id, String name) {}

    /** Holds Hindi and English quote lists read from the "Thoughts" sheet tab. */
    public record ThoughtsData(List<String> hindi, List<String> english) {
        public boolean isEmpty() { return hindi.isEmpty() && english.isEmpty(); }
    }

    // ── Client builders ───────────────────────────────────────────────────────

    private Sheets buildSheetsClient(String accessToken) throws GeneralSecurityException, IOException {
        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessToken, Date.from(Instant.now().plusSeconds(3600))));
        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APP_NAME)
                .build();
    }

    private Drive buildDriveClient(String accessToken) throws GeneralSecurityException, IOException {
        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessToken, Date.from(Instant.now().plusSeconds(3600))));
        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APP_NAME)
                .build();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Lists all Google Sheets owned by or shared with the teacher.
     * Used in the setup page to let the teacher pick which sheet to use.
     */
    public List<SheetInfo> listSpreadsheets(String accessToken) {
        try {
            Drive drive = buildDriveClient(accessToken);
            List<File> files = drive.files().list()
                    .setQ("mimeType='application/vnd.google-apps.spreadsheet' and trashed=false")
                    .setFields("files(id,name)")
                    .setPageSize(50)
                    .execute()
                    .getFiles();

            return files == null ? List.of() :
                    files.stream().map(f -> new SheetInfo(f.getId(), f.getName())).toList();
        } catch (Exception e) {
            log.error("Failed to list spreadsheets: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Reads the "Students" tab from the teacher's sheet.
     *
     * Expected columns (row 1 = header, data from row 2):
     *   A: Name | B: Grade | C: Strength | D: Weakness | E: Notes | F: Active (Yes/No)
     */
    public List<Student> readStudents(String accessToken, String sheetId) {
        try {
            Sheets sheets = buildSheetsClient(accessToken);
            ValueRange response = sheets.spreadsheets().values()
                    .get(sheetId, "Students!A2:F200")
                    .execute();

            List<List<Object>> rows = response.getValues();
            if (rows == null || rows.isEmpty()) return List.of();

            List<Student> students = new ArrayList<>();
            for (List<Object> row : rows) {
                String name     = cell(row, 0);
                if (name.isBlank()) continue;           // skip empty rows
                String grade    = cell(row, 1);
                String strength = cell(row, 2);
                String weakness = cell(row, 3);
                String notes    = cell(row, 4);
                boolean active  = !cell(row, 5).equalsIgnoreCase("No");
                Student s = new Student();
                s.setName(name);
                s.setGrade(grade);
                s.setStrength(strength);
                s.setWeakness(weakness);
                s.setNotes(notes);
                s.setActive(active);
                students.add(s);
            }
            return students;

        } catch (Exception e) {
            log.error("Failed to read students from sheet {}: {}", sheetId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Reads the "Thoughts" tab from the teacher's sheet.
     *
     * Expected columns (row 1 = header, data from row 2):
     *   A: Language (Hindi / English, case-insensitive) | B: Quote text
     *
     * Returns a ThoughtsData with separate Hindi and English lists.
     * If the tab is missing, returns empty lists (caller falls back to JSON).
     */
    public ThoughtsData readThoughts(String accessToken, String sheetId) {
        List<String> hindi   = new ArrayList<>();
        List<String> english = new ArrayList<>();
        try {
            Sheets sheets = buildSheetsClient(accessToken);
            ValueRange response = sheets.spreadsheets().values()
                    .get(sheetId, "Thoughts!A2:B500")
                    .execute();

            List<List<Object>> rows = response.getValues();
            if (rows != null) {
                for (List<Object> row : rows) {
                    String lang  = cell(row, 0).trim().toLowerCase();
                    String quote = cell(row, 1).trim();
                    if (quote.isBlank()) continue;
                    if (lang.equals("hindi"))   hindi.add(quote);
                    else if (lang.equals("english")) english.add(quote);
                }
            }
            log.info("Thoughts tab: {} Hindi, {} English quotes", hindi.size(), english.size());
        } catch (Exception e) {
            log.warn("Thoughts tab not found or unreadable in sheet {}: {}", sheetId, e.getMessage());
        }
        return new ThoughtsData(hindi, english);
    }

    /**
     * Reads the "Assembly" tab from the teacher's sheet.
     *
     * Expected columns (row 1 = header, data from row 2):
     *   A: Section | B: YouTube URL
     *
     * Known section names (case-insensitive):
     *   National Anthem, Morning Prayer, Pledge, Hindi Prayer
     *
     * Returns a map of lowercase-trimmed section name → YouTube URL.
     */
    public Map<String, String> readAssemblyLinks(String accessToken, String sheetId) {
        Map<String, String> links = new java.util.LinkedHashMap<>();
        try {
            Sheets sheets = buildSheetsClient(accessToken);
            ValueRange response = sheets.spreadsheets().values()
                    .get(sheetId, "Assembly!A2:B20")
                    .execute();

            List<List<Object>> rows = response.getValues();
            if (rows == null || rows.isEmpty()) return links;

            for (List<Object> row : rows) {
                String section = cell(row, 0).trim().toLowerCase();
                String url     = cell(row, 1).trim();
                if (!section.isBlank() && !url.isBlank()) {
                    links.put(section, url);
                }
            }
        } catch (Exception e) {
            log.warn("Assembly tab not found or unreadable in sheet {}: {}", sheetId, e.getMessage());
        }
        return links;
    }

    /**
     * Appends one quiz result row to the "Quiz Scores" tab.
     *
     * Columns: Date | Student Name | Grade | Topic | Score | Result
     */
    public void appendQuizScore(String accessToken, String sheetId,
                                String studentName, String grade,
                                String topic, String score, String result) {
        try {
            Sheets sheets = buildSheetsClient(accessToken);
            List<List<Object>> values = List.of(List.of(
                    java.time.LocalDate.now().toString(),
                    studentName, grade, topic, score, result));
            ValueRange body = new ValueRange().setValues(values);
            sheets.spreadsheets().values()
                    .append(sheetId, "Quiz Scores!A:F", body)
                    .setValueInputOption("RAW")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute();
        } catch (Exception e) {
            log.error("Failed to append quiz score: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String cell(List<Object> row, int index) {
        if (row == null || index >= row.size()) return "";
        Object val = row.get(index);
        return val == null ? "" : val.toString().trim();
    }
}
