package com.toefly.aigateway.adapter.out.valkey;

import com.toefly.aigateway.application.port.out.QuotaStorePort;
import com.toefly.aigateway.domain.quota.Admission;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.quota.ProviderQuota;
import com.toefly.aigateway.domain.quota.QuotaDemand;
import com.toefly.aigateway.domain.quota.QuotaDimension;
import com.toefly.aigateway.domain.quota.QuotaLimit;
import com.toefly.aigateway.domain.quota.Reservation;
import com.toefly.aigateway.domain.quota.ReservePolicy;
import com.toefly.aigateway.domain.quota.TokenBucket;
import com.toefly.aigateway.domain.routing.ModelRef;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Token buckets in Valkey shared by all replicas (§2.5.1, INV-AIG-05): one Lua script checks and takes every
 * dimension of a model at once, with Valkey's own clock.
 */
public class ValkeyQuotaStore implements QuotaStorePort {

    private static final Duration QUEUE_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate valkey;
    private final DefaultRedisScript<List> reserve;
    private final DefaultRedisScript<Long> settle;

    public ValkeyQuotaStore(StringRedisTemplate valkey) {
        this.valkey = valkey;
        this.reserve = new DefaultRedisScript<>();
        reserve.setLocation(new ClassPathResource("valkey/reserve.lua"));
        reserve.setResultType(List.class);
        this.settle = new DefaultRedisScript<>();
        settle.setLocation(new ClassPathResource("valkey/settle.lua"));
        settle.setResultType(Long.class);
    }

    @Override
    public Admission reserve(ProviderQuota quota, QuotaDemand demand, Priority priority, ReservePolicy policy) {
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();
        args.add(Integer.toString(quota.limits().size()));
        for (QuotaLimit limit : quota.limits()) {
            keys.add(bucket(quota.model(), limit.dimension()));
            args.add(Long.toString(limit.capacity()));
            args.add(Long.toString(limit.dimension().window().toMillis()));
            args.add(Double.toString(demand.need(limit.dimension())));
            args.add(Double.toString(policy.floor(limit, priority)));
        }
        keys.add(block(quota.model()));
        List<?> result = valkey.execute(reserve, keys, args.toArray());
        long status = ((Number) result.get(0)).longValue();
        long wait = ((Number) result.get(1)).longValue();
        if (status == 1) {
            return Admission.granted(new Reservation(UUID.randomUUID().toString(), quota.model(), demand));
        }
        return Admission.waitFor(status < 0 ? -1 : Math.max(1, wait));
    }

    @Override
    public void settle(ProviderQuota quota, Reservation reservation, QuotaDemand actual) {
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();
        for (QuotaLimit limit : quota.limits()) {
            double delta = reservation.demand().need(limit.dimension()) - actual.need(limit.dimension());
            if (delta != 0) {
                keys.add(bucket(quota.model(), limit.dimension()));
                args.add(Long.toString(limit.capacity()));
                args.add(Double.toString(delta));
            }
        }
        if (!keys.isEmpty()) {
            valkey.execute(settle, keys, args.toArray());
        }
    }

    @Override
    public void exhaust(ModelRef model, Duration pause) {
        valkey.opsForValue().set(block(model), "429", pause.isZero() ? Duration.ofSeconds(1) : pause);
    }

    @Override
    public Map<QuotaDimension, Double> remaining(ProviderQuota quota) {
        Map<QuotaDimension, Double> remaining = new EnumMap<>(QuotaDimension.class);
        Long now = valkey.execute(connection -> connection.serverCommands().time(), true);
        long nowMs = now == null ? System.currentTimeMillis() : now;
        boolean blocked = Boolean.TRUE.equals(valkey.hasKey(block(quota.model())));
        for (QuotaLimit limit : quota.limits()) {
            List<Object> data = valkey.opsForHash()
                    .multiGet(bucket(quota.model(), limit.dimension()), List.of("tokens", "updatedAt"));
            double tokens = limit.capacity();
            if (data.get(0) != null && data.get(1) != null) {
                tokens = new TokenBucket(Double.parseDouble(data.get(0).toString()), (long)
                                Double.parseDouble(data.get(1).toString()))
                        .refill(limit, nowMs)
                        .tokens();
            }
            remaining.put(limit.dimension(), blocked ? 0 : Math.max(0, tokens));
        }
        return remaining;
    }

    @Override
    public boolean enterQueue(UUID userId, Priority priority, int max) {
        String key = "aiq:userq:" + priority + ":" + userId;
        Long count = valkey.opsForValue().increment(key);
        valkey.expire(key, QUEUE_TTL);
        if (count != null && count > max) {
            valkey.opsForValue().decrement(key);
            return false;
        }
        return true;
    }

    @Override
    public void leaveQueue(UUID userId, Priority priority) {
        String key = "aiq:userq:" + priority + ":" + userId;
        Long left = valkey.opsForValue().decrement(key);
        if (left != null && left <= 0) {
            valkey.delete(key);
        }
    }

    static String bucket(ModelRef model, QuotaDimension dimension) {
        return "aiq:" + model.provider() + ":" + model.model() + ":" + dimension;
    }

    static String block(ModelRef model) {
        return "aiq:" + model.provider() + ":" + model.model() + ":block";
    }
}
