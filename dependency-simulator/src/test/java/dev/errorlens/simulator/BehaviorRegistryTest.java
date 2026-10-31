package dev.errorlens.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class BehaviorRegistryTest {

    private final BehaviorRegistry registry = new BehaviorRegistry(
            new SimulatorProperties("token", 10_000, 2, Duration.ofMinutes(10)));

    @Test
    void failFirstNRecoversAfterConfiguredFailures() {
        registry.configure("key-00000001", new Behavior(Behavior.Mode.FAIL_FIRST_N, 0, 2, 503));
        assertThat(registry.decide("key-00000001").status()).isEqualTo(503);
        assertThat(registry.decide("key-00000001").status()).isEqualTo(503);
        assertThat(registry.decide("key-00000001").status()).isEqualTo(200);
    }

    @Test
    void unknownKeyRespondsImmediately() {
        BehaviorRegistry.Decision decision = registry.decide(null);
        assertThat(decision.status()).isEqualTo(200);
        assertThat(decision.delayMs()).isZero();
    }

    @Test
    void limitsNumberOfConfiguredBehaviors() {
        assertThat(registry.configure("key-00000001", Behavior.immediate())).isTrue();
        assertThat(registry.configure("key-00000002", Behavior.immediate())).isTrue();
        assertThat(registry.configure("key-00000003", Behavior.immediate())).isFalse();
        assertThat(registry.reset()).isEqualTo(2);
    }
}
