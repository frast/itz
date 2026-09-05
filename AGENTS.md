# AGENTS.md

## Purpose and scope

This file contains repository-wide instructions for coding agents. Keep changes small,
reviewable, and consistent with the existing Java 21, Maven, Jakarta EE, JBoss EAP 8.1,
Oracle 19c, hexagonal architecture, and domain-driven design (DDD) setup. More specific
`AGENTS.md` files in subdirectories may refine these rules for their subtree.

## Before changing code

- Read `README.md`, the root `pom.xml`, and the POM and surrounding code of every
  module you intend to change.
- Inspect the current working tree and preserve unrelated user changes.
- Prefer the smallest coherent solution. Do not introduce frameworks, dependencies,
  modules, or architectural abstractions without a concrete use case.
- If requirements affect business meaning, state the relevant domain assumptions. Do
  not silently invent business rules.

## Architecture and dependency rule

Dependencies point inward. The domain must remain independent of application servers,
databases, transport protocols, persistence models, and frameworks.

```text
adapters/primary  --> application/usecases --> application/domain
adapters/secondary ------------------------> application/domain
bundle            --> adapters (composition and deployment only)
```

- `application/domain`: aggregates, entities, value objects, domain services, domain
  events, business rules, and domain errors. Keep it plain Java with no Jakarta,
  persistence, REST, CDI, or infrastructure imports.
- `application/usecases`: application services and ports. Orchestrate a single business
  capability, transactions, authorization decisions, and calls to the domain. Keep
  business invariants in the domain, not in use cases.
- `adapters/primary`: inbound adapters such as REST. Translate transport DTOs into use
  case input, validate transport syntax, invoke one or more use cases, and map results
  and errors back to the protocol. No business logic and no JPA access.
- `adapters/secondary`: outbound adapters such as JPA. Implement outbound ports and map
  between domain objects and persistence entities. Never expose JPA entities outside
  the adapter.
- `bundle`: composition, packaging, EAP configuration, and deployment only. Do not put
  domain or application behavior here.
- Define ports on the inside that owns the contract: inbound use-case APIs in the
  application layer and outbound interfaces beside the use cases/domain that require
  them. Adapters depend on those interfaces; inner layers never depend on adapters.
- Do not bypass module boundaries for convenience. Any new Maven dependency must follow
  the dependency direction above and use versions managed by the root POM where possible.

## DDD modeling rules

- Organize new domain code by business capability or bounded context, not by technical
  type folders such as `entities`, `services`, or `utils`.
- Use the ubiquitous language consistently in class, method, event, API, and test names.
  Avoid vague suffixes and generic containers such as `Manager`, `Helper`, or `Common`.
- An aggregate root is the only entry point for changing its aggregate. Enforce
  invariants in constructors, named factories, and behavior methods; do not create
  anemic models with public setters.
- Prefer immutable value objects and records when identity and mutable lifecycle are not
  required. Validate them at creation so invalid instances cannot exist.
- Reference other aggregates by identity. Keep transactions and consistency boundaries
  small; use domain events for consequences that need not be atomic.
- Repositories model aggregate persistence, not tables or generic CRUD. Return domain
  types and use domain language in their contracts.
- Domain events describe completed business facts in past tense and contain domain data,
  not framework or persistence objects.
- Do not apply DDD patterns ceremonially. A simple immutable type or application service
  is preferable when the business behavior is genuinely simple.

## Java and Jakarta EE guidelines

- Target Java 21 and use language features that improve clarity without reducing EAP
  compatibility.
- Follow normal Java conventions: four-space indentation, one public top-level type per
  file, descriptive names, braces for control flow, and no wildcard imports.
- Favor small cohesive classes, constructor injection, immutable state, and explicit
  return types. Avoid field injection, service locators, global mutable state, and
  nullable values as implicit control flow.
- Field injection is permitted only for REST controllers/resources where the framework
  supplies the controller dependency; all other application and adapter components must
  use constructor injection (or an explicit framework callback such as `@Context`).
- Keep methods focused. Extract a concept when it has a domain name or removes genuine
  duplication; do not create abstractions for a single speculative future use.
- Use exceptions for exceptional failures, not expected branching. Map domain and
  application errors to HTTP responses only in the REST adapter.
- Keep CDI, JAX-RS, JPA, and transaction annotations at the application/adapter boundary.
  A framework-required no-argument constructor must have the narrowest valid visibility.
- Never return persistence entities from REST resources. Introduce explicit request and
  response DTOs when the wire contract differs from the domain model or requires
  independent evolution.
- Never log credentials, tokens, personal data, or full sensitive payloads. Do not commit
  `.env`, Oracle installers, generated database images, secrets, or build artifacts.

## Persistence and API changes

- Keep JPA mappings in `adapters/secondary/jpa`; map explicitly to and from domain types.
- Treat schema changes as compatibility-sensitive. The current local persistence unit
  uses `drop-and-create`; do not mistake that for a production migration strategy.
- Do not introduce Liquibase or change schema-generation behavior without updating the
  deployment configuration and `README.md` together.
- Preserve API compatibility unless the task explicitly permits a breaking change.
  Validate request shape at the REST boundary and domain invariants in the domain.
- Use correct HTTP semantics and stable error payloads. Do not leak stack traces,
  database details, or internal exception messages to clients.

## Testing strategy

- Add or update tests with every behavior change. Test observable behavior and business
  rules rather than private implementation details.
- Domain tests are fast, deterministic unit tests without CDI, EAP, databases, clocks,
  networks, or filesystem dependencies.
- Use-case tests exercise orchestration through fake or in-memory port implementations.
- Adapter tests verify mapping, validation, protocol/persistence behavior, and error
  translation. Use integration tests only where the framework or database behavior is
  material.
- Add architecture tests when practical to enforce the dependency rule and prevent
  Jakarta/JPA imports in the domain.
- Cover the happy path, boundary values, invalid input, invariant violations, and relevant
  failure paths. Avoid brittle tests of generated SQL, incidental ordering, or formatting.

## Build and verification

Run the narrowest useful check first, then the repository-level check before handoff:

```bash
./mvnw -pl <changed-module> -am test
./mvnw verify
```

Every implementation change must be verified with Maven, and the relevant build and
tests must finish successfully before handoff. Do not report an implementation as
complete when the Maven build or tests are failing.

For packaging changes also run:

```bash
./mvnw -pl bundle/ear -am package
```

Use `./mvnw -s .mvn/settings.xml -pl bundle/ear -am -Pdeploy-eap install` only when a running local EAP stack and
valid local credentials are available. Do not start containers, deploy, reset Oracle, or
run `docker compose down -v` unless the task requires it; `down -v` destroys local data.

If a check cannot be run, report exactly which command was skipped or failed and why.
Do not claim verification from code inspection alone.

## Documentation and handoff

- Update `README.md` and relevant configuration in the same change when setup, runtime
  behavior, API contracts, environment variables, or operational steps change.
- Comments should explain non-obvious intent, invariants, or trade-offs, not restate code.
- Record consequential architectural decisions in an ADR if an ADR convention is added;
  do not hide long-term architecture decisions in source comments.
- At handoff, summarize the behavior changed, identify affected modules, list verification
  performed, and call out remaining risks or assumptions.
