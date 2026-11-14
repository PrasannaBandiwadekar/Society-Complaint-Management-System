# Society Complaint Management System

🔗 **Live Demo:** https://society-complaint-management-system.onrender.com
> Free-tier hosting on Render — allow 30-60 seconds to wake up if idle.

A full-stack maintenance complaint tracking platform for apartment societies. Residents raise complaints, admins assign technicians, workers resolve them — with automated SLA deadline tracking, event-driven email notifications, complete audit trails, a REST API with JWT authentication, and a fully automated CI/CD pipeline.

---

## Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| Language | Java 17 | Core application language |
| Framework | Spring Boot 3.3.4 | Auto-configuration, embedded Tomcat, starter ecosystem |
| Web Layer | Spring MVC + Thymeleaf | Server-rendered HTML dashboards for browser UI |
| REST API | Spring MVC @RestController | JSON endpoints for Postman / mobile / React clients |
| Security | Spring Security 6 | Role-based + object-level access control, form login, JWT filter chain |
| Auth Token | JWT (JJWT 0.12.6) | Stateless authentication for REST API consumers |
| Persistence | Spring Data JPA + Hibernate | ORM, entity management, JPQL JOIN FETCH queries |
| Database | MySQL 8 (Aiven — cloud hosted) | Primary relational data store |
| Messaging | Apache Kafka | Event-driven email pipeline (local Docker only) |
| Email | SendGrid API | Transactional email notifications (production) |
| Containerisation | Docker + Docker Compose | Full-stack local setup: app + MySQL + Kafka + Zookeeper + Redis |
| CI/CD | GitHub Actions | Automated build → test → Docker image push → Render deploy |
| Deployment | Render (free tier) | Live cloud hosting |
| Build Tool | Maven (with wrapper) | Dependency management and packaging |

---

## Architecture

### Production (Render + Aiven)
```
Browser / Postman
      │
      ├─── Thymeleaf UI (/resident/**, /admin/**, /worker/**)
      │         │ Spring Security (Form Login + In-Memory Session)
      │
      └─── REST API (/api/**)
                │ Spring Security (JWT Filter Chain — stateless)
                        │
              Spring MVC Controllers
                        │
              Service Layer (@Transactional)
                        │
                ┌───────┴────────┐
                │                │
           MySQL (Aiven)   SendGrid API
           (JPA/ORM)       (direct email)
```

### Local Docker (Full Stack)
```
Browser / Postman
      │
      ├─── Thymeleaf UI
      └─── REST API (/api/**)
                        │
              Spring MVC Controllers
                        │
              Service Layer (@Transactional)
                        │
      ┌─────────────────┼──────────────────┐
      │                 │                  │
   MySQL           Kafka Topic          Redis Cache
 (JPA/ORM)    complaint-notifications  dashboard-stats
                        │
            EmailNotificationConsumer
                        │
                  Gmail SMTP
```

**Flow when a resident raises a complaint:**
1. Form POST → `ResidentController` → `ComplaintServiceImpl.createComplaint()`
2. Complaint saved to MySQL → SLA deadline computed by `@PrePersist`
3. In local Docker: `ComplaintEventProducer` publishes event to Kafka → `EmailNotificationConsumer` sends email
4. In production: `ComplaintServiceImpl` calls `SendGridEmailService` directly
5. `DashboardStatsService` cache evicted → next admin dashboard load refreshes from DB

---

## Roles and Permissions

| Role | What they can do |
|---|---|
| **RESIDENT** | Register, raise complaints, view own complaints, reopen resolved complaints |
| **WORKER** | View assigned complaints, update status to In Progress / Resolved |
| **ADMIN** | View all complaints, assign workers, update any status, view overdue complaints, create additional admin accounts |

**Admin provisioning:** First admin is seeded automatically on startup via `DataSeeder` (`CommandLineRunner`). No admin can be created through public self-registration — a common security gap in similar projects. Additional admins are created only by an existing logged-in admin.

---

## Key Features

- **SLA tracking:** `CRITICAL`=4hr, `HIGH`=24hr, `MEDIUM`=48hr, `LOW`=72hr. `@PrePersist` computes deadline at creation. `@Scheduled` job runs every 5 minutes to flag breached complaints and escalate to admins.
- **Full audit trail:** Every status change creates a `ComplaintStatusHistory` row — old status, new status, who changed it, when, remarks — displayed as a timeline on every detail page.
- **Event-driven emails (local):** Kafka decouples email from complaint saving — HTTP response returns immediately, failed email never crashes the complaint flow.
- **Direct emails (production):** SendGrid API used in prod profile, bypassing SMTP port restrictions on Render's free tier.
- **Dual security filter chains:** `/api/**` uses stateless JWT chain. All other URLs use stateful form-login chain with CSRF enabled. Both from the same application.
- **Object-level authorization:** Beyond URL role checks, controllers verify ownership — Resident A cannot access Resident B's complaint even by guessing the URL.
- **Dashboard caching:** Admin dashboard counts cached with `@Cacheable` (5-minute TTL) in local Docker with Redis. In prod, runs fresh queries (Redis not available on free tier).
- **Containerised full stack:** `docker compose up --build` starts all five services (app, MySQL, Kafka, Zookeeper, Redis) with health checks ensuring correct startup order.
- **Automated CI/CD:** Every `git push` to `main` triggers build → test → Docker image push → Render deploy automatically.

