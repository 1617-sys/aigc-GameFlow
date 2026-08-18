package aigc.gameflow.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 待投递消息实体，对应事务 Outbox 表。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("generation_outbox")
public class GenerationOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String taskUuid;
    private String traceId;
    private String eventType;
    private String payload;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextAttemptTime;
    private String lockedBy;
    private LocalDateTime lockedUntil;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime sentTime;
    private LocalDateTime updateTime;
}
