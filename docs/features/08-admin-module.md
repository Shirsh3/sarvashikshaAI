# Feature: Admin Module

## Purpose

Provide administrative visibility: school-wide dashboard, charts, analytics, and searchable student insights.

## Main Routes

- `GET /admin` -> admin dashboard page
- `GET /admin/analytics` -> student analytics page
- `GET /admin/students` -> active student list

### JSON APIs

- `GET /admin/api/grades`
- `GET /admin/api/dashboard/summary`
- `GET /admin/api/dashboard/charts`
- `GET /admin/api/students/search`
- `GET /admin/api/analytics/student`

## Key Metrics

- active student count
- quiz counts
- response counts
- attendance rows / attendance percentage
- per-student trend and mastery signals

## Services Used

- `AdminDashboardService`
- `AdminAnalyticsService`
- repositories for attendance, quizzes, responses, students, grade reference

## Filters

- date range (`from`, `to`)
- grade filter (`KG`, `UKG`, `1..12`)

