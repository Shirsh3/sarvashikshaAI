# Feature: Authentication and Role-Based Access

## Purpose

Provide secure login/logout and control which pages/APIs a user can access based on role.

## Roles

- `TEACHER`
- `ADMIN`
- `SUPER_ADMIN`

## Login Flow

1. User opens `GET /login`.
2. Frontend posts credentials to `POST /api/auth/login`.
3. Backend authenticates using Spring Security `AuthenticationManager`.
4. Backend issues JWT token and stores it in HTTP-only cookie (`jwt.cookie-name`).
5. Frontend redirects based on returned role:
   - `TEACHER` -> `/teacher/dashboard`
   - `ADMIN` -> `/admin`
   - `SUPER_ADMIN` -> `/superadmin`

## Logout Flow

- API logout: `POST /api/auth/logout` clears auth cookie.
- UI logout link: `GET /logout` clears cookie and redirects to `/login`.

## Authorization Rules (SecurityConfig)

- Public:
  - `/login`, static assets, `/`, `/index.html`
  - `/api/auth/login`, `/api/auth/logout`
- Protected:
  - `/teacher/**` -> `TEACHER`, `ADMIN`, `SUPER_ADMIN`
  - `/admin/**` -> `ADMIN`, `SUPER_ADMIN`
  - `/superadmin/**` and `/api/superadmin/**` -> `SUPER_ADMIN` only
  - Other `/api/**` require authentication

## Token Handling

- JWT is validated by `JwtAuthenticationFilter`.
- Filter sets authenticated principal in Spring Security context.
- API requests return JSON `401` when unauthorized; page requests redirect to `/login`.

## Bootstrap Accounts

When `auth.bootstrap.enabled=true`, startup seeding creates:
- teacher / teacher123
- admin / admin123
- superadmin / superadmin123

Credentials are environment/property driven and can be overridden.

