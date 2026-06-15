# Production setup

## 1. Create Supabase database

1. Create a Supabase project.
2. Open SQL Editor.
3. Run the SQL in `supabase-schema.sql`.
4. Open Project Settings -> Database.
5. Copy the Postgres connection string. Prefer the pooler URL for hosted deploys and local networks without IPv6.

## 2. Configure environment variables

Copy `.env.example` to `.env` for local testing, then fill:

```env
DATABASE_URL=postgresql://...
SESSION_SECRET=long_random_secret
GMAIL_USER=...
GMAIL_APP_PASSWORD=...
NOTIFY_TO=...
HOST=0.0.0.0
```

On Render/Railway, add the same values in the service environment settings.

## 3. Run locally

```bash
npm install
npm start
```

Open:

- Website: `http://localhost:3000`
- Admin: `http://localhost:3000/admin`

Default admin on first database init:

- Username: `admin`
- Password: `xuantruong123`

Change this password immediately after first login.

## 4. Deploy suggestion

Deploy the `backend` folder as one Node.js service. It serves both the API and the static website.

If the direct URL like `db.<project-ref>.supabase.co:5432` fails with `ENOTFOUND`,
open Supabase -> Connect -> Connection string and copy the pooler URI instead.
It usually uses a host like `aws-0-...pooler.supabase.com` and port `6543`.

Recommended settings:

- Build command: `npm install`
- Start command: `npm start`
- Node version: 22 or newer
