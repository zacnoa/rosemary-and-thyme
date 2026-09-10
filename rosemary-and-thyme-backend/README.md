# Rosemary & Thyme Backend

## Core versions

| Technology | Version |
| --- | --- |
| Kotlin | 2.2.21 |
| Spring Boot | 4.0.5 |
| Java | 21 |
| Gradle | 9.4.1 |
| jOOQ | 3.21.2 |
| PostgreSQL | 16 (local Docker image) |

## Overview

Rosemary & Thyme is a recipe platform for discovering, publishing, and managing recipes. This repository contains its REST API, authentication system, recipe persistence, image lifecycle, transactional email workflows, and scheduled account maintenance.

The application is built with Kotlin and Spring Boot and uses PostgreSQL through jOOQ rather than an ORM. It supports public recipe browsing and fuzzy search, while authenticated users can create and edit recipes, upload images, like other users' recipes, manage visibility, and maintain their account.

Notable engineering choices include:

- database-backed application sessions with only SHA-256 token hashes stored at rest;
- email/password authentication and Google OpenID Connect login;
- normalized recipe data with explicit UUID-based ordering for ingredients, instructions, and images;
- transactional recipe synchronization with compensating Cloudinary cleanup;
- keyset pagination backed by opaque cursors and query-specific indexes;
- PostgreSQL trigram similarity search;
- centralized RFC 9457 `ProblemDetail` responses;
- delayed account deletion with a 30-day recovery window.

## Project structure

```text
.
├── db/
│   ├── schema.sql                 # Structural DDL and pg_trgm extension
│   ├── indexes.sql                # Query-specific PostgreSQL indexes
│   └── migrations/                # One-off migrations for existing databases
├── docker/
│   └── docker-compose.yaml        # Local PostgreSQL 16 and pgAdmin
├── src/
│   ├── main/
│   │   ├── kotlin/rosemary_and_thyme/backend/
│   │   │   ├── configurations/    # Security, CORS, and Google OIDC handling
│   │   │   ├── controllers/       # REST endpoints and exception mapping
│   │   │   ├── exceptions/        # Domain-specific application errors
│   │   │   ├── filters/           # Session authentication and IP rate limiting
│   │   │   ├── models/            # Authentication and recipe DTOs
│   │   │   ├── repositories/      # jOOQ database access
│   │   │   └── services/          # Auth, email, recipes, and rate limiting
│   │   └── resources/
│   │       └── application.yaml   # Runtime configuration and upload limits
│   └── test/kotlin/               # Spring Boot tests
├── build.gradle.kts               # Dependencies, toolchain, and jOOQ codegen
├── settings.gradle.kts            # Project and JDK resolver configuration
└── gradlew / gradlew.bat           # Gradle wrapper
```

The `rosemary_and_thyme.backend.jooqgenerated` package is generated from `db/schema.sql`. It is intentionally excluded from version control and recreated by `jooqCodegen`; `compileKotlin` depends on that task automatically.

## Application architecture

The application follows a controller-service-repository structure:

```text
HTTP request
    │
    ├── RateLimitFilter           IP limit for /auth requests
    ├── SessionAuthFilter         session_cookie → authenticated UsersRecord
    │
    ▼
Controller                       HTTP mapping and response construction
    │
    ▼
Service                          business rules, authorization, orchestration
    │
    ▼
Repository                       type-safe jOOQ queries and persistence
    │
    ▼
PostgreSQL                       app_auth and public schemas
```

Controllers stay focused on transport concerns. Services own authentication, authorization, validation, transactions, remote integrations, and cleanup. Repositories contain the jOOQ queries and return generated records or API-specific projections.

Spring Security uses `SessionCreationPolicy.STATELESS`: Spring does not create an HTTP session. On each request, `SessionAuthFilter` reads `session_cookie`, hashes its raw token, validates the corresponding database record, and places the owning `UsersRecord` in the request's security context.

## API endpoints

Public endpoints are marked **Public**. Every other endpoint requires a valid `session_cookie`.

### Authentication

