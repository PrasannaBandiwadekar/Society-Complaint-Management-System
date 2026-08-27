package com.society.complaint.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis configuration — two things are set up here:
 *
 * 1. CONNECTION: LettuceConnectionFactory tells Spring how to reach Redis.
 *    Lettuce is the underlying Redis client library (like JDBC is for SQL).
 *
 * 2. CACHE MANAGER: RedisCacheManager backs Spring's @Cacheable annotation.
 *    When you annotate a method with @Cacheable("dashboard-stats"), Spring
 *    stores the return value in Redis under that cache name. Next call with
 *    the same arguments returns the cached value without running the method.
 *    TTL = 5 minutes, so the cache auto-expires and the dashboard never shows
 *    data more than 5 minutes stale.
 *
 * HTTP SESSION STORAGE:
 * Spring Session is activated by @EnableRedisHttpSession (on the main app class).
 * It transparently stores each user's HTTP session in Redis instead of in-memory.
 * This means:
 * - If the app restarts, users stay logged in (sessions survive in Redis)
 * - If you run two instances of the app, both share sessions (horizontal scaling)
 */
@Configuration
@Profile("!prod")
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(redisHost, redisPort);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        // Store keys as plain strings, values as JSON
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                // Cache entries expire after 5 minutes automatically
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                // Don't cache null values (e.g. if a count query returns 0)
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .build();
    }
}
