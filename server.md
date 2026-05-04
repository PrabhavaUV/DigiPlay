# DigiPlay – IoT-Based Remote Digital Name Display System
# `server.md` — Backend Server & Web Dashboard

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Architecture](#architecture)
3. [Technology Stack](#technology-stack)
4. [Database Schema](#database-schema)
5. [API Design](#api-design)
6. [Approval Workflow](#approval-workflow)
7. [Authentication System](#authentication-system)
8. [Web Dashboard](#web-dashboard)
9. [Deployment Guide](#deployment-guide)
10. [Security Design](#security-design)
11. [Improvements & Future Enhancements](#improvements--future-enhancements)
12. [How to Convert to PDF](#how-to-convert-to-pdf)

---

## 1. System Overview

DigiPlay is an IoT platform enabling remote control of ESP32-based digital name display devices. The central server acts as the **single source of truth** — managing device registration, content update requests, admin approvals, and serving approved content to devices.

**Key Responsibilities of the Server:**
- Store and manage device records in PostgreSQL
- Receive update requests from the mobile app (stored as `PENDING`)
- Provide an admin web dashboard for reviewing and approving/rejecting requests
- Serve only `APPROVED` content to ESP32 devices via authenticated API endpoints
- Authenticate admins, mobile app users, and ESP32 devices independently

---

## 2. Architecture

### 2.1 High-Level Architecture

```
+------------------+        HTTPS        +------------------+
|  Mobile App      |  ───────────────>   |                  |
|  (Java Android)  |  POST /api/request  |   DigiPlay       |
+------------------+                     |   Central Server |
                                          |   (FastAPI)      |
+------------------+        HTTPS        |                  |
|  ESP32 Devices   |  ───────────────>   |   Port 8000      |
|  (Arduino)       |  GET /api/device/   |                  |
+------------------+  {id}/approved      +--------+---------+
                                                   |
+------------------+        HTTP         +--------+---------+
|  Admin Browser   |  ───────────────>   |  Web Dashboard   |
|  (Web UI)        |  Admin Panel        |  (Jinja2 / HTML) |
+------------------+                     +--------+---------+
                                                   |
                                          +--------+---------+
                                          |   PostgreSQL DB  |
                                          |   (Port 5432)    |
                                          +------------------+
```

### 2.2 Component Diagram

```
server/
├── main.py                  # FastAPI app entry point
├── database.py              # SQLAlchemy engine + session
├── models.py                # ORM models (Device, UpdateRequest, Admin)
├── schemas.py               # Pydantic validation schemas
├── auth.py                  # JWT auth logic
├── routers/
│   ├── devices.py           # Device CRUD endpoints
│   ├── requests.py          # Update request endpoints
│   ├── admin.py             # Admin approval endpoints
│   └── esp32.py             # ESP32 polling endpoints
├── templates/               # Jinja2 HTML templates
│   ├── base.html
│   ├── login.html
│   ├── dashboard.html
│   ├── devices.html
│   └── requests.html
├── static/
│   ├── style.css
│   └── app.js
├── requirements.txt
└── .env
```

---

## 3. Technology Stack

### 3.1 Backend Framework: FastAPI (Python)

| Criteria | Details |
|---|---|
| Language | Python 3.10+ |
| Framework | FastAPI |
| ORM | SQLAlchemy (sync) |
| Migrations | Alembic |
| Auth | JWT via `python-jose` |
| Password Hash | `passlib[bcrypt]` |
| Templates | Jinja2 |
| Server | Uvicorn |

**Why FastAPI?**
- Automatic OpenAPI/Swagger documentation
- Native async support (useful for scalability)
- Pydantic for data validation out-of-the-box
- Minimal boilerplate compared to Django
- Lightweight and fast

**Pros:** Fast development, auto docs, type-safe, lightweight
**Cons:** Smaller ecosystem than Django; Jinja2 templates are less flexible than React

**Alternatives:** Node.js + Express (lower memory, JS familiarity), Flask (simpler but no async native)

### 3.2 Database: PostgreSQL

| Criteria | Details |
|---|---|
| DB Engine | PostgreSQL 15+ |
| Driver | `psycopg2-binary` |
| Schema Mgmt | Alembic migrations |

**Why PostgreSQL?**
- Reliable ACID transactions (critical for approval workflows)
- Strong indexing for device queries
- JSON column support for metadata

### 3.3 Web Dashboard: Plain HTML/CSS + Minimal JS

**Why plain HTML/CSS?**
- Zero build tools required
- Runs in any browser
- Easy to maintain and style
- Jinja2 server-side rendering avoids JS framework overhead

---

## 4. Database Schema

### 4.1 Entity Relationship Diagram

```
+---------------+          +---------------------+         +--------------+
|    admins     |          |      devices        |         |   update_    |
+---------------+          +---------------------+         |   requests   |
| id (PK)       |          | id (PK, UUID)       |         +--------------+
| username      |          | name                |   1     | id (PK)      |
| password_hash |          | description         | ──────< | device_id FK |
| created_at    |          | device_token (HASH) |         | requested_by |
+---------------+          | current_content     |         | new_content  |
                           | is_online           |         | status       |
                           | last_seen           |         | reviewed_by  |
                           | created_at          |         | created_at   |
                           +---------------------+         | updated_at   |
                                                           +--------------+
```

### 4.2 SQL Schema

```sql
-- Admins table
CREATE TABLE admins (
    id          SERIAL PRIMARY KEY,
    username    VARCHAR(100) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW()
);

-- Devices table
CREATE TABLE devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    device_token    VARCHAR(256) UNIQUE NOT NULL,  -- hashed token for ESP32 auth
    current_content TEXT NOT NULL DEFAULT 'Hello!',
    is_online       BOOLEAN DEFAULT FALSE,
    last_seen       TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW()
);

-- Update requests table
CREATE TABLE update_requests (
    id              SERIAL PRIMARY KEY,
    device_id       UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    requested_by    VARCHAR(100) NOT NULL,          -- mobile user identifier
    new_content     TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    -- PENDING | APPROVED | REJECTED
    reviewed_by     VARCHAR(100),                   -- admin username
    admin_notes     TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    CONSTRAINT valid_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- Indexes for performance
CREATE INDEX idx_requests_device_id    ON update_requests(device_id);
CREATE INDEX idx_requests_status       ON update_requests(status);
CREATE INDEX idx_devices_last_seen     ON devices(last_seen);
```

### 4.3 Status State Machine

```
Mobile App Submits
       │
       ▼
  [ PENDING ]  ──── Admin Reviews ────►  [ APPROVED ]  ──► ESP32 Fetches & Applies
       │
       └──── Admin Rejects ────────────►  [ REJECTED ]
```

---

## 5. API Design

### 5.1 Authentication Endpoints

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| POST | `/auth/login` | Admin login → returns JWT | No |
| POST | `/auth/logout` | Invalidate session | Admin JWT |

### 5.2 Device Management Endpoints

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| GET | `/api/devices` | List all devices | Admin JWT |
| POST | `/api/devices` | Register new device | Admin JWT |
| GET | `/api/devices/{device_id}` | Get device details | Admin JWT |
| PUT | `/api/devices/{device_id}` | Update device metadata | Admin JWT |
| DELETE | `/api/devices/{device_id}` | Remove device | Admin JWT |

### 5.3 Update Request Endpoints (Mobile App)

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| POST | `/api/requests` | Submit new update request | App API Key |
| GET | `/api/requests/{device_id}` | Get request status by device | App API Key |

### 5.4 Admin Approval Endpoints

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| GET | `/api/admin/requests` | List all requests (filterable) | Admin JWT |
| POST | `/api/admin/requests/{id}/approve` | Approve a request | Admin JWT |
| POST | `/api/admin/requests/{id}/reject` | Reject a request | Admin JWT |

### 5.5 ESP32 Device Endpoint

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| GET | `/api/esp32/{device_id}/content` | Get approved content for device | Device Token |

### 5.6 Sample API Request/Response

**Submit Update Request (Mobile App):**
```json
POST /api/requests
Headers:
  X-API-Key: <app_api_key>
  Content-Type: application/json

Body:
{
  "device_id": "550e8400-e29b-41d4-a716-446655440000",
  "requested_by": "user_jane",
  "new_content": "Jane Doe – Product Manager"
}

Response 201:
{
  "request_id": 42,
  "device_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "Request submitted. Awaiting admin approval."
}
```

**ESP32 Poll for Approved Content:**
```json
GET /api/esp32/550e8400-e29b-41d4-a716-446655440000/content
Headers:
  X-Device-Token: <device_token>

Response 200:
{
  "device_id": "550e8400-e29b-41d4-a716-446655440000",
  "content": "Jane Doe – Product Manager",
  "updated_at": "2024-01-15T10:30:00Z",
  "checksum": "a3f5c2d1"
}
```

**Admin Approve Request:**
```json
POST /api/admin/requests/42/approve
Headers:
  Authorization: Bearer <jwt_token>
  Content-Type: application/json

Body:
{
  "admin_notes": "Verified. Approved for display."
}

Response 200:
{
  "request_id": 42,
  "status": "APPROVED",
  "device_id": "550e8400-...",
  "new_content": "Jane Doe – Product Manager"
}
```

---

## 6. Approval Workflow

### 6.1 Sequence Diagram

```
Mobile App       Server API        PostgreSQL       Admin Dashboard     ESP32
    │                │                 │                  │               │
    │──POST /request─►│                 │                  │               │
    │                │──INSERT status=─►│                  │               │
    │                │   PENDING        │                  │               │
    │◄── 201 PENDING─│                  │                  │               │
    │                │                  │                  │               │
    │                │                  │◄──GET /requests──│               │
    │                │                  │──return list─────►               │
    │                │                  │                  │               │
    │                │                  │◄─POST /approve───│               │
    │                │──UPDATE status=──►│                  │               │
    │                │   APPROVED        │                  │               │
    │                │──UPDATE devices.──►│                  │               │
    │                │  current_content  │                  │               │
    │                │                  │                  │◄──200 OK──────│
    │                │                  │                  │               │
    │                │                  │               (polling)          │
    │                │◄────────────────────────────GET /esp32/{id}/content─│
    │                │──SELECT approved──►│                  │               │
    │                │◄── content ───────│                  │               │
    │                │────────────────────────────────────content JSON─────►│
    │                │                  │                  │      (display) │
```

### 6.2 Edge Cases

| Scenario | Handling |
|---|---|
| Duplicate PENDING request | Server rejects with `409 Conflict` if active PENDING exists for device |
| Request rejected | Status set to REJECTED; device content unchanged |
| Multiple APPROVED requests | Only latest APPROVED used; device content updated once |
| ESP32 offline at approval time | Next poll cycle picks up approved content |
| Admin approves then content reverts | A new request must be submitted |
| Device deleted mid-request | Cascade delete removes all requests |

### 6.3 Implementation (Python/FastAPI)

```python
# routers/admin.py
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import UpdateRequest, Device
from ..auth import require_admin

router = APIRouter(prefix="/api/admin", tags=["admin"])

@router.post("/requests/{request_id}/approve")
def approve_request(
    request_id: int,
    notes: dict,
    db: Session = Depends(get_db),
    admin = Depends(require_admin)
):
    req = db.query(UpdateRequest).filter(
        UpdateRequest.id == request_id,
        UpdateRequest.status == "PENDING"
    ).first()

    if not req:
        raise HTTPException(status_code=404, detail="Request not found or not pending")

    # Approve request
    req.status = "APPROVED"
    req.reviewed_by = admin.username
    req.admin_notes = notes.get("admin_notes", "")

    # Update device's current content
    device = db.query(Device).filter(Device.id == req.device_id).first()
    device.current_content = req.new_content

    db.commit()
    return {"request_id": req.id, "status": "APPROVED", "device_id": str(req.device_id)}
```

---

## 7. Authentication System

### 7.1 Three-Tier Authentication

| Actor | Method | Token Type |
|---|---|---|
| Admin | Username + Password → JWT | Bearer JWT (24h expiry) |
| Mobile App | Static API Key per app instance | `X-API-Key` header |
| ESP32 Device | Unique per-device token (hashed in DB) | `X-Device-Token` header |

### 7.2 JWT Auth Implementation

```python
# auth.py
from jose import JWTError, jwt
from passlib.context import CryptContext
from datetime import datetime, timedelta
import os

SECRET_KEY = os.getenv("SECRET_KEY")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_HOURS = 24

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

def create_access_token(data: dict) -> str:
    expire = datetime.utcnow() + timedelta(hours=ACCESS_TOKEN_EXPIRE_HOURS)
    return jwt.encode({**data, "exp": expire}, SECRET_KEY, algorithm=ALGORITHM)

def verify_token(token: str) -> dict:
    try:
        return jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
    except JWTError:
        return None
```

### 7.3 Device Token Generation

```python
import secrets, hashlib

def generate_device_token() -> tuple[str, str]:
    """Returns (raw_token, hashed_token). Store only hash in DB."""
    raw = secrets.token_urlsafe(32)
    hashed = hashlib.sha256(raw.encode()).hexdigest()
    return raw, hashed

def verify_device_token(raw: str, stored_hash: str) -> bool:
    return hashlib.sha256(raw.encode()).hexdigest() == stored_hash
```

---

## 8. Web Dashboard

### 8.1 Pages & Routes

| Page | Route | Description |
|---|---|---|
| Login | `GET /login` | Admin login form |
| Dashboard | `GET /dashboard` | Overview: device count, pending count |
| Devices | `GET /devices` | List all registered devices |
| Requests | `GET /requests` | All update requests, filterable by status |
| Request Detail | `GET /requests/{id}` | View + approve/reject a request |

### 8.2 Dashboard HTML Template (Simplified)

```html
<!-- templates/dashboard.html -->
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>DigiPlay Admin</title>
    <link rel="stylesheet" href="/static/style.css">
</head>
<body>
    <nav class="navbar">
        <span class="brand">⬛ DigiPlay Admin</span>
        <a href="/devices">Devices</a>
        <a href="/requests">Requests</a>
        <a href="/logout">Logout</a>
    </nav>

    <main class="container">
        <h1>Dashboard</h1>
        <div class="stats-grid">
            <div class="stat-card">
                <h3>{{ device_count }}</h3>
                <p>Total Devices</p>
            </div>
            <div class="stat-card highlight">
                <h3>{{ pending_count }}</h3>
                <p>Pending Approvals</p>
            </div>
            <div class="stat-card">
                <h3>{{ online_count }}</h3>
                <p>Devices Online</p>
            </div>
        </div>

        <h2>Pending Requests</h2>
        <table class="data-table">
            <thead>
                <tr>
                    <th>Device</th>
                    <th>Requested By</th>
                    <th>New Content</th>
                    <th>Submitted</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
            {% for req in pending_requests %}
                <tr>
                    <td>{{ req.device.name }}</td>
                    <td>{{ req.requested_by }}</td>
                    <td>{{ req.new_content }}</td>
                    <td>{{ req.created_at.strftime('%Y-%m-%d %H:%M') }}</td>
                    <td>
                        <form method="POST" action="/requests/{{ req.id }}/approve" style="display:inline">
                            <button class="btn-approve">Approve</button>
                        </form>
                        <form method="POST" action="/requests/{{ req.id }}/reject" style="display:inline">
                            <button class="btn-reject">Reject</button>
                        </form>
                    </td>
                </tr>
            {% endfor %}
            </tbody>
        </table>
    </main>
</body>
</html>
```

### 8.3 CSS (Minimal)

```css
/* static/style.css */
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: 'Segoe UI', sans-serif; background: #f5f5f5; color: #333; }
.navbar { background: #1a1a2e; color: white; padding: 1rem 2rem;
          display: flex; align-items: center; gap: 2rem; }
.navbar a { color: #ccc; text-decoration: none; }
.brand { font-weight: bold; font-size: 1.2rem; margin-right: auto; }
.container { max-width: 1100px; margin: 2rem auto; padding: 0 1rem; }
.stats-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; margin: 1.5rem 0; }
.stat-card { background: white; border-radius: 8px; padding: 1.5rem;
             text-align: center; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
.stat-card h3 { font-size: 2rem; color: #1a1a2e; }
.stat-card.highlight { border-top: 4px solid #e74c3c; }
.data-table { width: 100%; border-collapse: collapse; background: white;
              border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
.data-table th { background: #1a1a2e; color: white; padding: 0.75rem 1rem; text-align: left; }
.data-table td { padding: 0.75rem 1rem; border-bottom: 1px solid #eee; }
.btn-approve { background: #27ae60; color: white; border: none; padding: 0.4rem 0.8rem;
               border-radius: 4px; cursor: pointer; }
.btn-reject  { background: #e74c3c; color: white; border: none; padding: 0.4rem 0.8rem;
               border-radius: 4px; cursor: pointer; margin-left: 0.5rem; }
```

---

## 9. Deployment Guide

### 9.1 Prerequisites

```
- Ubuntu 22.04 VPS (or any cloud VM)
- Python 3.10+
- PostgreSQL 15
- Nginx (reverse proxy)
- Certbot (SSL/TLS via Let's Encrypt)
```

### 9.2 Environment Setup

```bash
# 1. Clone repository
git clone https://github.com/your-org/digiplay-server.git
cd digiplay-server

# 2. Create virtual environment
python3 -m venv venv
source venv/bin/activate

# 3. Install dependencies
pip install -r requirements.txt

# 4. Configure environment variables
cp .env.example .env
nano .env
```

**`.env` file:**
```env
DATABASE_URL=postgresql://digiplay:strongpassword@localhost:5432/digiplay_db
SECRET_KEY=your-very-long-random-secret-key-here
APP_API_KEY=your-mobile-app-static-api-key
ENVIRONMENT=production
```

### 9.3 Database Setup

```bash
# Create PostgreSQL database and user
sudo -u postgres psql

CREATE DATABASE digiplay_db;
CREATE USER digiplay WITH PASSWORD 'strongpassword';
GRANT ALL PRIVILEGES ON DATABASE digiplay_db TO digiplay;
\q

# Run Alembic migrations
alembic upgrade head

# Seed initial admin account
python scripts/create_admin.py --username admin --password securepass123
```

### 9.4 Running the Server

```bash
# Development
uvicorn main:app --reload --host 0.0.0.0 --port 8000

# Production (via systemd)
sudo nano /etc/systemd/system/digiplay.service
```

**Systemd service file:**
```ini
[Unit]
Description=DigiPlay FastAPI Server
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/digiplay-server
Environment="PATH=/home/ubuntu/digiplay-server/venv/bin"
ExecStart=/home/ubuntu/digiplay-server/venv/bin/uvicorn main:app --host 127.0.0.1 --port 8000
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable digiplay
sudo systemctl start digiplay
```

### 9.5 Nginx + SSL Configuration

```nginx
# /etc/nginx/sites-available/digiplay
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name your-domain.com;

    ssl_certificate     /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    location / {
        proxy_pass         http://127.0.0.1:8000;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo certbot --nginx -d your-domain.com
sudo systemctl reload nginx
```

### 9.6 requirements.txt

```
fastapi==0.110.0
uvicorn[standard]==0.29.0
sqlalchemy==2.0.28
alembic==1.13.1
psycopg2-binary==2.9.9
python-jose[cryptography]==3.3.0
passlib[bcrypt]==1.7.4
jinja2==3.1.3
python-multipart==0.0.9
python-dotenv==1.0.1
```

---

## 10. Security Design

### 10.1 Threat Model & Mitigations

| Threat | Mitigation |
|---|---|
| Unauthorized admin access | Bcrypt-hashed passwords + JWT tokens with expiry |
| Device spoofing | Unique per-device SHA256-hashed tokens in DB |
| Replay attacks | JWT `exp` claim; device tokens are one-time-bound per device |
| Direct display manipulation | ESP32 only polls `/esp32/` endpoint; no device can push updates |
| Unapproved content display | ESP32 endpoint only returns `APPROVED` content |
| Brute force login | Rate limiting via Nginx; account lockout after N failures |
| MITM attacks | HTTPS enforced (Nginx + Certbot); HTTP redirected to HTTPS |
| SQL injection | SQLAlchemy ORM parameterized queries |
| XSS on dashboard | Jinja2 auto-escaping enabled |

### 10.2 Security Checklist

- [ ] `SECRET_KEY` is random, 32+ characters, stored in `.env` (never committed to git)
- [ ] Database credentials not hardcoded
- [ ] HTTPS enforced on all endpoints
- [ ] Admin passwords are bcrypt-hashed (never stored plaintext)
- [ ] Device tokens stored as SHA256 hash (raw token given only at registration)
- [ ] Rate limiting configured on Nginx
- [ ] `.env` added to `.gitignore`

---

## 11. Improvements & Future Enhancements

| Enhancement | Description | Priority |
|---|---|---|
| MQTT Support | Replace HTTP polling with MQTT pub/sub for real-time push to ESP32 | High |
| Role-Based Access | Multi-admin with roles: super-admin, reviewer, read-only | Medium |
| Audit Logs | Log every approval/rejection with admin, timestamp, reason | High |
| OTA Firmware Updates | Serve firmware binaries via API for remote ESP32 updates | Medium |
| Multi-Admin Support | Multiple admin accounts with separate credentials | Medium |
| Email Notifications | Notify admins on new pending requests via email | Low |
| Device Groups | Group devices for batch content updates | Low |
| E-Paper Optimization | Track which devices use e-paper and throttle update polling | Low |
| 2FA for Admin | TOTP-based two-factor authentication for admin login | Medium |
| Webhook Support | Notify external systems on approval events | Low |

---

## 12. How to Convert to PDF

### Using Pandoc (Recommended)

```bash
# Install pandoc
sudo apt install pandoc texlive-xetex

# Basic conversion
pandoc server.md -o server.pdf

# High-quality conversion with custom styling
pandoc server.md \
  --pdf-engine=xelatex \
  -V geometry:margin=1in \
  -V fontsize=11pt \
  -V mainfont="DejaVu Sans" \
  -o server.pdf

# With table of contents
pandoc server.md \
  --pdf-engine=xelatex \
  --toc \
  --toc-depth=2 \
  -V geometry:margin=1in \
  -o server.pdf
```

### Using VS Code

1. Install extension: **Markdown PDF** (yzane.markdown-pdf)
2. Open `server.md`
3. Right-click → **Markdown PDF: Export (pdf)**

### Using Typora

1. Open `server.md` in Typora
2. Go to **File → Export → PDF**
3. Choose theme and export

---

*DigiPlay Server Documentation — v1.0*
*Generated for production-grade implementation*
