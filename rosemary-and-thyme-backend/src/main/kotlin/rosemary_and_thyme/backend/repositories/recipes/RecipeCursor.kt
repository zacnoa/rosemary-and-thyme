package rosemary_and_thyme.backend.repositories.recipes

import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID


/** Encodes keyset-pagination positions for recipe feeds. */
internal object RecipeCursor {

    /** Encodes the final row of a page as an opaque cursor.
     * @param sortValue the row's active sort-key value.
     * @param id the row's recipe id used as a tie-breaker.
     * @return a URL-safe cursor string.
     */
    fun encode(sortValue: String, id: UUID): String {
        val valueBytes = sortValue.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + valueBytes.size + 2 * Long.SIZE_BYTES)
        buffer.putInt(valueBytes.size)
        buffer.put(valueBytes)
        buffer.putLong(id.mostSignificantBits)
        buffer.putLong(id.leastSignificantBits)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array())
    }

    /** Decodes a recipe feed cursor into its sort value and recipe id.
     * @param cursor the opaque cursor supplied by the client.
     * @return the encoded sort value and recipe id.
     * @throws IllegalArgumentException if the cursor is malformed.
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
