# HTTP Error Handling Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking. Obtain design review before implementation; this document proposes the design and does not authorize deployment changes beyond the repository rules.

**Goal:** Preserve HTTP semantics while returning predictable, safe JSON errors throughout the application's controllable HTTP boundary.

**Architecture:** Keep error translation in the REST adapter. Use a shared response policy, separate HTTP-exception handling, and a clearly named fallback for unexpected exceptions. Handle pre-JAX-RS errors at the servlet/EAP boundary, with bundle changes limited to configuration and composition.

**Tech Stack:** Java 21, Jakarta REST 3.1, Servlet 6, RESTEasy 6.2 supplied by the EAP BOM, JUnit, existing Maven tooling.

**Spec:** The user's request in this session: preserve HTTP statuses and relevant headers, standardize JSON, avoid exception-detail disclosure, and verify through RESTEasy. The proposed design below makes those requirements concrete.

## Global constraints

- Preserve the existing JSON object `{ "code": "...", "message": "..." }`, existing upload error codes, and successful response behavior. Do not migrate to Problem Details in this change.
- No stack traces, exception/cause messages, class names, rejected values, SQL, filesystem paths, token details, or upstream response bodies in client responses.
- Keep business rules and domain/application exceptions free of HTTP concerns.
- No new runtime framework. Add BOM-managed test providers only where needed to exercise actual multipart parsing and JSON serialization.
- Do not catch `Throwable` to claim recovery from VM errors. Do not unwrap arbitrary cause chains and guess their status.
- No Compose operations or independent schema resets. Use the authorized deployment command only on the existing EAP stack.
- JSON normalization covers uncommitted error responses under `/itz/api`. HEAD remains bodyless. Transport failures, disconnected clients, already committed responses, and errors outside the deployed application's boundary cannot guarantee JSON.

## Proposed design

### Response policy

Create `ApiErrorResponses` in the REST package as a small internal policy for fixed public messages and response construction. Keep correlation in `X-Request-ID`; do not add fields to the existing error DTO. Use `Cache-Control: no-store` for error responses.

| Condition | Status | Code | Fixed public message |
| --- | --- | --- | --- |
| Invalid HTTP request | 400 | BAD_REQUEST | The request is invalid |
| Missing/invalid authentication | 401 | UNAUTHORIZED | Authentication is required |
| Insufficient permission | 403 | FORBIDDEN | The current user is not allowed to perform this operation |
| Unknown resource | 404 | NOT_FOUND | The requested resource was not found |
| Wrong HTTP method | 405 | METHOD_NOT_ALLOWED | The HTTP method is not allowed for this resource |
| Unsupported response representation | 406 | NOT_ACCEPTABLE | The requested response format is not supported |
| Generic payload limit | 413 | PAYLOAD_TOO_LARGE | The request payload is too large |
| Unsupported request representation | 415 | UNSUPPORTED_MEDIA_TYPE | The request content type is not supported |
| Rate limit, if raised | 429 | TOO_MANY_REQUESTS | Too many requests |
| Service unavailable, if raised | 503 | SERVICE_UNAVAILABLE | The service is temporarily unavailable |
| Unexpected failure | 500 | REQUEST_FAILED | The request could not be completed |

Keep existing upload-specific mappings and messages for INVALID_UPLOAD, FILE_TOO_LARGE, FILE_INFECTED, and UPLOAD_FAILED. Other 4xx statuses retain their status with REQUEST_REJECTED / "The request was rejected"; other 5xx retain their status with REQUEST_FAILED / "The request could not be completed". Adding 429/503 mapping does not introduce rate limiting or retries.

For API errors, JSON is the fixed error representation, including a 406 caused by an incompatible Accept header. Do not renegotiate the error recursively. Preserve bodyless HEAD behavior and leave successful responses, redirects, 204, 304, and automatic OPTIONS alone.

### Provider responsibilities

