import { readFile } from 'node:fs/promises';
import { database, transaction } from './db.mjs';
import { hashPassword, validPassword } from './security.mjs';

const pool = database();
try {
    if (process.argv[2] === 'release-admin') {
        await transaction(pool, async (client) => {
            const admin = (await client.query("SELECT id FROM accounts WHERE login = 'admin' FOR UPDATE")).rows[0];
            if (!admin) throw new Error('Admin account missing');
            await client.query('DELETE FROM sessions WHERE account_id = $1', [admin.id]);
            await client.query('DELETE FROM device_transfers WHERE account_id = $1', [admin.id]);
            await client.query('UPDATE accounts SET device_key = NULL, device_label = NULL WHERE id = $1', [admin.id]);
        });
        console.log('Admin device released. Sign in on the replacement device.');
    } else {
        const schema = await readFile(new URL('./schema.sql', import.meta.url), 'utf8');
        await transaction(pool, async (client) => {
            await client.query('SELECT pg_advisory_xact_lock(4198241)');
            await client.query(schema);
            const existing = await client.query("SELECT 1 FROM accounts WHERE login = 'admin'");
            if (existing.rowCount) return;
            const password = process.env.NFX_ADMIN_PASSWORD;
            if (!validPassword(password, true)) throw new Error('Set NFX_ADMIN_PASSWORD for first setup');
            await client.query("INSERT INTO accounts (login, password_hash, role) VALUES ('admin', $1, 'admin')",
                [await hashPassword(password)]);
        });
        console.log('Database and admin account ready.');
    }
} catch (error) {
    console.error(`Setup failed: ${error.code ?? error.message}`);
    process.exitCode = 1;
} finally {
    await pool.end();
}
