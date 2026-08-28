# Society Complaint Management System
Live link - https://society-complaint-management-system.onrender.com

A full-stack maintenance complaint tracking platform for apartment societies, built with Spring Boot, featuring role-based access control, event-driven email notifications via Apache Kafka, Redis caching and session management, a dual-mode security layer supporting both Thymeleaf (session/cookie) and REST API (JWT) clients, and a fully automated CI/CD pipeline via GitHub Actions and Docker.

---

## Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| Language | Java 17 | Core application language |
| Framework | Spring Boot 3.3.4 | Auto-configuration, embedded Tomcat, starter ecosystem |
| Web Layer | Spring MVC + Thymeleaf | Server-rendered HTML dashboards for browser UI |
| REST API | Spring MVC (@RestController) | JSON API endpoints for mobile/Postman/React clients |
| Security | Spring Security 6 | Role-based access control, form login, JWT filter chain |
| Auth Token | JWT (JJWT 0.12.6) | Stateless authentication for REST API consumers |
| Persistence | Spring Data JPA + Hibernate | ORM, entity management, JPQL fetch-join queries |
| Database | MySQL 8 | Primary relational data store |
| Messaging | Apache Kafka | Event-driven email pipeline — decouples complaint events from email delivery |
| Cache + Session | Redis 7 | Dashboard stats caching (@Cacheable), HTTP session storage (Spring Session) |
| Email | Spring JavaMailSender + Gmail SMTP | Transactional email notifications sent asynchronously via Kafka consumer |
| Containerisation | Docker + Docker Compose | Full-stack local setup: app + MySQL + Kafka + Zookeeper + Redis in one command |
| CI/CD | GitHub Actions | Automated build → test → Docker image push → Render deploy on every git push |
| Build Tool | Maven (with wrapper) | Dependency management and packaging |

---

## Architecture Overview

```
Browser / Postman
      │
      ├─── Thymeleaf UI (/resident/**, /admin/**, /worker/**)
      │         │ Spring Security (Form Login + Session → Redis)
      │
      └─── REST API (/api/**)
                │ Spring Security (JWT Filter Chain — stateless)

                        │
              Spring MVC Controllers
                        │
              Service Layer (@Transactional)
                        │
           ┌────────────┼──────────────┐
           │            │              │
        MySQL       Kafka Topic    Redis Cache
      (JPA/ORM)  complaint-notif  dashboard-stats
                        │              │
               EmailNotificationConsumer
                        │
                  Gmail SMTP
```

**Flow when a resident raises a complaint:**
1. Form POST → `ResidentController` → `ComplaintServiceImpl.createComplaint()`
2. Complaint saved to MySQL → SLA deadline computed by `@PrePersist`
3. `ComplaintEventProducer` publishes a `ComplaintEvent` to Kafka topic `complaint-notifications`
4. `EmailNotificationConsumer` picks up the event → sends email via Gmail SMTP
5. `DashboardStatsService` cache evicted → next admin dashboard load refreshes from DB

---

## Roles and Permissions

| Role | What they can do |
|---|---|
| **RESIDENT** | Register, raise complaints, view own complaints, reopen resolved complaints |
| **WORKER** | View assigned complaints, update status to In Progress / Resolved |
| **ADMIN** | View all complaints, assign workers, update any status, view overdue (SLA breached) complaints, create additional admin accounts |

**Admin provisioning:** The first admin is seeded automatically on first startup via `DataSeeder` (a `CommandLineRunner`). No admin account can be created through public self-registration — a common security gap in similar projects. Additional admins are created only by an existing logged-in admin from the Admin panel.

---

## Key Features

