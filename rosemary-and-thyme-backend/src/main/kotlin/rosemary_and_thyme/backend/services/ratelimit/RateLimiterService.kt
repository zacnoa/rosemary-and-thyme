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

    fun tryConsume(scope: String, key: String, vararg bandwidths: Bandwidth): Boolean {
        val bucket = buckets.computeIfAbsent("$scope:$key") {
            val builder = Bucket.builder()
            bandwidths.forEach { builder.addLimit(it) }
            builder.build()
        }
        return bucket.tryConsume(1)
    }
}
