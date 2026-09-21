from fastapi import APIRouter
from app.api.v1.auth import router as auth_router
from app.api.v1.shop import router as shop_router
from app.api.v1.items import router as items_router
from app.api.v1.bills import router as bills_router
from app.api.v1.sync import router as sync_router

api_router = APIRouter()
api_router.include_router(auth_router)
api_router.include_router(shop_router)
api_router.include_router(items_router)
api_router.include_router(bills_router)
api_router.include_router(sync_router)
