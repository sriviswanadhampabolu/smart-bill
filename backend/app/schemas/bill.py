from pydantic import BaseModel, Field, ConfigDict
from typing import List, Optional
from datetime import datetime


class BillItemCreate(BaseModel):
    item_id: str
    name_snapshot: str
    qty: float = Field(..., gt=0.0)
    unit_type: str
    unit_price_snapshot: float = Field(..., ge=0.0)
    line_total: float = Field(..., ge=0.0)


class BillCreate(BaseModel):
    id: Optional[str] = None  # Client UUID if generated offline
    bill_number: str
    subtotal: float = Field(..., ge=0.0)
    discount: float = Field(0.0, ge=0.0)
    tax: float = Field(0.0, ge=0.0)
    total: float = Field(..., ge=0.0)
    payment_method: str = Field(..., pattern="^(cash|upi|credit)$")
    created_at: Optional[datetime] = None
    items: List[BillItemCreate] = Field(..., min_length=1)


class BillItemResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    item_id: str
    name_snapshot: str
    qty: float
    unit_type: str
    unit_price_snapshot: float
    line_total: float


class BillResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    shop_id: str
    bill_number: str
    subtotal: float
    discount: float
    tax: float
    total: float
    payment_method: str
    created_at: datetime
    server_received_at: datetime
    items: List[BillItemResponse]
