# Feature: Teacher Workflow

## Purpose

Provide daily classroom operations for teachers: dashboard, learning tools, student setup, and AI explanation.

## Main Screens

- `GET /teacher/dashboard` -> teacher home dashboard
- `GET /teacher/learning` -> learning hub
- `GET /teacher/setup` -> student + assembly setup
- `GET /` -> projector-friendly AI explain page (`index.html`)

## Core Teacher Capabilities

### 1) AI Explain Topic

- Endpoint: `POST /explain`
- Input: topic + grade + action from `TeachingRequest`
- Service: `TeachingService`
- Output: child-friendly explanation (`TeachingResponse`) rendered on main screen.

### 2) Student Management

- Save/update student: `POST /teacher/students/save`
- Delete student: `POST /teacher/students/delete`
- Uses `StudentCrudService` and persists in `students` table.

### 3) Setup Page as Control Center

`/teacher/setup` combines:
- student roster editing
- assembly media URL configuration
- links to classroom modules

### 4) Assembly Link Configuration

- Endpoint: `POST /teacher/assembly/save`
- Persists anthem/prayer/pledge/hindi prayer URLs in `assembly_config`.

## Menu Experience

Teacher sidebar is fetched from backend (`GET /api/menu`) so navigation is centrally controlled by DB config.

