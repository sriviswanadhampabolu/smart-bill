import json
from typing import List, Optional
from datetime import datetime, timezone, timedelta
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import func, desc
from app.db.session import get_db
from app.models.entities import Shop, Item, Bill, BillItem, StockLog, Embedding
from app.schemas.sync import (
    SyncPushRequest, SyncPushResponse, SyncPullResponse,
    ReportsSummaryResponse, TopSellingItem
)
from app.api.deps import get_current_shop

router = APIRouter(prefix="/sync", tags=["Sync & Reports"])


@router.get("/status")
def get_sync_status(current_shop: Shop = Depends(get_current_shop)):
    return {
        "status": "ready",
        "shop_id": current_shop.id,
        "server_time": datetime.now(timezone.utc).isoformat(),
        "protocol_version": "1.0"
    }


@router.get("/pull", response_model=SyncPullResponse)
def pull_sync_data(
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    """
    Returns full shop profile, active catalog items, embeddings, historical bills,
    and bill line items so client can restore state after storage clear or on a new device.
    """
    import base64

    # 1. Shop profile
    shop_data = {
        "id": current_shop.id,
        "name": current_shop.name,
        "owner_name": current_shop.owner_name,
        "phone": current_shop.phone,
        "upi_id": current_shop.upi_id,
        "currency_symbol": current_shop.currency_symbol,
        "pin_hash": current_shop.pin_hash,
        "settings_json": current_shop.settings_json,
    }

    # 2. Items
    items = db.query(Item).filter(
        Item.shop_id == current_shop.id,
        Item.is_active == True
    ).all()
    items_data = [
        {
            "id": it.id,
            "shop_id": it.shop_id,
            "name": it.name,
            "name_regional": it.name_regional,
            "category": it.category,
            "barcode": it.barcode,
            "unit_type": it.unit_type,
            "price": float(it.price),
            "stock_qty": float(it.stock_qty),
            "low_stock_threshold": float(it.low_stock_threshold),
            "is_active": it.is_active,
            "image_path": it.image_path,
        }
        for it in items
    ]

    item_ids = [it.id for it in items]

    # 3. Embeddings for shop items
    embeddings = []
    if item_ids:
        raw_embs = db.query(Embedding).filter(Embedding.item_id.in_(item_ids)).all()
        for e in raw_embs:
            vec_b64 = base64.b64encode(e.vector).decode("utf-8") if e.vector else ""
            embeddings.append({
                "id": e.id,
                "item_id": e.item_id,
                "vector_base64": vec_b64,
                "source": e.source,
                "quality_score": float(e.quality_score),
                "created_at": int(e.created_at.timestamp() * 1000)
            })

    # 4. Bills & Bill items
    bills = db.query(Bill).filter(Bill.shop_id == current_shop.id).order_by(Bill.created_at.desc()).all()
    bills_data = [
        {
            "id": b.id,
            "shop_id": b.shop_id,
            "bill_number": b.bill_number,
            "subtotal": float(b.subtotal),
            "discount": float(b.discount),
            "tax": float(b.tax),
            "total": float(b.total),
            "payment_method": b.payment_method,
            "created_at": int(b.created_at.timestamp() * 1000)
        }
        for b in bills
    ]

    bill_ids = [b.id for b in bills]
    bill_items_data = []
    if bill_ids:
        raw_bis = db.query(BillItem).filter(BillItem.bill_id.in_(bill_ids)).all()
        for bi in raw_bis:
            bill_items_data.append({
                "id": bi.id,
                "bill_id": bi.bill_id,
                "item_id": bi.item_id,
                "name_snapshot": bi.name_snapshot,
                "qty": float(bi.qty),
                "unit_type": bi.unit_type,
                "unit_price_snapshot": float(bi.unit_price_snapshot),
                "line_total": float(bi.line_total)
            })

    return SyncPullResponse(
        server_time=datetime.now(timezone.utc),
        has_more=False,
        shop=shop_data,
        items=items_data,
        embeddings=embeddings,
        bills=bills_data,
        bill_items=bill_items_data
    )


@router.post("/push", response_model=SyncPushResponse)
def push_sync_queue(
    payload: SyncPushRequest,
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    """
    Idempotent batch sync handler.
    Keyed by client UUIDs so network retries never double-post bills or movements.
    """
    import base64
    acknowledged_ids: List[str] = []

    for item_payload in payload.items:
        try:
            if item_payload.entity_type == "bill":
                existing_bill = db.query(Bill).filter(Bill.id == item_payload.entity_id).first()
                if not existing_bill and item_payload.operation == "INSERT":
                    data = json.loads(item_payload.payload_json)
                    new_bill = Bill(
                        id=item_payload.entity_id,
                        shop_id=current_shop.id,
                        bill_number=data.get("bill_number", f"BILL-{item_payload.entity_id[:8]}"),
                        subtotal=data.get("subtotal", data.get("total", 0.0)),
                        discount=data.get("discount", 0.0),
                        tax=data.get("tax", 0.0),
                        total=data.get("total", 0.0),
                        payment_method=data.get("payment_method", "cash"),
                        created_at=datetime.fromtimestamp(item_payload.created_at / 1000.0, timezone.utc)
                    )
                    db.add(new_bill)

                    # Insert line items if provided
                    if "items" in data and isinstance(data["items"], list):
                        for line in data["items"]:
                            bi = BillItem(
                                id=line.get("id"),
                                bill_id=new_bill.id,
                                item_id=line.get("item_id", ""),
                                name_snapshot=line.get("name_snapshot", "Item"),
                                qty=line.get("qty", 1.0),
                                unit_type=line.get("unit_type", "piece"),
                                unit_price_snapshot=line.get("unit_price_snapshot", 0.0),
                                line_total=line.get("line_total", 0.0)
                            )
                            db.add(bi)
                    db.commit()

            elif item_payload.entity_type == "item":
                data = json.loads(item_payload.payload_json)
                existing_item = db.query(Item).filter(Item.id == item_payload.entity_id).first()
                if existing_item:
                    if "name" in data: existing_item.name = data["name"].strip()
                    if "name_regional" in data: existing_item.name_regional = data["name_regional"]
                    if "category" in data: existing_item.category = data["category"].strip()
                    if "barcode" in data: existing_item.barcode = data["barcode"]
                    if "price" in data: existing_item.price = data["price"]
                    if "stock_qty" in data: existing_item.stock_qty = data["stock_qty"]
                    if "low_stock_threshold" in data: existing_item.low_stock_threshold = data["low_stock_threshold"]
                    if "is_active" in data: existing_item.is_active = data["is_active"]
                else:
                    new_item = Item(
                        id=item_payload.entity_id,
                        shop_id=current_shop.id,
                        name=data.get("name", "Unknown").strip(),
                        name_regional=data.get("name_regional"),
                        category=data.get("category", "General").strip(),
                        barcode=data.get("barcode"),
                        unit_type=data.get("unit_type", "piece"),
                        price=data.get("price", 0.0),
                        stock_qty=data.get("stock_qty", 0.0),
                        low_stock_threshold=data.get("low_stock_threshold", 5.0),
                        is_active=data.get("is_active", True)
                    )
                    db.add(new_item)
                db.commit()

            elif item_payload.entity_type == "embedding":
                data = json.loads(item_payload.payload_json)
                existing_emb = db.query(Embedding).filter(Embedding.id == item_payload.entity_id).first()
                if not existing_emb and item_payload.operation == "INSERT":
                    raw_vec = b""
                    if "vector_base64" in data and data["vector_base64"]:
                        raw_vec = base64.b64decode(data["vector_base64"])
                    new_emb = Embedding(
                        id=item_payload.entity_id,
                        item_id=data.get("item_id"),
                        vector=raw_vec,
                        source=data.get("source", "setup"),
                        quality_score=data.get("score", 1.0),
                        created_at=datetime.fromtimestamp(item_payload.created_at / 1000.0, timezone.utc)
                    )
                    db.add(new_emb)
                    db.commit()

            acknowledged_ids.append(item_payload.id)
        except Exception:
            db.rollback()
            # Skip failed items without failing the entire batch

    return SyncPushResponse(
        status="success",
        processed_count=len(acknowledged_ids),
        acknowledged_ids=acknowledged_ids,
        server_time=datetime.now(timezone.utc)
    )


@router.get("/reports/summary", response_model=ReportsSummaryResponse)
def get_reports_summary(
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    """
    Fast 5-second business report for the grocery shop owner.
    """
    now = datetime.now(timezone.utc)
    start_of_today = now.replace(hour=0, minute=0, second=0, microsecond=0)
    seven_days_ago = start_of_today - timedelta(days=6)

    # 1. Today's sales & bill count
    today_stats = db.query(
        func.coalesce(func.sum(Bill.total), 0.0),
        func.count(Bill.id)
    ).filter(
        Bill.shop_id == current_shop.id,
        Bill.created_at >= start_of_today
    ).first()

    today_sales = float(today_stats[0])
    today_bills = int(today_stats[1])

    # 2. Weekly sales & bill count
    weekly_stats = db.query(
        func.coalesce(func.sum(Bill.total), 0.0),
        func.count(Bill.id)
    ).filter(
        Bill.shop_id == current_shop.id,
        Bill.created_at >= seven_days_ago
    ).first()

    weekly_sales = float(weekly_stats[0])
    weekly_bills = int(weekly_stats[1])

    # 3. Total Stock Valuation (Sum of price * stock_qty for all active items)
    items = db.query(Item).filter(
        Item.shop_id == current_shop.id,
        Item.is_active == True
    ).all()

    stock_valuation = sum(float(item.price) * max(0.0, float(item.stock_qty)) for item in items)
    out_of_stock_items = [
        {"id": it.id, "name": it.name, "stock_qty": float(it.stock_qty), "price": float(it.price)}
        for it in items if float(it.stock_qty) <= 0
    ]

    # 4. Top Selling Items (by revenue and volume)
    top_items_query = db.query(
        BillItem.item_id,
        BillItem.name_snapshot,
        func.sum(BillItem.qty).label("total_qty"),
        func.sum(BillItem.line_total).label("total_rev")
    ).join(Bill, Bill.id == BillItem.bill_id).filter(
        Bill.shop_id == current_shop.id,
        Bill.created_at >= seven_days_ago
    ).group_by(BillItem.item_id, BillItem.name_snapshot).order_by(
        desc("total_rev")
    ).limit(5).all()

    top_selling = [
        TopSellingItem(
            item_id=r.item_id,
            name=r.name_snapshot,
            total_qty=float(r.total_qty),
            total_revenue=float(r.total_rev)
        )
        for r in top_items_query
    ]

    # 5. Daily 7-day breakdown
    daily_breakdown = []
    for i in range(7):
        day_date = seven_days_ago + timedelta(days=i)
        next_day = day_date + timedelta(days=1)
        day_total = db.query(func.coalesce(func.sum(Bill.total), 0.0)).filter(
            Bill.shop_id == current_shop.id,
            Bill.created_at >= day_date,
            Bill.created_at < next_day
        ).scalar()

        daily_breakdown.append({
            "day": day_date.strftime("%a"),
            "date": day_date.strftime("%d %b"),
            "sales": float(day_total)
        })

    return ReportsSummaryResponse(
        shop_id=current_shop.id,
        today_sales=today_sales,
        today_bills_count=today_bills,
        weekly_sales=weekly_sales,
        weekly_bills_count=weekly_bills,
        total_stock_valuation=stock_valuation,
        out_of_stock_count=len(out_of_stock_items),
        daily_breakdown=daily_breakdown,
        top_selling_items=top_selling,
        out_of_stock_items=out_of_stock_items
    )
