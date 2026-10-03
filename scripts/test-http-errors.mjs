// Run with Node 22+: node --env-file=.env scripts/test-http-errors.mjs
// Requires the local EAP deployment and a configured user with the upload role.
// Creates one 25 MiB test upload; rejected uploads must leave no stored content.
import assert from 'node:assert/strict';

const base = process.env.ITZ_TEST_API_URL ?? 'http://eap:8080/itz/api';
const tokenUrl = process.env.ITZ_TEST_TOKEN_URL ?? 'http://keycloak:8080/realms/itz/protocol/openid-connect/token';
const username = process.env.ITZ_TEST_USERNAME;
const password = process.env.ITZ_TEST_PASSWORD;
assert.ok(username && password, 'Set ITZ_TEST_USERNAME and ITZ_TEST_PASSWORD');
const requestId = 'http-error-smoke-test';
const timeout = () => AbortSignal.timeout(120000);

async function expectError(response, status, code, correlation = true) {
    assert.equal(response.status, status, `Expected HTTP ${status}, received ${response.status}`);
    assert.match(response.headers.get('content-type') ?? '', /^application\/json/);
    assert.equal(response.headers.get('cache-control'), 'no-store');
    const body = await response.json();
    assert.deepEqual(Object.keys(body).sort(), ['code', 'message']);
    assert.equal(body.code, code);
    assert.equal(typeof body.message, 'string');
    assert.doesNotMatch(JSON.stringify(body), /SECRET_MARKER|Exception|RESTEASY|ELY\d|stackTrace/);
    if (correlation) assert.equal(response.headers.get('x-request-id'), requestId);
    console.log(`PASS ${status} ${code}`);
    return response;
}

for (const path of ['/ping', '/files']) {
    for (const invalid of [false, true]) {
        const response = await fetch(base + path, {
            method: path === '/files' ? 'POST' : 'GET',
            headers: {'X-Request-ID': requestId, ...(invalid ? {Authorization: 'Bearer SECRET_MARKER'} : {})},
            signal: timeout(),
        });
        await expectError(response, 401, 'UNAUTHORIZED');
        assert.equal(response.headers.get('www-authenticate'), 'Bearer');
    }
}
const login = await fetch(tokenUrl, {
    method: 'POST',
    body: new URLSearchParams({client_id: 'itz-api', username, password, grant_type: 'password'}),
    signal: timeout(),
});
assert.equal(login.status, 200, 'Test login failed');
const {access_token: token} = await login.json();
assert.equal(typeof token, 'string');
const headers = {Authorization: `Bearer ${token}`, 'X-Request-ID': requestId};
const ping = await fetch(base + '/ping', {headers, signal: timeout()});
if (ping.status === 403) {
    await expectError(ping, 403, 'FORBIDDEN');
} else {
    assert.equal(ping.status, 200, `Expected HTTP 200 or 403, received ${ping.status}`);
    assert.equal(ping.headers.get('x-request-id'), requestId);
}
await expectError(await fetch(base + '/missing', {headers, signal: timeout()}), 404, 'NOT_FOUND');
const method = await expectError(await fetch(base + '/ping', {method: 'POST', headers, signal: timeout()}), 405, 'METHOD_NOT_ALLOWED');
assert.match(method.headers.get('allow') ?? '', /GET/);
await expectError(await fetch(base + '/ping', {headers: {...headers, Accept: 'text/plain'}, signal: timeout()}), 406, 'NOT_ACCEPTABLE');
await expectError(await fetch(base + '/files', {method: 'POST', headers: {...headers, 'Content-Type': 'text/plain'}, body: 'invalid', signal: timeout()}), 415, 'UNSUPPORTED_MEDIA_TYPE');
await expectError(await fetch(base + '/files', {method: 'POST', headers: {...headers, 'Content-Type': 'multipart/form-data'}, body: 'broken', signal: timeout()}), 400, 'BAD_REQUEST');
const missing = new FormData(); missing.append('other', 'test');
await expectError(await fetch(base + '/files', {method: 'POST', headers, body: missing, signal: timeout()}), 400, 'INVALID_UPLOAD');
for (const size of [25 * 1024 * 1024, 25 * 1024 * 1024 + 1, 28 * 1024 * 1024]) {
    const body = new FormData();
    body.append('file', new Blob([new Uint8Array(size)], {type: 'application/octet-stream'}), 'limit-test.bin');
    const response = await fetch(base + '/files', {method: 'POST', headers, body, signal: timeout()});
    if (size === 25 * 1024 * 1024) {
        const contentType = response.headers.get('content-type') ?? '';
        let stored;
        let resultCode = 'non-JSON response';
        if (contentType.startsWith('application/json')) {
            stored = await response.json();
            resultCode = typeof stored.code === 'string' ? stored.code : 'JSON response';
        }
        assert.equal(response.status, 201, `Exact-limit upload failed (${response.status}: ${resultCode})`);
        assert.equal(stored.size, size);
        assert.equal(stored.status, 'STORED');
        assert.equal(response.headers.get('x-request-id'), requestId);
        console.log('PASS 201 exact 25 MiB upload');
    } else {
        await expectError(response, 413, size === 25 * 1024 * 1024 + 1 ? 'FILE_TOO_LARGE' : 'PAYLOAD_TOO_LARGE');
    }
}
const boundary = 'http-error-chunked-boundary';
const encoder = new TextEncoder();
const prefix = encoder.encode(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="limit-test.bin"\r\nContent-Type: application/octet-stream\r\n\r\n`);
const suffix = encoder.encode(`\r\n--${boundary}--\r\n`);
const chunk = new Uint8Array(64 * 1024);
let prefixSent = false;
let sent = 0;
let suffixSent = false;
const fileSize = 28 * 1024 * 1024;
const streamedBody = new ReadableStream({
    pull(controller) {
        if (!prefixSent) {
            controller.enqueue(prefix);
            prefixSent = true;
        } else if (sent < fileSize) {
            const length = Math.min(chunk.length, fileSize - sent);
            controller.enqueue(chunk.subarray(0, length));
            sent += length;
        } else if (!suffixSent) {
            controller.enqueue(suffix);
            suffixSent = true;
        } else {
            controller.close();
        }
    },
});
await expectError(await fetch(base + '/files', {
    method: 'POST',
    headers: {...headers, 'Content-Type': `multipart/form-data; boundary=${boundary}`},
    body: streamedBody,
    duplex: 'half',
    signal: timeout(),
}), 413, 'PAYLOAD_TOO_LARGE');
const head = await fetch(base + '/missing', {method: 'HEAD', headers, signal: timeout()});
assert.equal(head.status, 404);
assert.equal(await head.text(), '');
console.log('PASS HEAD remains bodyless');
