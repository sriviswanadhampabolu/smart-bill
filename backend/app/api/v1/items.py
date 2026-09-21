from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from sqlalchemy import or_
from app.db.session import get_db
from app.models.entities import Shop, Item, StockLog
from app.schemas.item import (
    ItemCreate, ItemUpdate, ItemResponse, StockAdjustmentRequest
)
from app.api.deps import get_current_shop

router = APIRouter(prefix="/items", tags=["Inventory"])


def to_item_response(item: Item) -> ItemResponse:
    stock = float(item.stock_qty)
    threshold = float(item.low_stock_threshold)
    is_low = stock <= threshold
    return ItemResponse(
        id=item.id,
        shop_id=item.shop_id,
        name=item.name,
        name_regional=item.name_regional,
        category=item.category,
        barcode=item.barcode,
        unit_type=item.unit_type,
        price=float(item.price),
        stock_qty=stock,
        low_stock_threshold=threshold,
        is_active=item.is_active,
        is_low_stock=is_low,
        image_path=item.image_path,
        created_at=item.created_at,
        updated_at=item.updated_at
    )


@router.get("/", response_model=List[ItemResponse])
def list_items(
    q: Optional[str] = None,
    category: Optional[str] = None,
    low_stock_only: bool = False,
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    query = db.query(Item).filter(
        Item.shop_id == current_shop.id,
        Item.is_active == True
    )

    if category:
        query = query.filter(Item.category.ilike(category.strip()))

    if q:
        search_pattern = f"%{q.strip()}%"
        query = query.filter(
            or_(
                Item.name.ilike(search_pattern),
                Item.name_regional.ilike(search_pattern),
                Item.barcode == q.strip()
            )
        )

    if low_stock_only:
        query = query.filter(Item.stock_qty <= Item.low_stock_threshold)

    # Default sort: "lowest stock first" per design constraints
    items = query.order_by(Item.stock_qty.asc(), Item.name.asc()).all()
    return [to_item_response(item) for item in items]


@router.post("/", response_model=ItemResponse, status_code=status.HTTP_201_CREATED)
def create_item(
    payload: ItemCreate,
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    item = Item(
        shop_id=current_shop.id,
        name=payload.name.strip(),
        name_regional=payload.name_regional.strip() if payload.name_regional else None,
        category=payload.category.strip(),
        barcode=payload.barcode.strip() if payload.barcode else None,
        unit_type=payload.unit_type,
        price=payload.price,
        stock_qty=payload.stock_qty,
        low_stock_threshold=payload.low_stock_threshold,
        image_path=payload.image_path
    )
    db.add(item)
    db.commit()
    db.refresh(item)

    # If initial stock was provided, log it as restock
    if payload.stock_qty != 0:
        log = StockLog(
            item_id=item.id,
            change_qty=payload.stock_qty,
            reason="restock"
        )
        db.add(log)
        db.commit()

    return to_item_response(item)


@router.get("/{item_id}", response_model=ItemResponse)
def get_item(
    item_id: str,
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    item = db.query(Item).filter(
        Item.id == item_id,
        Item.shop_id == current_shop.id
    ).first()
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")
    return to_item_response(item)


@router.put("/{item_id}", response_model=ItemResponse)
def update_item(
    item_id: str,
    payload: ItemUpdate,
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    item = db.query(Item).filter(
        Item.id == item_id,
        Item.shop_id == current_shop.id
    ).first()
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")

    if payload.name is not None:
        item.name = payload.name.strip()
    if payload.name_regional is not None:
        item.name_regional = payload.name_regional.strip() if payload.name_regional else None
    if payload.category is not None:
        item.category = payload.category.strip()
    if payload.barcode is not None:
        item.barcode = payload.barcode.strip() if payload.barcode else None
    if payload.unit_type is not None:
        item.unit_type = payload.unit_type
    if payload.price is not None:
        item.price = payload.price
    if payload.low_stock_threshold is not None:
        item.low_stock_threshold = payload.low_stock_threshold
    if payload.is_active is not None:
        item.is_active = payload.is_active
    if payload.image_path is not None:
        item.image_path = payload.image_path

    # If stock_qty changed directly via edit, write correction log
    if payload.stock_qty is not None and payload.stock_qty != float(item.stock_qty):
        diff = payload.stock_qty - float(item.stock_qty)
        item.stock_qty = payload.stock_qty
        log = StockLog(
            item_id=item.id,
            change_qty=diff,
            reason="correction"
        )
        db.add(log)

    db.commit()
    db.refresh(item)
    return to_item_response(item)


@router.post("/{item_id}/adjust-stock", response_model=ItemResponse)
def adjust_stock(
    item_id: str,
    payload: StockAdjustmentRequest,
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    item = db.query(Item).filter(
        Item.id == item_id,
        Item.shop_id == current_shop.id
    ).first()
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")

    item.stock_qty = float(item.stock_qty) + payload.change_qty
    log = StockLog(
        item_id=item.id,
        change_qty=payload.change_qty,
        reason=payload.reason
    )
    db.add(log)
    db.commit()
    db.refresh(item)
    return to_item_response(item)


@router.delete("/{item_id}", status_code=status.HTTP_200_OK)
def delete_item(
    item_id: str,
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    item = db.query(Item).filter(
        Item.id == item_id,
        Item.shop_id == current_shop.id
    ).first()
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")

    item.is_active = False
    db.commit()
    return {"message": "Item deactivated successfully"}
