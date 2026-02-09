from __future__ import annotations

import os
import sys
import time
import types
import uuid
from pathlib import Path

from confluent_kafka import SerializingProducer
from confluent_kafka.schema_registry import (
    SchemaRegistryClient,
    topic_record_subject_name_strategy,
)
from confluent_kafka.schema_registry.protobuf import ProtobufSerializer
from confluent_kafka.serialization import StringSerializer
from google.protobuf import descriptor_pb2
from google.protobuf import descriptor_pool

PROJECT_DIR = Path(__file__).resolve().parent
GENERATED_PYTHON_DIR = PROJECT_DIR / "generated" / "python"
from google.protobuf.timestamp_pb2 import Timestamp  # noqa: E402

if GENERATED_PYTHON_DIR.exists():
    sys.path.insert(0, str(GENERATED_PYTHON_DIR))


def ensure_buf_validate_stub() -> None:
    try:
        from buf.validate import validate_pb2 as _  # noqa: F401
        return
    except ModuleNotFoundError:
        pass

    buf_module = sys.modules.setdefault("buf", types.ModuleType("buf"))
    validate_module = sys.modules.setdefault("buf.validate", types.ModuleType("buf.validate"))
    validate_pb2_module = types.ModuleType("buf.validate.validate_pb2")

    file_descriptor = descriptor_pb2.FileDescriptorProto(
        name="buf/validate/validate.proto",
        package="buf.validate",
        syntax="proto3",
    )
    validate_pb2_module.DESCRIPTOR = descriptor_pool.Default().AddSerializedFile(
        file_descriptor.SerializeToString()
    )

    sys.modules["buf.validate.validate_pb2"] = validate_pb2_module
    setattr(buf_module, "validate", validate_module)
    setattr(validate_module, "validate_pb2", validate_pb2_module)


try:
    from example.v1 import address_pb2
    from example.v1 import customer_event_pb2
    from example.v1 import event_metadata_pb2
    from example.v1 import tenant_pb2
except ModuleNotFoundError as exc:
    if exc.name in {"buf", "buf.validate", "buf.validate.validate_pb2"}:
        ensure_buf_validate_stub()
        from example.v1 import address_pb2
        from example.v1 import customer_event_pb2
        from example.v1 import event_metadata_pb2
        from example.v1 import tenant_pb2
    else:
        expected = GENERATED_PYTHON_DIR / "example" / "v1" / "customer_event_pb2.py"
        raise RuntimeError(
            "Unable to import generated protobuf module dependencies "
            f"(missing module: {exc.name}). Expected file: {expected}. "
            "Run `buf generate` from repo root and ensure imports are generated."
        ) from exc


def read_env(name: str, default: str) -> str:
    value = os.getenv(name)
    if value is None or value.strip() == "":
        return default
    return value


def delivery_report(err, msg) -> None:
    if err is not None:
        print(f"Delivery failed: {err}")
        return
    print(
        "Sent record to "
        f"{msg.topic()}-{msg.partition()} offset {msg.offset()} "
        "with subject strategy TopicRecordNameStrategy"
    )


def build_payload() -> tuple[str, customer_event_pb2.EventEnvelope]:
    event_time = Timestamp()
    event_time.FromMilliseconds(int(time.time() * 1000))

    customer_id = str(uuid.uuid4())
    address = address_pb2.PostalAddress(
        line1="100 Test Street",
        line2="Suite 10",
        city="Austin",
        state="TX",
        postal_code="78701",
        country_code="US",
    )
    tenant = tenant_pb2.TenantRef(
        tenant_id="tenant-a",
        region="us-east",
    )
    metadata = event_metadata_pb2.EventMetadata(
        event_id=str(uuid.uuid4()),
        source_system="integration-test-suite",
        producer="python-protobuf-producer",
        occurred_at=event_time,
    )
    payload = customer_event_pb2.EventEnvelope(
        customer_created=customer_event_pb2.CustomerCreated(
            id=customer_id,
            email=f"important.tester.{int(time.time())}@example.com",
            full_name="Important Tester",
            created_at=event_time,
            address=address,
            tenant=tenant,
        ),
        metadata=metadata,
    )
    return customer_id, payload


def main() -> None:
    bootstrap_servers = read_env("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092")
    schema_registry_url = read_env("SCHEMA_REGISTRY_URL", "http://localhost:8081/apis/ccompat/v7")
    topic = read_env("KAFKA_TOPIC", "customer-events")
    interval_seconds = float(read_env("SEND_INTERVAL_SECONDS", "10"))

    schema_registry_client = SchemaRegistryClient({"url": schema_registry_url})
    protobuf_serializer = ProtobufSerializer(
        customer_event_pb2.EventEnvelope,
        schema_registry_client,
        {
            "subject.name.strategy": topic_record_subject_name_strategy,
            "use.deprecated.format": False,
        },
    )

    producer = SerializingProducer(
        {
            "bootstrap.servers": bootstrap_servers,
            "client.id": "python-protobuf-producer",
            "key.serializer": StringSerializer("utf_8"),
            "value.serializer": protobuf_serializer,
        }
    )

    print(
        f"Sending protobuf messages to topic '{topic}' every {interval_seconds} seconds. "
        "Press Ctrl+C to stop."
    )

    try:
        while True:
            customer_id, payload = build_payload()
            producer.produce(
                topic=topic,
                key=customer_id,
                value=payload,
                on_delivery=delivery_report,
            )
            producer.poll(1.0)
            time.sleep(interval_seconds)
    except KeyboardInterrupt:
        print("Stopping producer...")
    finally:
        producer.flush(10)


if __name__ == "__main__":
    main()