---

## Project Structure

```
src/main/java/com/society/complaint/
├── api/                        # REST API layer
│   ├── AuthApiController.java  # POST /api/auth/login → returns JWT
│   ├── ComplaintApiController.java  # GET/POST/PUT /api/complaints/**
│   ├── JwtAuthFilter.java      # Validates Bearer token on /api/** requests
│   └── JwtService.java         # Generates and validates JWT tokens
├── config/
│   ├── AsyncConfig.java        # Custom @Async exception handler
│   ├── DataSeeder.java         # Seeds default admin on first startup
│   ├── KafkaConfig.java        # Kafka producer/consumer (local only, @Profile("!prod"))
│   ├── MailConfig.java         # Explicit JavaMailSender bean
│   ├── SecurityConfig.java     # Two filter chains: JWT (/api/**) + form login (UI)
│   ├── CustomUserDetails.java  # Wraps User entity for Spring Security
│   ├── CustomUserDetailsService.java
│   └── RoleBasedAuthSuccessHandler.java  # Redirects each role to its dashboard
├── controller/
│   ├── AuthController.java     # /login, /register
│   ├── AdminController.java    # /admin/**
│   ├── ResidentController.java # /resident/**
│   └── WorkerController.java   # /worker/**
├── dto/
│   ├── RegisterDto.java
│   ├── AdminCreateDto.java
│   └── ComplaintDto.java
├── entity/
│   ├── User.java               # RESIDENT, WORKER, ADMIN — one table, role column
│   ├── Complaint.java          # Core entity — lifecycle, SLA, relationships
│   └── ComplaintStatusHistory.java  # Audit trail
├── enums/
│   ├── Role.java
│   ├── ComplaintStatus.java    # OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED, REOPENED
│   ├── Priority.java           # LOW/MEDIUM/HIGH/CRITICAL — each carries SLA hours
│   └── ComplaintCategory.java
├── kafka/                      # Active in local Docker only (@Profile("!prod"))
│   ├── ComplaintEvent.java     # Kafka message payload
│   ├── ComplaintEventProducer.java
│   └── EmailNotificationConsumer.java
├── repository/
│   ├── UserRepository.java
│   ├── ComplaintRepository.java  # JOIN FETCH queries, SLA scheduler query
│   └── ComplaintStatusHistoryRepository.java
├── scheduler/
│   └── SlaScheduler.java       # @Scheduled every 5 min
└── service/
    ├── ComplaintService.java
    ├── UserService.java
    ├── EmailService.java
    ├── DashboardStatsService.java
    └── impl/
        ├── ComplaintServiceImpl.java   # publishOrSendDirectly() — Kafka in local, direct in prod
        ├── UserServiceImpl.java
        ├── SendGridEmailService.java   # Production email via SendGrid API
        ├── EmailServiceImpl.java       # Local email via Gmail SMTP
        └── NoOpEmailService.java       # Fallback when mail not configured

src/main/resources/
├── templates/
│   ├── auth/                   # login.html, register.html
│   ├── admin/                  # dashboard, complaint-detail, complaint-list,
│   │                           # workers, residents, admins, new-admin
│   ├── resident/               # dashboard, new-complaint, complaint-detail
│   ├── worker/                 # dashboard, complaint-detail
│   └── fragments/              # sidebar.html, badges.html
├── static/css/style.css        # Custom design system — no Bootstrap dependency
├── application.properties      # Base config — all secrets from env vars
└── application-prod.properties # Production overrides — disables Kafka

.github/workflows/ci-cd.yml
Dockerfile                      # Multi-stage: JDK compile → JRE runtime
docker-compose.yml              # Full stack: app + MySQL + Kafka + Zookeeper + Redis
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

**2. Copy `.env.example` to `.env` and fill in your credentials**
```bash
cp .env.example .env
```
Edit `.env`:
```
MYSQL_ROOT_PASSWORD=root123
SPRING_MAIL_USERNAME=your-gmail@gmail.com
SPRING_MAIL_PASSWORD=your16charapppassword
APP_DEFAULT_ADMIN_EMAIL=admin@society.com
APP_DEFAULT_ADMIN_PASSWORD=Admin@123
APP_JWT_SECRET=YourLongSecretKeyAtLeast32Characters
```

> Gmail App Password: Google Account → Security → 2-Step Verification → App Passwords

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

`http://localhost:8081` — login with the admin credentials from your `.env`

**Useful commands:**
```bash
docker compose up -d              # run in background
docker compose logs -f app        # watch app logs
docker compose down               # stop, data preserved
docker compose down -v            # stop and wipe all data
docker compose up -d --build app  # rebuild only the app after code changes
```

---

## Running without Docker (IntelliJ)

**Prerequisites:** Java 17, Maven, MySQL 8