- Rename the current upload-wide runtime mapper to `ApplicationExceptionMapper`: explicitly handle the known application/domain failures and map only unknown failures to sanitized 500. Retain the existing specific permission mapper or consolidate it only if tests prove unchanged provider selection.
- Add `HttpExceptionMapper implements ExceptionMapper<WebApplicationException>`: preserve 4xx/5xx statuses, rebuild the body from the fixed policy, and never copy an exception message or entity.
- Do not assume that this mapper intercepts all errors. Entity-bearing WebApplicationExceptions and specific built-in RESTEasy providers can bypass a general mapper. Add a final `ApiErrorResponseFilter` for JAX-RS 4xx/5xx responses to enforce the policy on these paths. It preserves approved status/code pairs but reconstructs their message from the fixed catalog; all other entities become generic safe errors. Coordinate its response-filter priority with correlation handling.
- Preserve only explicitly supported protocol headers: Allow for 405, a sanitized Bearer WWW-Authenticate challenge for 401, and validated Retry-After values for 429/503. Keep multi-valued Allow semantics. Rebuild Content-Type and discard stale Content-Length, Content-Encoding, entity validators, exception cookies, and diagnostic headers. Never blindly copy all headers. Add any additional header only with a concrete endpoint need and a regression test.
- Unknown exceptions are logged once using a safe category and correlation ID. Expected 4xx errors do not emit ERROR stack traces. Never pass raw exceptions to the ordinary application logger; their cause chains can contain sensitive data. Do not add a diagnostics endpoint.

### Container boundary

The current EAP returns HTML 401 with Bearer challenges and returned HTML 500 for the previous oversized-upload probe. These observations are not proof of the oversized failure's root cause.

Characterize the actual EAP error dispatch and multipart limits before changing configuration. For errors reaching the web application, add a minimal servlet error endpoint in the REST adapter and wire error pages in the WAR descriptor. It must not depend on authenticated user context, expose an externally usable error-details endpoint, or recursively trigger authentication. Use the same fixed error policy and preserve a sanitized Bearer challenge.

If EAP's OIDC response bypasses servlet error dispatch, an error page alone is insufficient: identify and document a supported EAP integration before claiming uniform JSON authentication failures. Do not weaken authentication or buffer every response as a workaround. Report any proven boundary limitation explicitly.

For upload limits, inspect actual listener, servlet, and RESTEasy multipart settings and server diagnostics. Keep the 25 MiB business limit; establish bounded transport limits with multipart overhead allowance. Verify exact-limit and limit-plus-one behavior. Do not disable size limits globally or translate every parser IOException into 413.

## Review focus

- Entity-bearing exceptions and built-in provider precedence must not bypass sanitization (Task 2).
- Protocol headers must survive without carrying token/provider diagnostic details (Tasks 1–3).
- HEAD, OPTIONS, redirects, and incompatible Accept headers must retain protocol semantics (Task 2).
- OIDC error dispatch must not loop or bypass security (Task 3).
- Upload size rejection and failures after response commitment must not be misclassified (Tasks 3–4).

## Task 1: Establish fixed error policy and mapper tests

**Files:** REST package `ApiErrorResponses.java`, `ApplicationExceptionMapper.java`, `HttpExceptionMapper.java`; existing `UploadExceptionMapper.java`, `ForbiddenExceptionMapper.java` and their tests. Update direct references in `FilesResourceTest.java`.

**Interfaces:** Shared `Response create(int status, String code)` constructs the approved DTO and fixed message; HTTP mapping adds only the supported validated headers.

- [ ] Write failing tests for status/code/message mapping, existing upload codes, unknown exceptions, exception bodies/messages containing a unique secret marker, multi-valued Allow, Bearer challenge sanitization, Retry-After seconds/date, and discarded stale/body-sensitive headers.
- [ ] Run `./mvnw -pl adapters/primary/rest -am test` and confirm failures demonstrate the current behavior.
- [ ] Implement the policy and mappers. Remove unconditional raw-exception logging. Test captured logs for the secret marker and duplicate logging.
- [ ] Rerun narrow tests; every supported error has the correct status and safe DTO.

## Task 2: Verify and normalize real RESTEasy error paths

**Files:** Add `ApiErrorResponseFilter.java`, `HttpErrorHandlingTest.java`, and `ApiErrorResponseFilterTest.java` in the REST module; adjust its POM only for required BOM-managed test providers.

**Interfaces:** The response filter uses Task 1's policy and runs on JAX-RS error responses without altering successful responses.

