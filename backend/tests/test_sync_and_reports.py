import json
import pytest
from datetime import datetime, timezone


@pytest.fixture
def auth_headers(client):
    signup_resp = client.post("/api/v1/auth/signup", json={
        "shop_name": "Balaji Provisions",
        "owner_name": "Ramesh",
        "phone": "9876543210",
        "password": "password123",
        "pin": "1234"
    })
    token = signup_resp.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


def test_idempotent_sync_push(client, auth_headers):
    # Setup test shop and push bill
    bill_uuid = "client-bill-uuid-999"
    push_payload = {
        "shop_id": "test-shop",
        "items": [
            {
                "id": "sync-queue-row-1",
                "entity_type": "bill",
                "entity_id": bill_uuid,
                "operation": "INSERT",
                "payload_json": json.dumps({
                    "bill_number": "BILL-20260919-099",
                    "total": 250.0,
                    "payment_method": "upi"
                }),
                "created_at": int(datetime.now(timezone.utc).timestamp() * 1000)
            }
        ]
    }

    # 1. First push: should process
    resp1 = client.post("/api/v1/sync/push", headers=auth_headers, json=push_payload)
    assert resp1.status_code == 200
    assert resp1.json()["processed_count"] == 1
    assert "sync-queue-row-1" in resp1.json()["acknowledged_ids"]

    # 2. Second identical push (network retry simulation): should acknowledge without duplicate error
    resp2 = client.post("/api/v1/sync/push", headers=auth_headers, json=push_payload)
    assert resp2.status_code == 200
    assert resp2.json()["processed_count"] == 1


def test_reports_summary_and_stock_valuation(client, auth_headers):
    # 1. Add two items
    client.post("/api/v1/items/", headers=auth_headers, json={
        "name": "Atta 5kg",
        "category": "Staples",
        "unit_type": "piece",
        "price": 200.0,
        "stock_qty": 10.0,  # Valuation = 2000.0
        "low_stock_threshold": 2.0
    })

    client.post("/api/v1/items/", headers=auth_headers, json={
        "name": "Salt 1kg",
        "category": "Staples",
        "unit_type": "piece",
        "price": 25.0,
        "stock_qty": 0.0,  # Out of stock!
        "low_stock_threshold": 5.0
    })

    # Fetch reports summary
    reports_resp = client.get("/api/v1/sync/reports/summary", headers=auth_headers)
    assert reports_resp.status_code == 200
    data = reports_resp.json()

    # Verify stock valuation: 10 * 200 = 2000.0
    assert data["total_stock_valuation"] == 2000.0
    # Verify out-of-stock count: Salt is 0
    assert data["out_of_stock_count"] == 1
    assert len(data["daily_breakdown"]) == 7


def test_sync_pull_restores_all_data(client, auth_headers):
    # 1. Create an item and a bill
    item_resp = client.post("/api/v1/items/", headers=auth_headers, json={
        "name": "Basmati Rice 1kg",
        "category": "Staples",
        "unit_type": "piece",
        "price": 120.0,
        "stock_qty": 50.0,
        "low_stock_threshold": 5.0
    })
    item_id = item_resp.json()["id"]

    client.post("/api/v1/bills/", headers=auth_headers, json={
        "bill_number": "BILL-TEST-PULL-01",
        "subtotal": 120.0,
        "discount": 0.0,
        "tax": 0.0,
        "total": 120.0,
        "payment_method": "cash",
        "items": [
            {
                "item_id": item_id,
                "name_snapshot": "Basmati Rice 1kg",
                "qty": 1.0,
                "unit_type": "piece",
                "unit_price_snapshot": 120.0,
                "line_total": 120.0
            }
        ]
    })

    # 2. Call /sync/pull
    pull_resp = client.get("/api/v1/sync/pull", headers=auth_headers)
    assert pull_resp.status_code == 200
    pull_data = pull_resp.json()

    assert pull_data["shop"]["name"] == "Balaji Provisions"
    assert len(pull_data["items"]) >= 1
    assert any(it["name"] == "Basmati Rice 1kg" for it in pull_data["items"])
    assert len(pull_data["bills"]) >= 1
    assert any(b["bill_number"] == "BILL-TEST-PULL-01" for b in pull_data["bills"])
    assert len(pull_data["bill_items"]) >= 1

