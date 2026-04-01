# Feature: Attendance Module

## Purpose

Track student attendance day-wise, allow updates, and export reports.

## Main Routes

- `GET /attendance` -> attendance page for a date
- `POST /attendance/mark` -> mark present/absent (AJAX)
- `GET /attendance/export` -> CSV export for date range

## Key Behavior

- One record per `(student_name, date)` enforced by unique constraint.
- Re-marking updates existing entry (idempotent behavior).
- Dashboard counters on page:
  - present count
  - absent count
  - unmarked count

## Export

- CSV includes: Date, Student Name, Status
- Date range defaults to recent window when not provided.
- UTF-8 BOM included for better spreadsheet compatibility.

## Persistence

- Table: `attendance`
- Entity: `AttendanceRecord`

