package io.dropwizard.health.conf;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class HealthCheckConfiguration {

    @NotEmpty
    @JsonProperty
    private String name;

    @JsonProperty
    private boolean critical = false;

    @Valid
    @NotNull
    @JsonProperty
    private Schedule schedule = new Schedule();

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public boolean isCritical() {
        return critical;
    }

    public void setCritical(final boolean critical) {
        this.critical = critical;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(final Schedule schedule) {
        this.schedule = schedule;
    }
}
