CREATE TABLE IF NOT EXISTS accounts (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login text NOT NULL UNIQUE,
    password_hash text NOT NULL,
    role text NOT NULL CHECK (role IN ('admin', 'user')),
    subscription_until timestamptz,
    device_key text,
    device_label text,
    CHECK ((role = 'admin' AND login = 'admin') OR
           (role = 'user' AND login ~ '^\+[1-9][0-9]{7,14}$')),
    CHECK ((device_key IS NULL) = (device_label IS NULL))
);

CREATE TABLE IF NOT EXISTS login_challenges (
    nonce text PRIMARY KEY,
    expires_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
    account_id integer PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS device_transfers (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id integer NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    device_key text NOT NULL,
    device_label text NOT NULL,
    requested_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (account_id, device_key)
);

CREATE TABLE IF NOT EXISTS subscription_grants (
    request_id uuid PRIMARY KEY,
    account_id integer NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    days integer NOT NULL CHECK (days BETWEEN 1 AND 3650),
    granted_at timestamptz NOT NULL DEFAULT now()
);
