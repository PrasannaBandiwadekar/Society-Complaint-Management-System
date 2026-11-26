# Society Complaint Management System

🔗 **Live Demo:** https://society-complaint-management-system.onrender.com
> Free-tier hosting on Render. If the app has been idle, allow 30–60 seconds for it to wake up.

A full-stack maintenance complaint tracking platform for apartment societies. Residents raise complaints, admins assign technicians, and workers resolve them. The system tracks SLA deadlines automatically, sends email notifications at every stage, keeps a complete audit trail, exposes a JWT-secured REST API, and deploys through an automated CI/CD pipeline.

---

## Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| Language | Java 17 | Core application language |
| Framework | Spring Boot 3.3.4 | Auto-configuration, embedded Tomcat, starter ecosystem |
| Web layer | Spring MVC + Thymeleaf | Server-rendered dashboards for the browser UI |
| REST API | Spring MVC `@RestController` | JSON endpoints for Postman, mobile, or React clients |
| Security | Spring Security 6 | Role-based and object-level access control, form login, JWT filter chain |
| Auth token | JWT (JJWT 0.12.6) | Stateless authentication for REST API clients |
| Persistence | Spring Data JPA + Hibernate | ORM, entity mapping, JPQL `JOIN FETCH` queries |
| Database (production) | PostgreSQL on Aiven (free tier) | Cloud-hosted relational store |
| Database (local Docker) | MySQL 8 | Relational store in the local Docker stack |
| Messaging (local Docker) | Apache Kafka | Event-driven email pipeline, decouples complaint events from email delivery |
| Email (production) | Brevo transactional email API | Notification emails sent over HTTPS (free plan: 300 emails/day) |
| Email (local Docker) | Spring JavaMailSender + Gmail SMTP | Sent by the Kafka consumer |
| Containers | Docker + Docker Compose | Local full stack: app, MySQL, Kafka, Zookeeper |
| CI/CD | GitHub Actions | Build, test, Docker image push, Render deploy on every push |
| Hosting | Render (free tier) | Live deployment |
| Build tool | Maven (with wrapper) | Dependencies and packaging |

> **Environment note:** Kafka only runs in the local Docker setup. Production uses the `prod` Spring profile, where Kafka is switched off and `ComplaintServiceImpl` calls the email service directly. Redis was removed from the application.

---

## Architecture

### Production (Render + Aiven)
```
Browser / Postman
      │
      ├── Thymeleaf UI (/resident/**, /admin/**, /worker/**)
      │       └── Spring Security: form login + in-memory session
      │
      └── REST API (/api/**)
              └── Spring Security: stateless JWT filter chain

                    Spring MVC controllers
                            │
                  Service layer (@Transactional)
                            │
                  ┌─────────┴──────────┐
                  │                    │
        PostgreSQL (Aiven)       Brevo email API
           (JPA / Hibernate)     (direct, over HTTPS)
```

### Local Docker (full stack)
```
Browser / Postman
      │
      ├── Thymeleaf UI
      └── REST API (/api/**)
                            │
                  Spring MVC controllers
                            │
                  Service layer (@Transactional)
                            │
              ┌─────────────┴──────────────┐
              │                            │
         MySQL (JPA)               Kafka topic
                                   complaint-notifications
                                           │
                               EmailNotificationConsumer
                                           │
                                     Gmail SMTP
```

**What happens when a resident raises a complaint**
1. Form POST → `ResidentController` → `ComplaintServiceImpl.createComplaint()`
2. The complaint is saved and `@PrePersist` computes the SLA deadline.
3. A status-history row records the initial state.
4. Local Docker: `ComplaintEventProducer` publishes an event to Kafka and `EmailNotificationConsumer` sends the email. Production: the service calls `BrevoEmailService` directly.
5. The user is redirected to the dashboard with a confirmation message.

---

## Roles and Permissions

| Role | Capabilities |
|---|---|
| **RESIDENT** | Register, raise complaints, view own complaints, reopen resolved complaints |
| **WORKER** | View assigned complaints, update status to In Progress or Resolved |
| **ADMIN** | View all complaints, assign workers, update any status, view overdue complaints, create more admin accounts |

**Admin provisioning:** the first admin is seeded on startup by `DataSeeder` (a `CommandLineRunner`) from environment variables. Admin accounts cannot be self-registered; extra admins are created by an existing logged-in admin.

---

## Key Features

