package com.airportbus.common;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class RedisCacheConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    // matchIfMissing=true:属性未设置时(生产)仍创建;设置为 none 时(测试)跳过
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        // 用 no-arg 构造器:序列化器自带正确的 @class 类型解析(writer/reader 匹配),
        // 再通过 configure() 在其内部 ObjectMapper 上注册 JavaTimeModule 以支持 LocalDate 等 JSR-310 类型。
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer()
                .configure(om -> om.registerModule(new JavaTimeModule()));
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))               // TTL 兜底
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer));
        // 默认允许缓存 null(未调 disableCachingNullValues)→ 防穿透
        return RedisCacheManager.builder(cf).cacheDefaults(config).build();
    }
}
