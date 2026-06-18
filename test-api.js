// test-api.js
// Run this script using: node test-api.js <YOUR_JWT_TOKEN>
// Defaults to localhost:3000 for local mock-mode testing.
// Override with: BASE_URL=https://your-domain.com node test-api.js <TOKEN>

const token = process.argv[2];
const BASE_URL = process.env.BASE_URL || 'http://localhost:3000';

if (!token) {
  console.error("❌ Please provide your JWT token as an argument.");
  console.log("Usage: node test-api.js eyJhbGciOiJIUzI1Ni...");
  process.exit(1);
}

const headers = {
  "Authorization": `Bearer ${token}`,
  "Content-Type": "application/json"
};

async function runTests() {
  console.log("==========================================");
  console.log("🤖 GMAIL AI BACKEND - E2E TEST SCRIPT");
  console.log("==========================================\n");

  try {
    // 1. Fetch all pending drafts
    console.log("Fetching pending drafts...");
    const draftsRes = await fetch(`${BASE_URL}/api/drafts`, { headers });
    
    if (!draftsRes.ok) throw new Error(`HTTP ${draftsRes.status}: ${await draftsRes.text()}`);
    
    const drafts = await draftsRes.json();
    console.log(`✅ Found ${drafts.length} draft(s).\n`);

    if (drafts.length === 0) {
      console.log("No drafts available to approve. Send an email to your linked Gmail account first!");
      return;
    }

    // Display the first draft
    const draftToTest = drafts[0];
    console.log("--- DRAFT DETAILS ---");
    console.log(`ID: ${draftToTest.id}`);
    console.log(`Original Email ID: ${draftToTest.emailId}`);
    console.log(`Generated Content:\n${draftToTest.generatedContent}`);
    console.log("---------------------\n");

    // 2. Approve and Send the draft
    console.log(`Approving draft ${draftToTest.id}...`);
    const approveRes = await fetch(`${BASE_URL}/api/drafts/${draftToTest.id}/approve`, {
      method: "POST",
      headers
    });

    if (!approveRes.ok) throw new Error(`HTTP ${approveRes.status}: ${await approveRes.text()}`);
    
    const approveData = await approveRes.json();
    console.log("✅ Draft Approved and Sent!");
    console.log(`Message ID: ${approveData.messageId}`);
    
  } catch (err) {
    console.error("\n❌ TEST FAILED:", err.message);
  }
}

runTests();
