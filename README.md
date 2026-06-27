# Society Complaint Management System

A full-stack Spring Boot MVC application for tracking apartment/society maintenance complaints, with role-based dashboards (Resident / Worker / Admin), SLA deadline tracking via a scheduled job, and email notifications on every status change.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3.4 (Spring MVC) |
| View Layer | Thymeleaf (server-rendered HTML) + a custom CSS design system (no Bootstrap dependency) |
| Persistence | Spring Data JPA (Hibernate) |
| Database | MySQL 8 |
| Security | Spring Security 6 (form login, role-based access control, BCrypt) |
| Scheduling | Spring `@Scheduled` (SLA breach detection) |
| Email | Spring `JavaMailSender` + Gmail SMTP, sent `@Async` |
| Build Tool | Maven |

---

## Prerequisites (install these first)

1. **JDK 17+** — check with `java -version`
2. **Maven 3.8+** — check with `mvn -version`
3. **MySQL 8** running locally (or accessible) — check with `mysql --version`
4. An IDE — IntelliJ IDEA (Community is fine) or VS Code with Java extensions

---

## Setup Steps

### 1. Create the MySQL database
You don't strictly need to pre-create it — `createDatabaseIfNotExist=true` in the JDBC URL will create it automatically the first time the app connects, **as long as the MySQL user has permission to create databases**. If you'd rather be explicit:

```sql
CREATE DATABASE society_complaint_db;
```

### 2. Configure `src/main/resources/application.properties`
Open the file and update these three things:

```properties
spring.datasource.password=YOUR_MYSQL_PASSWORD

spring.mail.username=your-email@gmail.com
spring.mail.password=YOUR_16_CHAR_APP_PASSWORD
```

### 3. Generate a Gmail App Password (required for email to work)
Gmail will **not** accept your normal account password for SMTP. You need an "App Password":

1. Go to your Google Account → **Security**
2. Turn on **2-Step Verification** (required before App Passwords are available)
3. Search for "**App Passwords**" in the Google Account search bar → open it
4. Create a new app password (name it e.g. "SpringBootApp")
5. Google gives you a 16-character code like `abcd efgh ijkl mnop` — copy it **without spaces**
6. Paste it into `spring.mail.password` in `application.properties`

If you don't want to set this up right now, just set `app.mail.enabled=false` in `application.properties` — the app will log emails to the console instead of sending them, and everything else still works.

### 4. Build and run

```bash
cd society-complaint-system
mvn clean install
mvn spring-boot:run
```

The app starts on **http://localhost:8080**

Hibernate will auto-create all tables (`ddl-auto=update`) on first run — no manual schema scripts needed.

---

## Fixes Applied After Initial Build (read this if you pulled an earlier copy)

If you're using a freshly downloaded copy of this project, these are already fixed. Documenting them here because they're good interview talking points about debugging real Spring/JPA/Hibernate issues:

**1. `StackOverflowError` on login / viewing a complaint**
`User`, `Complaint`, and `ComplaintStatusHistory` originally used Lombok's `@Data`, which generates `toString()`/`equals()`/`hashCode()` from *every* field. Since these entities reference each other bidirectionally (`Complaint` holds a `List<ComplaintStatusHistory>`, and each `ComplaintStatusHistory` holds a `Complaint` back-reference), calling `toString()` anywhere — including implicitly, e.g. when Spring Security logs the authenticated principal — recursed infinitely. **Fix:** replaced `@Data` with `@Getter @Setter @EqualsAndHashCode(of = "id") @ToString(exclude = {...})` on all three entities, scoping equality to the primary key (the JPA-recommended approach) and excluding relationship fields from `toString()`.

**2. `LazyInitializationException` when viewing a complaint**
`spring.jpa.open-in-view=false` closes the Hibernate session as soon as the controller method returns — but the Thymeleaf templates access `complaint.resident`, `complaint.worker`, and `complaint.statusHistory` *while rendering*, which happens after that. **Fix:** added a `findByIdWithDetails()` query in `ComplaintRepository` using `LEFT JOIN FETCH` to eagerly load everything the detail pages need in one query, and pointed `ComplaintService.getById()` at it. List queries (`findAllByOrderByCreatedAtDesc`, etc.) got the same fetch-join treatment since list pages also print `resident`/`worker` names.

