# 9. Load-test the containerized backend inside Linux

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

The first load tests ran the backend on the Windows host and k6 in a container. Between 0 and
1,683 of 2,000 simultaneous connections were refused, varying from run to run, while every request
that reached the application was handled correctly. Windows caps a socket's listen backlog at
about 200, and Docker Desktop's port proxy adds its own limits, so the measurements described the
developer laptop, not the system.

## Decision

Ship a production-style container for the backend (`backend/Dockerfile`: multi-stage, layered jar,
non-root, Temurin JRE) and a Compose `app` profile that sets `net.core.somaxconn=8192`. Load tests
run k6 in a container on the same Docker network against `http://backend:8080`. Tomcat's
`accept-count` is raised from 100 to 10,000.

## Consequences

- Connection errors under a 5,000-shopper burst dropped to zero; results are reproducible.
- The whole backend can run with Docker Compose, not only its dependencies.
- The load generator still shares the machine with the system under test; `performance.md` treats
  the numbers as a lower bound and lists a multi-machine benchmark as a next step.
