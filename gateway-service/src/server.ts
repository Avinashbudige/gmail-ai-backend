import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import dotenv from 'dotenv';
import jwt from 'jsonwebtoken';
import axios from 'axios';
import cookieParser from 'cookie-parser';
import crypto from 'crypto';

dotenv.config();

const app = express();
app.use(helmet());

// -----------------------------------------------------------------
// CORS — restrict to known frontend origins only.
// An open cors() accepts ANY origin, enabling cross-site request forgery.
// -----------------------------------------------------------------
const allowedOrigins = process.env.ALLOWED_ORIGINS
  ? process.env.ALLOWED_ORIGINS.split(',').map(o => o.trim())
  : ['http://localhost:5173', 'http://localhost:8081'];

app.use(cors({
  origin: allowedOrigins,
  credentials: true  // required so cookies are sent cross-origin in dev
}));

app.use(express.json());
app.use(cookieParser());

const PORT = process.env.PORT || 3000;
const CORE_SERVICE_URL = process.env.CORE_SERVICE_URL || 'http://localhost:8080';
const GMAIL_MODE = process.env.GMAIL_MODE || 'mock';
const INTERNAL_SERVICE_SECRET = process.env.INTERNAL_SERVICE_SECRET || '';

// -----------------------------------------------------------------
// JWT SECRET — fail-fast if missing in production.
// For dev/mock mode: generate a random-per-process secret instead of
// a hardcoded fallback, so tokens cannot be forged with a known key.
// -----------------------------------------------------------------
let JWT_SECRET: string;
if (!process.env.JWT_SECRET) {
  if (process.env.NODE_ENV === 'production') {
    console.error('[FATAL] JWT_SECRET environment variable is not set. Cannot start in production without it.');
    process.exit(1);
  }
  JWT_SECRET = crypto.randomBytes(32).toString('hex');
  console.warn('[Security] JWT_SECRET not set — using a random per-process secret for dev/mock mode.');
  console.warn('[Security] All tokens will be invalidated on server restart. Set JWT_SECRET in .env for persistence.');
} else {
  JWT_SECRET = process.env.JWT_SECRET;
}

// Extended request type to include authenticated user details
interface AuthenticatedRequest extends Request {
  user?: {
    id: string;
    email: string;
  };
}

// -----------------------------------------------------------------
// JWT Authentication Middleware
// Accepts the token from either the httpOnly cookie OR the
// Authorization: Bearer header (for backward compat / test scripts).
// -----------------------------------------------------------------
const authenticateJWT = (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  // Prefer cookie (more secure) over header
  const token = req.cookies?.draftly_token || 
                (req.headers.authorization?.startsWith('Bearer ') 
                  ? req.headers.authorization.split(' ')[1] 
                  : null);

  if (!token) {
    return res.status(401).json({ error: 'Unauthorized: Missing token' });
  }

  jwt.verify(token, JWT_SECRET, (err: any, decoded: any) => {
    if (err) {
      return res.status(403).json({ error: 'Forbidden: Invalid token' });
    }
    req.user = { id: decoded.id, email: decoded.email };
    next();
  });
};

// -----------------------------------------------------------------
// AUTH FLOW ENDPOINTS
// -----------------------------------------------------------------

// Endpoint to trigger Google OAuth2 login
app.get('/auth/gmail', (req: Request, res: Response) => {
  if (GMAIL_MODE === 'mock') {
    // In Mock Mode, return a configurable callback URL
    const gatewayBase = process.env.GATEWAY_URL || `http://localhost:${PORT}`;
    const mockAuthUrl = `${gatewayBase}/auth/callback?code=mock_authorization_code_12345`;
    return res.json({ url: mockAuthUrl });
  }

  // Live Mode: Build Google OAuth2 redirect URL
  const rootUrl = 'https://accounts.google.com/o/oauth2/v2/auth';
  const options = {
    redirect_uri: process.env.GOOGLE_REDIRECT_URI as string,
    client_id: process.env.GOOGLE_CLIENT_ID as string,
    access_type: 'offline',
    response_type: 'code',
    prompt: 'consent',
    include_granted_scopes: 'true',
    state: crypto.randomBytes(16).toString('hex'),
    scope: [
      'https://www.googleapis.com/auth/userinfo.email',
      'https://www.googleapis.com/auth/gmail.modify'
    ].join(' ')
  };

  const qs = new URLSearchParams(options);
  res.json({ url: `${rootUrl}?${qs.toString()}` });
});

