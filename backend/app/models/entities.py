import uuid
from datetime import datetime, timezone
from sqlalchemy import (
    Column, String, Numeric, Float, Boolean, Integer, 
    ForeignKey, LargeBinary, Text, DateTime
)
from sqlalchemy.orm import relationship
from app.db.session import Base


def generate_uuid() -> str:
    return str(uuid.uuid4())


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


class Shop(Base):
    __tablename__ = "shops"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    name = Column(String(150), nullable=False)
    owner_name = Column(String(150), nullable=False)
    phone = Column(String(20), unique=True, nullable=False, index=True)
    upi_id = Column(String(100), nullable=True)
    currency_symbol = Column(String(10), default="₹", nullable=False)
    password_hash = Column(String(255), nullable=True)
    pin_hash = Column(String(255), nullable=True)
    settings_json = Column(Text, default="{}", nullable=False)
    created_at = Column(DateTime(timezone=True), default=now_utc, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=now_utc, onupdate=now_utc, nullable=False)

    items = relationship("Item", back_populates="shop", cascade="all, delete-orphan")
    bills = relationship("Bill", back_populates="shop", cascade="all, delete-orphan")


class Item(Base):
    __tablename__ = "items"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    shop_id = Column(String(36), ForeignKey("shops.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(200), nullable=False)
    name_regional = Column(String(200), nullable=True)
    category = Column(String(100), nullable=False)
    barcode = Column(String(50), nullable=True, index=True)
    unit_type = Column(String(20), nullable=False)  # 'piece' or 'weight'
    price = Column(Numeric(10, 2), nullable=False)
    stock_qty = Column(Numeric(10, 3), default=0, nullable=False)
    low_stock_threshold = Column(Numeric(10, 3), default=5, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    image_path = Column(String(500), nullable=True)
    created_at = Column(DateTime(timezone=True), default=now_utc, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=now_utc, onupdate=now_utc, nullable=False)

    shop = relationship("Shop", back_populates="items")
    embeddings = relationship("Embedding", back_populates="item", cascade="all, delete-orphan")
    stock_logs = relationship("StockLog", back_populates="item", cascade="all, delete-orphan")


class Embedding(Base):
    __tablename__ = "embeddings"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    item_id = Column(String(36), ForeignKey("items.id", ondelete="CASCADE"), nullable=False, index=True)
    vector = Column(LargeBinary, nullable=False)
    source = Column(String(30), default="setup", nullable=False)  # 'setup' or 'correction'
    quality_score = Column(Float, default=1.0, nullable=False)
    created_at = Column(DateTime(timezone=True), default=now_utc, nullable=False)

    item = relationship("Item", back_populates="embeddings")


class Bill(Base):
    __tablename__ = "bills"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    shop_id = Column(String(36), ForeignKey("shops.id", ondelete="CASCADE"), nullable=False, index=True)
    bill_number = Column(String(50), nullable=False)
    subtotal = Column(Numeric(10, 2), nullable=False)
    discount = Column(Numeric(10, 2), default=0, nullable=False)
    tax = Column(Numeric(10, 2), default=0, nullable=False)
    total = Column(Numeric(10, 2), nullable=False)
    payment_method = Column(String(30), nullable=False)  # 'cash', 'upi', 'credit'
    created_at = Column(DateTime(timezone=True), default=now_utc, nullable=False)
    server_received_at = Column(DateTime(timezone=True), default=now_utc, nullable=False)

    shop = relationship("Shop", back_populates="bills")
    items = relationship("BillItem", back_populates="bill", cascade="all, delete-orphan")


class BillItem(Base):
    __tablename__ = "bill_items"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    bill_id = Column(String(36), ForeignKey("bills.id", ondelete="CASCADE"), nullable=False, index=True)
    item_id = Column(String(36), nullable=False)
    name_snapshot = Column(String(200), nullable=False)
    qty = Column(Numeric(10, 3), nullable=False)
    unit_type = Column(String(20), nullable=False)
    unit_price_snapshot = Column(Numeric(10, 2), nullable=False)
    line_total = Column(Numeric(10, 2), nullable=False)

    bill = relationship("Bill", back_populates="items")


class StockLog(Base):
    __tablename__ = "stock_log"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    item_id = Column(String(36), ForeignKey("items.id", ondelete="CASCADE"), nullable=False, index=True)
    change_qty = Column(Numeric(10, 3), nullable=False)
    reason = Column(String(30), nullable=False)  # 'sale', 'restock', 'correction'
    bill_id = Column(String(36), ForeignKey("bills.id", ondelete="SET NULL"), nullable=True)
    created_at = Column(DateTime(timezone=True), default=now_utc, nullable=False)

    item = relationship("Item", back_populates="stock_logs")
