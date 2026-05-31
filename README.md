# AI-Powered Gmail Reply Automation (Polyglot Backend)

An event-driven, secure, and resilient system that fetches unread emails from Gmail, automatically generates drafts using LLMs (like Groq or OpenAI) tailored to the user's signature and preferred tone, and allows the user to review, edit, or approve replies before sending.

This project is built using a **polyglot architecture**:
- **`gateway-service` (Node.js)**: Responsible for routing, Google OAuth2 integration, JWT session management, and proxying API requests.
- **`core-service` (Java / Spring Boot)**: Handles business logic, email syncing schedulers, databases, audit logging, and email sending with idempotency check and automatic retries.

---

## Technical Stack & Ports
- **Node.js Gateway**: Port `3000`
- **Java Core Service**: Port `8080`
- **Databases**:
  - H2 Database (In-memory, local dev fallback)
  - PostgreSQL (Production relational DB)
  - MongoDB (Production document storage for email content)
  - Redis (Caching and sessions)
  - RabbitMQ (Production async queues)

---

## Quick Start (Mock Mode - Zero Setup)

You can run the entire system locally out of the box with zero external configuration (no Google OAuth setup or LLM keys required) by running it in **Mock Mode**.

### 1. Start the Node.js API Gateway
```bash
cd gateway-service
npm install
npm run dev
```
*(Runs on http://localhost:3000)*

### 2. Start the Java Core Service
Make sure you are in the `core-service/` directory and run:
```bash
..\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
```
*(Runs on http://localhost:8080. It will automatically download dependencies, compile, and boot a local in-memory H2 database)*

---

## Testing the Flow End-to-End

### Step 1: Simulate Gmail Auth Callback
1. Open your browser and go to: `http://localhost:3000/auth/gmail`
2. It returns a callback link. Copy and visit it: `http://localhost:3000/auth/callback?code=mock_authorization_code_12345`
3. Copy the **`token`** JWT value from the JSON response.

### Step 2: Observe Sync Service
Check your Java Spring Boot terminal logs. Within 20 seconds, the sync scheduler will trigger:
`[AI-Draft] Successfully generated and stored draft for: boss@workplace.com`

### Step 3: Fetch Pending Drafts
Use curl or Postman to fetch drafts, replacing `<TOKEN>` with your JWT:
```bash
curl -H "Authorization: Bearer <TOKEN>" http://localhost:3000/api/drafts
```

### Step 4: Edit a Draft (Optional)
Modify the AI generated content by replacing `<DRAFT_ID>` with the draft UUID:
```bash
curl -X PUT -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d "{\"content\":\"Hi Boss, yes the report is complete. Thanks!\"}" \
  http://localhost:3000/api/drafts/<DRAFT_ID>
```

### Step 5: Approve and Send the Draft
Approve the draft and queue it to send:
```bash
curl -X POST -H "Authorization: Bearer <TOKEN>" http://localhost:3000/api/drafts/<DRAFT_ID>/approve
```

---

## Production / Live Setup

### 1. Database & Infrastructure Stack
Run the production PostgreSQL, MongoDB, Redis, and RabbitMQ containers:
```bash
docker-compose up -d
```

### 2. Environment Variables (.env)
Update `gateway-service/.env` with your real keys:
```env
GMAIL_MODE=live
GROQ_API_KEY=gsk_...
GOOGLE_CLIENT_ID=your_id
GOOGLE_CLIENT_SECRET=your_secret
GOOGLE_REDIRECT_URI=http://localhost:3000/auth/callback
```
Once configured, restarting the gateway will route auth to the real Google login screen and generate responses using LLMs.
