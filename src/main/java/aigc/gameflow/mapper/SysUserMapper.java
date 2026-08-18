package aigc.gameflow.mapper;

import aigc.gameflow.model.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 用户数据访问接口。 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    // 在 SQL 中判断余额并扣减，避免“先查询再扣费”的并发超扣。
    @Update("UPDATE sys_user SET balance = balance - 1 WHERE id = #{userId} AND balance > 0")
    int debitBalance(@Param("userId") Long userId);
}
