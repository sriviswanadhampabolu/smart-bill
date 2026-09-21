import pytest


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


def test_create_and_list_items(client, auth_headers):
    # 1. Create a piece item (Biscuit packet)
    resp1 = client.post("/api/v1/items/", headers=auth_headers, json={
        "name": "Parle-G 100g",
        "name_regional": "पारले-जी",
        "category": "Snacks",
        "barcode": "8901719101010",
        "unit_type": "piece",
        "price": 10.0,
        "stock_qty": 50.0,
        "low_stock_threshold": 10.0
    })
    assert resp1.status_code == 201
    item1 = resp1.json()
    assert item1["name"] == "Parle-G 100g"
    assert item1["is_low_stock"] is False

    # 2. Create a weight item (Sona Masoori Rice) with low stock
    resp2 = client.post("/api/v1/items/", headers=auth_headers, json={
        "name": "Sona Masoori Rice",
        "name_regional": "चावल",
        "category": "Staples",
        "unit_type": "weight",
        "price": 60.0,
        "stock_qty": 4.0,
        "low_stock_threshold": 10.0
    })
    assert resp2.status_code == 201
    item2 = resp2.json()
    assert item2["is_low_stock"] is True

    # 3. List items sorted by lowest stock first (default)
    list_resp = client.get("/api/v1/items/", headers=auth_headers)
    assert list_resp.status_code == 200
    items = list_resp.json()
    assert len(items) == 2
    assert items[0]["name"] == "Sona Masoori Rice"
    assert items[1]["name"] == "Parle-G 100g"

    # 4. Filter by low_stock_only
    low_resp = client.get("/api/v1/items/?low_stock_only=true", headers=auth_headers)
    assert low_resp.status_code == 200
    assert len(low_resp.json()) == 1
    assert low_resp.json()[0]["name"] == "Sona Masoori Rice"

    # 5. Search query
    search_resp = client.get("/api/v1/items/?q=Parle", headers=auth_headers)
    assert search_resp.status_code == 200
    assert len(search_resp.json()) == 1
    assert search_resp.json()[0]["name"] == "Parle-G 100g"


def test_stock_adjustment_and_logging(client, auth_headers):
    create_resp = client.post("/api/v1/items/", headers=auth_headers, json={
        "name": "Tata Salt 1kg",
        "category": "Staples",
        "unit_type": "piece",
        "price": 28.0,
        "stock_qty": 5.0,
        "low_stock_threshold": 10.0
    })
    item_id = create_resp.json()["id"]

    adjust_resp = client.post(f"/api/v1/items/{item_id}/adjust-stock", headers=auth_headers, json={
        "change_qty": 20.0,
        "reason": "restock"
    })
    assert adjust_resp.status_code == 200
    assert adjust_resp.json()["stock_qty"] == 25.0
    assert adjust_resp.json()["is_low_stock"] is False


def test_bill_creation_atomic_stock_decrement(client, auth_headers):
    soap_resp = client.post("/api/v1/items/", headers=auth_headers, json={
        "name": "Lifebuoy Soap",
        "category": "Personal Care",
        "unit_type": "piece",
        "price": 35.0,
        "stock_qty": 15.0,
        "low_stock_threshold": 5.0
    })
    soap_id = soap_resp.json()["id"]

    sugar_resp = client.post("/api/v1/items/", headers=auth_headers, json={
        "name": "White Sugar",
        "category": "Staples",
        "unit_type": "weight",
        "price": 44.0,
        "stock_qty": 20.0,
        "low_stock_threshold": 5.0
    })
    sugar_id = sugar_resp.json()["id"]

    bill_payload = {
        "bill_number": "BILL-20260919-001",
        "subtotal": 136.0,
        "discount": 0.0,
        "tax": 0.0,
        "total": 136.0,
        "payment_method": "cash",
        "items": [
            {
                "item_id": soap_id,
                "name_snapshot": "Lifebuoy Soap",
                "qty": 2.0,
                "unit_type": "piece",
                "unit_price_snapshot": 35.0,
                "line_total": 70.0
            },
            {
                "item_id": sugar_id,
                "name_snapshot": "White Sugar",
                "qty": 1.5,
                "unit_type": "weight",
                "unit_price_snapshot": 44.0,
                "line_total": 66.0
            }
        ]
    }

    bill_resp = client.post("/api/v1/bills/", headers=auth_headers, json=bill_payload)
    assert bill_resp.status_code == 201
    bill = bill_resp.json()
    assert bill["bill_number"] == "BILL-20260919-001"
    assert bill["total"] == 136.0
    assert len(bill["items"]) == 2

    # Check soap stock decremented
    get_soap = client.get(f"/api/v1/items/{soap_id}", headers=auth_headers)
    assert get_soap.json()["stock_qty"] == 13.0

    # Check sugar stock decremented
    get_sugar = client.get(f"/api/v1/items/{sugar_id}", headers=auth_headers)
    assert get_sugar.json()["stock_qty"] == 18.5
