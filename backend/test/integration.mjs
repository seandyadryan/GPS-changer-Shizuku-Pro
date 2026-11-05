import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, randomUUID, sign } from 'node:crypto';

const base = process.env.NFX_TEST_URL;
const adminPassword = process.env.NFX_TEST_ADMIN_PASSWORD;
assert(base && adminPassword, 'Provide the URL and admin password for a disposable test database');

function device(label) {
    const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    return {
        label, publicKey: publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
        sign: (message) => sign('sha256', Buffer.from(message), privateKey).toString('base64'),
    };
}

async function call(method, path, body, session) {
    const raw = body === undefined ? '' : JSON.stringify(body);
    const headers = { 'Content-Type': 'application/json' };
    if (session) {
        const time = Date.now();
        const hash = createHash('sha256').update(raw).digest('hex');
        headers.Authorization = `Bearer ${session.token}`;
        headers['X-NFX-Time'] = time.toString();
        headers['X-NFX-Proof'] = session.device.sign(`nfx-request-v1:${session.token}:${method}:${path}:${time}:${hash}`);
    }
    const response = await fetch(new URL(path, base), { method, headers, body: raw || undefined });
    return { status: response.status, json: await response.json() };
}

async function login(device, login, password, requestTransfer = false) {
    const challenge = await call('POST', '/v1/challenge', {});
    assert.equal(challenge.status, 200);
    const { nonce } = challenge.json;
    const input = { login, password, deviceKey: device.publicKey, deviceLabel: device.label, nonce,
        signature: device.sign(`nfx-login-v1:${nonce}`), requestTransfer };
    const result = await call('POST', '/v1/login', input);
    return { ...result, session: { token: result.json.token, device }, input };
}

const adminDevice = device('Admin test device');
const one = device('Device A');
const two = device('Device B');
const phone = '081000002345';
const password = randomUUID();

assert.equal((await login(adminDevice, 'admin', randomUUID())).json.code, 'INVALID_CREDENTIALS');
const admin = await login(adminDevice, 'admin', adminPassword);
assert.equal(admin.status, 200);
assert.equal(admin.json.account.role, 'admin');
assert.equal((await call('POST', '/v1/login', admin.input)).json.code, 'INVALID_DEVICE_PROOF');

const user = await call('POST', '/v1/admin/users', { login: phone, password }, admin.session);
assert.equal(user.status, 201);
assert.equal(user.json.login, '+6281000002345');
assert.equal((await call('POST', '/v1/admin/users', { login: user.json.login, password }, admin.session)).json.code, 'ACCOUNT_EXISTS');
assert.equal((await login(one, phone, password)).json.code, 'SUBSCRIPTION_EXPIRED');

const path = `/v1/admin/users/${user.json.id}/subscription`;
const grant = { days: 30, requestId: randomUUID() };
const initial = await call('POST', path, grant, admin.session);
assert.equal(initial.status, 200);
assert(Date.parse(initial.json.subscriptionUntil) > Date.now());
const repeated = await call('POST', path, grant, admin.session);
assert.equal(repeated.json.subscriptionUntil, initial.json.subscriptionUntil);

const [first, second] = await Promise.all([login(one, phone, password), login(two, phone, password)]);
assert.deepEqual([first.status, second.status].sort(), [200, 409]);
const old = first.status === 200 ? first : second;
const replacement = first.status === 200 ? two : one;
assert.equal((await call('GET', '/v1/admin/users', undefined, old.session)).status, 403);
assert.equal((await call('GET', '/v1/session', undefined, { ...old.session, device: replacement })).status, 401);
const pending = await login(replacement, phone, password, true);
assert.equal(pending.json.code, 'TRANSFER_PENDING');
assert.equal((await login(replacement, phone, password)).json.code, 'TRANSFER_PENDING');
assert.equal((await call('GET', '/v1/session', undefined, old.session)).status, 200);

let list = await call('GET', '/v1/admin/users', undefined, admin.session);
assert.equal(list.json.transfers.length, 1);
assert.equal(list.json.transfers[0].deviceCode, pending.json.deviceCode);
assert.equal((await call('POST', `/v1/admin/transfers/${list.json.transfers[0].id}`, { approve: false }, admin.session)).status, 200);
assert.equal((await login(replacement, phone, password)).json.code, 'DEVICE_BOUND');
assert.equal((await login(replacement, phone, password, true)).json.code, 'TRANSFER_PENDING');
list = await call('GET', '/v1/admin/users', undefined, admin.session);
assert.equal((await call('POST', `/v1/admin/transfers/${list.json.transfers[0].id}`, { approve: true }, admin.session)).status, 200);

assert.equal((await call('GET', '/v1/session', undefined, old.session)).status, 401);
assert.equal((await login(old.session.device, phone, password)).json.code, 'DEVICE_BOUND');
const moved = await login(replacement, phone, password);
assert.equal(moved.status, 200);
assert.equal((await call('POST', '/v1/logout', {}, moved.session)).status, 200);
assert.equal((await call('GET', '/v1/session', undefined, moved.session)).status, 401);
assert.equal((await login(replacement, phone, password)).status, 200);
console.log('Passed: admin login, duplicate phone, subscription, idempotent grant, concurrent device binding, key proof, rejection, approval, revocation and logout.');
