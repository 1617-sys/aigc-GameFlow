package aigc.gameflow.service;

import aigc.gameflow.image.GenerationEventType;
import aigc.gameflow.mapper.GenerationEventMapper;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.model.entity.GenerationEvent;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 记录并查询任务生命周期事件。事件记录失败不会中断主业务。 */
@Slf4j
@Service
public class GenerationEventService {

    private final GenerationEventMapper generationEventMapper;

    public GenerationEventService(GenerationEventMapper generationEventMapper) {
        this.generationEventMapper = generationEventMapper;
    }

    public void record(GenTask task, GenerationEventType type, String message) {
        record(task, type, message, null);
    }

    public void record(GenTask task, GenerationEventType type, String message, Object payload) {
        if (task == null) {
            return;
        }

        // 审计事件属于辅助信息，因此采用 best effort，失败只记日志。
        try {
            generationEventMapper.insert(GenerationEvent.builder()
                    .taskUuid(task.getTaskUuid())
                    .traceId(task.getTraceId())
                    .eventType(type.name())
                    .message(message)
                    .payload(payload == null ? null : JSON.toJSONString(payload))
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record generation event, taskUuid={}, eventType={}, error={}",
                    task.getTaskUuid(), type, e.getMessage());
        }
    }

    public List<GenerationEvent> listByTask(String taskUuid) {
        return generationEventMapper.selectList(
                new QueryWrapper<GenerationEvent>()
                        .eq("task_uuid", taskUuid)
                        .orderByAsc("create_time")
        );
    }
}
