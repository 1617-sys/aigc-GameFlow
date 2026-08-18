package aigc.gameflow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑配置：主队列失败后进入延迟重试队列，超过次数后由消费者送入死信队列。
 */
@Configuration
public class RabbitConfig {

    public static final String GENERATION_EXCHANGE = "generation.exchange";
    public static final String RETRY_EXCHANGE = "generation.retry.exchange";
    public static final String DLX_EXCHANGE = "generation.dlx";

    public static final String TASK_QUEUE = "generation.execute.q";
    public static final String RETRY_QUEUE = "generation.retry.q";
    public static final String DLQ = "generation.dlq";

    public static final String EXECUTE_ROUTING_KEY = "generation.execute";
    public static final String RETRY_ROUTING_KEY = "generation.retry";
    public static final String DLQ_ROUTING_KEY = "generation.dead";

    @Bean
    public DirectExchange generationExchange() {
        return new DirectExchange(GENERATION_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(TASK_QUEUE)
                .deadLetterExchange(RETRY_EXCHANGE)
                .deadLetterRoutingKey(RETRY_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue retryQueue() {
        // 重试消息停留 10 秒后，通过死信交换机重新回到执行队列。
        return QueueBuilder.durable(RETRY_QUEUE)
                .ttl(10_000)
                .deadLetterExchange(GENERATION_EXCHANGE)
                .deadLetterRoutingKey(EXECUTE_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding taskBinding(Queue taskQueue, DirectExchange generationExchange) {
        return BindingBuilder.bind(taskQueue).to(generationExchange).with(EXECUTE_ROUTING_KEY);
    }

    @Bean
    public Binding retryBinding(Queue retryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(retryQueue).to(retryExchange).with(RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ_ROUTING_KEY);
    }
}
