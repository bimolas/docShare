# SecureDocSharing

> A secure, event-driven document sharing platform built around Spring Boot, Angular, Keycloak, Kafka, MinIO, PostgreSQL, Redis, and Docker.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-19-DD0031?style=flat-square&logo=angular&logoColor=white)](https://angular.dev/)
[![Keycloak](https://img.shields.io/badge/Keycloak-IAM-4D4D4D?style=flat-square&logo=keycloak&logoColor=white)](https://www.keycloak.org/)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-Event%20Streaming-231F20?style=flat-square&logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](https://www.docker.com/)

## Overview

SecureDocSharing is a full-stack application for securely sharing documents while separating application concerns from identity, storage, messaging, caching, and infrastructure services.

The project combines a Spring Boot backend and Angular frontend with a containerized infrastructure layer. Keycloak handles identity and access management, MinIO provides S3-compatible object storage, Kafka provides event streaming, PostgreSQL stores application data, and Redis provides caching.

## Architecture

```text
                         ┌─────────────────────┐
                         │      Angular 19      │
                         │      Frontend       │
                         └──────────┬──────────┘
                                    │ HTTPS
                                    ▼
                         ┌─────────────────────┐
                         │    Spring Boot 3    │
                         │       Backend       │
                         └──────┬────┬────┬────┘
                                │    │    │
                 ┌──────────────┘    │    └──────────────┐
                 ▼                   ▼                   ▼
          ┌────────────┐      ┌────────────┐      ┌────────────┐
          │ PostgreSQL │      │   Redis    │      │   MinIO    │
          │ Application│      │   Cache    │      │  Documents │
          │    Data    │      │            │      │   / S3     │
          └────────────┘      └────────────┘      └────────────┘
                 
                 ┌─────────────────────────────────────────┐
                 │              Infrastructure              │
                 │ Keycloak · Kafka · Zookeeper · Mailpit │
                 └─────────────────────────────────────────┘
```

## Core Technologies

| Layer | Technology | Responsibility |
|---|---|---|
| Frontend | Angular 19 | Web application UI |
| Backend | Spring Boot 3 / Java 21 | REST API and business logic |
| Identity | Keycloak | Authentication, users, roles and clients |
| Database | PostgreSQL | Persistent application data |
| Object storage | MinIO | S3-compatible document storage |
| Messaging | Apache Kafka + Zookeeper | Event streaming |
| Cache | Redis | Application caching |
| Email testing | Mailpit | Local email inspection |
| Infrastructure | Docker Compose | Local service orchestration |

## Project Structure

```text
secure-document-sharings/
├── docker-compose.yml
├── keycloak/
│   └── realm-export.json
├── src/
│   └── ...                  # Spring Boot backend
├── pom.xml
└── securedocsharing-frontend/
    ├── package.json
    └── src/
```

## Infrastructure

The complete local environment is orchestrated with Docker Compose:

| Service | Port | Purpose |
|---|---:|---|
| PostgreSQL | `5432` | Application database |
| Keycloak | `8080` | Identity and access management |
| MinIO | `9000` / `9001` | Object storage and console |
| Kafka | `9092` | Event streaming |
| Zookeeper | `2181` | Kafka coordination |
| Redis | `6379` | Cache |
| Mailpit | `8025` | Email testing UI |

Keycloak is initialized from `keycloak/realm-export.json`, including the `docshare` realm configuration.

## Getting Started

### Prerequisites

- Docker 24+
- Docker Compose v2+
- JDK 21
- Node.js 20+
- npm 10+

### 1. Start infrastructure

```bash
docker compose up -d
docker compose ps
```

Wait for the containers to become healthy before starting the application.

### 2. Start the backend

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
mvnw.cmd spring-boot:run
```

The backend runs on HTTPS port `8443`.

### 3. Start the frontend

```bash
cd securedocsharing-frontend
npm install
npm start
```

The Angular development server runs at `http://localhost:4200`.

### 4. Open the application

```text
http://localhost:4200
```

The application redirects authentication to Keycloak.

## Engineering Highlights

- **Centralized identity management** with Keycloak instead of implementing authentication as an isolated frontend concern.
- **Event-driven infrastructure** using Kafka for asynchronous communication.
- **Dedicated object storage** through MinIO rather than storing document binaries directly in PostgreSQL.
- **Caching layer** with Redis.
- **Containerized development environment** with all supporting infrastructure defined in Docker Compose.
- **Separated frontend and backend** allowing each application layer to evolve independently.
- **Reproducible Keycloak setup** through realm configuration committed with the project.

## Development Commands

```bash
# Backend
./mvnw spring-boot:run

# Frontend
cd securedocsharing-frontend
npm install
npm start

# Infrastructure
docker compose up -d
docker compose ps
docker compose down
```

## Troubleshooting

### Keycloak login loop

Make sure the frontend is running exactly at:

```text
http://localhost:4200
```

The configured Keycloak client expects this development URL.

### Backend cannot connect to PostgreSQL

Verify that the infrastructure containers are running:

```bash
docker compose ps
```

### Maven wrapper permission denied

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

## Project Status

This repository is a development project focused on demonstrating secure application architecture, identity management, event-driven components, object storage, caching, and containerized infrastructure.
