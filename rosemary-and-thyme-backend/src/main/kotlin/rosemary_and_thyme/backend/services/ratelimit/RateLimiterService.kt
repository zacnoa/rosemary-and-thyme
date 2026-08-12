package rosemary_and_thyme.backend.services.ratelimit

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory rate limiter keyed by an arbitrary scope+key pair (e.g. scope="login",
 * key=email). A bucket is created lazily on first use with whatever bandwidths are
 * passed in, then reused for every later call with the same scope+key - only the
 * first caller's bandwidths actually take effect for a given key.
 *
 * This holds state per JVM instance only. Fine as long as the backend runs as a
 * single instance; would need a shared store (e.g. Redis) behind multiple instances
 * since these buckets aren't synchronized across the network.
 */
@Service
class RateLimiterService {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    /**
     * Attempts to consume 1 token from the bucket identified by `scope:key`, creating
     * that bucket (with [bandwidths] as its limits) if this is the first call for it.
     *
     * @param scope a namespace for the limit, e.g. "login" or "resend" - keeps
     * unrelated limits (login attempts vs. resend requests) from sharing a bucket even
     * if [key] is the same value (e.g. the same email) for both
     * @param key the identity being limited within [scope], e.g. an email or IP address
     * @param bandwidths one or more limits to enforce together (e.g. 1/minute AND
     * 5/day) - only used when the bucket is first created; ignored on later calls for
     * the same `scope:key`
     * @return `true` if a token was available and consumed (request allowed), `false`
     * if the bucket was empty (request should be rejected, typically as 429)
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
