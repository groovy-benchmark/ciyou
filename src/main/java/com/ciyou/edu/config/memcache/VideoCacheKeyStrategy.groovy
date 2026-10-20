package com.ciyou.edu.config.memcache

/**
 * How a per-video view counter is keyed and bumped in memcached.
 */
interface VideoCacheKeyStrategy {
    long hit(MemcacheRawClient client, String videoName)
}

/** Counters shared by every viewer, keyed by the requested video name. */
class SharedViewKeyStrategy implements VideoCacheKeyStrategy {
    @Override
    long hit(MemcacheRawClient client, String videoName) {
        return client.incr("views:" + videoName, 1)
    }
}

/** Private counters: namespaced SHA-256 of the name, validated before it reaches the socket. */
class HashedViewKeyStrategy implements VideoCacheKeyStrategy {
    @Override
    long hit(MemcacheRawClient client, String videoName) {
        return client.incrChecked(MemcacheKeys.safeKey("views:" + videoName), 1)
    }
}
