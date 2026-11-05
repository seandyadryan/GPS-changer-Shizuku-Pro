import { createServer } from 'node:http';
import { database, transaction } from './db.mjs';
import {
    deviceCode, hashPassword, isToken, matchesPassword, normalizeLogin, randomToken,
    tokenHash, validPassword, verifyDevice, verifyProof,
} from './security.mjs';

const pool = database();
const windows = new Map();
const challengeWindow = 5 * 60 * 1000;
const trustProxy = process.env.NFX_TRUST_PROXY === '1';

class RequestError extends Error {
    constructor(status, code) {
        super(code);
        this.status = status;
        this.code = code;
    }
}

function fail(status, code) {
    throw new RequestError(status, code);
}

function rateLimit(key, limit, duration) {
    const now = Date.now();
    let entry = windows.get(key);
    if (!entry || entry.until <= now) {
        if (windows.size >= 10000) fail(429, 'TRY_LATER');
        entry = { count: 0, until: now + duration };
        windows.set(key, entry);
    }
    if (++entry.count > limit) fail(429, 'TRY_LATER');
}

function accountJson(account) {
    return {
        id: account.id,
        login: account.login,
        role: account.role,
        subscriptionUntil: account.subscription_until,
        deviceLabel: account.device_label,
        deviceCode: deviceCode(account.device_key),
    };
}

function requireSubscription(account) {
    if (account.role !== 'admin' && !account.subscribed) fail(403, 'SUBSCRIPTION_EXPIRED');
}

async function readBody(request) {
    const chunks = [];
    let length = 0;
    for await (const chunk of request) {
        length += chunk.length;
        if (length > 8192) fail(413, 'BODY_TOO_LARGE');
        chunks.push(chunk);
    }
    return Buffer.concat(chunks);
}

function jsonBody(raw) {
    let body;
    try { body = JSON.parse(raw.toString('utf8')); } catch { fail(400, 'INVALID_INPUT'); }
    if (!body || typeof body !== 'object' || Array.isArray(body)) fail(400, 'INVALID_INPUT');
    return body;
}

async function login(body, address) {
    const identifier = normalizeLogin(body.login);
    if (!identifier || !validPassword(body.password) || !isToken(body.nonce)) fail(400, 'INVALID_INPUT');
    rateLimit(`login:${address}`, 60, challengeWindow);
    rateLimit(`account:${identifier}:${address}`, 10, challengeWindow);
    const nonce = await pool.query(
        'DELETE FROM login_challenges WHERE nonce = $1 AND expires_at > now() RETURNING nonce', [body.nonce]);
    if (!nonce.rowCount) fail(401, 'INVALID_DEVICE_PROOF');
    const key = verifyDevice(body.nonce, body.deviceKey, body.signature);
    if (!key) fail(401, 'INVALID_DEVICE_PROOF');
    const label = typeof body.deviceLabel === 'string'
        ? body.deviceLabel.replace(/[\p{C}]/gu, '').trim().slice(0, 80) || 'Android'
        : 'Android';

    const original = (await pool.query('SELECT id, password_hash FROM accounts WHERE login = $1', [identifier])).rows[0];
    if (!await matchesPassword(body.password, original?.password_hash)) fail(401, 'INVALID_CREDENTIALS');

    return transaction(pool, async (client) => {
        const account = (await client.query(`SELECT *, subscription_until > now() AS subscribed
            FROM accounts WHERE id = $1 FOR UPDATE`, [original.id])).rows[0];
        if (!account || account.password_hash !== original.password_hash) fail(401, 'INVALID_CREDENTIALS');
        if (account.device_key && account.device_key !== key) {
            if (body.requestTransfer === true) {
                await client.query(`INSERT INTO device_transfers (account_id, device_key, device_label)
                    VALUES ($1, $2, $3) ON CONFLICT (account_id, device_key) DO NOTHING`, [account.id, key, label]);
            }
            const pending = await client.query('SELECT 1 FROM device_transfers WHERE account_id = $1 AND device_key = $2',
                [account.id, key]);
            return { status: 409, body: { code: pending.rowCount ? 'TRANSFER_PENDING' : 'DEVICE_BOUND', deviceCode: deviceCode(key) } };
        }

        requireSubscription(account);
        if (!account.device_key) {
            await client.query('UPDATE accounts SET device_key = $2, device_label = $3 WHERE id = $1', [account.id, key, label]);
            account.device_key = key;
            account.device_label = label;
        }
        const token = randomToken();
        await client.query(`INSERT INTO sessions (account_id, token_hash, expires_at)
            VALUES ($1, $2, now() + interval '24 hours') ON CONFLICT (account_id) DO UPDATE
            SET token_hash = excluded.token_hash, expires_at = excluded.expires_at`, [account.id, tokenHash(token)]);
        return { status: 200, body: { token, account: accountJson(account) } };
    });
}

