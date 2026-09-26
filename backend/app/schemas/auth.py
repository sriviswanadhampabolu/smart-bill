from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, Dict, Any
from datetime import datetime


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    shop_id: str
    owner_name: str
    shop_name: str
    currency_symbol: str = "₹"
    has_pin: bool = False


class SignupRequest(BaseModel):
    shop_name: str = Field(..., min_length=2, max_length=150)
    owner_name: str = Field(..., min_length=2, max_length=150)
    phone: str = Field(..., min_length=10, max_length=15)
    email: Optional[str] = None
    address: Optional[str] = None
    password: Optional[str] = Field(None, min_length=4)
    pin: Optional[str] = Field(None, min_length=4, max_length=4)
    upi_id: Optional[str] = None
    currency_symbol: str = "₹"


class LoginRequest(BaseModel):
    phone: str
    password: Optional[str] = None
    pin: Optional[str] = None


class GoogleAuthRequest(BaseModel):
    email: str
    display_name: str
    id_token: Optional[str] = None
    photo_url: Optional[str] = None
    phone: Optional[str] = None


class PinLoginRequest(BaseModel):
    phone: str
    pin: str = Field(..., min_length=4, max_length=4)


class SetPinRequest(BaseModel):
    pin: str = Field(..., min_length=4, max_length=4)


class ShopUpdateRequest(BaseModel):
    name: Optional[str] = None
    owner_name: Optional[str] = None
    phone: Optional[str] = None
    email: Optional[str] = None
    address: Optional[str] = None
    upi_id: Optional[str] = None
    currency_symbol: Optional[str] = None
    settings_json: Optional[Dict[str, Any]] = None


class ShopResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    name: str
    owner_name: str
    phone: str
    email: Optional[str] = None
    address: Optional[str] = None
    upi_id: Optional[str] = None
    currency_symbol: str
    has_pin: bool
    settings_json: Dict[str, Any]
    created_at: datetime
    updated_at: datetime
