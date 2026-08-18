package aigc.gameflow.mapper;

import aigc.gameflow.model.entity.GenTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 生成任务数据访问接口，包含租约领取、续租和受条件保护的状态迁移。 */
@Mapper
public interface GenTaskMapper extends BaseMapper<GenTask> {

    @Update("""
            UPDATE gen_task
            SET status = #{runningStatus}, worker_id = #{workerId},
                lease_expire_time = #{leaseExpireTime}, last_heartbeat_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE task_uuid = #{taskUuid} AND status IN (#{pendingStatus}, #{retryingStatus})
            """)
    int claimForExecution(
            @Param("taskUuid") String taskUuid,
            @Param("workerId") String workerId,
            @Param("leaseExpireTime") LocalDateTime leaseExpireTime,
            @Param("now") LocalDateTime now,
            @Param("runningStatus") int runningStatus,
            @Param("pendingStatus") int pendingStatus,
            @Param("retryingStatus") int retryingStatus
    );

    @Update("""
            UPDATE gen_task
            SET lease_expire_time = #{leaseExpireTime}, last_heartbeat_time = #{now}, update_time = #{now}
            WHERE task_uuid = #{taskUuid} AND status = #{runningStatus} AND worker_id = #{workerId}
            """)
    int renewLease(
            @Param("taskUuid") String taskUuid,
            @Param("workerId") String workerId,
            @Param("runningStatus") int runningStatus,
            @Param("leaseExpireTime") LocalDateTime leaseExpireTime,
            @Param("now") LocalDateTime now
    );

    @Update("""
            UPDATE gen_task
            SET status = #{targetStatus}, error_msg = #{errorMsg}, retry_count = #{retryCount},
                worker_id = NULL, lease_expire_time = NULL, last_heartbeat_time = NULL,
                version = version + 1, update_time = #{now}
            WHERE task_uuid = #{taskUuid} AND status = #{expectedStatus} AND worker_id = #{workerId}
            """)
    int transitionOwnedStatus(
            @Param("taskUuid") String taskUuid,
            @Param("workerId") String workerId,
            @Param("expectedStatus") int expectedStatus,
            @Param("targetStatus") int targetStatus,
            @Param("errorMsg") String errorMsg,
            @Param("retryCount") int retryCount,
            @Param("now") LocalDateTime now
    );

    @Update("""
            UPDATE gen_task
            SET status = #{targetStatus}, error_msg = #{errorMsg}, retry_count = #{retryCount},
                worker_id = NULL, lease_expire_time = NULL, last_heartbeat_time = NULL,
                version = version + 1, update_time = #{now}
            WHERE task_uuid = #{taskUuid} AND status = #{runningStatus}
              AND worker_id = #{workerId} AND lease_expire_time < #{now}
            """)
    int recoverExpiredLease(
            @Param("taskUuid") String taskUuid,
            @Param("workerId") String workerId,
            @Param("runningStatus") int runningStatus,
            @Param("targetStatus") int targetStatus,
            @Param("errorMsg") String errorMsg,
            @Param("retryCount") int retryCount,
            @Param("now") LocalDateTime now
    );

    @Update("""
            UPDATE gen_task
            SET status = #{targetStatus}, error_msg = #{errorMsg}, retry_count = #{retryCount},
                worker_id = NULL, lease_expire_time = NULL, last_heartbeat_time = NULL,
                version = version + 1, update_time = #{now}
            WHERE task_uuid = #{taskUuid} AND status = #{expectedStatus}
            """)
    int transitionStatus(
            @Param("taskUuid") String taskUuid,
            @Param("expectedStatus") int expectedStatus,
            @Param("targetStatus") int targetStatus,
            @Param("errorMsg") String errorMsg,
            @Param("retryCount") int retryCount,
            @Param("now") LocalDateTime now
    );
}