async function authenticated(request, raw) {
    const token = request.headers.authorization?.match(/^Bearer ([\w-]{43})$/)?.[1];
    const timestamp = request.headers['x-nfx-time'];
    if (!token || typeof timestamp !== 'string' || !/^\d{13}$/.test(timestamp) ||
        Math.abs(Date.now() - Number(timestamp)) > challengeWindow) fail(401, 'SESSION_EXPIRED');
    const hash = tokenHash(token);
    const account = (await pool.query(`SELECT accounts.*, accounts.subscription_until > now() AS subscribed
        FROM sessions JOIN accounts ON sessions.account_id = accounts.id
        WHERE sessions.token_hash = $1 AND sessions.expires_at > now()`, [hash])).rows[0];
    const message = `nfx-request-v1:${token}:${request.method}:${request.url}:${timestamp}:${tokenHash(raw)}`;
    if (!account || !verifyProof(message, account.device_key, request.headers['x-nfx-proof'])) fail(401, 'SESSION_EXPIRED');
    requireSubscription(account);
    return { account, hash };
}

async function createUser(body) {
    const identifier = normalizeLogin(body.login);
    if (!identifier || identifier === 'admin' || !validPassword(body.password, true)) fail(400, 'INVALID_INPUT');
    const password = await hashPassword(body.password);
    const result = await pool.query(`INSERT INTO accounts (login, password_hash, role) VALUES ($1, $2, 'user')
        ON CONFLICT (login) DO NOTHING RETURNING *`, [identifier, password]);
    if (!result.rowCount) fail(409, 'ACCOUNT_EXISTS');
    return accountJson(result.rows[0]);
}

async function grantSubscription(id, body) {
    const { days, requestId } = body;
    if (!Number.isInteger(days) || days < 1 || days > 3650 || typeof requestId !== 'string' ||
        !/^[a-f\d]{8}-(?:[a-f\d]{4}-){3}[a-f\d]{12}$/i.test(requestId)) fail(400, 'INVALID_INPUT');
    return transaction(pool, async (client) => {
        const account = (await client.query("SELECT * FROM accounts WHERE id = $1 AND role = 'user' FOR UPDATE", [id])).rows[0];
        if (!account) fail(404, 'ACCOUNT_NOT_FOUND');
        const grant = await client.query(`INSERT INTO subscription_grants (request_id, account_id, days)
            VALUES ($1, $2, $3) ON CONFLICT DO NOTHING RETURNING request_id`, [requestId, id, days]);
        if (!grant.rowCount) {
            const previous = (await client.query('SELECT account_id, days FROM subscription_grants WHERE request_id = $1', [requestId])).rows[0];
            if (previous?.account_id !== id || previous.days !== days) fail(409, 'INVALID_INPUT');
            return accountJson(account);
        }
        const updated = await client.query(`UPDATE accounts SET subscription_until =
            greatest(subscription_until, now()) + make_interval(days => $2::integer) WHERE id = $1 RETURNING *`, [id, days]);
        return accountJson(updated.rows[0]);
    });
}

async function handleTransfer(id, body) {
    if (body.approve !== true && body.approve !== false) fail(400, 'INVALID_INPUT');
    await transaction(pool, async (client) => {
        const candidate = (await client.query('SELECT account_id FROM device_transfers WHERE id = $1', [id])).rows[0];
        if (!candidate) fail(404, 'TRANSFER_NOT_FOUND');
        await client.query('SELECT id FROM accounts WHERE id = $1 FOR UPDATE', [candidate.account_id]);
        const transfer = (await client.query('DELETE FROM device_transfers WHERE id = $1 RETURNING *', [id])).rows[0];
        if (!transfer) fail(404, 'TRANSFER_NOT_FOUND');
        if (body.approve) {
            await client.query('UPDATE accounts SET device_key = $2, device_label = $3 WHERE id = $1',
                [transfer.account_id, transfer.device_key, transfer.device_label]);
            await client.query('DELETE FROM sessions WHERE account_id = $1', [transfer.account_id]);
            await client.query('DELETE FROM device_transfers WHERE account_id = $1', [transfer.account_id]);
        }
    });
}