// OAuth Callback Endpoint
app.get('/auth/callback', async (req: Request, res: Response) => {
  const code = req.query.code as string;

  if (!code) {
    return res.status(400).json({ error: 'Authorization code missing' });
  }

  try {
    let email = 'mock.user@gmail.com';
    let refreshToken = 'mock_refresh_token_xyz_98765';

    if (GMAIL_MODE !== 'mock') {
      // Live Mode: Exchange code for tokens with Google OAuth
      const tokenUrl = 'https://oauth2.googleapis.com/token';
      const values = {
        code,
        client_id: process.env.GOOGLE_CLIENT_ID as string,
        client_secret: process.env.GOOGLE_CLIENT_SECRET as string,
        redirect_uri: process.env.GOOGLE_REDIRECT_URI as string,
        grant_type: 'authorization_code'
      };

      const tokenResponse = await axios.post(tokenUrl, new URLSearchParams(values), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
      });

      const { access_token, refresh_token } = tokenResponse.data;
      refreshToken = refresh_token;

      // Get user email
      const userResponse = await axios.get(
        `https://www.googleapis.com/oauth2/v2/userinfo?access_token=${access_token}`
      );
      email = userResponse.data.email;
    }

    // Sync profile to Java core-service (inject service-to-service secret)
    const syncResponse = await axios.post(`${CORE_SERVICE_URL}/api/users/sync-profile`, {
      email,
      refreshToken
    }, {
      headers: { 'X-Internal-Secret': INTERNAL_SERVICE_SECRET }
    });

    const user = syncResponse.data;

    // Generate JWT Session Token (expires in 24 hours)
    const token = jwt.sign({ id: user.id, email: user.email }, JWT_SECRET, {
      expiresIn: '24h'
    });

    // ----------------------------------------------------------------
    // Set JWT as an httpOnly cookie instead of a URL query parameter.
    // URL tokens appear in browser history, server logs, Referer headers,
    // and analytics tools — all places an attacker can read them.
    // httpOnly cookies are inaccessible to JavaScript (XSS-resistant).
    // ----------------------------------------------------------------
    res.cookie('draftly_token', token, {
      httpOnly: true,
      sameSite: 'strict',
      secure: process.env.NODE_ENV === 'production',
      maxAge: 24 * 60 * 60 * 1000  // 24 hours in ms
    });

    const frontendUrl = process.env.FRONTEND_URL || 'http://localhost:5173';
    res.redirect(frontendUrl);
  } catch (error: any) {
    console.error('[Gateway] OAuth Error:', error.message);
    res.status(500).json({ error: 'Authentication failed', details: error.message });
  }
});

// -----------------------------------------------------------------
// /api/me — returns the authenticated user's profile info.
// Since the JWT is now httpOnly (JS can't read it), the frontend
// calls this endpoint to get id/email for display.
// -----------------------------------------------------------------
app.get('/api/me', authenticateJWT, (req: AuthenticatedRequest, res: Response) => {
  res.json({ id: req.user!.id, email: req.user!.email });
});

// Logout — clears the httpOnly cookie
app.post('/auth/logout', (_req: Request, res: Response) => {
  res.clearCookie('draftly_token', { httpOnly: true, sameSite: 'strict' });
  res.json({ status: 'logged out' });
});


