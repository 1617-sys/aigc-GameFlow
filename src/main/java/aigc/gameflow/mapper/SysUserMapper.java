package aigc.gameflow.mapper;

import aigc.gameflow.model.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Update("UPDATE sys_user SET balance = balance - 1 WHERE id = #{userId} AND balance > 0")
    int debitBalance(@Param("userId") Long userId);
}
