# NFX Location PRO API

The Android app connects to `https://nfxlocationpro.amarlo.online/`. The API uses PostgreSQL and runs on Docker behind Caddy. Phone numbers that start with `0` are interpreted as Indonesian numbers. International numbers can use an explicit country code, such as `+628123456789`.

## Setup

1. Create an empty database `nfx_location_pro` and a dedicated login role that owns it. Connect to that database using `NFX_DATABASE_URL` in the local shell or a private `.env` for Docker Compose.
2. Set `NFX_ADMIN_PASSWORD` in the shell for the first setup. `npm ci && npm run db:setup` creates the schema and the `admin` account. The setup command keeps the existing account password on subsequent runs.
3. Start the API with `npm start`. For Docker, use the `nfx-location-pro` network for the API, PostgreSQL, and the HTTPS reverse proxy. Run `docker compose build`, then `docker compose run --rm -T -e NFX_ADMIN_PASSWORD api node setup.mjs`, then `docker compose up -d --wait`.
4. Route the domain to `nfx-location-api:8080`. With the Compose configuration, the reverse proxy must overwrite `X-Real-IP` with the actual client address.

Passwords are salted and hashed in PostgreSQL. Android keeps the device signing key in Android Keystore and keeps the session in memory. It checks session validity on returning to the foreground and periodically while open. An active connection to the API is required for the account and manager UI.

## Device transfer

The first successful login binds the account to its Android installation. On the replacement device, sign in and tap **Request device transfer**. Share the displayed device code with the admin. In the admin panel, refresh the list, match the code, then approve the request. The new device can sign in after approval. Reinstalling the app or clearing its data can generate a new device key and require another approval.

If the original admin device is lost, a server operator can release the admin binding and revoke the admin session. Run `docker compose run --rm -T api node setup.mjs release-admin` on the server, then sign in on the replacement device. This operation only affects the `admin` account.
