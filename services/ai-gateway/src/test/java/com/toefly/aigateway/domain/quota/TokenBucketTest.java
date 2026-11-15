package com.toefly.aigateway.domain.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.toefly.aigateway.domain.routing.ModelRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    static final QuotaLimit TPM = new QuotaLimit(QuotaDimension.TPM, 6000);
    static final QuotaLimit TPD = new QuotaLimit(QuotaDimension.TPD, 200_000);

    @Test
    void refillsContinuouslyUpToTheCapacity() {
        TokenBucket empty = new TokenBucket(0, 0);

        assertThat(empty.refill(TPM, 30_000).tokens()).isCloseTo(3000, within(0.001));
        assertThat(empty.refill(TPM, 600_000).tokens()).isEqualTo(6000);
    }

    @Test
    void waitIsTheTimeUntilNeedAndFloorFit() {
        TokenBucket bucket = new TokenBucket(1000, 0);

        assertThat(bucket.waitFor(TPM, 500, 0)).isZero();
        assertThat(bucket.waitFor(TPM, 2000, 0)).isEqualTo(10_000);
        assertThat(bucket.waitFor(TPM, 7000, 0)).isEqualTo(-1);
    }

    @Test
    void gradingLeavesTenPercentOfTheDailyBudgetAndBackgroundThirty() {
        ReservePolicy policy = ReservePolicy.defaults();

        assertThat(policy.floor(TPD, Priority.INTERACTIVE)).isZero();
        assertThat(policy.floor(TPD, Priority.GRADING)).isEqualTo(20_000);
        assertThat(policy.floor(TPD, Priority.BACKGROUND)).isEqualTo(60_000);
        assertThat(policy.floor(TPM, Priority.BACKGROUND)).isZero();
    }

    @Test
    void lowerLimitFromTheProviderWinsAndAHigherOneIsIgnored() {
        ProviderQuota quota = new ProviderQuota(ModelRef.parse("GROQ:openai/gpt-oss-20b"), List.of(TPM, TPD));

        assertThat(quota.lowered(QuotaDimension.TPM, 5000)
                        .limit(QuotaDimension.TPM)
                        .orElseThrow()
                        .capacity())
                .isEqualTo(5000);
        assertThat(quota.lowered(QuotaDimension.TPM, 9000)).isSameAs(quota);
    }
}
