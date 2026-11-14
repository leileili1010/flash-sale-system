# 8. A React single-page app instead of server-rendered pages

- **Status:** Accepted
- **Date:** 2026-11-12

## Context

The reference project rendered Thymeleaf templates, copied from a Chinese-language marketplace
template, and pre-generated static HTML for item pages into the source tree at runtime. A flash
sale needs a highly interactive client: second-accurate countdowns, a buy button with many
states, polling while an order is created, and a payment timer.

## Decision

A React 19 + TypeScript single-page app (Vite, Tailwind CSS, TanStack Query, React Router)
talks to the backend's REST API. Product artwork is static files served with the app. The
catalog is cached in Redis on the server and polled by the client.

## Consequences

- The backend is a pure API: easier to scale, test and secure.
- The countdown uses the server's clock (offset measured from `/api/time`), so it ends exactly when
  the server starts accepting purchases.
- Error handling keys off stable problem `code`s rather than message text.
- Pre-generated static pages are no longer needed: hot catalog data comes from Redis, and the
  browser caches product responses.
