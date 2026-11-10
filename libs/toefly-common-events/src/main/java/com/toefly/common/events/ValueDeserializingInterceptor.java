package com.toefly.common.events;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.ClassUtils;

/**
 * Deserializes record values just before the listener, while the consumer itself reads {@code byte[]}. The error
 * handler gets the record as it was polled, so a record that fails in the listener goes to {@code <topic>.retry} or
 * {@code <topic>.dlq} with its original bytes and schema id, and the listener of {@code .retry} reads it the same way.
 * A value the delegate cannot read becomes a {@link DeserializationException}, which is not retried.
 */
public class ValueDeserializingInterceptor implements RecordInterceptor<Object, Object>, DisposableBean {

    private final List<Deserializer<?>> created = new CopyOnWriteArrayList<>();
    private final ThreadLocal<Deserializer<?>> delegates = ThreadLocal.withInitial(this::newDelegate);
    private volatile @Nullable Object delegateType;
    private volatile Map<String, Object> configs = Map.of();

    /**
     * Takes over the value deserializer of a consumer factory.
     *
     * @param consumerConfigs the factory configuration, with the configured value deserializer
     */
    void adopt(Map<String, Object> consumerConfigs) {
        Map<String, Object> copy = new HashMap<>(consumerConfigs);
        this.delegateType = copy.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
        this.configs = Map.copyOf(copy);
    }

    boolean adopted() {
        return delegateType != null;
    }

    @Override
    public @Nullable ConsumerRecord<Object, Object> intercept(
            ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        if (delegateType == null || !(record.value() instanceof byte[] data)) {
            return record;
        }
        Object value;
        try {
            value = delegates.get().deserialize(record.topic(), record.headers(), data);
        } catch (RuntimeException e) {
            throw new DeserializationException(
                    "Failed to deserialize the value of " + record.topic() + '-' + record.partition() + '@'
                            + record.offset(),
                    data,
                    false,
                    e);
        }
        return new ConsumerRecord<>(
                record.topic(),
                record.partition(),
                record.offset(),
                record.timestamp(),
                record.timestampType(),
                record.serializedKeySize(),
                record.serializedValueSize(),
                record.key(),
                value,
                record.headers(),
                record.leaderEpoch());
    }

    private Deserializer<?> newDelegate() {
        Object type = delegateType;
        try {
            Class<?> clazz = type instanceof Class<?> c ? c : ClassUtils.forName(String.valueOf(type), null);
            Deserializer<?> delegate = (Deserializer<?>) BeanUtils.instantiateClass(clazz);
            delegate.configure(configs, false);
            created.add(delegate);
            return delegate;
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException("Value deserializer " + type + " is not available", e);
        }
    }

    @Override
    public void destroy() {
        created.forEach(Deserializer::close);
        created.clear();
    }
}
