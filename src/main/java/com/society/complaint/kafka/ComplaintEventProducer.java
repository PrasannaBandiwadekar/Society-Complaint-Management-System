package com.society.complaint.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * The PRODUCER — publishes ComplaintEvent objects to Kafka topics.
 *
 * Plain English analogy: think of Kafka as a post office, the topic as
 * a named mailbox (e.g. "complaint-notifications"), and this class as
 * the person dropping the letter in. It doesn't know or care who picks
 * it up — that's the consumer's job.
 *
 * KafkaTemplate is Spring's wrapper around the raw Kafka client — it
 * handles serialisation, connection pooling, and retries automatically.
 */

@Component
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class ComplaintEventProducer {

    // All events go to this single topic — the consumer filters by eventType
    public static final String TOPIC = "complaint-notifications";

    private final KafkaTemplate<String, ComplaintEvent> kafkaTemplate;

    public void publish(ComplaintEvent event) {
        // Use complaintId as the Kafka message key — this ensures all events
        // for the same complaint go to the same partition, preserving order.
        String key = String.valueOf(event.getComplaintId());

        CompletableFuture<SendResult<String, ComplaintEvent>> future =
                kafkaTemplate.send(TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} event for complaint #{}: {}",
                        event.getEventType(), event.getComplaintId(), ex.getMessage());
            } else {
                log.info("Published {} event for complaint #{} to partition {}",
                        event.getEventType(),
                        event.getComplaintId(),
                        result.getRecordMetadata().partition());
            }
        });
    }
}
