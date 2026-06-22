"""
Redis-based rate limiting using token bucket algorithm.
Falls back to in-memory implementation if Redis is unavailable.
"""

import logging
import time
from collections import defaultdict
from redis.asyncio import Redis
from .config import settings

logger = logging.getLogger(__name__)

# Global Redis client
_redis_client: Redis | None = None
_redis_available: bool = False

# In-memory fallback for rate limiting when Redis unavailable
_memory_buckets = defaultdict(lambda: {"tokens": 300, "last_update": time.time()})


async def get_redis() -> Redis | None:
    """Get or create Redis client, returns None if unavailable."""
    global _redis_client, _redis_available
    if _redis_client is None and not _redis_available:
        try:
            redis_url = settings.redis_url
            _redis_client = await Redis.from_url(
                redis_url, decode_responses=True, socket_connect_timeout=2
            )
            # Test connection
            await _redis_client.ping()
            logger.info("Redis connected successfully")
            _redis_available = True
        except Exception as e:
            logger.warning(f"Redis unavailable, using in-memory rate limiting: {e}")
            _redis_available = False
            _redis_client = None
    return _redis_client if _redis_available else None


async def close_redis():
    """Close Redis connection."""
    global _redis_client
    if _redis_client:
        try:
            await _redis_client.close()
        except Exception:
            pass
        _redis_client = None


def _check_memory_rate_limit(key: str, requests_per_minute: int) -> bool:
    """In-memory rate limit check (fallback)."""
    now = time.time()
    bucket = _memory_buckets[key]

    # Refill tokens
    elapsed = now - bucket["last_update"]
    tokens_to_add = (elapsed / 60.0) * requests_per_minute
    bucket["tokens"] = min(requests_per_minute, bucket["tokens"] + tokens_to_add)
    bucket["last_update"] = now

    if bucket["tokens"] >= 1.0:
        bucket["tokens"] -= 1.0
        return False
    return True


async def is_rate_limited(
    client_ip: str, endpoint: str, requests_per_minute: int
) -> bool:
    """
    Check if request is rate limited using Redis token bucket.
    Falls back to in-memory implementation if Redis unavailable.

    Args:
        client_ip: Client IP address
        endpoint: API endpoint
        requests_per_minute: Rate limit threshold

    Returns:
        True if rate limited (request rejected), False if allowed
    """
    redis = await get_redis()
    key = f"rate_limit:{endpoint}:{client_ip}"

    if redis:
        try:
            # Use Redis INCR with expiration
            current = await redis.incr(key)

            # Set expiration on first request
            if current == 1:
                await redis.expire(key, 60)  # 1 minute window

            if current > requests_per_minute:
                logger.debug(
                    f"Rate limit exceeded for {key} (limit: {requests_per_minute}/min)"
                )
                return True

            return False
        except Exception as e:
            logger.error(f"Rate limit check failed: {e}")
            # Fail open - allow request if Redis fails
            return False
    else:
        # Use in-memory fallback
        return _check_memory_rate_limit(key, requests_per_minute)


async def blacklist_token(token: str, ttl_seconds: int = 3600):
    """Add token to blacklist (for logout, token revocation, etc.)."""
    redis = await get_redis()
    if not redis:
        logger.warning("Token blacklist unavailable (Redis not connected)")
        return
    key = f"token_blacklist:{token}"
    try:
        await redis.setex(key, ttl_seconds, "revoked")
    except Exception as e:
        logger.error(f"Failed to blacklist token: {e}")


async def is_token_blacklisted(token: str) -> bool:
    """Check if token is blacklisted."""
    redis = await get_redis()
    if not redis:
        # Without Redis, we can't track blacklist (tokens won't be revoked)
        return False
    key = f"token_blacklist:{token}"
    try:
        result = await redis.get(key)
        return result is not None
    except Exception as e:
        logger.error(f"Failed to check token blacklist: {e}")
        return False


async def set_cache(key: str, value: str, ttl_seconds: int = 300):
    """Set a cache entry with TTL."""
    redis = await get_redis()
    if not redis:
        logger.warning("Cache unavailable (Redis not connected)")
        return
    try:
        await redis.setex(key, ttl_seconds, value)
    except Exception as e:
        logger.error(f"Failed to set cache: {e}")


async def get_cache(key: str) -> str | None:
    """Get a cache entry."""
    redis = await get_redis()
    if not redis:
        return None
    try:
        return await redis.get(key)
    except Exception as e:
        logger.error(f"Failed to get cache: {e}")
        return None


async def delete_cache(key: str):
    """Delete a cache entry."""
    redis = await get_redis()
    try:
        await redis.delete(key)
    except Exception as e:
        logger.error(f"Failed to delete cache: {e}")
