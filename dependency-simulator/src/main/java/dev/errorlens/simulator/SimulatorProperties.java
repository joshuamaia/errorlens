package dev.errorlens.simulator;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
        @DefaultValue("lab-control-token") String controlToken,
        @DefaultValue("10000") int maxDelayMs,
        @DefaultValue("1000") int maxBehaviors,
        @DefaultValue("10m") Duration behaviorTtl) {
}
