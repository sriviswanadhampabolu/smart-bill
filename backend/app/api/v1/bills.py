from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.db.session import get_db
from app.models.entities import Shop, Item, Bill, BillItem, StockLog
from app.schemas.bill import BillCreate, BillResponse, BillItemResponse
from app.api.deps import get_current_shop

router = APIRouter(prefix="/bills", tags=["Billing"])


def to_bill_response(bill: Bill) -> BillResponse:
    item_responses = [
        BillItemResponse(
            id=item.id,
            item_id=item.item_id,
            name_snapshot=item.name_snapshot,
            qty=float(item.qty),
            unit_type=item.unit_type,
            unit_price_snapshot=float(item.unit_price_snapshot),
            line_total=float(item.line_total)
        )
        for item in bill.items
    ]
    return BillResponse(
        id=bill.id,
        shop_id=bill.shop_id,
        bill_number=bill.bill_number,
        subtotal=float(bill.subtotal),
        discount=float(bill.discount),
        tax=float(bill.tax),
        total=float(bill.total),
        payment_method=bill.payment_method,
        created_at=bill.created_at,
        server_received_at=bill.server_received_at,
        items=item_responses
    )


@router.post("/", response_model=BillResponse, status_code=status.HTTP_201_CREATED)
def create_bill(
    payload: BillCreate,
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    # Atomic transaction for bill creation, item snapshots, and stock decrements
    bill_kwargs = {
        "shop_id": current_shop.id,
        "bill_number": payload.bill_number,
        "subtotal": payload.subtotal,
        "discount": payload.discount,
        "tax": payload.tax,
        "total": payload.total,
        "payment_method": payload.payment_method,
    }
    if payload.id:
        bill_kwargs["id"] = payload.id
    if payload.created_at:
        bill_kwargs["created_at"] = payload.created_at

    bill = Bill(**bill_kwargs)
    db.add(bill)
    db.flush()  # assign bill.id

    for line in payload.items:
        # 1. Snapshot into bill_items
        bill_item = BillItem(
            bill_id=bill.id,
            item_id=line.item_id,
            name_snapshot=line.name_snapshot,
            qty=line.qty,
            unit_type=line.unit_type,
            unit_price_snapshot=line.unit_price_snapshot,
            line_total=line.line_total
        )
        db.add(bill_item)

        # 2. Atomically decrement stock in Item
        item = db.query(Item).filter(
            Item.id == line.item_id,
            Item.shop_id == current_shop.id
        ).first()

        if item:
            item.stock_qty = float(item.stock_qty) - line.qty
            
            # 3. Log stock movement
            log = StockLog(
                item_id=item.id,
                change_qty=-line.qty,
                reason="sale",
                bill_id=bill.id
            )
            db.add(log)

    db.commit()
    db.refresh(bill)
    return to_bill_response(bill)


@router.get("/", response_model=List[BillResponse])
def list_bills(
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    bills = db.query(Bill).filter(
        Bill.shop_id == current_shop.id
    ).order_by(Bill.created_at.desc()).all()
    return [to_bill_response(b) for b in bills]


@router.get("/{bill_id}", response_model=BillResponse)
def get_bill(
    bill_id: str,
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    bill = db.query(Bill).filter(
        Bill.id == bill_id,
        Bill.shop_id == current_shop.id
    ).first()
    if not bill:
        raise HTTPException(status_code=404, detail="Bill not found")
    return to_bill_response(bill)