- **SLA tracking:** Each priority level (`CRITICAL`=4hr, `HIGH`=24hr, `MEDIUM`=48hr, `LOW`=72hr) sets a deadline at complaint creation via `@PrePersist`. A `@Scheduled` job runs every 5 minutes to flag breached complaints and escalate to admins via Kafka.
- **Full audit trail:** Every status change creates a `ComplaintStatusHistory` row — old status, new status, changed by, timestamp — displayed as a timeline on every complaint detail page.
- **Event-driven emails:** `ComplaintServiceImpl` publishes to Kafka instead of calling email code directly. This means the HTTP response returns immediately, email is delivered asynchronously, and a failed email never crashes the main complaint-saving flow.
- **Redis session store:** HTTP sessions are stored in Redis via `@EnableRedisHttpSession`. Users stay logged in across app restarts. Sessions shared across multiple app instances if scaled horizontally.
- **Redis dashboard caching:** Admin dashboard counts (Total, Open, Assigned, In Progress, Resolved, Overdue) are cached in Redis with a 5-minute TTL via `@Cacheable`. Cache is evicted via `@CacheEvict` whenever a complaint status changes.
- **Dual security filter chains:** `/api/**` uses a stateless JWT chain (no session created). All other URLs use a stateful form-login chain with CSRF enabled. Both run from the same application.
- **Object-level authorization:** Beyond URL-level role checks, controllers explicitly verify ownership — a resident can only view their own complaints, a worker can only update complaints assigned to them.
- **Containerised full stack:** One `docker compose up --build` starts all five services (app, MySQL, Kafka, Zookeeper, Redis) with health checks ensuring correct startup order.

---

## Project Structure

```
src/main/java/com/society/complaint/
├── api/                        # REST API layer (JWT auth, JSON responses)
│   ├── AuthApiController.java  # POST /api/auth/login → returns JWT token
│   ├── ComplaintApiController.java  # GET/POST/PUT /api/complaints/**
│   ├── JwtAuthFilter.java      # Validates Bearer token on every /api/** request
│   └── JwtService.java         # Generates and validates JWT tokens
├── config/                     # Spring configuration classes
│   ├── AsyncConfig.java        # Custom @Async exception handler (never silent failures)
│   ├── DataSeeder.java         # Seeds default admin on first startup
│   ├── KafkaConfig.java        # Producer/consumer beans with JSON serialisation
│   ├── RedisConfig.java        # Redis connection + @Cacheable cache manager (5 min TTL)
│   ├── SecurityConfig.java     # Two filter chains: JWT (/api/**) + form login (UI)
│   ├── CustomUserDetails.java  # Wraps User entity for Spring Security
│   ├── CustomUserDetailsService.java  # Loads user by email for auth
│   └── RoleBasedAuthSuccessHandler.java  # Redirects each role to its own dashboard
├── controller/                 # Thymeleaf MVC controllers
│   ├── AuthController.java     # /login, /register
│   ├── AdminController.java    # /admin/** (dashboard, assign, status, manage admins)
│   ├── ResidentController.java # /resident/** (dashboard, raise, view, reopen)
│   └── WorkerController.java   # /worker/** (dashboard, view, update status)
├── dto/                        # Form-binding objects (never bind forms to entities)
│   ├── RegisterDto.java        # Public registration form
│   ├── AdminCreateDto.java     # Admin-only create admin form
│   └── ComplaintDto.java       # Raise complaint form
├── entity/                     # JPA entities (database tables)
│   ├── User.java               # All three roles — RESIDENT, WORKER, ADMIN
│   ├── Complaint.java          # Core entity — lifecycle, SLA, relationships
│   └── ComplaintStatusHistory.java  # Audit trail — one row per status change
├── enums/                      # Domain enumerations
│   ├── Role.java               # ADMIN, RESIDENT, WORKER
│   ├── ComplaintStatus.java    # OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED, REOPENED
│   ├── Priority.java           # LOW/MEDIUM/HIGH/CRITICAL — each carries SLA hours
│   └── ComplaintCategory.java  # PLUMBING, ELECTRICAL, CLEANING, etc.
├── kafka/                      # Kafka event pipeline
│   ├── ComplaintEvent.java     # Message payload — serialised to JSON for the topic
│   ├── ComplaintEventProducer.java  # Publishes events to complaint-notifications topic
│   └── EmailNotificationConsumer.java  # Listens to topic, sends email per event type
├── repository/                 # Spring Data JPA repositories
│   ├── UserRepository.java     # findByEmail, findByRole
│   ├── ComplaintRepository.java # JOIN FETCH queries, SLA scheduler query
│   └── ComplaintStatusHistoryRepository.java
├── scheduler/
│   └── SlaScheduler.java       # @Scheduled every 5 min — flags overdue, fires escalation
└── service/
    ├── ComplaintService.java   # Interface
    ├── UserService.java        # Interface
    ├── DashboardStatsService.java  # @Cacheable Redis stats for admin dashboard
    └── impl/
        ├── ComplaintServiceImpl.java  # Core business logic — publishes Kafka events
        └── UserServiceImpl.java       # Registration, admin creation, BCrypt hashing

src/main/resources/
├── templates/                  # Thymeleaf HTML views
│   ├── auth/                   # login.html, register.html
│   ├── admin/                  # dashboard, complaint-detail, complaint-list, workers, residents, admins, new-admin
│   ├── resident/               # dashboard, new-complaint, complaint-detail
│   ├── worker/                 # dashboard, complaint-detail
│   └── fragments/              # sidebar.html (role-aware nav), badges.html (status/priority badges)
├── static/css/style.css        # Custom design system — sidebar shell, color tokens, badges, tables
└── application.properties      # All secrets read from environment variables

.github/workflows/ci-cd.yml    # GitHub Actions — build → test → Docker push → Render deploy
Dockerfile                     # Multi-stage build: JDK compile stage + JRE runtime stage
docker-compose.yml             # Full stack: app + MySQL + Kafka + Zookeeper + Redis
```

