package rosemary_and_thyme.backend.repositories.recipes

import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID

/**
 * Opaque "load next page" cursor for [RecipeRepository.selectRecipeFeed]/
 * [RecipeRepository.selectLikedRecipeFeed]'s keyset pagination. Carries the id and
 * trailing sort-key value of the last row a page ended on, so the next page's query
 * can resume with `(sortKey, id) < (cursorSortKey, cursorId)` (or `>` for an
 * ascending order) instead of a plain `OFFSET`, which would skip/duplicate rows when
 * the underlying order (e.g. like count) changes between page loads.
 *
 * Deliberately hand-rolled length-prefixed binary + Base64, not JSON: the sort-key
 * value is a recipe's own `name` for one of the three orders
 * ([RecipeRepository.searchOrder]), which can contain absolutely any character
 * (quotes, delimiters, control characters) a user typed into a recipe title - a
 * naive delimiter-joined string could be corrupted by that, and pulling in a JSON
 * dependency purely for a two-field struct isn't worth it.
 *
 * The caller is expected to already know which of the three sort-key types
 * (likes as a `Long`, similarity as a `Double`, or name/timestamp as a `String`) a
 * decoded cursor holds - that's implied by the same `q`/`orderByLikes` parameters
 * the original request carried, not by the cursor itself, so [sortValue] round-trips
 * as a plain `String` and each call site parses it back with `.toLong()`/
 * `.toDouble()`/`OffsetDateTime.parse(...)` as appropriate.
 */
internal object RecipeCursor {

    /** @return an opaque cursor encoding [sortValue] and [id] */
    fun encode(sortValue: String, id: UUID): String {
        val valueBytes = sortValue.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + valueBytes.size + 2 * Long.SIZE_BYTES)
        buffer.putInt(valueBytes.size)
        buffer.put(valueBytes)
        buffer.putLong(id.mostSignificantBits)
        buffer.putLong(id.leastSignificantBits)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array())
    }

    /**
     * @return the (sortValue, id) pair [encode] produced
     * @throws IllegalArgumentException if [cursor] isn't a validly-encoded cursor -
     * maps to a clean 400 via [rosemary_and_thyme.backend.controllers.GlobalExceptionHandler.handleBadRequest]
     * rather than a raw 500, on the off chance a client sends a hand-crafted/stale one.
     * Deliberately also the outcome for a cursor whose length prefix doesn't match its
     * actual size (see the `require` below) rather than trusting that prefix outright -
     * this is reachable from an unauthenticated request (`GET /recipe/search?cursor=`),
     * so a forged prefix (e.g. claiming a multi-gigabyte value) must be rejected
     * *before* [ByteArray] tries to allocate that many bytes, not after.
     */
    fun decode(cursor: String): Pair<String, UUID> = try {
        val bytes = Base64.getUrlDecoder().decode(cursor)
        val buffer = ByteBuffer.wrap(bytes)
        val valueLength = buffer.int
        // Encode always produces exactly 4 (length prefix) + valueLength + 16 (UUID)
        // bytes - anything else means either corruption or a forged length prefix.
        require(valueLength == bytes.size - Int.SIZE_BYTES - 2 * Long.SIZE_BYTES)
        val valueBytes = ByteArray(valueLength)
        buffer.get(valueBytes)
        val id = UUID(buffer.long, buffer.long)
        String(valueBytes, Charsets.UTF_8) to id
    } catch (e: Exception) {
        throw IllegalArgumentException("Malformed pagination cursor", e)
    }
}
