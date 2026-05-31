import React from 'react';

export default function PrivacyPolicy() {
  return (
    <div className="legal-page">
      <div className="legal-content fade-in">
        <a href="/" className="back-link">← Back to Home</a>
        <h1>Privacy Policy</h1>
        <p>Last updated: {new Date().toLocaleDateString()}</p>
        
        <section>
          <h2>1. Introduction</h2>
          <p>Welcome to Draftly. We respect your privacy and are committed to protecting your personal data. This privacy policy will inform you as to how we look after your personal data when you visit our website and tell you about your privacy rights.</p>
        </section>

        <section>
          <h2>2. Data We Collect</h2>
          <p>Draftly requires access to your Gmail account to function. We collect and process the following data:</p>
          <ul>
            <li><strong>Email Content:</strong> We read incoming emails to generate AI-assisted draft replies. We do not permanently store your incoming email bodies.</li>
            <li><strong>Account Information:</strong> Your email address and basic profile information provided by Google.</li>
            <li><strong>Drafts:</strong> The AI-generated drafts and any modifications you make to them before sending.</li>
          </ul>
        </section>

        <section>
          <h2>3. How We Use Your Data</h2>
          <p>Your data is strictly used to provide the Draftly service. Specifically:</p>
          <ul>
            <li>To generate contextual AI replies using third-party language models.</li>
            <li>To display your drafts for your approval before sending.</li>
            <li>We do <strong>not</strong> sell your data to third parties.</li>
            <li>We do <strong>not</strong> use your email data to train our own AI models.</li>
          </ul>
        </section>

        <section>
          <h2>4. Third-Party AI Providers</h2>
          <p>To generate email drafts, the content of your emails is securely transmitted to our AI partners (e.g., OpenAI or Groq) via API. These providers are bound by strict data processing agreements and do not use your private email data to train their public models.</p>
        </section>

        <section>
          <h2>5. Google API Services User Data Policy</h2>
          <p>Draftly's use and transfer of information received from Google APIs to any other app will adhere to the <a href="https://developers.google.com/terms/api-services-user-data-policy" target="_blank" rel="noopener noreferrer">Google API Services User Data Policy</a>, including the Limited Use requirements.</p>
        </section>

        <section>
          <h2>6. Contact Us</h2>
          <p>If you have any questions about this privacy policy, please contact us at avinashbudigework@gmail.com.</p>
        </section>
      </div>
    </div>
  );
}
