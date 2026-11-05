import pg from 'pg';

export function database() {
    if (!process.env.NFX_DATABASE_URL) throw new Error('Set NFX_DATABASE_URL');
    return new pg.Pool({ connectionString: process.env.NFX_DATABASE_URL, connectionTimeoutMillis: 5000 });
}

export async function transaction(pool, work) {
    const client = await pool.connect();
    try {
        await client.query('BEGIN');
        const result = await work(client);
        await client.query('COMMIT');
        return result;
    } catch (error) {
        await client.query('ROLLBACK');
        throw error;
    } finally {
        client.release();
    }
}