---

## Running Locally with Docker (Recommended)

### Prerequisites
- Docker Desktop installed and running
- Git

### Steps

**1. Clone the repository**
```bash
git clone https://github.com/YOUR_USERNAME/society-complaint-system.git
cd society-complaint-system
```

**2. Configure credentials**

Open `docker-compose.yml` and fill in your real Gmail credentials:
```yaml
SPRING_MAIL_USERNAME: your-gmail@gmail.com
SPRING_MAIL_PASSWORD: your16charapppassword   # no spaces
APP_DEFAULT_ADMIN_EMAIL: your-admin-email@gmail.com
APP_DEFAULT_ADMIN_PASSWORD: YourPassword123
```

> Gmail App Password: Google Account → Security → 2-Step Verification → App Passwords → Create

**3. Start the full stack**
```bash
docker compose up --build
```

Wait for:
```
society-app | Started ComplaintManagementSystemApplication in X seconds
society-app | partitions assigned: [complaint-notifications-0]
```

**4. Open the app**

Browser: `http://localhost:8081`

Login with the admin credentials you set in step 2.

**Useful commands:**
```bash
docker compose up -d              # run in background
docker compose logs -f app        # watch app logs
docker compose down               # stop (data preserved)
docker compose down -v            # stop + wipe all data
docker compose up -d --build app  # rebuild only the app after code changes
```

---

## Running Locally without Docker (IntelliJ)

**Prerequisites:** Java 17, Maven, MySQL 8 running locally

**1.** Create database:
```sql
CREATE DATABASE society_complaint_db;
```

**2.** Copy `application.properties.example` to `application.properties` and fill in your MySQL password and Gmail App Password.

**3.** Run `ComplaintManagementSystemApplication.java` from IntelliJ.

App starts on `http://localhost:8080`.

---

## REST API Usage

### Login (get a JWT token)
```
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{ "email": "admin@society.com", "password": "Admin@123" }
```
Response:
```json
{ "token": "eyJhbGci...", "email": "admin@society.com", "role": "ADMIN" }
```

