# SecureDocSharing

A full-stack secure document sharing platform built with **Angular 19**, **Spring Boot 3**, **Keycloak**, **MinIO**, **Kafka**, and **PostgreSQL**.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Step 1 — Start Infrastructure](#step-1--start-infrastructure-docker-compose)
- [Step 2 — Start the Backend](#step-2--start-the-backend-spring-boot)
- [Step 3 — Start the Frontend](#step-3--start-the-frontend-angular)
- [Access the Application](#access-the-application)
- [Demo Accounts](#demo-accounts)
- [Service URLs](#service-urls)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

Make sure the following tools are installed:

| Tool | Version | Install |
|------|---------|---------|
| Docker | 24+ | https://docs.docker.com/get-docker/ |
| Docker Compose | v2+ | Included with Docker Desktop |
| Java JDK | 21 | https://adoptium.net/ |
| Node.js | 20+ | https://nodejs.org/ |
| npm | 10+ | Included with Node.js |

Verify your setup:

```bash
docker --version
docker compose version
java -version
node --version
npm --version
```

---

## Project Structure

```
secure-document-sharings/
├── docker-compose.yml            # All infrastructure services
├── keycloak/
│   └── realm-export.json         # Auto-imported Keycloak realm (users + config)
├── src/                          # Spring Boot backend (Java 21)
├── pom.xml
└── securedocsharing-frontend/    # Angular 19 frontend
    ├── package.json
    └── src/
```

---

## Step 1 — Start Infrastructure (Docker Compose)

Open a terminal in the **project root** and run:

```bash
docker compose up -d
```

This starts:

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL | 5432 | Application database |
| Keycloak | 8080 | Identity & authentication |
| MinIO | 9000 / 9001 | File storage (S3-compatible) |
| Kafka + Zookeeper | 9092 | Event streaming |
| Mailpit | 8025 | Email testing UI |
| Redis | 6379 | Caching |

> **Keycloak is auto-configured on first start** — the realm `docshare`, all users, roles, and client settings are automatically imported from `keycloak/realm-export.json`. No manual Keycloak configuration is needed.

Wait **30–60 seconds** for all services to become healthy, then verify:

```bash
docker compose ps
```

All services should show **Up** status.

Optionally confirm Keycloak is ready:

```bash
curl http://localhost:8080/realms/docshare/.well-known/openid-configuration
```

You should receive a JSON response (not an error).

---

## Step 2 — Start the Backend (Spring Boot)

Open a **new terminal** in the project root:

```bash
./mvnw spring-boot:run
```

> On Windows use: `mvnw.cmd spring-boot:run`

The backend starts on **port 8443** (HTTPS). Wait until you see:

```
Started SecureDocSharingApplication in X.XXX seconds
```

> First startup may take 1–2 minutes while Maven downloads dependencies.

---

## Step 3 — Start the Frontend (Angular)

Open a **new terminal** and navigate to the frontend folder:

```bash
cd securedocsharing-frontend
npm install
npm start
```

The Angular dev server starts at **http://localhost:4200**.

> `npm install` is only needed once (or when `package.json` changes).

---

## Access the Application

Once all three steps are complete, open your browser at:

```
http://localhost:4200
```

You will be automatically redirected to the Keycloak login page.

---



## Troubleshooting

**Port already in use?**
```bash
docker compose down
docker compose up -d
```

**Keycloak login loop or "Invalid redirect URI"?**
Make sure the Angular dev server is running on exactly `http://localhost:4200`. The Keycloak client is pre-configured for this URL only.

**Backend fails to connect to the database?**
Ensure `docker compose up -d` completed successfully and all containers show `Up` before starting Spring Boot.

**`./mvnw` permission denied?**
```bash
chmod +x mvnw
./mvnw spring-boot:run
```

**`npm install` errors?**
Make sure you are on Node.js 20+:
```bash
node --version   # must show v20.x.x or higher
```