**3. Public registration allowing ADMIN self-signup**
Originally the `/register` page let anyone pick `ADMIN` from a dropdown — a real security hole. **Fix:**
- Removed `ADMIN` from the public registration dropdown entirely
- Added a server-side check in `AuthController` that rejects `role=ADMIN` even if someone posts it directly (e.g. via curl), since hiding an option in the UI never substitutes for an actual check
- Added `DataSeeder`, which creates exactly one default admin account on first startup (configurable via `app.default-admin.email` / `app.default-admin.password` in `application.properties`) — this is how the *first* admin gets into the system
- Added an admin-only **"Add Admin"** screen (`/admin/admins`, `/admin/admins/new`) so an existing admin can create additional admin accounts — this is how you get *more than one* admin, safely, since that page is itself behind `hasRole('ADMIN')`

**Default admin login after first run:** `admin@society.com` / `Admin@123` (change these in `application.properties` before your first `mvn spring-boot:run`, or change the password after logging in).

**4. UI overhaul**
Replaced the plain Bootstrap-default look with a custom design system: a fixed sidebar (role-aware navigation), a navy/blue/white palette with semantic status colors, Inter typeface, and a consistent badge/table/card language across every page. No more default Bootstrap blue buttons and unstyled tables.

---

## How to Demo This (for interview / recording a walkthrough video)

1. On first startup, a default admin account is created automatically: `admin@society.com` / `Admin@123` (or whatever you set in `application.properties` under `app.default-admin.*`)
2. Go to `http://localhost:8080/register` and register **2 accounts**:
   - One as **RESIDENT** (give it a flat number, e.g. `A-204`)
   - One as **WORKER** (pick a specialization, e.g. Plumber)
3. Log in as the **Resident** → click "Raise complaint" → fill the form, pick **Priority: CRITICAL** (this gives a 4-hour SLA so you can demo the overdue flow quickly — or pick `LOW` if you just want to show the happy path)
4. Log out → log in as **Admin** (`admin@society.com` / `Admin@123`) → see the complaint on the dashboard → click "Manage" → assign it to your Worker account
5. Check the Resident's email inbox — they should get an "Assigned" notification (or check console logs if email is disabled)
6. Log in as the **Worker** → see the assigned complaint → update status to `IN_PROGRESS`, then later `RESOLVED`
7. Each status change fires an email to the resident automatically
8. To demo the **SLA scheduler**: raise a complaint with `CRITICAL` priority and just wait — the `@Scheduled` job runs every 5 minutes and will flag it `OVERDUE` automatically if it isn't resolved by the deadline, and email all admins an escalation notice
9. To demo **multiple admins**: while logged in as the default admin, open the sidebar → Admins → "+ Add admin" → create a second admin account, then log in with it to confirm it works

---

## Project Structure

```
src/main/java/com/society/complaint/
├── config/          # Spring Security config, UserDetailsService, login success handler, DataSeeder
├── controller/       # AuthController, AdminController, ResidentController, WorkerController
├── dto/              # RegisterDto, AdminCreateDto, ComplaintDto (form-binding objects, separate from entities)
├── entity/           # User, Complaint, ComplaintStatusHistory (JPA entities)
├── enums/            # Role, ComplaintStatus, Priority (with SLA hours), ComplaintCategory
├── repository/       # Spring Data JPA repositories (with fetch-joined queries for detail/list views)
├── scheduler/        # SlaScheduler - the @Scheduled SLA-breach checker
└── service/          # Business logic interfaces + impl/ package

src/main/resources/
├── templates/        # Thymeleaf views: admin/, resident/, worker/, auth/, fragments/ (sidebar, badges)
└── static/css/       # Custom design system (sidebar shell, color tokens, tables, badges, forms) - no Bootstrap dependency
```

---

## Key Design Decisions (good interview talking points)

**1. SLA deadline calculation**
Each `Priority` enum value carries its own SLA window (`CRITICAL`=4h, `HIGH`=24h, `MEDIUM`=48h, `LOW`=72h). When a complaint is created, `@PrePersist` computes `slaDeadline = createdAt + priority.slaHours` once and stores it — so the scheduler only has to compare against a stored timestamp, not recompute business rules on every run.

