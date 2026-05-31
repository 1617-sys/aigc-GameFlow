package aigc.gameflow.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitConfig {

    // 必须和你 TaskListener 里写的队列名一模一样！
    public static final String TASK_QUEUE = "aigc.task.queue";


    @Bean
    public Queue taskQueue() {
        // true 表示持久化（重启 RabbitMQ 后队列还在）
        // false, false 是是否独占和是否自动删除，通常设为 false
        return new Queue(TASK_QUEUE, true);
    }
}