- **SLA tracking:** `CRITICAL`=4h, `HIGH`=24h, `MEDIUM`=48h, `LOW`=72h. `@PrePersist` computes the deadline at creation and a `@Scheduled` job runs every 5 minutes to flag breached complaints and escalate to admins.
- **Audit trail:** every status change inserts a `ComplaintStatusHistory` row (old status, new status, who, when, remarks), shown as a timeline on each complaint.
- **Resilient email:** a failed email is logged and never breaks the complaint workflow. Locally, Kafka decouples delivery from the request; in production, emails go out through an HTTPS API call. If the provider is not configured, sending is skipped and logged.
- **Two security chains:** `/api/**` uses stateless JWT; everything else uses form login with CSRF protection.
- **Object-level authorization:** controllers verify ownership, so Resident A cannot open Resident B's complaint by guessing a URL.
- **Containerised local stack:** `docker compose up --build` starts the app, MySQL, Kafka and Zookeeper with health checks controlling start order.
- **Automated pipeline:** a push to `main` triggers build, tests, a Docker image push and a Render deploy.

---

## Project Structure

```
src/main/java/com/society/complaint/
├── api/                  # REST layer: AuthApiController, ComplaintApiController, JwtAuthFilter, JwtService
├── config/               # SecurityConfig, KafkaConfig (!prod), DataSeeder, AsyncConfig,
│                         # CustomUserDetails(+Service), RoleBasedAuthSuccessHandler
├── controller/           # AuthController, AdminController, ResidentController, WorkerController
├── dto/                  # RegisterDto, AdminCreateDto, ComplaintDto
├── entity/               # User, Complaint, ComplaintStatusHistory
├── enums/                # Role, ComplaintStatus, Priority (carries SLA hours), ComplaintCategory
├── kafka/                # ComplaintEvent, ComplaintEventProducer, EmailNotificationConsumer (all !prod)
├── repository/           # UserRepository, ComplaintRepository (JOIN FETCH queries), history repository
├── scheduler/            # SlaScheduler
└── service/
    ├── ComplaintService, UserService, EmailService, DashboardStatsService
    └── impl/             # ComplaintServiceImpl, UserServiceImpl,
                          # BrevoEmailService (the single EmailService implementation)

src/main/resources/
├── templates/            # auth/, admin/, resident/, worker/, fragments/
├── static/css/style.css  # custom design system
├── application.properties
└── application-prod.properties   # production profile (Kafka off)

.github/workflows/ci-cd.yml
Dockerfile                # multi-stage: JDK build, JRE runtime
docker-compose.yml        # app + MySQL + Kafka + Zookeeper
```

---

## Running Locally

### Option A: Docker (full stack with Kafka)

Prerequisites: Docker Desktop running, Git.

```bash
git clone https://github.com/PrasannaBandiwadekar/Society-Complaint-Management-System.git
cd Society-Complaint-Management-System
cp .env.example .env      # then fill in your values
docker compose up --build
```

Example `.env` (use your own values; never commit this file):
```
MYSQL_ROOT_PASSWORD=choose_a_password
SPRING_MAIL_USERNAME=your-gmail@gmail.com
SPRING_MAIL_PASSWORD=your16charapppassword
APP_DEFAULT_ADMIN_EMAIL=admin@society.com
APP_DEFAULT_ADMIN_PASSWORD=choose_a_strong_password
APP_JWT_SECRET=a-long-random-string-of-at-least-32-characters
```

Open `http://localhost:8081` once the log shows `Started ComplaintManagementSystemApplication`.

### Option B: Maven without Kafka

Requires Java 17, Maven and a local MySQL database named `society_complaint_db`. Copy `application.properties.example` to `application.properties` and fill in your values.

```powershell
.\mvnw spring-boot:run "-Dspring-boot.run.profiles=prod"
```

The `prod` profile switches Kafka off. Without Brevo settings, emails are skipped and logged. The app starts on the port printed in the log (8082 or 8080). Run it from the folder that contains `pom.xml`; running a single file with `javac` will not work.

---

## Deployment (Render + Aiven PostgreSQL + Brevo)

1. Create a **PostgreSQL** service on Aiven (free plan) and note the host, port, user and password.
2. Create a Brevo account, add and verify a sender email address, and generate an API key.
3. Create a Render **Web Service** from the GitHub repo with runtime **Docker**.
4. Set these environment variables on Render:

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://HOST:PORT/defaultdb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `avnadmin` |
| `SPRING_DATASOURCE_PASSWORD` | database password from Aiven |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | `org.postgresql.Driver` |
| `BREVO_API_KEY` | Brevo API key |
| `BREVO_FROM_EMAIL` | sender address verified in Brevo |
| `BREVO_FROM_NAME` | optional display name |
| `APP_MAIL_ENABLED` | `true` |
| `APP_DEFAULT_ADMIN_EMAIL` | admin email |
| `APP_DEFAULT_ADMIN_PASSWORD` | admin password |
| `APP_JWT_SECRET` | long random string (32+ characters) |
| `SERVER_PORT` | `8080` |

