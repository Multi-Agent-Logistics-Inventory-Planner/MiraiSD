"""Adapters for external system integration (Kafka, Supabase)."""

from .kafka_consumer import KafkaEventConsumer
from .supabase_repo import SupabaseRepo

__all__ = ["KafkaEventConsumer", "SupabaseRepo"]
