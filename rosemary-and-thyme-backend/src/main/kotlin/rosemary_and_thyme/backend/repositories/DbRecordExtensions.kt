package rosemary_and_thyme.backend.repositories

/**
 * jOOQ generates a nullable Kotlin type for any column that has a database-side
 * DEFAULT, even when the column is NOT NULL - because the value doesn't have to be
 * supplied when building a record for an insert. Once a row has actually been
 * fetched from the DB though, that column is guaranteed non-null by the schema.
 *
 * This documents that invariant at the call site with a clear failure message
 * instead of a bare `!!`, should it ever turn out to be wrong.
 *
 * @param column the column's name, used only to make the failure message actionable
 * @return this value, non-null
 * @throws IllegalStateException if this value is actually `null` (the DB invariant was violated)
 */
fun <T : Any> T?.dbNotNull(column: String): T =
    this ?: throw IllegalStateException("Expected column '$column' to be non-null (DB invariant violated)")
