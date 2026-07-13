package com.github.nankotsu029.landformcraft.ai.spi;

import com.github.nankotsu029.landformcraft.model.ProviderUsage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderQuotaTest {
    @Test
    void enforcesLocalRequestRateAndCumulativeTokenBudget() {
        var policy = new TerrainDesignPolicy(
                Duration.ofSeconds(1), 1, Duration.ZERO, 256, 1, 300
        );
        var quota = new ProviderQuota(
                policy, Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC)
        );

        quota.beforeAttempt();
        TerrainDesignException rate = assertThrows(TerrainDesignException.class, quota::beforeAttempt);
        assertEquals(ProviderFailureCode.LOCAL_RATE_LIMIT, rate.code());

        TerrainDesignException tokens = assertThrows(
                TerrainDesignException.class,
                () -> quota.record(new ProviderUsage(200, 101, 301))
        );
        assertEquals(ProviderFailureCode.TOKEN_BUDGET_EXCEEDED, tokens.code());
    }
}
