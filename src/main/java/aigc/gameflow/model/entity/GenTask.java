package aigc.gameflow.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@TableName("gen_task")
public class GenTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 对应 task_uuid 字段
    private String taskUuid;

    private String prompt;    // 中文
    private String promptEn;  // 英文

    // 状态: 0-排队, 1-生成中, 2-成功, 3-失败
    private Integer status;

    private String imageUrl;

    // 对应 JSON 类型的 parameters 字段，MyBatis-Plus 会自动当作 String 处理
    private String parameters;

    private String errorMsg;

    // 自动填充时间，不需要手写
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
