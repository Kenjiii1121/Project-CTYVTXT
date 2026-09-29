# Backend V2 - Java Spring Boot

This backend keeps the existing website and admin API contract while replacing
Node.js/Express with Java 21, Spring Boot, JDBC and PostgreSQL.

## Architecture

```text
Browser -> REST controller -> service -> JdbcTemplate -> PostgreSQL
                    |              |
                 session       transaction
```

The public pages remain in the repository root. The admin pages remain in
`backend/admin`. V2 serves both, so the current frontend does not need API URL
changes.

## Local demo without Supabase

Use Java 21 and Maven 3.9+:

```powershell
$env:APP_FORCE_H2='true'
mvn spring-boot:run
```

Open:

- Website: `http://localhost:3001`
- Admin: `http://localhost:3001/admin`
- Health: `http://localhost:3001/healthz`

`APP_FORCE_H2` prevents the imported V1 `.env` from selecting the real database.
H2 is memory-only. Restarting the app clears demo data. The default demo admin
is created only when the database has no admins; change that password after the
first login.

## PostgreSQL/Supabase

Copy `.env.example` to `.env` and set `DATABASE_URL` and `SESSION_SECRET`. V2
also reads `../backend/.env` for local compatibility with V1. Do not commit real
credentials.

V1 and V2 use the same business tables. V2 verifies the existing Node.js scrypt
password hashes, so existing admin accounts remain usable. Spring Session uses
its own `SPRING_SESSION` tables.

## Commands

```powershell
mvn test
mvn package
java -jar target/transport-backend-v2-2.0.0-SNAPSHOT.jar
```

V2 defaults to port `3001`, leaving V1 on `3000`. Set `PORT=3000` only when V1
is stopped.

## API compatibility

- `/api/login`, `/api/logout`, `/api/me`, `/api/change-password`
- `/api/bookings`, `/api/customers`, `/api/vehicles`, `/api/trips`
- `/api/stats`, `/api/stats/revenue`, `/api/admins`
- `/healthz`

The implementation includes database transactions for booking and trip state
changes, session authentication, owner-only account management, security
headers, CORS allow-listing and simple per-IP rate limiting.