// -----------------------------------------------------------------
// INTERNAL AI DRAFT GENERATION ENDPOINT
// -----------------------------------------------------------------
app.post('/internal/ai/generate', async (req: Request, res: Response) => {
  const { sender, subject, body, tone, signature, threadHistory, writingHistory } = req.body;

  // Check if we are running in Mock Mode
  if (GMAIL_MODE === 'mock') {
    let mockReply = '';

    switch (tone?.toUpperCase()) {
      case 'CONCISE':
        mockReply = `Hi ${sender.split(' ')[0] || sender},\n\nGot it, thanks. I will check and get back to you.\n\nBest,\n${signature || 'Mock User'}`;
        break;
      case 'FRIENDLY':
        mockReply = `Hey ${sender.split(' ')[0] || sender}!\n\nThanks for reaching out! I've received your email about "${subject}" and I'm on it. Will catch up with you soon!\n\nCheers,\n${signature || 'Mock User'}`;
        break;
      case 'FORMAL':
        mockReply = `Dear ${sender},\n\nI acknowledge receipt of your email regarding "${subject}". I shall review the details and provide a formal response in due course.\n\nSincerely,\n${signature || 'Mock User'}`;
        break;
      case 'PROFESSIONAL':
      default:
        mockReply = `Dear ${sender},\n\nThank you for your email regarding "${subject}". I have received your request and will review it. I will follow up with you as soon as possible.\n\nBest regards,\n${signature || 'Mock User'}`;
        break;
    }

    return res.json({ draft: mockReply, provider: 'mock' });
  }

  // Live Mode: Dynamic AI generation with Groq / OpenAI-compatible API
  try {
    // -------------------------------------------------------------------
    // 1. Collect all available providers in preferred fallback order
    // -------------------------------------------------------------------
    const availableProviders: any[] = [];
    
    // Primary: Gemini
    if (process.env.GEMINI_API_KEY) {
      availableProviders.push({ name: 'Gemini', apiKey: process.env.GEMINI_API_KEY, providerUrl: 'https://generativelanguage.googleapis.com/v1beta/openai/chat/completions', modelName: 'gemini-1.5-flash' });
    }
    // Secondary: Groq
    if (process.env.GROQ_API_KEY) {
      availableProviders.push({ name: 'Groq', apiKey: process.env.GROQ_API_KEY, providerUrl: 'https://api.groq.com/openai/v1/chat/completions', modelName: 'mixtral-8x7b-32768' });
    }
    // Tertiary: Sarvam AI
    if (process.env.SARVAM_API_KEY) {
      availableProviders.push({ name: 'Sarvam AI', apiKey: process.env.SARVAM_API_KEY, providerUrl: 'https://api.sarvam.ai/v1/chat/completions', modelName: 'sarvam-105b' });
    }
    // Quaternary: Mistral
    if (process.env.MISTRAL_API_KEY) {
      availableProviders.push({ name: 'Mistral', apiKey: process.env.MISTRAL_API_KEY, providerUrl: 'https://api.mistral.ai/v1/chat/completions', modelName: 'mistral-large-latest' });
    }
    // Fallback: OpenAI
    if (process.env.OPENAI_API_KEY) {
      availableProviders.push({ name: 'OpenAI', apiKey: process.env.OPENAI_API_KEY, providerUrl: 'https://api.openai.com/v1/chat/completions', modelName: 'gpt-4o-mini' });
    }

    if (availableProviders.length === 0) {
      throw new Error('No AI API keys are configured in environment variables');
    }

    // -------------------------------------------------------------------
    // Build thread context from prior emails in the same conversation
    // -------------------------------------------------------------------
    let threadContext = '';
    if (threadHistory && threadHistory.length > 0) {
      threadContext = '\n\nPrior conversation thread (for context):\n';
      for (const msg of threadHistory) {
        threadContext += `<thread_message>\nFrom: ${msg.sender}\nSubject: ${msg.subject}\n<body>\n${msg.body}\n</body>\n</thread_message>\n`;
      }
    }

    // -------------------------------------------------------------------
    // Build writing style context from past sent emails
    // -------------------------------------------------------------------
    let writingStyleContext = '';
    if (writingHistory && writingHistory.length > 0) {
      writingStyleContext = '\n\nHere are examples of how this user writes their email replies (match their style closely):\n';
      for (const pastReply of writingHistory) {
        writingStyleContext += `<writing_sample>\n${pastReply}\n</writing_sample>\n`;
      }
    }

    // -------------------------------------------------------------------
    // System prompt with structural delimiters around user-supplied data.
    // -------------------------------------------------------------------
    const systemPrompt = `You are a helpful email assistant. Generate a reply to the incoming email.
Tone: ${tone || 'professional'}
User's signature: ${signature || ''}
Rules:
- Match the tone and signature perfectly.
- Do not mention you are an AI assistant.
- Be concise and accurate.
- If writing style samples are provided, match the user's natural writing style closely.${threadContext}${writingStyleContext}`;

    // -------------------------------------------------------------------
    // Wrap email body in XML delimiters so the model treats it as data
    // -------------------------------------------------------------------
    const userPrompt = `Reply to this email:

<original_email>
From: ${sender}
Subject: ${subject}
<body>
${body}
</body>
</original_email>`;

    // -------------------------------------------------------------------
    // Sequential LLM Routing & Automatic Fallback
    // -------------------------------------------------------------------
    let response;
    let successfulProvider = null;
    let lastError = null;

    for (const provider of availableProviders) {
      console.log(`[Gateway] Attempting AI generation with ${provider.name}...`);
      let retries = 1; 
      let attempt = 0;

      while (attempt <= retries) {
        try {
          response = await axios.post(provider.providerUrl, {
            model: provider.modelName,
            messages: [
              { role: 'system', content: systemPrompt },
              { role: 'user', content: userPrompt }
            ],
            temperature: 0.7,
            max_tokens: 500
          }, {
            headers: { 'Authorization': `Bearer ${provider.apiKey}` },
            timeout: 15000 // 15 second timeout to prevent hanging the queue
          });

          successfulProvider = provider.name;
          break; // Break the internal retry loop
        } catch (err: any) {
          lastError = err;
          if (err.response && err.response.status === 429 && attempt < retries) {
            attempt++;
            const delayMs = Math.pow(2, attempt) * 1000;
            console.warn(`[Gateway] ${provider.name} rate limited (429). Retrying in ${delayMs}ms...`);
            await new Promise(resolve => setTimeout(resolve, delayMs));
          } else {
            const status = err.response ? err.response.status : 'Network/Timeout';
            console.error(`[Gateway] ${provider.name} failed (${status}). Failing over to next provider...`);
            break; // Break retry loop, move to next provider in the array
          }
        }
      }

      if (response) {
        break; // Successfully got a response, break the outer provider loop
      }
    }

    if (!response) {
      throw new Error(`All configured AI providers failed. Last error: ${lastError?.message}`);
    }

    const aiReply = response.data.choices[0].message.content;
    res.json({ draft: aiReply, provider: successfulProvider });
  } catch (error: any) {
    console.error('[Gateway] AI Generation Error:', error.message);
    res.status(500).json({ error: 'AI generation failed', details: error.message });
  }
});


