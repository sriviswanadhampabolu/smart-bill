import pytest


def test_root_endpoint(client):
    response = client.get("/")
    assert response.status_code == 200
    assert response.json()["status"] == "online"


def test_signup_success(client):
    payload = {
        "shop_name": "Sri Balaji Provisions",
        "owner_name": "Ramesh Kumar",
        "phone": "9876543210",
        "password": "owner_secure_password",
        "pin": "1234",
        "upi_id": "balaji@okaxis",
        "currency_symbol": "₹"
    }
    response = client.post("/api/v1/auth/signup", json=payload)
    assert response.status_code == 201
    data = response.json()
    assert "access_token" in data
    assert data["shop_name"] == "Sri Balaji Provisions"
    assert data["owner_name"] == "Ramesh Kumar"
    assert data["has_pin"] is True
    assert data["currency_symbol"] == "₹"


def test_signup_duplicate_phone_fails(client):
    payload = {
        "shop_name": "Shop A",
        "owner_name": "Owner A",
        "phone": "9999999999",
        "password": "secret_pass_1",
        "pin": "4321"
    }
    resp1 = client.post("/api/v1/auth/signup", json=payload)
    assert resp1.status_code == 201

    # Attempt duplicate
    resp2 = client.post("/api/v1/auth/signup", json=payload)
    assert resp2.status_code == 400
    assert "already exists" in resp2.json()["detail"]


def test_password_login_and_pin_login(client):
    signup_payload = {
        "shop_name": "Lakshmi Stores",
        "owner_name": "Suresh",
        "phone": "9845012345",
        "password": "counter_pass_123",
        "pin": "5678"
    }
    client.post("/api/v1/auth/signup", json=signup_payload)

    # 1. Login with password
    login_resp = client.post("/api/v1/auth/login", json={
        "phone": "9845012345",
        "password": "counter_pass_123"
    })
    assert login_resp.status_code == 200
    assert "access_token" in login_resp.json()

    # 2. Login with wrong password
    bad_pass = client.post("/api/v1/auth/login", json={
        "phone": "9845012345",
        "password": "wrong_password"
    })
    assert bad_pass.status_code == 401

    # 3. Fast counter PIN unlock
    pin_resp = client.post("/api/v1/auth/pin-login", json={
        "phone": "9845012345",
        "pin": "5678"
    })
    assert pin_resp.status_code == 200
    assert pin_resp.json()["has_pin"] is True

    # 4. Wrong PIN unlock
    bad_pin = client.post("/api/v1/auth/pin-login", json={
        "phone": "9845012345",
        "pin": "0000"
    })
    assert bad_pin.status_code == 401


def test_shop_profile_and_setup_update(client):
    # Register shop
    signup_resp = client.post("/api/v1/auth/signup", json={
        "shop_name": "Anand Kirana",
        "owner_name": "Anand",
        "phone": "9123456789",
        "password": "password123",
        "pin": "9999"
    })
    token = signup_resp.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # Fetch profile
    profile_resp = client.get("/api/v1/shop/profile", headers=headers)
    assert profile_resp.status_code == 200
    profile = profile_resp.json()
    assert profile["name"] == "Anand Kirana"
    assert profile["has_pin"] is True

    # Update shop setup (UPI ID, regional language to Kannada, tax enable)
    update_resp = client.put("/api/v1/shop/profile", headers=headers, json={
        "upi_id": "anandkirana@upi",
        "settings_json": {
            "regional_language": "kn",
            "tax_enabled": True,
            "default_tax_rate": 5.0
        }
    })
    assert update_resp.status_code == 200
    updated = update_resp.json()
    assert updated["upi_id"] == "anandkirana@upi"
    assert updated["settings_json"]["regional_language"] == "kn"
    assert updated["settings_json"]["tax_enabled"] is True


def test_sync_status(client):
    signup_resp = client.post("/api/v1/auth/signup", json={
        "shop_name": "Quick Kirana",
        "owner_name": "Ravi",
        "phone": "9000011111",
        "password": "password123"
    })
    token = signup_resp.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    resp = client.get("/api/v1/sync/status", headers=headers)
    assert resp.status_code == 200
    assert resp.json()["status"] == "ready"
    assert resp.json()["protocol_version"] == "1.0"
