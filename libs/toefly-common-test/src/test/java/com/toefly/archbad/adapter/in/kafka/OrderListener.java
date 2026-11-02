package com.toefly.archbad.adapter.in.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

public class OrderListener {

    private final KafkaTemplate<String, String> template;

    public OrderListener(KafkaTemplate<String, String> template) {
        this.template = template;
    }

    @KafkaListener(topics = "toefly.demo.order-placed.v1")
    void on(String value) {
        template.send("toefly.demo.order-shipped.v1", value);
    }
}