// -----------------------------------------------------------------
// INTERNAL AI DRAFT REFINEMENT ENDPOINT
// -----------------------------------------------------------------
app.post('/internal/ai/refine', async (req: Request, res: Response) => {
  const { originalEmail, currentDraft, userPrompt } = req.body;

  if (GMAIL_MODE === 'mock') {
    return res.json({ draft: `[Mock AI Copilot Refined]: ${currentDraft}\n(Refined with: ${userPrompt})`, provider: 'moc    // -------------------------------------------------------------------
    // 1. Collect all available providers in preferred fallback order
    // -------------------------------------------------------------------
    const availableProviders: any[] = [];
    
    // Primary: Gemini
    if (process.env.GEMINI_API_KEY) {
      availableProviders.push({ name: 'Gemini', apiKey: process.env.GEMINI_API_KEY, providerUrl: 'https://generativelanguage.googleapis.com/v1beta/openai/chat/completions', modelName: 'gemini-1.5-flash' });
    }
    // Secondary: Groq
    if (process.env.GROQ_API_KEY) {
      availableProviders.push({ name: 'Groq', apiKey: process.env.GROQ_API_KEY, providerUrl: 'https://api.groq.com/openai/v1/chat/completions', modelName: 'llama3-8b-8192' });
    }
    // Tertiary: Sarvam AI
    if (process.env.SARVAM_API_KEY) {
      availableProviders.push({ name: 'Sarvam AI', apiKey: process.env.SARVAM_API_KEY, providerUrl: 'https://api.sarvam.ai/v1/chat/completions', modelName: 'sarvam-105b' });
    }
    // Quaternary: Mistral
    if (process.env.MISTRAL_API_KEY) {
      availableProviders.push({ name: 'Mistral', apiKey: process.env.MISTRAL_API_KEY, providerUrl: 'https://api.mistral.ai/v1/chat/completions', modelName: 'mistral-large-latest' });
    }
    // Fallback: OpenAI
    if (process.env.OPENAI_API_KEY) {
      availableProviders.push({ name: 'OpenAI', apiKey: process.env.OPENAI_API_KEY, providerUrl: 'https://api.openai.com/v1/chat/completions', modelName: 'gpt-4o-mini' });
    }

    if (availableProviders.length === 0) {
      throw new Error('No AI API keys are configured in environment variables');
    }

    // Structural delimiters around user-controlled content (prompt injection protection)
    const systemPrompt = `You are an expert email assistant refining a draft.
The user has provided an instruction to modify the draft.

<original_email_context>
${originalEmail}
</original_email_context>

<current_draft>
${currentDraft}
</current_draft>

Rule:
- Return ONLY the refined email text. No pleasantries, no markdown blocks, no 'Here is your refined draft'. Just the exact email content to be sent.`;

    // -------------------------------------------------------------------
    // Sequential LLM Routing & Automatic Fallback
    // -------------------------------------------------------------------
    let response;
    let successfulProvider = null;
    let lastError = null;

    for (const provider of availableProviders) {
      console.log(`[Gateway] Attempting AI refinement with ${provider.name}...`);
      let retries = 1;
      let attempt = 0;

      while (attempt <= retries) {
        try {
          response = await axios.post(provider.providerUrl, {
            model: provider.modelName,
            messages: [
              { role: 'system', content: systemPrompt },
              { role: 'user', content: `Instruction: ${userPrompt}` }
            ],
            temperature: 0.7,
            max_tokens: 500
          }, {
            headers: { 'Authorization': `Bearer ${provider.apiKey}` },
            timeout: 15000
          });

          successfulProvider = provider.name;
          break; // Break the internal retry loop
        } catch (err: any) {
          lastError = err;
          if (err.response && err.response.status === 429 && attempt < retries) {
            attempt++;
            const delayMs = Math.pow(2, attempt) * 1000;
            console.warn(`[Gateway] ${provider.name} rate limited (429). Retrying in ${delayMs}ms...`);
            await new Promise(resolve => setTimeout(resolve, delayMs));
          } else {
            const status = err.response ? err.response.status : 'Network/Timeout';
            console.error(`[Gateway] ${provider.name} failed (${status}). Failing over to next provider...`);
            break; // Break retry loop, move to next provider in the array
          }
        }
      }

      if (response) {
        break; // Successfully got a response, break the outer provider loop
      }
    }

    if (!response) {
      throw new Error(`All configured AI providers failed. Last error: ${lastError?.message}`);
    }

    const aiReply = response.data.choices[0].message.content;
    res.json({ draft: aiReply, provider: successfulProvider });
  } catch (error: any) {
    console.error('[Gateway] AI Refinement Error:', error.message);
    res.status(500).json({ error: 'AI refinement failed', details: error.message });
  }sage });
  }
});


