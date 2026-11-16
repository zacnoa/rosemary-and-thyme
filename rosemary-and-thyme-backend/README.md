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

When a recipe has images, the search response includes its first `imageId` and an `imageUrl`. Set `RECIPE_MEDIA_WORKER_URL` to the Worker's HTTPS origin. It defaults to `https://rosemary-and-thyme.com`.

A media Worker is required to serve these URLs. It must verify the RS256 signature, issuer `rosemary-and-thyme`, audience `rosemary-and-thyme-worker`, and expiration before serving an image. Both token types expire after 15 minutes.

| Token source | Required path match |
|--------------|---------------------|
| Search URL `/recipe-images/{userId}/{recipeId}/{imageId}?token=…` | `userId`, `recipeId`, and `imageId` |
| Detail cookie `recipe_media_token` | `userId` and `recipeId` |

A search URL token must include `imageId`. The browser sends the detail cookie only to hosts under `rosemary-and-thyme.com` on `/recipe-images/` paths. `GET /recipe/{id}` issues the cookie after checking visibility. A private recipe is visible only to its authenticated owner, and other viewers receive `404 Not Found`. Exclude the `token` query parameter from request logs.

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
