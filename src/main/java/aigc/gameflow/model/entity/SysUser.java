package aigc.gameflow.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 系统用户实体，余额代表可提交的生成次数。 */
@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password; // 存储 BCrypt 哈希，不保存明文密码
    private Integer balance;
    private String role;     // 当前使用 USER、ADMIN 两种角色

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