// -----------------------------------------------------------------
// PROXY AGENT ROUTING (PROXIES TO SPRING BOOT CORE)
// -----------------------------------------------------------------
app.all('/api/*', async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  // Bypass JWT authentication for the webhook endpoint (called by Google Pub/Sub)
  if (req.originalUrl.startsWith('/api/webhook/gmail')) {
    return next();
  }
  // Also bypass /api/me (handled above with its own middleware)
  if (req.originalUrl === '/api/me') {
    return next();
  }
  // Otherwise, apply JWT authentication
  authenticateJWT(req, res, next);
}, async (req: AuthenticatedRequest, res: Response) => {
  try {
    const targetUrl = `${CORE_SERVICE_URL}${req.originalUrl}`;

    // Create a copy of headers to avoid mutating the original request
    const proxyHeaders: Record<string, any> = { ...req.headers };
    delete proxyHeaders['host'];
    delete proxyHeaders['content-length']; // Let axios recalculate content-length
    delete proxyHeaders['cookie'];         // Never forward cookies to backend

    // Inject the authenticated user ID (from JWT) and service-to-service secret
    proxyHeaders['X-User-ID'] = req.user?.id;
    proxyHeaders['X-Internal-Secret'] = INTERNAL_SERVICE_SECRET;
    proxyHeaders['Content-Type'] = 'application/json';

    const response = await axios({
      method: req.method,
      url: targetUrl,
      data: req.body,
      headers: proxyHeaders,
      validateStatus: () => true
    });

    res.status(response.status).json(response.data);
  } catch (error: any) {
    console.error(`[Gateway] Proxy error to ${req.originalUrl}:`, error.message);
    res.status(503).json({ error: 'Core service currently unavailable' });
  }
});

// Gateway Health Endpoint
app.get('/health', (_req, res) => {
  res.json({
    status: 'UP',
    service: 'API-Gateway',
    mode: GMAIL_MODE,
    timestamp: new Date().toISOString()
  });
});

app.listen(PORT, () => {
  console.log(`[Gateway] API Gateway running on http://localhost:${PORT}`);
});