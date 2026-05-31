package aigc.gameflow.mapper;

import aigc.gameflow.model.entity.GenTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

// 1. 必须加 @Mapper 注解，交给 Spring 管理
@Mapper
// 2. 必须是 interface，不是 class
// 3. 必须继承 BaseMapper<你操作的实体类>
public interface GenTaskMapper extends BaseMapper<GenTask> {

    // BaseMapper 已经帮你写好了 insert, delete, update, select...

}
