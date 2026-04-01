# Feature: Super Admin Module

## Purpose

Centralize runtime configuration controls for role-based menu visibility and navigation behavior.

## Main Routes

- Page: `GET /superadmin`
- API: `GET /api/superadmin/menu-items`
- API: `POST /api/superadmin/menu-items`

## Access

- Restricted to `SUPER_ADMIN` role only.

## Configurable Fields

Per menu item, Super Admin can edit:
- `label` (display text)
- `href` (route path)
- `icon`
- `sortOrder`
- `enabledTeacher`
- `enabledAdmin`

## Runtime Effect

- `GET /api/menu` reads DB-backed configuration from `menu_items`.
- Teacher/Admin sidebars update based on current enabled flags and order.
- Disabled role flags hide menu items from that role's UI.

## Persistence

- Table: `menu_items`
- Updated via `SuperAdminMenuApiController`

