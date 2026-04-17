# AI Workflow — Quick Reference

**Purpose:** One-page reference for AI agents working on Football OS Phase 0.

---

## File Structure

```
ai-workflow/
├── AI-CONTEXT.md           ← Read this first (project overview)
├── PHASE0-STATUS.md        ← Update this (track progress)
├── DECISIONS.md            ← Check this (resolved ambiguities)
├── SPRINT-001-*.md         ← Sprint 0.1 details
├── SPRINT-002-*.md         ← Sprint 0.2 details
├── TASKS-001-*.md          ← Individual task instructions
└── QUICK-REFERENCE.md      ← This file
```

---

## Before Starting Work

1. Read `AI-CONTEXT.md` — Understand the project
2. Check `PHASE0-STATUS.md` — See what's done
3. Read relevant `SPRINT-XXX-*.md` — Understand sprint goals
4. Read relevant `TASKS-XXX-*.md` — Get exact instructions
5. Check `DECISIONS.md` — Resolve ambiguities

---

## After Completing Work

1. Run `mvn clean install -DskipTests` — Verify build
2. Run tests — All must pass
3. Update `PHASE0-STATUS.md` — Mark task complete
4. Commit — `git commit -m "feat: <description>"`

---

## Common Commands

```bash
# Build
mvn clean install

# Run service
cd fos-governance-service && mvn spring-boot:run

# Run tests
mvn test

# Start infrastructure
docker-compose up -d

# Stop infrastructure
docker-compose down

# Check health
curl http://localhost:8081/actuator/health
```

---

## Ports

| Service | Port |
|---------|------|
| Governance | 8081 |
| Gateway | 8080 |
| Keycloak | 8180 |
| PostgreSQL | 5432 |
| MongoDB | 27017 |
| Kafka | 9092 |

---

## Sprint Order

```
0.1 → 0.2 → 0.3 → 0.4 → 0.5 → 0.6
```

Don't skip sprints.

---

## When Blocked

1. Check `DECISIONS.md` — May be answered
2. Add question to `DECISIONS.md` — Under "Open Questions"
3. Continue with other tasks if possible
