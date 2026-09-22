---
name: rest-api-contract-change
description: Use when changing a REST endpoint's path, method, request or response shape, multipart field, status code, or OpenAPI contract in this Java project.
---

# REST API contract changes

Use the OpenAPI document as the source of truth for the public REST contract. First inspect the current schema and implementation: the requested behavior or field may already exist.

For a contract change:

1. Read the relevant REST module POM, OpenAPI operation, resource, mapper, and tests. Trace into the use case and domain only when the behavior or source of response data requires it.
2. Trace the source of each requested value and assess compatibility. If a value has no defined source, state the missing decision and offer conditional options or ask; do not present one as the assumed implementation. Identify breaking changes clearly and do not invent domain behavior.
3. Edit `adapters/primary/rest/src/main/openapi/openapi.yaml`, then regenerate and inspect generated signatures:

   ```bash
   ./mvnw -pl adapters/primary/rest -am generate-sources
   ```

   Generated files under `target/generated-sources/openapi` are outputs; do not edit them by hand.
4. Keep transport syntax checks and wire mapping in the REST adapter, and business invariants in the domain. Add or update adapter tests for the contract and its error cases; add deeper tests only where behavior changes.
5. Update README and `requests/requests.http` when public examples or documented behavior change. Run the narrow REST reactor tests, then `./mvnw verify` before handoff.

Report the resulting wire-contract change, compatibility impact, and the verification commands that passed or could not run. Follow the repository's `AGENTS.md` for environment-operation boundaries.
