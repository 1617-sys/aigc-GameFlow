package aigc.gameflow.mapper;

import aigc.gameflow.model.entity.GenerationOutbox;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface GenerationOutboxMapper extends BaseMapper<GenerationOutbox> {

    @Update("""
            UPDATE generation_outbox
            SET status = 'PROCESSING', locked_by = #{workerId}, locked_until = #{lockedUntil},
                update_time = NOW()
            WHERE event_id = #{eventId}
              AND (
                    (status = 'PENDING' AND next_attempt_time <= NOW())
                    OR (status = 'PROCESSING' AND locked_until < NOW())
                  )
            """)
    int claim(
            @Param("eventId") String eventId,
            @Param("workerId") String workerId,
            @Param("lockedUntil") LocalDateTime lockedUntil
    );

    @Update("""
            UPDATE generation_outbox
            SET status = 'SENT', sent_time = NOW(), locked_by = NULL, locked_until = NULL,
                last_error = NULL, update_time = NOW()
            WHERE event_id = #{eventId} AND status = 'PROCESSING' AND locked_by = #{workerId}
            """)
    int markSent(@Param("eventId") String eventId, @Param("workerId") String workerId);

    @Update("""
            UPDATE generation_outbox
            SET status = 'PENDING', retry_count = retry_count + 1,
                next_attempt_time = #{nextAttemptTime}, last_error = #{lastError},
                locked_by = NULL, locked_until = NULL, update_time = NOW()
            WHERE event_id = #{eventId} AND status = 'PROCESSING' AND locked_by = #{workerId}
            """)
    int scheduleRetry(
            @Param("eventId") String eventId,
            @Param("workerId") String workerId,
            @Param("nextAttemptTime") LocalDateTime nextAttemptTime,
            @Param("lastError") String lastError
    );
}
