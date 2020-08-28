package io.dropwizard.health.core;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.health.conf.HealthCheckConfiguration;
import io.dropwizard.health.conf.Schedule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HealthCheckManagerTest {
    private static final String NAME = "test";
    private static final String NAME_2 = "test2";
    @Mock
    private HealthCheckScheduler scheduler;

    @Test
    public void shouldIgnoreUnconfiguredAddedHealthChecks() {
        // given
        final HealthCheckManager manager = new HealthCheckManager(Collections.emptyList(), scheduler, new MetricRegistry());

        // when
        manager.onHealthCheckAdded(NAME, mock(HealthCheck.class));

        // then
        verifyNoInteractions(scheduler);
    }

    @Test
    public void shouldScheduleHealthCheckWhenConfiguredHealthCheckAdded() {
        // given
        final HealthCheckConfiguration config = new HealthCheckConfiguration();
        config.setName(NAME);
        config.setCritical(true);
        config.setSchedule(new Schedule());
        final HealthCheckManager manager = new HealthCheckManager(Collections.singletonList(config), scheduler, new MetricRegistry());

        // when
        manager.onHealthCheckAdded(NAME, mock(HealthCheck.class));

        // then
        verifyCheckWasScheduled(scheduler, NAME, true);
    }

    @Test
    public void shouldNotEncounterNameConflictWhenMultipleConfiguredHealthCheckAddedForSeparateManagersWithTheSameRegistry() {
        // given
        final HealthCheckConfiguration config = new HealthCheckConfiguration();
        config.setName(NAME);
        config.setCritical(true);
        config.setSchedule(new Schedule());
        final HealthCheckScheduler scheduler2 = mock(HealthCheckScheduler.class);
        final HealthCheckManager manager = new HealthCheckManager(Collections.singletonList(config), scheduler, new MetricRegistry());
        final HealthCheckManager manager2 = new HealthCheckManager(Collections.singletonList(config), scheduler2, new MetricRegistry(),
                "test2");

        // when
        manager.onHealthCheckAdded(NAME, mock(HealthCheck.class));
        manager2.onHealthCheckAdded(NAME, mock(HealthCheck.class));

        // then
        verifyCheckWasScheduled(scheduler, NAME, true);
        verifyCheckWasScheduled(scheduler2, NAME, true);
    }

    @Test
    public void shouldUnscheduleTaskWhenHealthCheckRemoved() {
        // given
        final ScheduledHealthCheck healthCheck = mock(ScheduledHealthCheck.class);
        final HealthCheckManager manager = new HealthCheckManager(Collections.emptyList(), scheduler, new MetricRegistry(), null,
                ImmutableMap.of(NAME, healthCheck));

        // when
        manager.onHealthCheckRemoved(NAME, mock(HealthCheck.class));

        // then
        verify(scheduler).unschedule(NAME);
    }

    @Test
    public void shouldDoNothingWhenStateChangesForUnconfiguredHealthCheck() {
        // given
        final HealthCheckManager manager = new HealthCheckManager(Collections.emptyList(), scheduler, new MetricRegistry());

        // when
        manager.onStateChanged(NAME, false);

        // then
        verifyNoInteractions(scheduler);
    }

    @Test
    public void shouldMarkServerUnhealthyWhenCriticalHealthCheckFails() {
        // given
        final HealthCheckConfiguration config = new HealthCheckConfiguration();
        config.setName(NAME);
        config.setCritical(true);
        config.setSchedule(new Schedule());
        final HealthCheckManager manager = new HealthCheckManager(Collections.singletonList(config), scheduler, new MetricRegistry());
        manager.initializeAppHealth();
        final HealthCheck check = mock(HealthCheck.class);

        // when
        manager.onHealthCheckAdded(NAME, check);
        boolean beforeFailure = manager.getIsAppHealthy().get();
        manager.onStateChanged(NAME, false);
        boolean afterFailure = manager.getIsAppHealthy().get();

        // then
        assertThat(beforeFailure)
                .isTrue();
        assertThat(afterFailure)
                .isFalse();
        verifyCheckWasScheduled(scheduler, NAME, true);
    }

    @Test
    public void shouldMarkServerHealthyWhenCriticalHealthCheckRecovers() {
        // given
        final HealthCheckConfiguration config = new HealthCheckConfiguration();
        config.setName(NAME);
        config.setCritical(true);
        config.setSchedule(new Schedule());
        final HealthCheckManager manager = new HealthCheckManager(Collections.singletonList(config), scheduler, new MetricRegistry());
        final HealthCheck check = mock(HealthCheck.class);

        // when
        manager.onHealthCheckAdded(NAME, check);
        manager.onStateChanged(NAME, false);
        boolean beforeRecovery = manager.getIsAppHealthy().get();
        manager.onStateChanged(NAME, true);
        boolean afterRecovery = manager.getIsAppHealthy().get();

        // then
        assertThat(beforeRecovery)
                .isFalse();
        assertThat(afterRecovery)
                .isTrue();
        ArgumentCaptor<ScheduledHealthCheck> checkCaptor = ArgumentCaptor.forClass(ScheduledHealthCheck.class);
        ArgumentCaptor<Boolean> healthyCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(scheduler, times(3)).schedule(checkCaptor.capture(), healthyCaptor.capture());
        assertThat(checkCaptor.getAllValues().get(0).getName())
                .isEqualTo(NAME);
        assertThat(checkCaptor.getAllValues().get(0).isCritical())
                .isTrue();
        assertThat(healthyCaptor.getAllValues().get(0))
                .isTrue();
        assertThat(checkCaptor.getAllValues().get(1).getName())
                .isEqualTo(NAME);
        assertThat(checkCaptor.getAllValues().get(1).isCritical())
                .isTrue();
        assertThat(healthyCaptor.getAllValues().get(1))
                .isFalse();
        assertThat(checkCaptor.getAllValues().get(2).getName())
                .isEqualTo(NAME);
        assertThat(checkCaptor.getAllValues().get(2).isCritical())
                .isTrue();
        assertThat(healthyCaptor.getAllValues().get(2))
                .isTrue();
    }

    @Test
    public void shouldNotChangeServerStateWhenNonCriticalHealthCheckFails() {
        // given
        final HealthCheckConfiguration config = new HealthCheckConfiguration();
        config.setName(NAME);
        config.setCritical(false);
        config.setSchedule(new Schedule());
        final HealthCheck check = mock(HealthCheck.class);
        final HealthCheckManager manager = new HealthCheckManager(Collections.singletonList(config), scheduler, new MetricRegistry());
        manager.initializeAppHealth();

        // when
        manager.onHealthCheckAdded(NAME, check);
        manager.onStateChanged(NAME, false);
        boolean afterFailure = manager.getIsAppHealthy().get();

        // then
        verifyCheckWasScheduled(scheduler, NAME, false);
        assertThat(afterFailure).isTrue();
    }

    @Test
    public void shouldNotChangeServerStateWhenNonCriticalHealthCheckRecovers() {
        // given
        final HealthCheckConfiguration nonCriticalConfig = new HealthCheckConfiguration();
        nonCriticalConfig.setName(NAME);
        nonCriticalConfig.setCritical(false);
        nonCriticalConfig.setSchedule(new Schedule());
        final HealthCheckConfiguration criticalConfig = new HealthCheckConfiguration();
        criticalConfig.setName(NAME_2);
        criticalConfig.setCritical(true);
        criticalConfig.setSchedule(new Schedule());
        final List<HealthCheckConfiguration> configs = ImmutableList.of(nonCriticalConfig, criticalConfig);
        final HealthCheckManager manager = new HealthCheckManager(configs, scheduler, new MetricRegistry());
        final HealthCheck check = mock(HealthCheck.class);

        // when
        manager.onHealthCheckAdded(NAME, check);
        manager.onHealthCheckAdded(NAME_2, check);
        manager.onStateChanged(NAME, false);
        manager.onStateChanged(NAME_2, false);
        boolean beforeRecovery = manager.getIsAppHealthy().get();
        manager.onStateChanged(NAME, true);
        boolean afterRecovery = manager.getIsAppHealthy().get();

        // then
        assertThat(beforeRecovery)
                .isFalse();
        assertThat(afterRecovery)
                .isFalse();
        ArgumentCaptor<ScheduledHealthCheck> checkCaptor = ArgumentCaptor.forClass(ScheduledHealthCheck.class);
        ArgumentCaptor<Boolean> healthyCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(scheduler, times(5)).schedule(checkCaptor.capture(), healthyCaptor.capture());
        assertThat(checkCaptor.getAllValues().get(0).getName())
                .isEqualTo(NAME);
        assertThat(checkCaptor.getAllValues().get(0).isCritical())
                .isFalse();
        assertThat(healthyCaptor.getAllValues().get(0))
                .isTrue();
        assertThat(checkCaptor.getAllValues().get(1).getName())
                .isEqualTo(NAME_2);
        assertThat(checkCaptor.getAllValues().get(1).isCritical())
                .isTrue();
        assertThat(healthyCaptor.getAllValues().get(1))
                .isTrue();
        assertThat(checkCaptor.getAllValues().get(2).getName())
                .isEqualTo(NAME);
        assertThat(checkCaptor.getAllValues().get(2).isCritical())
                .isFalse();
        assertThat(healthyCaptor.getAllValues().get(2))
                .isFalse();
        assertThat(checkCaptor.getAllValues().get(3).getName())
                .isEqualTo(NAME_2);
        assertThat(checkCaptor.getAllValues().get(3).isCritical())
                .isTrue();
        assertThat(healthyCaptor.getAllValues().get(3))
                .isFalse();
        assertThat(checkCaptor.getAllValues().get(4).getName())
                .isEqualTo(NAME);
        assertThat(checkCaptor.getAllValues().get(4).isCritical())
                .isFalse();
        assertThat(healthyCaptor.getAllValues().get(4))
                .isTrue();
    }

    @Test
    public void shouldRecordNumberOfHealthyAndUnhealthyHealthChecks() {
        // given
        final Schedule schedule = new Schedule();
        final HealthCheckConfiguration nonCriticalConfig = new HealthCheckConfiguration();
        nonCriticalConfig.setName(NAME);
        nonCriticalConfig.setCritical(false);
        nonCriticalConfig.setSchedule(schedule);
        final HealthCheckConfiguration criticalConfig = new HealthCheckConfiguration();
        criticalConfig.setName(NAME_2);
        criticalConfig.setCritical(true);
        criticalConfig.setSchedule(schedule);
        final List<HealthCheckConfiguration> configs = ImmutableList.of(nonCriticalConfig, criticalConfig);
        final HealthCheck check = mock(HealthCheck.class);
        final MetricRegistry metrics = new MetricRegistry();
        final ScheduledHealthCheck check1 = new ScheduledHealthCheck(NAME, nonCriticalConfig.isCritical(), check,
                schedule, new State(NAME, schedule.getFailureAttempts(), schedule.getSuccessAttempts(),
                (name, newState) -> {}), metrics.counter(NAME + ".healthy"), metrics.counter(NAME + ".unhealthy"));
        final ScheduledHealthCheck check2 = new ScheduledHealthCheck(NAME_2, criticalConfig.isCritical(), check,
                schedule, new State(NAME, schedule.getFailureAttempts(), schedule.getSuccessAttempts(),
                (name, newState) -> {}), metrics.counter(NAME_2 + ".healthy"), metrics.counter(NAME_2 + ".unhealthy"));
        final HealthCheckManager manager = new HealthCheckManager(configs, scheduler, metrics, null,
                ImmutableMap.of(NAME, check1, NAME_2, check2));

        // then
        assertThat(metrics.gauge(manager.getAggregateHealthyName(), null).getValue())
                .isEqualTo(2L);
        assertThat(metrics.gauge(manager.getAggregateUnhealthyName(), null).getValue())
                .isEqualTo(0L);

        // when
        when(check.execute()).thenReturn(HealthCheck.Result.unhealthy("because"));
        // Fail 3 times, to trigger unhealthy state change
        check2.run();
        check2.run();
        check2.run();

        // then
        assertThat(metrics.gauge(manager.getAggregateHealthyName(), null).getValue())
                .isEqualTo(1L);
        assertThat(metrics.gauge(manager.getAggregateUnhealthyName(), null).getValue())
                .isEqualTo(1L);
    }

    private void verifyCheckWasScheduled(HealthCheckScheduler scheduler, String name, boolean critical) {
        ArgumentCaptor<ScheduledHealthCheck> checkCaptor = ArgumentCaptor.forClass(ScheduledHealthCheck.class);
        verify(scheduler).schedule(checkCaptor.capture(), eq(true));
        assertThat(checkCaptor.getValue().getName())
                .isEqualTo(name);
        assertThat(checkCaptor.getValue().isCritical())
                .isEqualTo(critical);
    }
}