> **Use a JDBC URL.** Aiven shows a `postgres://user:password@host...` service URI. Do not paste that into `SPRING_DATASOURCE_URL`; Java needs the `jdbc:postgresql://` form, with username and password in their own variables.

Hibernate (`ddl-auto=update`) creates the tables on first start and `DataSeeder` creates the admin account.

### Free-tier caveats
- Render's free web service sleeps after about 15 minutes of inactivity.
- Aiven can power off free services that see no continued activity; power the service back on from the Aiven console if the app cannot reach its database.
- Aiven's free MySQL plan is being retired (services powered off and data deleted on December 21, 2026), which is why production runs on Aiven PostgreSQL.
- Brevo's free plan allows 300 emails per day. Because the sender is a personal address rather than an own domain, messages may land in the spam folder.
- SendGrid was used earlier, but it no longer offers a permanent free plan, so production moved to Brevo.

---

## CI/CD Pipeline

```
git push to main
     │
     ▼
[Job 1] Build & Test         every push and pull request
     ▼
[Job 2] Docker Build & Push  main branch only
     ▼
[Job 3] Deploy to Render     deploy-hook webhook
```

Required GitHub secrets: `DOCKER_USERNAME`, `DOCKER_PASSWORD` (Docker Hub access token), `RENDER_DEPLOY_HOOK_URL`.

---

## REST API

```
POST /api/auth/login
{ "email": "admin@society.com", "password": "..." }
→ { "token": "eyJ...", "email": "...", "role": "ADMIN" }
```
Send `Authorization: Bearer <token>` on every other request.

| Method | URL | Role | Description |
|---|---|---|---|
| POST | /api/auth/login | Public | Get a JWT |
| GET | /api/complaints | ADMIN | All complaints |
| GET | /api/complaints/mine | RESIDENT | Own complaints |
| GET | /api/complaints/assigned | WORKER | Assigned complaints |
| GET | /api/complaints/overdue | ADMIN | SLA-breached complaints |
| POST | /api/complaints | RESIDENT | Raise a complaint |
| PUT | /api/complaints/{id}/status | ADMIN, WORKER | Update status |
| PUT | /api/complaints/{id}/assign/{workerId} | ADMIN | Assign a worker |

---

## Problems Solved During Development

| Problem | Root cause | Fix |
|---|---|---|
| `StackOverflowError` on login | Lombok `@Data` on bidirectional JPA entities creates a recursive `toString()` | `@Getter @Setter @EqualsAndHashCode(of="id") @ToString(exclude={...})` |
| `LazyInitializationException` on detail pages | `open-in-view=false` closes the session before Thymeleaf reads lazy fields | `LEFT JOIN FETCH` queries in the repository |
| Badges not rendering | `th:switch` compared an enum to a string literal | `th:switch="${priority?.name()}"` |
| Kafka consumer connecting to `localhost` in Docker | `@Value` read a different variable name than the one Docker set | Read `spring.kafka.bootstrap-servers`, which Spring maps from `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| Email failure crashed the Kafka consumer | `sendEmail()` rethrew SMTP exceptions out of the listener | Catch and log; never rethrow |
| Redis session crash on Render | `spring-session-data-redis` auto-configures a session filter even when disabled by property | Removed the Redis dependencies |
| Email blocked on Render | Free tier blocks outbound SMTP | Switched production email to an HTTPS email API |
| SendGrid returned `401 Maximum credits exceeded` | SendGrid has no permanent free plan; the trial limit was reached | Migrated production email to Brevo (300 emails/day free) |
| Startup crash without an email key locally | `@Value` placeholder had no default | Default empty value; skip sending and log when not configured |
| Deployed app could not reach its database | Aiven free MySQL service was gone or powered off | Migrated production to Aiven PostgreSQL |
| `Driver org.postgresql.Driver claims to not accept jdbcUrl` | Service URI (`postgres://...`) used instead of a JDBC URL | Use `jdbc:postgresql://host:port/db?sslmode=require` with separate user and password variables |

---

## Possible Enhancements

- Pagination on complaint lists (currently loads all rows)
- Photo upload on complaints
- Resident rating after resolution
- Admin analytics dashboard (complaints by category, average resolution time per worker)
- Optimistic locking (`@Version`) for concurrent admin assignment
- Flyway migrations instead of `ddl-auto=update`
- Own domain for the email sender, plus keep-alive or paid hosting so the app and database do not sleep