async function route(request) {
    const { method, url } = request;
    const address = trustProxy ? request.headers['x-real-ip'] ?? request.socket.remoteAddress : request.socket.remoteAddress;
    rateLimit(`http:${address}`, 600, challengeWindow);

    if (method === 'GET' && (url === '/' || url === '/health')) {
        await pool.query('SELECT 1');
        return { body: { service: 'NFX Location PRO', status: 'ok' } };
    }
    if (method === 'POST' && url === '/v1/challenge') {
        rateLimit(`challenge:${address}`, 90, challengeWindow);
        const nonce = randomToken();
        await pool.query("INSERT INTO login_challenges (nonce, expires_at) VALUES ($1, now() + interval '2 minutes')", [nonce]);
        return { body: { nonce, time: Date.now() } };
    }
    const raw = method === 'POST' ? await readBody(request) : Buffer.alloc(0);
    if (method === 'POST' && url === '/v1/login') return login(jsonBody(raw), address);

    const { account, hash } = await authenticated(request, raw);
    if (method === 'GET' && url === '/v1/session') return { body: { account: accountJson(account) } };
    if (method === 'POST' && url === '/v1/logout') {
        await pool.query('DELETE FROM sessions WHERE token_hash = $1', [hash]);
        return { body: { ok: true } };
    }
    if (account.role !== 'admin') fail(403, 'FORBIDDEN');
    if (method === 'GET' && url === '/v1/admin/users') {
        const users = await pool.query("SELECT * FROM accounts WHERE role = 'user' ORDER BY login");
        const requests = await pool.query(`SELECT t.id, t.device_label, t.device_key, a.login
            FROM device_transfers t JOIN accounts a ON a.id = t.account_id ORDER BY t.requested_at`);
        return { body: { users: users.rows.map(accountJson), transfers: requests.rows.map((row) => ({
            id: row.id, login: row.login, deviceLabel: row.device_label, deviceCode: deviceCode(row.device_key),
        })) } };
    }
    if (method === 'POST' && url === '/v1/admin/users') return { status: 201, body: await createUser(jsonBody(raw)) };
    const subscription = url.match(/^\/v1\/admin\/users\/([1-9]\d{0,8})\/subscription$/);
    if (method === 'POST' && subscription) return { body: await grantSubscription(Number(subscription[1]), jsonBody(raw)) };
    const transfer = url.match(/^\/v1\/admin\/transfers\/([1-9]\d{0,8})$/);
    if (method === 'POST' && transfer) {
        await handleTransfer(Number(transfer[1]), jsonBody(raw));
        return { body: { ok: true } };
    }
    fail(404, 'NOT_FOUND');
}

const server = createServer({ requestTimeout: 15000, headersTimeout: 10000 }, async (request, response) => {
    response.setHeader('Content-Type', 'application/json; charset=utf-8');
    response.setHeader('Cache-Control', 'no-store');
    try {
        const result = await route(request);
        response.writeHead(result.status ?? 200).end(JSON.stringify(result.body));
    } catch (error) {
        if (error instanceof RequestError) response.writeHead(error.status).end(JSON.stringify({ code: error.code }));
        else {
            console.error('Request failed', error.code ?? error.name);
            response.writeHead(500).end(JSON.stringify({ code: 'SERVER_ERROR' }));
        }
    }
});

setInterval(() => {
    for (const [key, entry] of windows) if (entry.until <= Date.now()) windows.delete(key);
    pool.query('DELETE FROM login_challenges WHERE expires_at <= now()').catch(() => console.error('Challenge cleanup failed'));
}, 60000).unref();
pool.on('error', (error) => console.error('Database pool error', error.code ?? error.name));
const port = Number(process.env.PORT ?? 8080);
server.listen(port, '0.0.0.0', () => console.log(`NFX API listening on ${port}`));