| Method | Endpoint | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/auth/register` | Public | Create an unverified password account and send a verification email. |
| `POST` | `/auth/verify-email` | Public | Consume a verification token and create a session. |
| `POST` | `/auth/verify-email/resend` | Public | Request a replacement verification email without disclosing account state. |
| `POST` | `/auth/login` | Public | Authenticate a verified password account and create a session. |
| `GET` | `/auth/google?redirect=...` | Public | Start the Google OAuth2/OIDC authorization flow. |
| `POST` | `/auth/password-reset/request` | Public | Request a reset email without disclosing whether the account exists. |
| `POST` | `/auth/password-reset/confirm` | Public | Consume a reset token, replace the password, rotate sessions, and sign in. |
| `POST` | `/auth/logout` | Authenticated | Revoke the current session and clear its cookie. |

Spring Security handles the internal Google authorization and callback paths under `/oauth2/**` and `/login/oauth2/**`. After a successful callback, the backend resolves or creates the local user, creates an application session, and redirects to the sanitized frontend path.

### Current user

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/user/aboutme` | Return the current account, deletion state, and whether it has a password. |
| `PUT` | `/user/password` | Change the password, revoke all sessions, and issue a replacement session. |
| `POST` | `/user/delete` | Schedule account deletion. Repeating the request renews the grace period. |
| `DELETE` | `/user/delete` | Cancel a pending deletion. |
| `GET` | `/user/recipes/feed?q=&cursor=` | Search and paginate the current user's recipes, including private ones. |
| `GET` | `/user/recipes/liked?cursor=` | Paginate recipes liked by the current user. |

### Recipes

| Method | Endpoint | Access | Purpose |
| --- | --- | --- | --- |
| `GET` | `/recipe/search?q=&userId=&cursor=` | Public | Search public recipes and return a cursor-paginated feed. |
| `GET` | `/recipe/{id}` | Public | Return a public recipe, or a private recipe when requested by its owner. |
| `POST` | `/recipe` | Authenticated | Create a recipe from a multipart DTO and image files. |
| `PUT` | `/recipe/{id}` | Authenticated owner | Replace the complete editable state of a recipe. |
| `DELETE` | `/recipe/{id}` | Authenticated owner | Delete a recipe and clean up its images. |
| `PATCH` | `/recipe/{id}/private` | Authenticated owner | Change recipe visibility. |
| `POST` | `/recipe/{id}/like` | Authenticated | Like another user's public recipe. |
| `DELETE` | `/recipe/{id}/like` | Authenticated | Remove the current user's like. |

Feed responses contain up to 20 items and a nullable `nextCursor`. The cursor encodes the last row's active sort value and recipe UUID as URL-safe Base64, providing deterministic keyset pagination without an offset or a separate count query.

## Authentication and session lifecycle

Password accounts are stored with BCrypt password hashes. Registration creates an inactive account and sends a 24-hour email-verification link through Resend. Verification marks the token as used, verifies the account, and creates the first session. Password-reset links expire after one hour; a successful reset revokes all existing sessions before issuing a new one.

Google login delegates the authorization code exchange, state handling, ID-token validation, and provider metadata resolution to Spring Security's OAuth2 client support. Provider identities are stored separately in `app_auth.oauth_identities`, allowing a verified Google identity to be linked to an existing account with the same verified email and leaving room for additional providers.

Application sessions use the following lifecycle:

1. The server generates 32 cryptographically secure random bytes and returns the URL-safe token only in an HTTP-only cookie.
2. A SHA-256 hash of that token is stored in `app_auth.sessions`; the raw credential is never persisted.
3. Each request hashes the cookie again and checks that its session exists, is not revoked, has not expired, and still has an owner.
4. Logout marks the current session as revoked. Password changes and resets revoke every session for the account.
5. A scheduled job removes revoked and expired rows daily at 03:30 server time.

`session_cookie` is configured as `HttpOnly`, `Secure`, `SameSite=Lax`, scoped to `/`, and valid for 30 days. Its domain comes from `COOKIE_DOMAIN`. CORS allows credentialed requests only from the configured `FRONTEND_URL`.

## Recipe persistence and image handling

Recipe writes use `multipart/form-data`. The JSON part must be named `recipe`; every new image is a separate JPEG or PNG part whose field name is the image's temporary UUID. The server accepts at most 8 MB per file and 30 MB for the complete multipart request.

`RecipeWriteDTO` separates entity data from its relationships and display order:

```text
images: Map<UUID, RecipeImageDTO>
ingredients: Map<UUID, IngredientDTO>
instructions: Map<UUID, InstructionDTO>

heroImagesOrder: List<UUID>
ingredientsOrder: List<UUID>
instructionsOrder: List<UUID>
instruction.images: List<UUID>
```

This mirrors the frontend editor model while preserving stable entity identity. Server-side validation enforces a maximum of 50 ingredients, 50 instructions, 20 images, 1,000 description words, and 100 title characters. It also verifies that ordering lists and image relationships contain valid, unique IDs.

The write flow coordinates PostgreSQL and Cloudinary carefully:

1. Validate the recipe graph, image metadata, ownership, MIME types, and uploaded parts.
2. Upload new files to Cloudinary and replace temporary entries with their secure URLs.
3. Synchronize the recipe row, ingredients, instructions, join tables, images, and ordering in one database transaction.
4. After the transaction commits, remove remote files that the user deleted from the recipe.
5. If upload or persistence fails, delete newly uploaded Cloudinary assets as compensating cleanup.

Recipe deletion captures the associated URLs, deletes the database graph transactionally, and then removes the remote assets. Cloudinary deletion is retried once and failures are logged so external cleanup cannot invalidate an already committed database operation.

## Database and jOOQ

The database is split into two PostgreSQL schemas:

- `app_auth` contains users, sessions, OAuth identities, email-verification tokens, and password-reset tokens.
- `public` contains recipes, ingredients, instructions, images, ordered image relationships, and recipe likes.

Foreign keys use cascading deletion where the child data belongs to an account, recipe, or instruction. Composite unique keys preserve per-recipe ordering, while the composite primary key on `recipe_likes (recipe_id, user_id)` prevents duplicate likes.

Search uses PostgreSQL's `pg_trgm` extension, the `%` similarity operator, and `similarity(...)` ordering. `db/indexes.sql` adds the GIN trigram index plus B-tree indexes for ownership filters, account cleanup, foreign keys, and each cursor-pagination order.

jOOQ generation parses `db/schema.sql` with `DDLDatabase`; it does not require a live database. Index definitions intentionally live in `db/indexes.sql` because they do not affect generated Kotlin types.

Generate the jOOQ sources explicitly with:

```bash
./gradlew jooqCodegen
```

On Windows PowerShell or Command Prompt, use `gradlew.bat` instead of `./gradlew`.

## Rate limiting and scheduled cleanup

Bucket4j provides layered token-bucket limits:

| Scope | Limit | Key |
| --- | --- | --- |
| All `/auth` traffic | 20 requests per minute | Client IP (`CF-Connecting-IP`, then remote address) |
| Login | 10 attempts per 15 minutes | Email address |
| Verification-email resend | 1 per minute and 5 per day | Email address |
| Password-reset request | 1 per minute and 5 per day | Email address |
| Password change | 5 attempts per 15 minutes | User ID |

Buckets are stored in memory. Their state resets when the application restarts and is not shared between application instances; a distributed store would be required for horizontally scaled enforcement.

Spring scheduling runs two daily maintenance jobs:

- `03:00` server time: permanently delete accounts whose 30-day grace period has elapsed, including owned recipes and remote images;
- `03:30` server time: remove revoked and expired session rows.

## Error responses

Controller errors are mapped centrally to `application/problem+json` responses using Spring's RFC 9457 `ProblemDetail`. Domain errors cover invalid credentials, conflicts, invalid or expired tokens, authorization failures, recipe validation, rate limits, malformed requests, and oversized uploads.

A typical response has this shape:

```json
{
  "type": "about:blank",
  "title": "Invalid recipe",
  "status": 400,
  "detail": "Too many images (max 20)",
  "instance": "/recipe"
}
```

Unexpected exceptions are logged server-side and return a generic message so implementation details are not exposed to clients.

## Environment variables

Spring imports an optional project-root `.env` file. The file is ignored by Git.

| Variable | Required | Purpose | Local example |
| --- | --- | --- | --- |
| `DB_URL` | Yes | JDBC connection URL | `jdbc:postgresql://localhost:432/rosemary_and_thyme` |
| `DB_USER` | Yes | PostgreSQL user | `noa` |
| `DB_PASSWORD` | Yes | PostgreSQL password | `noa` |
| `GOOGLE_CLIENT_ID` | Yes | Google OAuth2 client ID | Provider value |
| `GOOGLE_CLIENT_SECRET` | Yes | Google OAuth2 client secret | Provider value |
| `CLOUDINARY_NAME` | Yes | Cloudinary cloud name | Provider value |
| `CLOUDINARY_KEY` | Yes | Cloudinary API key | Provider value |
| `CLOUDINARY_SECRET` | Yes | Cloudinary API secret | Provider value |
| `RESEND_API_KEY` | Yes | Resend API key | Provider value |
| `RESEND_FROM_EMAIL` | Yes | Verified sender address | `noreply@example.com` |
| `FRONTEND_URL` | No | CORS origin, email-link base, and OAuth redirect base | `http://localhost:3000` |
| `COOKIE_DOMAIN` | No | Domain applied to authentication cookies | `localhost` |

Example `.env`:

```dotenv
DB_URL=jdbc:postgresql://localhost:432/rosemary_and_thyme
DB_USER=noa
DB_PASSWORD=noa

GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret

CLOUDINARY_NAME=your-cloud-name
CLOUDINARY_KEY=your-cloudinary-key
CLOUDINARY_SECRET=your-cloudinary-secret

RESEND_API_KEY=your-resend-api-key
RESEND_FROM_EMAIL=noreply@example.com

FRONTEND_URL=http://localhost:3000
COOKIE_DOMAIN=localhost
```

Use real provider credentials for Google login, image uploads, and transactional email. Never commit the `.env` file.

## Running locally

### Prerequisites

- Docker with Docker Compose
- Java 21, or a compatible environment in which Gradle can download the required toolchain through the configured Foojay resolver
- Google, Cloudinary, and Resend credentials for their respective integration flows
- the Rosemary & Thyme frontend at `http://localhost:3000`, or a matching `FRONTEND_URL`

### Start PostgreSQL and pgAdmin

```bash
docker compose -f docker/docker-compose.yaml up -d
```

The local services are:

| Service | Address | Credentials |
| --- | --- | --- |
| PostgreSQL | `localhost:432` | Database `rosemary_and_thyme`, user `noa`, password `noa` |
| pgAdmin | `http://localhost:5050` | `noa@noa.com` / `noa` |

The Compose file does not initialize the schema automatically. From Bash, apply the structural DDL first and the indexes second:

```bash
docker compose -f docker/docker-compose.yaml exec -T postgres psql -U noa -d rosemary_and_thyme < db/schema.sql
docker compose -f docker/docker-compose.yaml exec -T postgres psql -U noa -d rosemary_and_thyme < db/indexes.sql
```

PowerShell equivalent:

```powershell
Get-Content -Raw db/schema.sql | docker compose -f docker/docker-compose.yaml exec -T postgres psql -U noa -d rosemary_and_thyme
Get-Content -Raw db/indexes.sql | docker compose -f docker/docker-compose.yaml exec -T postgres psql -U noa -d rosemary_and_thyme
```

Then create `.env` from the example above and start the API:

```bash
./gradlew bootRun
```

The API starts at `http://localhost:8080` unless Spring's server configuration is overridden.

Stop the local containers with:

```bash
docker compose -f docker/docker-compose.yaml down
```

The named PostgreSQL volume keeps the database between container restarts. Add `-v` only when you intentionally want to delete that local data.

## Build and test commands

| Command | Purpose |
| --- | --- |
| `./gradlew bootRun` | Start the API for development. |
| `./gradlew jooqCodegen` | Regenerate Kotlin database types from `db/schema.sql`. |
| `./gradlew test` | Run the test suite. |
| `./gradlew build` | Generate jOOQ sources, compile, test, and create the executable Spring Boot JAR. |
| `./gradlew bootJar` | Create only the executable Spring Boot JAR in `build/libs`. |

The plain Gradle `jar` task is disabled so deployments cannot accidentally select a non-executable archive. The current automated coverage consists of a basic `@SpringBootTest` context-load test; authentication, repository, recipe transaction, and integration flows remain candidates for broader test coverage.
