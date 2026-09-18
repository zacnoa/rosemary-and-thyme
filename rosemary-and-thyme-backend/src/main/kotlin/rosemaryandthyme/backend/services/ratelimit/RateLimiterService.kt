package rosemaryandthyme.backend.services.ratelimit

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/** Stores in-memory token buckets for scoped rate limits. */
@Service
class RateLimiterService {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    /** Consumes one token from a scoped bucket.
     * @param scope the rate-limit namespace.
     * @param key the identity within that namespace.
     * @param bandwidths limits used when creating a bucket.
     * @return whether a token was available.
     */
    fun tryConsume(scope: String, key: String, vararg bandwidths: Bandwidth): Boolean {
        val bucket = buckets.computeIfAbsent("$scope:$key") {
            val builder = Bucket.builder()
            bandwidths.forEach { builder.addLimit(it) }
            builder.build()
        }
        return bucket.tryConsume(1)
    }
}
