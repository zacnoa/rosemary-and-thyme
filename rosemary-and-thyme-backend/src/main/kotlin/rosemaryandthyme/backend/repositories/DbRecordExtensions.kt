package rosemaryandthyme.backend.repositories

/** Returns a non-null database value or fails with the column name.
 * @param column the database column used in the failure message.
 * @return this value as a non-null type.
 * @throws IllegalStateException if the value is `null`.
 */
fun <T : Any> T?.dbNotNull(column: String): T =
    this ?: throw IllegalStateException("Expected column '$column' to be non-null (DB invariant violated)")
