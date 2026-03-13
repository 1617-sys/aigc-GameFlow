package aigc.gameflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * 解决 Redis 数据存储中的乱码问题
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate，解决乱码问题
     * Key 使用 String 序列化
     * Value 使用 JSON 序列化
     * 
     * @param connectionFactory Redis 连接工厂
     * @return 配置好的 RedisTemplate 实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        // 1. 设置连接工厂
        template.setConnectionFactory(connectionFactory);

        // 2. 定义序列化规则
        // Key 序列化器 (使用 String)
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        // Value 序列化器 (使用 Spring 内置的 Jackson JSON)
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        // 3. 配置 Key 和 HashKey
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 4. 配置 Value 和 HashValue
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        // 5. 配置默认序列化器（用于其他情况）
        template.setDefaultSerializer(jsonSerializer);

        // 6. 初始化
        template.afterPropertiesSet();

        return template;
    }
}