### Use the token
Add to every subsequent request:
```
Authorization: Bearer eyJhbGci...
```

### Endpoints
| Method | URL | Role | Description |
|---|---|---|---|
| POST | /api/auth/login | Public | Get JWT token |
| GET | /api/complaints | ADMIN | All complaints |
| GET | /api/complaints/mine | RESIDENT | Own complaints |
| GET | /api/complaints/assigned | WORKER | Assigned complaints |
| GET | /api/complaints/overdue | ADMIN | SLA breached complaints |
| POST | /api/complaints | RESIDENT | Raise a complaint |
| PUT | /api/complaints/{id}/status | ADMIN, WORKER | Update status |
| PUT | /api/complaints/{id}/assign/{workerId} | ADMIN | Assign worker |

---

## CI/CD Pipeline (GitHub Actions)

Every `git push` to `main` triggers three automatic jobs:

```
Push to main
     │
     ▼
[Job 1] Build & Test          ← runs on every push AND pull request
     │  mvn clean install
     │  mvn test
     │  (fails here = nothing deploys)
     │
     ▼
[Job 2] Docker Build & Push   ← only on push to main
     │  docker build
     │  push to Docker Hub (tagged :latest + :commit-sha)
     │
     ▼
[Job 3] Deploy to Render      ← triggers Render deploy webhook
        new version live in ~2 min
```

**GitHub Secrets required:**
| Secret | Value |
|---|---|
| `DOCKER_USERNAME` | Your Docker Hub username |
| `DOCKER_PASSWORD` | Docker Hub access token |
| `RENDER_DEPLOY_HOOK_URL` | From Render dashboard → your service → Settings → Deploy Hook |

---

## Bugs Fixed During Development (good interview talking points)

| Bug | Root Cause | Fix |
|---|---|---|
| `StackOverflowError` on login | Lombok `@Data` on bidirectional JPA entities generates `toString()` that recurses infinitely between `Complaint` ↔ `ComplaintStatusHistory` | Replaced `@Data` with `@Getter @Setter @EqualsAndHashCode(of="id") @ToString(exclude={...})` |
| `LazyInitializationException` on complaint view | `open-in-view=false` closes Hibernate session before Thymeleaf renders; templates access lazy fields after session closed | Added `LEFT JOIN FETCH` queries in repository — loads all needed data eagerly while session is still open |
| Status/priority badges not rendering | Thymeleaf `th:switch="${priority}"` compares enum to string literal — `Priority.LOW.equals("LOW")` is always `false` | Changed to `th:switch="${priority?.name()}"` to compare String-to-String |
| `NotSerializableException` on Redis session save | Spring Session serializes the entire session to Redis using Java serialization; `User` entity didn't implement `Serializable` | Added `implements Serializable` to `User`, `CustomUserDetails`, `Complaint`, `ComplaintStatusHistory` |
| Email crash taking down Kafka consumer | `sendEmail()` was rethrowing SMTP exceptions, which propagated out of the Kafka listener and crashed the consumer thread | Removed `throw e` — email failures now log and continue; complaint is already saved, email is a side effect |
| Kafka consumer connecting to `localhost:9092` inside Docker | `KafkaConfig` read `${KAFKA_BOOTSTRAP_SERVERS}` but Docker compose set `SPRING_KAFKA_BOOTSTRAP_SERVERS` — different variable names | Changed `@Value` to read `${spring.kafka.bootstrap-servers}` which Spring Boot's relaxed binding maps correctly from the env var |

---

## Possible Enhancements (mention if asked "what would you add next?")

- Pagination on complaint list pages (currently loads all rows)
- File/photo upload on complaints (MultipartFile → S3/local storage)
- Resident rating/feedback after resolution
- Admin analytics dashboard (complaints by category, avg resolution time per worker)
- Optimistic locking (`@Version`) to handle concurrent admin assignments
- Flyway for database migration versioning instead of `ddl-auto=update`
