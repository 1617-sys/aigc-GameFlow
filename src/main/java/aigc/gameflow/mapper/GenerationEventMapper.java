package aigc.gameflow.mapper;

import aigc.gameflow.model.entity.GenerationEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 任务生命周期事件的数据访问接口。 */
@Mapper
public interface GenerationEventMapper extends BaseMapper<GenerationEvent> {
}
