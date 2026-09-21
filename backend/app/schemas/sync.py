from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field
from datetime import datetime


class SyncQueueItemPayload(BaseModel):
    id: str  # sync_queue id
    entity_type: str  # 'bill', 'item', 'stock_log', 'embedding'
    entity_id: str
    operation: str  # 'INSERT', 'UPDATE', 'DELETE'
    payload_json: str
    created_at: int


class SyncPushRequest(BaseModel):
    shop_id: str
    items: List[SyncQueueItemPayload] = Field(..., default_factory=list)


class SyncPushResponse(BaseModel):
    status: str = "success"
    processed_count: int
    acknowledged_ids: List[str]
    server_time: datetime


class SyncPullResponse(BaseModel):
    server_time: datetime
    has_more: bool = False
    shop: Optional[Dict[str, Any]] = None
    items: List[Dict[str, Any]] = Field(default_factory=list)
    embeddings: List[Dict[str, Any]] = Field(default_factory=list)
    bills: List[Dict[str, Any]] = Field(default_factory=list)
    bill_items: List[Dict[str, Any]] = Field(default_factory=list)


class TopSellingItem(BaseModel):
    item_id: str
    name: str
    total_qty: float
    total_revenue: float


class ReportsSummaryResponse(BaseModel):
    shop_id: str
    today_sales: float
    today_bills_count: int
    weekly_sales: float
    weekly_bills_count: int
    total_stock_valuation: float
    out_of_stock_count: int
    daily_breakdown: List[Dict[str, Any]]
    top_selling_items: List[TopSellingItem]
    out_of_stock_items: List[Dict[str, Any]]