- [ ] Use RESTEasy's dispatcher and actual JSON/multipart providers, with test-only resources and directly constructed fake use cases. Avoid booting Weld solely for these behavior tests.
- [ ] Before implementation, reproduce 404, 405, 406, 415, malformed multipart 400, and synthetic 500 through request dispatch. Assert serialized JSON, not just a returned Java object.
- [ ] Test entity-bearing WebApplicationException responses, a built-in provider error, a checked resource exception, and a response containing a forged ErrorResponse message. Assert no secret marker in response body or headers.
- [ ] Implement normalization and only the additional specific provider mappings required by demonstrated RESTEasy behavior. Verify client input failures remain 400 while server serialization/return-value validation failures remain 500.
- [ ] Add tests for HEAD body suppression, automatic OPTIONS/Allow, redirects, explicit 204/304 responses, 406 with incompatible Accept, and correlation on success and error responses.
- [ ] Run narrow tests and confirm provider precedence with the BOM-selected RESTEasy version. Use a real serialization provider, not a test-only error writer that masks production behavior.

## Task 3: Standardize controllable EAP errors and upload limits

**Files:** REST adapter servlet/error-boundary implementation and tests; `bundle/war/src/main/webapp/WEB-INF/web.xml`; relevant module POMs; EAP configuration only if diagnosis requires it.

**Interfaces:** Container error handling consumes status only, ignores error message/exception request attributes, and emits Task 1's fixed JSON envelope.

- [ ] Capture authenticated and unauthenticated baseline responses and inspect EAP diagnostics/limits without printing credentials, tokens, or sensitive log payloads.
- [ ] Implement and test the supported error-dispatch integration. Verify direct access cannot reveal error attributes, authentication still applies to API endpoints, and no dispatch loops occur.
- [ ] Diagnose the oversized-upload 500 before changing limits; add focused regression coverage for the identified cause and bounded configuration.
- [ ] Deploy to existing EAP and verify missing/invalid token 401 JSON + Bearer, insufficient-role 403 JSON, route 404, method 405 + Allow, malformed multipart 400, 25 MiB success, and 25 MiB + 1 byte 413 JSON. Also test rejection above the transport cap. Use synthetic upload data and configured test credentials only.
- [ ] Retain explicit documented exceptions for failures outside the application's control or after commitment. If authentication JSON cannot be achieved through supported integration, stop and present the concrete limitation before marking the overall goal complete.

## Task 4: Contract, verification, and handoff

**Files:** `adapters/primary/rest/src/main/openapi/openapi.yaml`, `README.md`, `requests/requests.http`; repeatable local smoke-test script under `scripts/` if needed.

- [ ] Document fixed codes/messages, JSON media types, preserved headers, 404/405/406/415 behavior, and tested container boundaries. Remove HTML response alternatives only after live tests prove the new behavior.
- [ ] Regenerate with `./mvnw -pl adapters/primary/rest -am generate-sources`; inspect signatures and preserve EntityPart multipart mapping.
- [ ] Run sequentially: `./mvnw -pl adapters/primary/rest -am test`, `./mvnw verify`, and `./mvnw -pl bundle/ear -am package`.
- [ ] Deploy using `./mvnw -s .mvn/settings.xml -pl bundle/ear -am -Pdeploy-eap install` and rerun live contract checks. Do not run Maven builds concurrently against shared output directories.
- [ ] Request read-only architecture-reviewer review of the completed diff because REST and bundle boundaries are involved. Address architecture, authentication, header, disclosure, and test risks.
- [ ] Handoff includes changed behavior, exact passing commands, live-test matrix, and remaining boundary limitations. Do not claim all errors are JSON when the response is already committed or the request never reaches the app.

## References

- [Jakarta REST 3.1 specification](https://jakarta.ee/specifications/restful-ws/3.1/jakarta-restful-ws-spec-3.1.pdf): exception mapping, entity-bearing responses, HEAD behavior.
- [RESTEasy 6.2 exception handling](https://docs.jboss.org/resteasy/docs/6.2.2.Final/userguide/html/ch30.html): built-in exceptions, provider precedence, sanitized upstream responses.

## Execution

Recommended: implement sequentially in this session, with the repository-required architecture review after REST/bundle integration. Tasks share response policy and runtime evidence, so parallel implementation provides little benefit. This plan is for review; application code is unchanged.
