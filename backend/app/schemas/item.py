from pydantic import BaseModel, Field, ConfigDict
from typing import Optional
from datetime import datetime


class ItemBase(BaseModel):
    name: str = Field(..., min_length=1, max_length=200)
    name_regional: Optional[str] = None
    category: str = Field(..., min_length=1, max_length=100)
    barcode: Optional[str] = None
    unit_type: str = Field(..., pattern="^(piece|weight)$")
    price: float = Field(..., ge=0.0)
    stock_qty: float = Field(0.0)
    low_stock_threshold: float = Field(5.0, ge=0.0)
    image_path: Optional[str] = None


class ItemCreate(ItemBase):
    pass


class ItemUpdate(BaseModel):
    name: Optional[str] = None
    name_regional: Optional[str] = None
    category: Optional[str] = None
    barcode: Optional[str] = None
    unit_type: Optional[str] = Field(None, pattern="^(piece|weight)$")
    price: Optional[float] = Field(None, ge=0.0)
    stock_qty: Optional[float] = None
    low_stock_threshold: Optional[float] = Field(None, ge=0.0)
    is_active: Optional[bool] = None
    image_path: Optional[str] = None


class StockAdjustmentRequest(BaseModel):
    change_qty: float
    reason: str = Field("restock", pattern="^(restock|correction)$")


class ItemResponse(ItemBase):
    model_config = ConfigDict(from_attributes=True)

    id: str
    shop_id: str
    is_active: bool
    is_low_stock: bool = False
    created_at: datetime
    updated_at: datetime
