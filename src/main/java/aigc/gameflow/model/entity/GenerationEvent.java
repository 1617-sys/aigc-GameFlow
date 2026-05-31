package aigc.gameflow.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("generation_event")
public class GenerationEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskUuid;

    private String traceId;

    private String eventType;

    private String message;

    private String payload;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
