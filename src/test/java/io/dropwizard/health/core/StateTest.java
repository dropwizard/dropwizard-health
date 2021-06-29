package io.dropwizard.health.core;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class StateTest {
    private static final String NAME = "test";
    private StateChangedCallback callback;
    private AtomicBoolean didStateChange;

    @Before
    public void setUp() {
        callback = mock(StateChangedCallback.class);
        this.didStateChange = new AtomicBoolean(false);
    }

    @Test
    public void singleFailureShouldNotChangeStateIfThresholdNotExceeded() {
        final State state = new State(NAME, 2, 1, true,
                (healthCheckName, newState) -> didStateChange.set(true));
        state.failure();

        assertThat(didStateChange.get()).isFalse();
        assertThat(state.getHealthy().get()).isTrue();
    }

    @Test
    public void singleFailureShouldChangeStateIfThresholdExceeded() {
        final State state = new State(NAME, 1, 1, true,
                (healthCheckName, newState) -> didStateChange.set(true));
        assertThat(state.getHealthy().get()).isTrue();

        state.failure();

        assertThat(didStateChange.get()).isTrue();
        assertThat(state.getHealthy().get()).isFalse();
    }

    @Test
    public void singleSuccessShouldNotChangeStateIfThresholdNotExceeded() {
        final State state = new State(NAME, 1, 2, false,
                (healthCheckName, newState) -> didStateChange.set(true));
        assertThat(state.getHealthy().get()).isFalse();

        state.success();

        assertThat(didStateChange.get()).isFalse();
        assertThat(state.getHealthy().get()).isFalse();
    }

    @Test
    public void singleSuccessShouldChangeStateIfThresholdExceeded() {
        final State state = new State(NAME, 1, 1, false,
                (healthCheckName, newState) -> didStateChange.set(true));
        assertThat(state.getHealthy().get()).isFalse();

        state.success();

        assertThat(didStateChange.get()).isTrue();
        assertThat(state.getHealthy().get()).isTrue();
    }

    @Test
    public void failureFollowedByRecoveryShouldAllowAStateChangeToUnhealthyAfterAnotherFailureOccurs() {
        final State state = new State(NAME, 1, 1, true,
                (healthCheckName, newState) -> didStateChange.set(true));

        state.failure();

        assertThat(didStateChange.get()).isTrue();
        assertThat(state.getHealthy().get()).isFalse();

        didStateChange.set(false);

        state.success();

        assertThat(didStateChange.get()).isTrue();
        assertThat(state.getHealthy().get()).isTrue();

        didStateChange.set(false);

        state.failure();

        assertThat(didStateChange.get()).isTrue();
        assertThat(state.getHealthy().get()).isFalse();
    }

    @Test
    public void successFollowedByFailureShouldAllowAStateChangeToHealthyAfterAnotherSuccessOccurs() {
        final State state = new State(NAME, 1, 1, false,
                (healthCheckName, newState) -> didStateChange.set(true));
        assertThat(state.getHealthy().get()).isFalse();

        state.success();

        assertThat(didStateChange.get()).isTrue();
        assertThat(state.getHealthy().get()).isTrue();

        didStateChange.set(false);

        state.failure();

        assertThat(didStateChange.get()).isTrue();
        assertThat(state.getHealthy().get()).isFalse();

        didStateChange.set(false);

        state.success();

        assertThat(didStateChange.get()).isTrue();
        assertThat(state.getHealthy().get()).isTrue();
    }

    @Test
    public void dependencyFailingThenRecoveringTriggersStateChangeEventsCorrectly() {
        // given
        final State state = new State(NAME, 3, 2, true, callback);

        // when / then
        state.success(); // start success
        state.failure(); // first failure
        state.failure();
        state.failure(); // should trigger callback transitioning to unhealthy
        verify(callback).onStateChanged(NAME, false);

        state.success(); // dependency recovering and starts returning healthy
        state.success(); // should trigger callback transitioning to healthy
        verify(callback).onStateChanged(NAME, true);
    }
}
