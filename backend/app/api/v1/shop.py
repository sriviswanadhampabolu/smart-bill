import json
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.db.session import get_db
from app.models.entities import Shop
from app.schemas.auth import ShopResponse, ShopUpdateRequest
from app.api.deps import get_current_shop

router = APIRouter(prefix="/shop", tags=["Shop Setup"])


@router.get("/profile", response_model=ShopResponse)
def get_shop_profile(current_shop: Shop = Depends(get_current_shop)):
    try:
        settings_dict = json.loads(current_shop.settings_json) if current_shop.settings_json else {}
    except Exception:
        settings_dict = {}

    return ShopResponse(
        id=current_shop.id,
        name=current_shop.name,
        owner_name=current_shop.owner_name,
        phone=current_shop.phone,
        upi_id=current_shop.upi_id,
        currency_symbol=current_shop.currency_symbol,
        has_pin=current_shop.pin_hash is not None,
        settings_json=settings_dict,
        created_at=current_shop.created_at,
        updated_at=current_shop.updated_at
    )


@router.put("/profile", response_model=ShopResponse)
def update_shop_profile(
    request: ShopUpdateRequest,
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    if request.name is not None:
        current_shop.name = request.name.strip()
    if request.owner_name is not None:
        current_shop.owner_name = request.owner_name.strip()
    if request.upi_id is not None:
        current_shop.upi_id = request.upi_id.strip() if request.upi_id else None
    if request.currency_symbol is not None:
        current_shop.currency_symbol = request.currency_symbol.strip()
    if request.settings_json is not None:
        try:
            current_settings = json.loads(current_shop.settings_json) if current_shop.settings_json else {}
        except Exception:
            current_settings = {}
        current_settings.update(request.settings_json)
        current_shop.settings_json = json.dumps(current_settings)

    db.commit()
    db.refresh(current_shop)

    settings_dict = json.loads(current_shop.settings_json) if current_shop.settings_json else {}
    return ShopResponse(
        id=current_shop.id,
        name=current_shop.name,
        owner_name=current_shop.owner_name,
        phone=current_shop.phone,
        upi_id=current_shop.upi_id,
        currency_symbol=current_shop.currency_symbol,
        has_pin=current_shop.pin_hash is not None,
        settings_json=settings_dict,
        created_at=current_shop.created_at,
        updated_at=current_shop.updated_at
    )
