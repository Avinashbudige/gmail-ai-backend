import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import dotenv from 'dotenv';
import jwt from 'jsonwebtoken';
import axios from 'axios';

dotenv.config();

const app = express();
app.use(helmet());
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || 'fallback_secret';
const CORE_SERVICE_URL = process.env.CORE_SERVICE_URL || 'http://localhost:8080';
const GMAIL_MODE = process.env.GMAIL_MODE || 'mock';

// Extended request type to include authenticated user details
interface AuthenticatedRequest extends Request {
  user?: {
    id: string;
    email: string;
  };
}

// JWT Authentication Middleware
const authenticateJWT = (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  const authHeader = req.headers.authorization;

  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.split(' ')[1];

    jwt.verify(token, JWT_SECRET, (err, decoded: any) => {
      if (err) {
        return res.status(403).json({ error: 'Forbidden: Invalid token' });
      }
      req.user = { id: decoded.id, email: decoded.email };
      next();
    });
  } else {
    res.status(401).json({ error: 'Unauthorized: Missing token' });
  }
};

// ----------------------------------------------------
// AUTH FLOW ENDPOINTS
// ----------------------------------------------------

// Endpoint to trigger Google OAuth2 login
app.get('/auth/gmail', (req: Request, res: Response) => {
  if (GMAIL_MODE === 'mock') {
    // In Mock Mode, we mock the OAuth redirect path
    const mockAuthUrl = `http://localhost:3000/auth/callback?code=mock_authorization_code_12345`;
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
      refreshToken = refresh_token; // Google only sends this on first consent

      // Get user email
      const userResponse = await axios.get(
        `https://www.googleapis.com/oauth2/v2/userinfo?access_token=${access_token}`
      );
      email = userResponse.data.email;
    }

    // Sync profile to Java core-service
    const syncResponse = await axios.post(`${CORE_SERVICE_URL}/api/users/sync-profile`, {
      email,
      refreshToken
    });

    const user = syncResponse.data;

    // Generate JWT Session Token (expires in 24 hours)
    const token = jwt.sign({ id: user.id, email: user.email }, JWT_SECRET, {
      expiresIn: '24h'
    });

    // Redirect the user back to the React Frontend Dashboard, passing the JWT token
    const frontendUrl = process.env.FRONTEND_URL || 'https://draftly.email/dashboard';
    res.redirect(`${frontendUrl}?token=${token}`);
  } catch (error: any) {
    console.error('[Gateway] OAuth Error:', error.message);
    res.status(500).json({ error: 'Authentication failed', details: error.message });
  }
});


// ----------------------------------------------------
// INTERNAL AI DRAFT GENERATION ENDPOINT
// ----------------------------------------------------
app.post('/internal/ai/generate', async (req: Request, res: Response) => {
  const { sender, subject, body, tone, signature } = req.body;

  // Check if we are running in Mock Mode
  if (GMAIL_MODE === 'mock') {
    let mockReply = '';
    
    // Generate a simple template-based reply based on tone
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
    const apiKey = process.env.GROQ_API_KEY || process.env.OPENAI_API_KEY;
    if (!apiKey) {
      throw new Error('AI API key is missing in environment variables');
    }

    const providerUrl = process.env.GROQ_API_KEY 
      ? 'https://api.groq.com/openai/v1/chat/completions' 
      : 'https://api.openai.com/v1/chat/completions';

    const modelName = process.env.GROQ_API_KEY ? 'llama-3.1-8b-instant' : 'gpt-4o-mini';
    
    const systemPrompt = `You are a helpful email assistant. Generate a reply to the incoming email.
Tone: ${tone || 'professional'}
User's signature: ${signature || ''}
Rules:
- Match the tone and signature perfectly.
- Do not mention you are an AI assistant.
- Be concise and accurate.`;

    const userPrompt = `Original Email:
From: ${sender}
Subject: ${subject}
Body: ${body}`;

    // --- Exponential Backoff Retry Logic ---
    let response;
    let retries = 3;
    let attempt = 0;

    while (attempt <= retries) {
      try {
        response = await axios.post(providerUrl, {
          model: modelName,
          messages: [
            { role: 'system', content: systemPrompt },
            { role: 'user', content: userPrompt }
          ],
          temperature: 0.7,
          max_tokens: 500
        }, {
          headers: { 'Authorization': `Bearer ${apiKey}` }
        });
        
        // If successful, break out of the retry loop
        break; 

      } catch (err: any) {
        // If the error is an HTTP 429 (Rate Limit Exceeded) and we have retries left
        if (err.response && err.response.status === 429 && attempt < retries) {
          attempt++;
          // Calculate exponential delay: 2s, 4s, 8s
          const delayMs = Math.pow(2, attempt) * 1000; 
          console.warn(`[Gateway] Rate limited (429). Retrying attempt ${attempt} in ${delayMs}ms...`);
          
          // Pause execution for the calculated delay before restarting the loop
          await new Promise(resolve => setTimeout(resolve, delayMs));
        } else {
          // If it's a different error (e.g. 401 Unauthorized), or we ran out of retries, throw it
          throw err; 
        }
      }
    }

    if (!response) {
      throw new Error('AI provider did not return a response');
    }

    const aiReply = response.data.choices[0].message.content;
    res.json({ draft: aiReply, provider: modelName });
    // ---------------------------------------
  } catch (error: any) {
    console.error('[Gateway] AI Generation Error:', error.message);
    res.status(500).json({ error: 'AI generation failed', details: error.message });
  }
});


// ----------------------------------------------------
// PROXY AGENT ROUTING (PROXIES TO SPRING BOOT CORE)
// ----------------------------------------------------
app.all('/api/*', authenticateJWT, async (req: AuthenticatedRequest, res: Response) => {
  try {
    const targetUrl = `${CORE_SERVICE_URL}${req.originalUrl}`;
    
    const response = await axios({
      method: req.method,
      url: targetUrl,
      data: req.body,
      headers: {
        'X-User-ID': req.user?.id, // Forward authenticated User UUID
        'Content-Type': 'application/json'
      },
      validateStatus: () => true // Allow proxying of any status (400, 404, 500, etc.)
    });

    res.status(response.status).json(response.data);
  } catch (error: any) {
    console.error(`[Gateway] Proxy error to ${req.originalUrl}:`, error.message);
    res.status(503).json({ error: 'Core service currently unavailable' });
  }
});

// Gateway Health Endpoint
app.get('/health', (req, res) => {
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