package aigc.gameflow.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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
@TableName("gen_task")
public class GenTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskUuid;
    private String idempotencyKey;
    private String requestHash;
    private Integer version;
    private Integer retryCount;
    private String workerId;
    private LocalDateTime leaseExpireTime;
    private LocalDateTime lastHeartbeatTime;

    private String prompt;
    private String promptEn;
    private String negativePrompt;

    private Integer status;

    private String provider;
    private String model;
    private String size;
    private String quality;
    private String providerJobId;

    private String imageUrl;
    private String parameters;
    private String errorMsg;

    private Long userId;
    private String sourceApp;
    private String externalRunId;
    private String callbackUrl;
    private String callbackStatus;
    private String callbackError;
    private Long latencyMs;
    private String traceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
