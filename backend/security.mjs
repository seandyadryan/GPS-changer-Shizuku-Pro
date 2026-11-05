import { createHash, createPublicKey, randomBytes, scrypt, timingSafeEqual, verify } from 'node:crypto';
import { promisify } from 'node:util';

const derive = promisify(scrypt);
const scryptOptions = { N: 32768, r: 8, p: 1, maxmem: 64 * 1024 * 1024 };
const dummyHash = `${'0'.repeat(32)}:${'0'.repeat(64)}`;

export function normalizeLogin(value) {
    if (typeof value !== 'string' || value.length > 40) return null;
    const login = value.trim();
    if (login.toLowerCase() === 'admin') return 'admin';
    let number = login.replace(/[\s()-]/g, '');
    if (number.startsWith('0')) number = '+62' + number.slice(1);
    if (number.startsWith('62')) number = '+' + number;
    return /^\+[1-9]\d{7,14}$/.test(number) ? number : null;
}

export function validPassword(value, creating = false) {
    return typeof value === 'string' && value.length >= (creating ? 8 : 1) &&
        Buffer.byteLength(value) <= 128;
}

export async function hashPassword(password) {
    const salt = randomBytes(16);
    const hash = await derive(password, salt, 32, scryptOptions);
    return `${salt.toString('hex')}:${hash.toString('hex')}`;
}

export async function matchesPassword(password, storedHash) {
    const [salt, expected] = (storedHash ?? dummyHash).split(':');
    const actual = await derive(password, Buffer.from(salt, 'hex'), 32, scryptOptions);
    return timingSafeEqual(actual, Buffer.from(expected, 'hex')) && Boolean(storedHash);
}

export const randomToken = () => randomBytes(32).toString('base64url');
export const tokenHash = (token) => createHash('sha256').update(token).digest('hex');
export const isToken = (value) => typeof value === 'string' && /^[\w-]{43}$/.test(value);

function parseDevice(encodedKey) {
    if (typeof encodedKey !== 'string' || encodedKey.length > 160) return null;
    const key = createPublicKey({ key: Buffer.from(encodedKey, 'base64'), format: 'der', type: 'spki' });
    return key.asymmetricKeyType === 'ec' && key.asymmetricKeyDetails.namedCurve === 'prime256v1' ? key : null;
}

export function verifyProof(message, encodedKey, signature) {
    if (typeof signature !== 'string' || signature.length > 120) return null;
    try {
        const key = parseDevice(encodedKey);
        if (!key || !verify('sha256', Buffer.from(message), key, Buffer.from(signature, 'base64'))) return null;
        return key.export({ type: 'spki', format: 'der' }).toString('base64');
    } catch {
        return null;
    }
}

export function verifyDevice(nonce, encodedKey, signature) {
    return verifyProof(`nfx-login-v1:${nonce}`, encodedKey, signature);
}

export const deviceCode = (key) => key ? tokenHash(key).slice(0, 12).toUpperCase() : null;
