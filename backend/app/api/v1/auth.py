import json
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.db.session import get_db
from app.models.entities import Shop
from app.core.security import hash_secret, verify_secret, create_access_token
from app.schemas.auth import (
    SignupRequest, LoginRequest, PinLoginRequest, 
    SetPinRequest, TokenResponse, GoogleAuthRequest
)
from app.api.deps import get_current_shop

router = APIRouter(prefix="/auth", tags=["Auth"])


@router.post("/signup", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
def signup(request: SignupRequest, db: Session = Depends(get_db)):
    clean_phone = request.phone.strip()
    existing = db.query(Shop).filter(Shop.phone == clean_phone).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="A shop with this phone number already exists"
        )
    
    password_hash = hash_secret(request.password) if request.password else None
    pin_hash = hash_secret(request.pin) if request.pin else hash_secret("1234")

    shop = Shop(
        name=request.shop_name.strip(),
        owner_name=request.owner_name.strip(),
        phone=clean_phone,
        email=request.email.strip() if request.email else None,
        address=request.address.strip() if request.address else None,
        upi_id=request.upi_id.strip() if request.upi_id else None,
        currency_symbol=request.currency_symbol or "₹",
        password_hash=password_hash,
        pin_hash=pin_hash,
        settings_json=json.dumps({
            "language": "en",
            "regional_language": "hi",
            "tax_enabled": False,
            "default_tax_rate": 0.0,
            "bluetooth_printer_mac": None,
            "qr_codes": []
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
    clean_phone = request.phone.strip()
    shop = db.query(Shop).filter(Shop.phone == clean_phone).first()
    if not shop:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Account not found. Please register first."
        )
    
    # Check either password or PIN
    authenticated = False
    if request.password and shop.password_hash:
        if verify_secret(request.password, shop.password_hash):
            authenticated = True
    if not authenticated and request.pin and shop.pin_hash:
        if verify_secret(request.pin, shop.pin_hash):
            authenticated = True
    if not authenticated and request.password and shop.pin_hash and not shop.password_hash:
        # User entered PIN in password field
        if verify_secret(request.password, shop.pin_hash):
            authenticated = True

    if not authenticated:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid credentials. Please verify your phone number and PIN/password."
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


@router.post("/google", response_model=TokenResponse)
def google_auth(request: GoogleAuthRequest, db: Session = Depends(get_db)):
    """Google authorization endpoint for instant login/registration."""
    clean_email = request.email.strip().lower()
    shop = db.query(Shop).filter(Shop.email == clean_email).first()
    
    if not shop and request.phone:
        shop = db.query(Shop).filter(Shop.phone == request.phone.strip()).first()
        if shop:
            shop.email = clean_email
            db.commit()

    if not shop:
        # Create user profile from Google info
        display_name = request.display_name.strip() if request.display_name else "Shopkeeper"
        temp_phone = request.phone.strip() if request.phone else f"g_{clean_email[:12]}"
        
        shop = Shop(
            name=f"{display_name}'s Store",
            owner_name=display_name,
            phone=temp_phone,
            email=clean_email,
            currency_symbol="₹",
            pin_hash=hash_secret("1234"),
            settings_json=json.dumps({
                "language": "en",
                "auth_provider": "google",
                "photo_url": request.photo_url,
                "qr_codes": []
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
