const emailAddress = process.argv[2] || "avinashbudigework@gmail.com";
const serverUrl = process.argv[3] || "https://draftly.email/api/webhook/gmail";

console.log(`\n📧 Simulating Google Pub/Sub Webhook for: ${emailAddress}`);
console.log(`🌐 Target: ${serverUrl}\n`);

// Create the Base64 payload exactly as Google Pub/Sub sends it
const dataString = JSON.stringify({ emailAddress });
const base64Data = Buffer.from(dataString).toString('base64');

const payload = {
  message: {
    data: base64Data
  }
};

async function testWebhook() {
  try {
    const response = await fetch(serverUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    const responseText = await response.text();
    console.log(`✅ Server responded with status: ${response.status}`);
    console.log(`💬 Response body: ${responseText}\n`);
    
    if (response.status === 200) {
      console.log("🎉 Success! The webhook was accepted.");
      console.log("Check your 'docker compose logs -f core-service' to see the AI fetching emails!");
    }
  } catch (error) {
    console.error("❌ Error sending webhook:", error.message);
  }
}

testWebhook();
