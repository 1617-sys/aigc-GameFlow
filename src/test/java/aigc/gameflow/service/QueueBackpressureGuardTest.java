package aigc.gameflow.service;

import aigc.gameflow.exception.ServiceOverloadedException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/** 验证积压阈值、Broker 不可用和关闭开关三种背压分支。 */
class QueueBackpressureGuardTest {

    @Test
    void allowsRequestBelowBacklogThreshold() {
        QueueBackpressureGuard guard = guard(true, 5);
        guard.updateSnapshot(4, true);

        assertDoesNotThrow(guard::checkAcceptingNewTasks);
    }

    @Test
    void rejectsRequestAtBacklogThreshold() {
        QueueBackpressureGuard guard = guard(true, 5);
        guard.updateSnapshot(5, true);

        assertThrows(ServiceOverloadedException.class, guard::checkAcceptingNewTasks);
    }

    @Test
    void rejectsRequestWhenRabbitMqStateIsUnavailable() {
        QueueBackpressureGuard guard = guard(true, 5);
        guard.updateSnapshot(0, false);

        assertThrows(ServiceOverloadedException.class, guard::checkAcceptingNewTasks);
    }

    @Test
    void disabledGuardDoesNotRejectRequest() {
        QueueBackpressureGuard guard = guard(false, 0);

        assertDoesNotThrow(guard::checkAcceptingNewTasks);
    }

    private QueueBackpressureGuard guard(boolean enabled, long threshold) {
        return new QueueBackpressureGuard(mock(RabbitTemplate.class), enabled, threshold);
    }
}
