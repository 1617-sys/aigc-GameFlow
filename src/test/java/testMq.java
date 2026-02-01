import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/test")
public class testMq {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostMapping("/test-mq")
    public String testMq(@RequestBody String msg) {
        // 发送一条纯文本消息到队列
        rabbitTemplate.convertAndSend("aigc.task.queue", msg);
        return "消息已发送给 MQ";
    }
}