**1.** Create database:
```sql
CREATE DATABASE society_complaint_db;
```

**2.** Copy `application.properties.example` to `application.properties` and fill in real values.

**3.** Run `ComplaintManagementSystemApplication.java` from IntelliJ. App starts on `http://localhost:8080`.

---

## REST API

### Get a JWT token
```
POST /api/auth/login
Content-Type: application/json

{ "email": "admin@society.com", "password": "Admin@123" }
```
Response:
```json
{ "token": "eyJhbGci...", "email": "admin@society.com", "role": "ADMIN" }
```

### Use the token
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
| GET | /api/complaints/overdue | ADMIN | SLA breached |
| POST | /api/complaints | RESIDENT | Raise a complaint |
| PUT | /api/complaints/{id}/status | ADMIN, WORKER | Update status |
| PUT | /api/complaints/{id}/assign/{workerId} | ADMIN | Assign worker |

---

## CI/CD Pipeline

```
git push to main
     │
     ▼
[Job 1] Build & Test        ← every push + every pull request
     │  mvn clean install
     │  mvn test
     │  fail here = nothing deploys
     ▼
[Job 2] Docker Build & Push ← main branch only
     │  docker build
     │  push :latest + :commit-sha to Docker Hub
     ▼
[Job 3] Deploy to Render    ← triggers deploy webhook
        live in ~2 minutes
```

**Required GitHub Secrets:**
| Secret | Where to get it |
|---|---|
| `DOCKER_USERNAME` | Your Docker Hub username |
| `DOCKER_PASSWORD` | Docker Hub → Account Settings → Access Tokens |
| `RENDER_DEPLOY_HOOK_URL` | Render → your service → Settings → Deploy Hook |

---

## Bugs Fixed During Development

| Bug | Root Cause | Fix |
|---|---|---|
| `StackOverflowError` on login | Lombok `@Data` on bidirectional JPA entities generates `toString()` that recurses infinitely | Replaced with `@Getter @Setter @EqualsAndHashCode(of="id") @ToString(exclude={...})` |
| `LazyInitializationException` on complaint view | `open-in-view=false` closes Hibernate session before Thymeleaf renders lazy fields | Added `LEFT JOIN FETCH` queries — loads all needed data while session is open |
| Badges not rendering | `th:switch="${priority}"` compares enum to string — `Priority.LOW.equals("LOW")` is always `false` | Changed to `th:switch="${priority?.name()}"` for String-to-String comparison |
| `NotSerializableException` on session save | Spring Session serializes session to Redis; `User` didn't implement `Serializable` | Added `implements Serializable` to `User`, `CustomUserDetails`, `Complaint`, `ComplaintStatusHistory` |
| Kafka consumer crash on email failure | `sendEmail()` rethrew SMTP exceptions, propagating out of `@KafkaListener` and crashing the consumer thread | Removed `throw e` — email failures log and continue |
| Kafka consumer connecting to `localhost` inside Docker | `KafkaConfig` read `${KAFKA_BOOTSTRAP_SERVERS}` but compose set `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Changed `@Value` to `${spring.kafka.bootstrap-servers}` — Spring's relaxed binding maps it correctly |
| Redis session crashing on Render | `spring-session-data-redis` auto-configures `SessionRepositoryFilter` even when disabled via properties | Removed `spring-session-data-redis` from `pom.xml` entirely for prod |
| Email blocked on Render | Render free tier blocks outbound SMTP port 587 | Switched to SendGrid API (uses HTTPS port 443, never blocked) |
| Email not firing on status update in prod | `SendGridEmailService` not injecting as `EmailService` due to `@ConditionalOnBean` timing issue | Removed `@ConditionalOnBean`, added explicit `MailConfig` bean, used `@ConditionalOnMissingBean(EmailServiceImpl.class)` for fallback |

---

## Deployment (Render + Aiven)

### Required Render Environment Variables
| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://YOUR_AIVEN_HOST:PORT/defaultdb?useSSL=true&requireSSL=true&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `SPRING_DATASOURCE_USERNAME` | `avnadmin` |
| `SPRING_DATASOURCE_PASSWORD` | Aiven password |
| `SENDGRID_API_KEY` | SendGrid API key |
| `SENDGRID_FROM_EMAIL` | Verified sender email |
| `APP_MAIL_ENABLED` | `true` |
| `APP_DEFAULT_ADMIN_EMAIL` | Admin email |
| `APP_DEFAULT_ADMIN_PASSWORD` | Admin password |
| `APP_JWT_SECRET` | Long random string (32+ chars) |
| `SERVER_PORT` | `8080` |

---

## Possible Enhancements

- Pagination on complaint list pages (currently loads all rows)
- File/photo upload on complaints (MultipartFile → S3/local storage)
- Resident rating/feedback after resolution
- Admin analytics dashboard (complaints by category, avg resolution time per worker)
- Optimistic locking (`@Version`) to handle concurrent admin assignments
- Flyway for database migration versioning instead of `ddl-auto=update`
- Re-introduce Redis caching on a paid deployment tier
