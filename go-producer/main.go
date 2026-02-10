package main

import (
	"fmt"
	"log"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
	"github.com/confluentinc/confluent-kafka-go/v2/schemaregistry"
	"github.com/confluentinc/confluent-kafka-go/v2/schemaregistry/serde"
	srprotobuf "github.com/confluentinc/confluent-kafka-go/v2/schemaregistry/serde/protobuf"
	examplev1 "github.com/modulus100/apicurio-confluent-schema-investigation/generated/go/example/v1"
	"google.golang.org/protobuf/types/known/timestamppb"
)

func main() {
	bootstrapServers := readEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092")
	schemaRegistryURL := readEnv("SCHEMA_REGISTRY_URL", "http://localhost:8081")
	schemaRegistryUsername := os.Getenv("SCHEMA_REGISTRY_USERNAME")
	schemaRegistryPassword := os.Getenv("SCHEMA_REGISTRY_PASSWORD")
	topic := readEnv("KAFKA_TOPIC", "customer-events")
	interval := parseInterval(readEnv("SEND_INTERVAL_SECONDS", "10"))
	autoRegisterSchemas := parseBoolEnv("SCHEMA_AUTO_REGISTER", true)
	normalizeSchemas := parseBoolEnv("SCHEMA_NORMALIZE_SCHEMAS", true)

	eventRecordFullName := string((&examplev1.EventEnvelope{}).ProtoReflect().Descriptor().FullName())
	subject := topicRecordSubject(topic, eventRecordFullName)

	schemaRegistryConfig := newSchemaRegistryConfig(
		strings.TrimRight(schemaRegistryURL, "/"),
		schemaRegistryUsername,
		schemaRegistryPassword,
	)

	schemaRegistryClient, err := schemaregistry.NewClient(schemaRegistryConfig)
	if err != nil {
		log.Fatalf("Unable to create schema registry client: %v", err)
	}

	serializerConfig := srprotobuf.NewSerializerConfig()
	serializerConfig.AutoRegisterSchemas = autoRegisterSchemas
	serializerConfig.NormalizeSchemas = normalizeSchemas

	serializer, err := srprotobuf.NewSerializer(schemaRegistryClient, serde.ValueSerde, serializerConfig)
	if err != nil {
		log.Fatalf(
			"Unable to create protobuf schema serializer for %s: %v",
			schemaRegistryURL,
			err,
		)
	}
	serializer.SubjectNameStrategy = topicRecordNameStrategy(eventRecordFullName)

	producer, err := kafka.NewProducer(&kafka.ConfigMap{
		"bootstrap.servers": bootstrapServers,
		"client.id":         "go-protobuf-producer",
	})
	if err != nil {
		log.Fatalf("Unable to create producer: %v", err)
	}
	defer producer.Close()

	done := make(chan os.Signal, 1)
	signal.Notify(done, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		for event := range producer.Events() {
			switch e := event.(type) {
			case *kafka.Message:
				if e.TopicPartition.Error != nil {
					log.Printf("Delivery failed: %v", e.TopicPartition.Error)
					continue
				}
				log.Printf(
					"Sent record to %s[%d]@%v with subject %q",
					*e.TopicPartition.Topic,
					e.TopicPartition.Partition,
					e.TopicPartition.Offset,
					subject,
				)
			}
		}
	}()

	log.Printf("Sending protobuf messages to topic %q every %s. Press Ctrl+C to stop.", topic, interval)
	log.Printf(
		"Schema serializer config: auto_register=%t normalize=%t subject=%q",
		autoRegisterSchemas,
		normalizeSchemas,
		subject,
	)

	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	send := func() {
		envelope := buildEnvelope()
		payload, err := serializer.Serialize(topic, envelope)
		if err != nil {
			log.Printf("Serialize error: %v", err)
			return
		}

		message := &kafka.Message{
			TopicPartition: kafka.TopicPartition{Topic: &topic, Partition: kafka.PartitionAny},
			Key:            []byte(envelope.GetCustomerCreated().GetId()),
			Value:          payload,
		}

		if err := producer.Produce(message, nil); err != nil {
			log.Printf("Produce error: %v", err)
		}
	}

	send()
	for {
		select {
		case <-done:
			log.Printf("Stopping producer...")
			_ = producer.Flush(10_000)
			return
		case <-ticker.C:
			send()
		}
	}
}

func newSchemaRegistryConfig(url, username, password string) *schemaregistry.Config {
	if strings.TrimSpace(username) == "" {
		return schemaregistry.NewConfig(url)
	}
	return schemaregistry.NewConfigWithBasicAuthentication(url, username, password)
}

func topicRecordSubject(topic, fullRecordName string) string {
	return fmt.Sprintf("%s-%s", topic, fullRecordName)
}

func topicRecordNameStrategy(fullRecordName string) serde.SubjectNameStrategyFunc {
	return func(topic string, _ serde.Type, _ schemaregistry.SchemaInfo) (string, error) {
		return topicRecordSubject(topic, fullRecordName), nil
	}
}

func buildEnvelope() *examplev1.EventEnvelope {
	now := timestamppb.Now()
	ts := time.Now().UnixNano()

	customer := &examplev1.CustomerCreated{
		Id:        randomUUID(),
		Email:     fmt.Sprintf("important.tester+%d@example.com", ts),
		FullName:  "Important Tester",
		CreatedAt: now,
		Address: &examplev1.PostalAddress{
			Line1:       "100 Test Street",
			Line2:       "Suite 10",
			City:        "Austin",
			State:       "TX",
			PostalCode:  "78701",
			CountryCode: "US",
		},
		Tenant: &examplev1.TenantRef{
			TenantId: "tenant-a",
			Region:   "us-east",
		},
	}

	return &examplev1.EventEnvelope{
		CustomerCreated: customer,
		Metadata: &examplev1.EventMetadata{
			EventId:      randomUUID(),
			SourceSystem: "integration-test-suite",
			Producer:     "go-protobuf-producer",
			OccurredAt:   now,
		},
	}
}

func readEnv(key, fallback string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	return value
}

func parseInterval(value string) time.Duration {
	seconds, err := strconv.ParseFloat(value, 64)
	if err != nil || seconds <= 0 {
		log.Fatalf("Invalid SEND_INTERVAL_SECONDS value %q (must be > 0)", value)
	}
	return time.Duration(seconds * float64(time.Second))
}

func parseBoolEnv(key string, fallback bool) bool {
	value := strings.ToLower(strings.TrimSpace(os.Getenv(key)))
	if value == "" {
		return fallback
	}
	switch value {
	case "1", "true", "yes", "y", "on":
		return true
	case "0", "false", "no", "n", "off":
		return false
	default:
		log.Fatalf("Invalid %s value %q (expected true/false)", key, value)
		return fallback
	}
}

func randomUUID() string {
	// UUID format without external dependency.
	// Good enough for testing payload uniqueness.
	now := time.Now().UnixNano()
	return fmt.Sprintf(
		"%08x-%04x-%04x-%04x-%012x",
		uint32(now>>32),
		uint16(now>>16),
		uint16(now),
		uint16(now>>48),
		uint64(now)&0x0000FFFFFFFFFFFF,
	)
}
