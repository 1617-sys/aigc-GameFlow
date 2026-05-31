package aigc.gameflow.mapper;

import aigc.gameflow.model.entity.GenerationEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GenerationEventMapper extends BaseMapper<GenerationEvent> {
}
