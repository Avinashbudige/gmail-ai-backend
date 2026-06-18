# AI-Powered Gmail Reply Automation (Polyglot Backend)

An event-driven, secure, and resilient system that fetches unread emails from Gmail, automatically generates draft replies using LLMs (like Groq or OpenAI) tailored to the user's signature and preferred tone, and allows the user to review, edit, or approve replies before sending.

This project is built using a **polyglot architecture**:
- **`gateway-service` (Node.js)**: Responsible for routing, Google OAuth2 integration, JWT session management, CORS, and proxying API requests to the core service.
- **`core-service` (Java / Spring Boot)**: Handles business logic, email syncing, Gmail API integration, databases, and email sending with idempotency and automatic retries.
- **`frontend` (React / Vite)**: Dashboard for reviewing, editing, and approving AI-generated drafts.

---

## Technical Stack & Ports
- **Node.js Gateway**: Port `3000`
- **Java Core Service**: Port `8080` *(internal — not exposed to clients directly)*
- **React Frontend**: Port `5173` (dev) / Port `8081` (Docker/Nginx)
- **Databases**:
  - H2 Database (in-memory, local dev default)
  - PostgreSQL (production, activated via `prod` Spring profile)

> **Note on infrastructure stubs**: `docker-compose.yml` also defines Redis and RabbitMQ containers. These are not yet wired into the application code — they are infrastructure stubs for planned future features (session caching and async job queues respectively).

---

## Security Architecture

The system follows a trust boundary model:
- All client traffic enters through the **gateway** (port 3000) which validates JWTs.
- The **core-service** (port 8080) is internal-only and validates an `X-Internal-Secret` header on every request. Requests without it receive `403 Forbidden`.
- JWTs are stored in `httpOnly; SameSite=strict` cookies — not in `localStorage` or URL parameters.

### Required environment variables for production

```env
# Gateway
JWT_SECRET=<random-256-bit-string>
INTERNAL_SERVICE_SECRET=<shared-secret-matching-core-service>
ALLOWED_ORIGINS=https://your-frontend-domain.com

# Core Service
GMAIL_ENCRYPTION_KEY=<exactly-32-byte-random-string>
INTERNAL_SERVICE_SECRET=<same-shared-secret-as-gateway>
```

---

## Quick Start (Mock Mode — Zero Setup)

You can run the entire system locally with no external configuration (no Google OAuth or LLM keys required) using **Mock Mode**.

### 1. Start the Node.js API Gateway
```bash
cd gateway-service
npm install
npm run dev
```
*(Runs on http://localhost:3000)*

### 2. Start the Java Core Service

> **Required**: Set the encryption key before starting. Any 32+ character string works in development:
```bash
# Windows
set GMAIL_ENCRYPTION_KEY=thisisalocaldevelopmentkey12345
# Linux/Mac
export GMAIL_ENCRYPTION_KEY=thisisalocaldevelopmentkey12345
```

```bash
..\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
```
*(Runs on http://localhost:8080. Downloads dependencies and boots an in-memory H2 database.)*

---

## Testing the Flow End-to-End

### Step 1: Simulate Gmail Auth Callback
1. Open your browser and go to: `http://localhost:3000/auth/gmail`
2. It returns a callback URL. Visit it in your browser:
   `http://localhost:3000/auth/callback?code=mock_authorization_code_12345`
3. The gateway sets an `httpOnly` session cookie and redirects you to the frontend.

### Step 2: Trigger Email Sync
Email sync is **event-driven** — it is triggered by Google Pub/Sub webhook push notifications, not a background scheduler.

In mock mode, trigger a sync manually:
```bash
curl -X POST http://localhost:8080/api/webhook/gmail \
  -H "Content-Type: application/json" \
  -d '{"message":{"data":"eyJlbWFpbEFkZHJlc3MiOiJtb2NrLnVzZXJAZ21haWwuY29tIn0="}}'
```
Then check your Java terminal logs for: `[AI-Draft] Successfully generated and stored draft for: boss@workplace.com`

### Step 3: Fetch Pending Drafts
```bash
curl http://localhost:3000/api/drafts/pending \
  --cookie "draftly_token=<TOKEN>"
```

*(Or use the React frontend at http://localhost:5173 — the cookie is set automatically.)*

### Step 4: Edit a Draft (Optional)
```bash
curl -X PUT http://localhost:3000/api/drafts/<DRAFT_ID> \
  -H "Content-Type: application/json" \
  --cookie "draftly_token=<TOKEN>" \
  -d '{"content":"Hi Boss, yes the report is complete. Thanks!"}'
```

### Step 5: Approve and Send
```bash
curl -X POST http://localhost:3000/api/drafts/<DRAFT_ID>/approve \
  --cookie "draftly_token=<TOKEN>"
```

---

## E2E Test Script

```bash
# Get your token from the /api/me endpoint after logging in via cookie,
# or from a curl on /auth/callback in mock mode (inspect Set-Cookie header):
curl -v "http://localhost:3000/auth/callback?code=mock_authorization_code_12345" 2>&1 | grep "draftly_token"

# Run the E2E test script (defaults to localhost:3000):
node test-api.js <TOKEN>

# Or against production:
BASE_URL=https://your-domain.com node test-api.js <TOKEN>
```

---

## Production / Live Setup

### 1. Database & Infrastructure Stack
```bash
docker-compose up -d
```

### 2. Environment Variables
Update `gateway-service/.env` and set core-service env vars:
```env
# Gateway .env
GMAIL_MODE=live
JWT_SECRET=<strong-random-secret>
INTERNAL_SERVICE_SECRET=<shared-with-core-service>
ALLOWED_ORIGINS=https://your-frontend.com
GROQ_API_KEY=gsk_...
GOOGLE_CLIENT_ID=your_id
GOOGLE_CLIENT_SECRET=your_secret
GOOGLE_REDIRECT_URI=https://your-gateway.com/auth/callback
FRONTEND_URL=https://your-frontend.com

# Core service (via Docker or environment)
GMAIL_ENCRYPTION_KEY=<exactly-32-chars>
INTERNAL_SERVICE_SECRET=<same-as-gateway>
```

Once configured, restarting the services will route auth to the real Google login screen and generate responses using LLMs.
