# backend

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:
 * [Ktor Documentation](https://ktor.io/docs/home.html)
 * [Ktor GitHub page](https://github.com/ktorio/ktor)
 * [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). [Request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).


## Features
Here's a list of features included in this project:

| Name | Description |
|------|-------------|
| Recipe search | Fuzzy title search with cursor pagination and signed image URLs |

## Database calls and coroutines

The backend uses blocking jOOQ/JDBC calls through a HikariCP connection pool. Suspend service functions run their database work in `withContext(Dispatchers.IO)`, so a waiting JDBC call does not occupy a request-processing thread. A database transaction stays inside one IO block. Operations that also call R2 keep those suspend calls outside the JDBC block.

Synchronous database helpers such as `createSession` and `requestEmailVerification` run inside their caller's IO-backed transaction; the OIDC callback moves its standalone `createSession` call to IO. The scheduled account cleanup already runs on IO. Switching dispatchers does not make JDBC non-blocking or increase the connection pool's capacity: database concurrency is still limited by the HikariCP pool and PostgreSQL.

## Recipe search

`GET /recipe/search?q=lasagna&limit=20` returns a JSON array of `RecipeFeedItem` objects. It matches published recipe titles using PostgreSQL's `pg_trgm` `%` operator and sorts matches by like count, then recipe ID, both descending.

| Query parameter | Rules |
|-----------------|-------|
| `q` | Required. The trimmed search term must contain 1 to 100 characters. |
| `limit` | Optional. An integer from 1 to 50. The default is 20. |
| `cursor` | Optional. Send the previous response's `X-Next-Cursor` with the same search term. |

Guests see public recipes. A valid session also lets the viewer see their own private recipes. Drafts do not appear in the search. Invalid parameters return `400 Bad Request`.

The response includes `X-Next-Cursor` only when there is another page. The cursor is a Base64URL position, and the query uses the current like counts on each request. If those counts change between requests, recipes can move between pages.

### Recipe images

When a recipe has images, the search response includes its first `imageId` and an `imageUrl`. `GET /recipe/{id}` returns `imageIds` as an object that maps each image ID to its own signed Worker URL:

```json
{
  "imageIds": {
    "74668f2a-45f0-4328-9f7d-1d234567890a": "https://rosemary-and-thyme.com/recipe-images/{userId}/{recipeId}/74668f2a-45f0-4328-9f7d-1d234567890a?token={jwt}"
  }
}
```

The image ID remains the stable identifier used by `heroImagesOrder` and instruction image references. The URL is temporary and is not stored in the database. Stored recipe content and recipe write requests still use an `imageIds` array. Set `RECIPE_MEDIA_WORKER_URL` to the Worker's HTTPS origin; it defaults to `https://rosemary-and-thyme.com`.

Both search and detail URLs have the path `/recipe-images/{userId}/{recipeId}/{imageId}?token=…`. Each token expires after 15 minutes and contains `userId`, `recipeId`, and `imageId`. The Worker verifies the RS256 signature, issuer `rosemary_and_thyme`, audience `rosemary_and_thyme_worker`, expiration, and exact match of all three claims to the path before reading the image. The detail endpoint checks recipe visibility before issuing URLs. A private recipe or draft is visible only to its authenticated owner; other viewers receive `404 Not Found`. The detail endpoint no longer issues a media cookie. Exclude the `token` query parameter from request logs.

## Recipe image uploads

`POST /recipe/{id}/images/presign` accepts the complete intended `imageIds` set and an `imageSizes` map containing the exact byte size of each new file. The authenticated user must own the recipe. The request can contain at most 20 image IDs.

The backend derives deleted and new image IDs from the saved recipe and the requested set. The limit applies to the images retained after saving. Old image objects are deleted after the recipe update succeeds, so replacements can temporarily coexist with them in R2.

```json
{
  "imageIds": ["74668f2a-45f0-4328-9f7d-1d234567890a"],
  "imageSizes": {"74668f2a-45f0-4328-9f7d-1d234567890a": 2097152}
}
```

The response contains an upload grant for each image not already in R2. Each grant includes `imageId`, `uploadUrl`, and `headers`. Send the raw PNG file as the body of a `PUT` to `uploadUrl`, using the returned `Content-Type: image/png` and `If-None-Match: *` headers. The browser sets `Content-Length` from the file body. Other HTTP clients must send the same content length that was declared in `imageSizes`.

Each URL expires after ten minutes and signs the exact file size, which must be between one byte and 10 MiB. The `If-None-Match` condition rejects writes while an object already exists at that key. If the object is deleted before the URL expires, the URL can create it again. Configure the R2 bucket's CORS rule to allow `PUT`, `Content-Type`, and `If-None-Match` from the application's origin.

## Recipe updates

`GET /recipe/{id}` includes the recipe's current `version`. Send that value as a required field in the JSON body of `PUT /recipe/{id}`. A successful replacement increments the version and returns `200 OK` with `{"version":2}`.

A stale version returns `409 Conflict` with problem type `problem/recipe-version-conflict` before checking images in R2. The final database update also checks the version atomically, so a conflicting save that occurs during image verification is rejected before deleting any images. Reload the recipe before submitting another replacement.

`PATCH /recipe/{id}/private` also increments the edit version because replacements include the privacy flag. Likes and the made counter do not change it.

For an existing database, apply the `ALTER TABLE public.recipes ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 1` statement from `db/schema.sql`, then run `./gradlew jooqCodegen` to generate the `VERSION` field. Docker's schema initialization runs only when creating a new database volume.

## Recipe interactions and deletion

Only public, completed recipes can receive likes, and owners cannot like their own recipes. The made counter can be incremented for a public, completed recipe or the authenticated owner's private, completed recipe. Drafts are excluded from both operations.

`DELETE /recipe/{id}` checks ownership and deletes the recipe, then removes the image objects referenced in its content. An R2 failure can leave orphan objects after the database deletion. A future orphan cleanup job must remove those objects and uploads abandoned before saving.

`POST /user/delete` schedules account deletion after 30 days, and `DELETE /user/delete` cancels the request. Both return `200 OK` after the database update succeeds.

## Email verification

`POST /auth/register` returns `verificationId` and `emailSent`. Keep the ID while registration is pending. If `emailSent` is `false`, use that ID to request a replacement email.

| Endpoint | JSON body |
|----------|-----------|
| `POST /auth/verify-email` | `{"verificationId":"…","code":"012345"}` |
| `POST /auth/verify-email/resend` | `{"verificationId":"…"}` |

The six-digit code expires after five minutes. Confirming consumes it and starts a session. Resending replaces the code and resets its expiry under the same ID, including after the previous code expires. Configure rate limiting for confirmation in Cloudflare before exposing the endpoint.

## Password reset

`POST /auth/password-reset/request` returns `200 OK` with the same message for a missing account, a reset email accepted by the provider, or an email delivery failure. Invalid request bodies and invalid email input still receive their normal error responses. The authenticated `/user/password-reset/request` route retains its delivery error response.

## Building & Running
To build or run the project, use one of the following tasks:


| Task | Description |
|------|-------------|
| `./gradlew test`    | Run the tests     |
| `./gradlew build`   | Build the project |
| `./gradlew run`     | Run the server    |

If the server starts successfully, you'll see the following output:
```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```
