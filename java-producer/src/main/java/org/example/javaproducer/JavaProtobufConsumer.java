package org.example.javaproducer;

import example.v1.EventEnvelope;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

public final class JavaProtobufConsumer {
    private JavaProtobufConsumer() {
    }

    public static void main(String[] args) {
        String bootstrapServers = readEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092");
        String schemaRegistryUrl = readEnv("SCHEMA_REGISTRY_URL", "http://localhost:8081");
        String topic = readEnv("KAFKA_TOPIC", "customer-events");
        String groupId = readEnv("KAFKA_GROUP_ID", "java-protobuf-consumer");

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                KafkaProtobufDeserializer.class.getName()
        );
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(
                AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY,
                TopicRecordNameStrategy.class.getName()
        );
        props.put(
                KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE,
                EventEnvelope.class.getName()
        );

        AtomicBoolean stopping = new AtomicBoolean(false);

        try (KafkaConsumer<String, EventEnvelope> consumer = new KafkaConsumer<>(props)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                stopping.set(true);
                System.out.println("Stopping consumer...");
                consumer.wakeup();
            }));

            consumer.subscribe(List.of(topic));
            System.out.printf(
                    "Consuming protobuf messages from topic '%s' (group '%s'). Press Ctrl+C to stop.%n",
                    topic,
                    groupId
            );

            while (!stopping.get()) {
                ConsumerRecords<String, EventEnvelope> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, EventEnvelope> record : records) {
                    logRecord(record);
                }
            }
        } catch (WakeupException wakeupException) {
            if (!stopping.get()) {
                throw wakeupException;
            }
        }
    }

    private static void logRecord(ConsumerRecord<String, EventEnvelope> record) {
        EventEnvelope payload = record.value();
        String payloadCase = detectEventType(payload);
        String payloadText = payload == null ? "null" : payload.toString().replace('\n', ' ').trim();

        System.out.printf(
                "Received topic=%s partition=%d offset=%d key=%s event=%s payload=%s%n",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                payloadCase,
                payloadText
        );
    }

    private static String detectEventType(EventEnvelope payload) {
        if (payload == null) {
            return "UNKNOWN";
        }
        if (payload.hasCustomerCreated()) {
            return "CUSTOMER_CREATED";
        }
        return "UNKNOWN";
    }

    private static String readEnv(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
