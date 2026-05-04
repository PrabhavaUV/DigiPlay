# DigiPlay: IoT-Based Remote Digital Display System

## 📌 Project Overview
DigiPlay is an integrated IoT platform designed to manage and update digital name displays (ESP32-based hardware) remotely. The system features a web-based administrative dashboard for real-time monitoring and content approval, coupled with a mobile application for submitting update requests.

This repository focuses on the **Backend Infrastructure**, providing the core logic for device management, request handling, and real-time data synchronization.

---

## 🚀 Key Features
- **Centralized Admin Dashboard:** Monitor hardware status (Online/Offline) and manage content queues.
- **Embedded MQTT Broker:** Uses the high-performance **Aedes** engine for instantaneous message delivery to hardware.
- **Bi-directional Communication:** Hardware status is tracked via MQTT presence, while content is pushed via Pub/Sub.
- **Mobile Integration:** Dedicated API endpoints for mobile app authentication and content submission.
- **Approval Workflow:** Content updates go through an administrative review gate before being deployed to the displays.

---

## 🛠️ Technical Architecture
- **Runtime Environment:** Node.js (v18+)
- **Backend Framework:** Express.js
- **Database Layer:** PostgreSQL (via Sequelize ORM) for persistent storage.
- **Communication Protocol:** MQTT (Standard TCP Port 1883) for hardware interaction.
- **Frontend Layer:** Nunjucks Templating Engine with a custom Glassmorphic CSS design.

### Project Structure
```text
server/
├── index.js          # Main Application Entry & MQTT Core
├── models.js         # Database Models (Admins, Devices, Requests)
├── auth.js           # JWT & Bcrypt Authentication Logic
├── database.js       # Database Connection Configuration
├── routes/           # REST API Controller Layer
├── templates/        # Nunjucks HTML Layouts
├── static/           # UI Assets (CSS & Media)
└── scripts/          # System Setup & Deployment Utilities
```

---

## ⚙️ Quick Start

### 1. Prerequisites
- Node.js installed on your machine.
- A local database (PostgreSQL or SQLite).

### 2. Installation
```bash
# Install dependencies
npm install

# Initialize secure admin credentials
npm run seed
```

### 3. Execution
```bash
# Start the production server
npm start

# For development (with hot-reload)
npm run dev
```

### 4. Access
- **Web Dashboard:** `http://localhost:8000`
- **MQTT Broker:** `mqtt://your-ip-address:1883`

---
*DigiPlay — An IoT College Project exploring secure real-time hardware management.*

