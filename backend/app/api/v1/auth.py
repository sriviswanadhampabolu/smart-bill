import json
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.db.session import get_db
from app.models.entities import Shop
from app.core.security import hash_secret, verify_secret, create_access_token
from app.schemas.auth import (
    SignupRequest, LoginRequest, PinLoginRequest, 
    SetPinRequest, TokenResponse
)
from app.api.deps import get_current_shop

router = APIRouter(prefix="/auth", tags=["Auth"])


@router.post("/signup", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
def signup(request: SignupRequest, db: Session = Depends(get_db)):
    # Check if shop with phone already exists
    existing = db.query(Shop).filter(Shop.phone == request.phone.strip()).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="A shop with this phone number already exists"
        )
    
    if not request.password and not request.pin:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Either a password or a 4-digit PIN must be provided"
        )
    
    password_hash = hash_secret(request.password) if request.password else None
    pin_hash = hash_secret(request.pin) if request.pin else None

    shop = Shop(
        name=request.shop_name.strip(),
        owner_name=request.owner_name.strip(),
        phone=request.phone.strip(),
        upi_id=request.upi_id.strip() if request.upi_id else None,
        currency_symbol=request.currency_symbol or "₹",
        password_hash=password_hash,
        pin_hash=pin_hash,
        settings_json=json.dumps({
            "language": "en",
            "regional_language": "hi",
            "tax_enabled": False,
            "default_tax_rate": 0.0,
            "bluetooth_printer_mac": None
        })
    )
    db.add(shop)
    db.commit()
    db.refresh(shop)

    token = create_access_token(shop.id)
    return TokenResponse(
        access_token=token,
        token_type="bearer",
        shop_id=shop.id,
        owner_name=shop.owner_name,
        shop_name=shop.name,
        currency_symbol=shop.currency_symbol,
        has_pin=shop.pin_hash is not None
    )


@router.post("/login", response_model=TokenResponse)
def login(request: LoginRequest, db: Session = Depends(get_db)):
    shop = db.query(Shop).filter(Shop.phone == request.phone.strip()).first()
    if not shop or not shop.password_hash:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid phone number or password"
        )
    
    if not verify_secret(request.password, shop.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid phone number or password"
        )

    token = create_access_token(shop.id)
    return TokenResponse(
        access_token=token,
        token_type="bearer",
        shop_id=shop.id,
        owner_name=shop.owner_name,
        shop_name=shop.name,
        currency_symbol=shop.currency_symbol,
        has_pin=shop.pin_hash is not None
    )


@router.post("/pin-login", response_model=TokenResponse)
def pin_login(request: PinLoginRequest, db: Session = Depends(get_db)):
    """Fast counter PIN unlock for shop owner standing at billing desk."""
    shop = db.query(Shop).filter(Shop.phone == request.phone.strip()).first()
    if not shop or not shop.pin_hash:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid phone or PIN"
        )
    
    if not verify_secret(request.pin, shop.pin_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid phone or PIN"
        )

    token = create_access_token(shop.id)
    return TokenResponse(
        access_token=token,
        token_type="bearer",
        shop_id=shop.id,
        owner_name=shop.owner_name,
        shop_name=shop.name,
        currency_symbol=shop.currency_symbol,
        has_pin=True
    )


@router.post("/set-pin", status_code=status.HTTP_200_OK)
def set_pin(
    request: SetPinRequest,
    current_shop: Shop = Depends(get_current_shop),
    db: Session = Depends(get_db)
):
    """Set or update the 4-digit PIN for fast app unlocking."""
    current_shop.pin_hash = hash_secret(request.pin)
    db.commit()
    return {"message": "4-digit PIN successfully updated", "has_pin": True}
