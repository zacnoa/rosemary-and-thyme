package rosemary_and_thyme.services

/**
 * Generates usernames from the curated word list bundled with the application.
 * Four independently selected words are joined with underscores, and words may repeat.
 */
object UsernameService {

    private const val USERNAME_WORD_COUNT = 4
    private const val MINIMUM_AVAILABLE_WORDS = 256
    private val validWordPattern = Regex("^[a-z]+$")

    private val words: List<String> = loadWords()

    /**
     * Generates a username candidate from four randomly selected words.
     * Database uniqueness must still be enforced when the candidate is persisted.
     *
     * @return generated lowercase username with underscore separators
     */
    fun generateUsername(): String =
        List(USERNAME_WORD_COUNT) { words.random() }
            .joinToString(separator = "_")

    /**
     * Loads and validates the bundled username vocabulary once during service initialization.
     *
     * @return unique lowercase words available for username generation
     * @throws IllegalStateException if the resource is missing or its contents are invalid
     */
    private fun loadWords(): List<String> {
        val loadedWords = checkNotNull(
            UsernameService::class.java.getResourceAsStream("/username-words.txt")
        ) {
            "Username word list is missing"
        }.bufferedReader().useLines { lines ->
            lines
                .map(String::trim)
                .filter(String::isNotBlank)
                .toList()
        }

        check(loadedWords.size >= MINIMUM_AVAILABLE_WORDS) {
            "Username word list must contain at least $MINIMUM_AVAILABLE_WORDS words"
        }
        check(loadedWords.size == loadedWords.distinct().size) {
            "Username word list must not contain duplicates"
        }
        check(loadedWords.all(validWordPattern::matches)) {
            "Username words must contain only lowercase ASCII letters"
        }

        return loadedWords
    }
}
