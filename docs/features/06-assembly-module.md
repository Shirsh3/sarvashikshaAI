# Feature: Assembly Module

## Purpose

Run structured school assembly with configurable videos, daily thoughts, and language content.

## Main Routes

- `GET /assembly` -> assembly screen
- `POST /assembly/regenerate` -> regenerate thought pool

## Content Shown

- Thought of the day (Hindi + English)
- Word of the day (English/Hindi)
- Habit of the day
- Assembly text blocks:
  - anthem
  - pledge
  - prayer

## Video Support

Reads configured links and resolves YouTube IDs for:
- anthem
- pledge
- morning prayer
- hindi prayer

`YouTubeEmbedCheckService` validates embeddability and helps avoid blank iframes.

## Configuration Source

- Teacher-managed links in `assembly_config` via setup screen.
- Thought pool managed via `ThoughtConfigService` and thought data model.

