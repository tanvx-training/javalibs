package io.javalibs.cache.spring;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalibs.cache.CacheKeys;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Factory for the standard javalibs {@link RedisCacheConfiguration}: String keys
 * with the {@code <prefix>::<cacheName>::} convention and JSON values.
 *
 * <p><strong>Serialization trade-off:</strong> values are serialized with
 * {@link GenericJackson2JsonRedisSerializer}, which embeds the Java class name
 * ({@code @class}) in the JSON so entries deserialize without per-cache type
 * configuration. Consequences: (1) renaming/moving a cached DTO class breaks
 * existing entries — flush the affected caches when refactoring cached types;
 * (2) caches must not be shared between services that do not share the DTO
 * classes. Keep cached values as small, dedicated DTOs.
 */
public final class RedisCacheConfigurations {

    private RedisCacheConfigurations() {
    }

    /**
     * Builds the standard cache configuration.
     *
     * @param keyPrefix       application-level prefix (usually the service name);
     *                        full keys become {@code <keyPrefix>::<cacheName>::<key>}
     * @param ttl             entry time-to-live
     * @param cacheNullValues whether {@code null} values are cached
     * @return the configuration to use as default or per-cache override
     */
    public static RedisCacheConfiguration standard(String keyPrefix, Duration ttl, boolean cacheNullValues) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .prefixCacheNameWith(keyPrefix + CacheKeys.SECTION_SEPARATOR)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer()));
        return cacheNullValues ? config : config.disableCachingNullValues();
    }

    /**
     * JSON value serializer with {@code java.time} support and default typing so
     * records and final classes round-trip (see class Javadoc for the trade-offs).
     */
    public static GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(mapper, null);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
