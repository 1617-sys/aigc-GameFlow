package aigc.gameflow.service;

import aigc.gameflow.config.RabbitConfig;
import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.model.entity.GenTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j

public class TaskService {
    @Autowired
    private GenTaskMapper genTaskMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public String submitTask(String prompt){
        String taskUuid = UUID.randomUUID().toString();
        GenTask genTask = GenTask.builder()
                .taskUuid(taskUuid)
                .prompt(prompt)
                .status(0)
                .createTime(LocalDateTime.now())
                .build();

        genTaskMapper.insert(genTask);
        log.info("✅ 任务已入库, ID: {}",taskUuid);

        rabbitTemplate.convertAndSend(RabbitConfig.TASK_QUEUE, taskUuid);
        log.info("🚀 任务已发送至 MQ队列: {}", RabbitConfig.TASK_QUEUE);

        return taskUuid;
    }
}
