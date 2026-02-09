package org.example.javaproducer;

import com.example.v1.EventMetadata;
import com.example.v1.PostalAddress;
import com.example.v1.TenantRef;
import com.google.protobuf.Timestamp;
import example.v1.CustomerCreated;
import example.v1.EventEnvelope;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

public final class JavaProtobufProducer {
    private JavaProtobufProducer() {
    }

    public static void main(String[] args) {
        String bootstrapServers = readEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092");
        String schemaRegistryUrl = readEnv("SCHEMA_REGISTRY_URL", "http://localhost:8081");
        String topic = readEnv("KAFKA_TOPIC", "customer-events");
        long intervalMillis = parseIntervalMillis(readEnv("SEND_INTERVAL_SECONDS", "10"));

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "java-protobuf-producer");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                KafkaProtobufSerializer.class.getName()
        );
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(AbstractKafkaSchemaSerDeConfig.NORMALIZE_SCHEMAS, true);
        props.put(
                AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY,
                TopicRecordNameStrategy.class.getName()
        );

        try (
                KafkaProducer<String, EventEnvelope> producer = new KafkaProducer<>(props);
                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()
        ) {
            CountDownLatch shutdownSignal = new CountDownLatch(1);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Stopping producer...");
                scheduler.shutdownNow();
                shutdownSignal.countDown();
            }));

            System.out.printf(
                    "Sending protobuf messages to topic '%s' every %.3f seconds. Press Ctrl+C to stop.%n",
                    topic,
                    intervalMillis / 1000.0
            );

            scheduler.scheduleAtFixedRate(
                    () -> sendOneRecord(producer, topic),
                    0,
                    intervalMillis,
                    TimeUnit.MILLISECONDS
            );

            try {
                shutdownSignal.await();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                System.out.println("Stopping producer...");
            }
        }
    }

    private static void sendOneRecord(KafkaProducer<String, EventEnvelope> producer, String topic) {
        try {
            Instant now = Instant.now();
            Timestamp eventTime = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();

            PostalAddress address = PostalAddress.newBuilder()
                    .setLine1("100 Test Street")
                    .setLine2("Suite 10")
                    .setCity("Austin")
                    .setState("TX")
                    .setPostalCode("78701")
                    .setCountryCode("US")
                    .build();

            TenantRef tenant = TenantRef.newBuilder()
                    .setTenantId("tenant-a")
                    .setRegion("us-east")
                    .build();

            CustomerCreated customerCreated = CustomerCreated.newBuilder()
                    .setId(UUID.randomUUID().toString())
                    .setEmail("important.tester+" + System.currentTimeMillis() + "@example.com")
                    .setFullName("Important Tester")
                    .setCreatedAt(eventTime)
                    .setAddress(address)
                    .setTenant(tenant)
                    .build();

            EventMetadata metadata = EventMetadata.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setSourceSystem("integration-test-suite")
                    .setProducer("java-protobuf-producer")
                    .setOccurredAt(eventTime)
                    .build();

            EventEnvelope payload = EventEnvelope.newBuilder()
                    .setCustomerCreated(customerCreated)
                    .setMetadata(metadata)
                    .build();

            ProducerRecord<String, EventEnvelope> record =
                    new ProducerRecord<>(topic, customerCreated.getId(), payload);

            RecordMetadata recordMetadata = producer.send(record).get(10, TimeUnit.SECONDS);
            System.out.printf(
                    "Sent record to %s-%d offset %d with subject strategy TopicRecordNameStrategy%n",
                    recordMetadata.topic(),
                    recordMetadata.partition(),
                    recordMetadata.offset()
            );
        } catch (Exception exception) {
            System.err.printf("Failed to send record: %s%n", exception.getMessage());
            exception.printStackTrace(System.err);
        }
    }

    private static String readEnv(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static long parseIntervalMillis(String secondsText) {
        try {
            double seconds = Double.parseDouble(secondsText);
            if (seconds <= 0) {
                throw new IllegalArgumentException("SEND_INTERVAL_SECONDS must be > 0");
            }
            return (long) (seconds * 1000);
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException(
                    "Invalid SEND_INTERVAL_SECONDS value: " + secondsText,
                    numberFormatException
            );
        }
    }
}