**2. The scheduler is idempotent**
`SlaScheduler` runs every 5 minutes and calls `ComplaintService.runSlaCheck()`, which queries only complaints that are *not yet* flagged overdue and *not* resolved/closed. An `escalationSent` boolean ensures the escalation email only fires once per complaint, even though the scheduler re-runs repeatedly — this is a common real-world gotcha (avoiding duplicate notifications) worth mentioning if asked.

**3. Audit trail via `ComplaintStatusHistory`**
Rather than overwriting the status field silently, every transition inserts a new history row (old status → new status, who changed it, optional remarks, timestamp). This is the same pattern used in real ticketing systems (Jira, ServiceNow) and gives you something concrete to point to about schema design.

**4. Role-based access control at two levels**
- **URL-level**: `SecurityConfig` restricts `/admin/**`, `/resident/**`, `/worker/**` by role using `hasRole()`.
- **Object-level**: Inside controllers (e.g. `ResidentController.viewComplaint`), there's an explicit check that the logged-in resident actually owns the complaint they're trying to view — URL-level role checks alone don't stop Resident A from guessing Resident B's complaint ID in the URL. This is worth explicitly mentioning — it shows you understand RBAC isn't just about roles, it's about object ownership too.

**5. Email failures never break the main flow**
`EmailServiceImpl` wraps every send in try/catch and only logs failures. If Gmail SMTP times out, the complaint still gets created/updated in the database — email is a side effect, not a dependency of the core transaction.

**6. DTOs instead of binding entities directly to forms**
`RegisterDto`, `AdminCreateDto`, and `ComplaintDto` are separate from the `User`/`Complaint` entities. This avoids over-posting vulnerabilities (a malicious form field trying to set `role=ADMIN` or `overdue=false` directly) and keeps validation annotations on the input boundary, not mixed into persistence logic.

**7. Admin accounts are provisioned, not self-registered**
Letting anyone pick "Admin" on a public sign-up form is a real vulnerability, not just a UX nitpick — it means any user can grant themselves full control. Instead: exactly one admin is seeded on first startup (`DataSeeder`, a `CommandLineRunner` that checks if any admin already exists before creating one), and every additional admin is created by an *existing*, authenticated admin through a screen that's itself behind `hasRole('ADMIN')`. The public registration form never offers `ADMIN` as an option, and the server rejects `role=ADMIN` even if it's submitted directly, so the protection doesn't rely on hiding a dropdown option.

---

## Possible Follow-Up Questions to Prepare For

- "What happens if two admins try to assign a worker to the same complaint at the same time?" → Currently last-write-wins; you could mention adding optimistic locking (`@Version`) as a future improvement.
- "How would you scale the email sending if you had 10,000 residents?" → Mention moving from `@Async` in-process to a message queue (RabbitMQ/Kafka) + dedicated consumer service.
- "Why Thymeleaf instead of React?" → Be honest: simpler for a fresher-scope project, server-rendered, no separate API layer needed, and it's a very common stack in Indian service-based companies (TCS, Infosys, Wipro, Capgemini) that still use Spring MVC + JSP/Thymeleaf heavily.
- "How do you prevent the same complaint from being escalated multiple times?" → Point to the `escalationSent` flag.

---

## Possible Resume Bullet Points

- Built a full-stack Spring Boot MVC application with role-based dashboards (Admin/Resident/Worker) using Spring Security, Spring Data JPA, and MySQL, implementing complete CRUD and status-transition workflows with an audit trail.
- Implemented automated SLA breach detection using Spring's `@Scheduled` jobs, flagging overdue complaints and triggering escalation emails to admins via `JavaMailSender` and `@Async`.
- Designed a relational schema with `User`, `Complaint`, and `ComplaintStatusHistory` entities, enforcing object-level authorization so users can only access their own records.

---

## Possible Future Enhancements (mention if asked "what would you add next?")

- File/photo upload for complaints (e.g. picture of the leaking pipe) — would use `MultipartFile` + local/S3 storage
- Pagination on complaint lists (currently loads all records — fine for a demo, not for 10k+ rows)
- REST API + a separate frontend (React) instead of server-rendered Thymeleaf, for a more "modern" stack
- Admin analytics dashboard (charts: complaints by category, average resolution time per worker)
- Rating/feedback from resident after resolution
