import hashlib
import hmac
import secrets
from datetime import datetime, timedelta, timezone
from typing import Any, Union, Optional
import jwt
from app.config import settings


def hash_secret(secret: str, salt: Optional[str] = None) -> str:
    """Secure PBKDF2-HMAC-SHA256 hash for passwords and PINs."""
    if not salt:
        salt = secrets.token_hex(16)
    key = hashlib.pbkdf2_hmac(
        'sha256',
        secret.encode('utf-8'),
        salt.encode('utf-8'),
        100000
    )
    return f"{salt}${key.hex()}"


def verify_secret(secret: str, hashed: str) -> bool:
    """Verifies a secret against its salt$hash string."""
    try:
        salt, expected_hash = hashed.split('$', 1)
        computed = hash_secret(secret, salt)
        return hmac.compare_digest(computed, hashed)
    except Exception:
        return False


def create_access_token(subject: Union[str, Any], expires_delta: Optional[timedelta] = None) -> str:
    now = datetime.now(timezone.utc)
    if expires_delta:
        expire = now + expires_delta
    else:
        expire = now + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    
    to_encode = {
        "exp": expire,
        "iat": now,
        "sub": str(subject)
    }
    encoded_jwt = jwt.encode(to_encode, settings.SECRET_KEY, algorithm=settings.ALGORITHM)
    return encoded_jwt


def decode_access_token(token: str) -> Optional[dict]:
    try:
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.ALGORITHM])
        return payload
    except jwt.PyJWTError:
        return None
