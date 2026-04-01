# Feature: Leaderboard Module

## Purpose

Display ranked student performance for motivation and quick classroom recognition.

## Main Route

- `GET /leaderboard`

## Behavior

- Uses `LeaderboardService` to compute sorted rows.
- UI splits:
  - top 3 podium entries
  - remaining list below

## Data Inputs

Leaderboard calculations are derived from persisted classroom activity datasets (quiz responses, attendance, and related signals through service logic).

