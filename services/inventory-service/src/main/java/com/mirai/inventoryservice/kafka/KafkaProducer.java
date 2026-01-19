package com.mirai.inventoryservice.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class KafkaProducer {
    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public KafkaProducer(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Send event to Kafka topic
     * @param topic Kafka topic name
     * @param key Partition key (usually item_id)
     * @param message Event message payload
     */
    public void sendEvent(String topic, String key, Map<String, Object> message) {
        CompletableFuture<SendResult<String, Map<String, Object>>> future = 
                kafkaTemplate.send(topic, key, message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Sent message to topic [{}] with key [{}]: offset={}", 
                        topic, key, result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send message to topic [{}] with key [{}]: {}", 
                        topic, key, ex.getMessage());
            }
        });
    }
}

