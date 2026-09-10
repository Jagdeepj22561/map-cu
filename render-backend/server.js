const express = require("express");
const cors = require("cors");
const SibApiV3Sdk = require("sib-api-v3-sdk");
const multer = require("multer");
const cloudinary = require("cloudinary").v2;
const rateLimit = require("express-rate-limit");
const { createClient } = require("@supabase/supabase-js");
const WebSocket = require("ws");

const app = express();
app.use(cors());
app.use(express.json());

const requiredEnvironmentVariables = [
  "API_KEY",
  "BREVO_API_KEY",
  "BREVO_SENDER_EMAIL",
  "SUPABASE_URL",
  "SUPABASE_SERVICE_ROLE_KEY",
];

const missingEnvironmentVariables = requiredEnvironmentVariables.filter(
  (name) => !process.env[name]
);

if (missingEnvironmentVariables.length > 0) {
  throw new Error(
    `Missing required environment variables: ${missingEnvironmentVariables.join(", ")}`
  );
}

/* ================= RATE LIMIT ================= */

app.use(
  rateLimit({
    windowMs: 60 * 1000,
    max: 30,
    message: { error: "Too many requests, slow down" },
    standardHeaders: true,
    legacyHeaders: false,
  })
);

/* ================= API KEY ================= */

app.use((req, res, next) => {
  const clientKey = req.headers["x-api-key"];

  if (!clientKey || clientKey !== process.env.API_KEY) {
    return res.status(403).json({ error: "Unauthorized - Invalid API Key" });
  }

  return next();
});

/* ================= SUPABASE ================= */

// This key bypasses Row Level Security. It must exist only in Render's
// environment variables, never in the Android/iOS application or Git.
const supabase = createClient(
  process.env.SUPABASE_URL,
  process.env.SUPABASE_SERVICE_ROLE_KEY,
  {
    auth: {
      autoRefreshToken: false,
      persistSession: false,
    },
    // Node 20 has no native WebSocket. Supabase initializes its Realtime
    // client internally, so provide the Node transport even though this
    // server only performs authenticated admin requests.
    realtime: {
      transport: WebSocket,
    },
  }
);

/* ================= BREVO ================= */

const defaultClient = SibApiV3Sdk.ApiClient.instance;
defaultClient.authentications["api-key"].apiKey = process.env.BREVO_API_KEY;
const emailApi = new SibApiV3Sdk.TransactionalEmailsApi();

/* ================= CLOUDINARY ================= */

cloudinary.config({
  cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
  api_key: process.env.CLOUDINARY_API_KEY,
  api_secret: process.env.CLOUDINARY_API_SECRET,
});

/* ================= MULTER ================= */

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024 },
});

/* ================= OTP STORE ================= */

// This preserves the current single-Render-instance behaviour. Move this to
// Redis before scaling Render to multiple instances or restarting frequently.
const otpStore = new Map();
const EMAIL_TIMEOUT_MS = 20_000;

function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

function validateOtp(email, otp) {
  const stored = otpStore.get(email);

  if (!stored) return "No OTP requested";
  if (Date.now() > stored.expires) {
    otpStore.delete(email);
    return "OTP expired";
  }
  if (stored.code !== otp) return "Wrong OTP";

  return null;
}

/* ================= ROUTES ================= */

app.get("/health", (_, res) => res.json({ status: "OK" }));

/* ---------- GENERATE OTP ---------- */

app.post("/generate-otp", async (req, res) => {
  const email = normalizeEmail(req.body.email);

  if (!email.endsWith("@cuchd.in")) {
    return res.status(400).json({ error: "Only @cuchd.in emails allowed" });
  }

  const now = Date.now();
  let userData = otpStore.get(email) || { count: 0, firstRequestTime: now };

  if (now - userData.firstRequestTime > 24 * 60 * 60 * 1000) {
    userData = { count: 0, firstRequestTime: now };
  }

  if (userData.count >= 5) {
    return res.status(429).json({ error: "Max OTP limit reached (5/day)" });
  }

  if (userData.lastRequest && now - userData.lastRequest < 60 * 1000) {
    return res.status(429).json({ error: "Wait 1 minute before retry" });
  }

  const otp = Math.floor(100000 + Math.random() * 900000).toString();
  otpStore.set(email, {
    ...userData,
    code: otp,
    expires: now + 5 * 60 * 1000,
    count: userData.count + 1,
    lastRequest: now,
  });

  try {
    await Promise.race([
      emailApi.sendTransacEmail({
        sender: { email: process.env.BREVO_SENDER_EMAIL, name: "Campus Map" },
        to: [{ email }],
        subject: "Campus Map OTP",
        textContent: `Your OTP is: ${otp}`,
      }),
      new Promise((_, reject) => setTimeout(
        () => reject(new Error("Brevo email request timed out")),
        EMAIL_TIMEOUT_MS
      )),
    ]);

    return res.json({ status: "OTP_SENT" });
  } catch (error) {
    const providerMessage =
      error?.response?.body?.message ||
      error?.response?.body?.code ||
      error?.message ||
      "Unknown Brevo error";
    console.error("Brevo OTP email error:", providerMessage);
    return res.status(502).json({
      error: `Email delivery failed: ${providerMessage}`,
    });
  }
});

/* ---------- VERIFY OTP AND CREATE SUPABASE USER ---------- */

app.post("/verify-otp-create", async (req, res) => {
  const email = normalizeEmail(req.body.email);
  const otp = String(req.body.otp || "").trim();
  const password = String(req.body.password || "");
  const name = String(req.body.name || "").trim();

  if (!email || !otp || !password || !name) {
    return res.status(400).json({ error: "Missing fields" });
  }

  if (!email.endsWith("@cuchd.in")) {
    return res.status(400).json({ error: "Only @cuchd.in emails allowed" });
  }

  const otpError = validateOtp(email, otp);
  if (otpError) return res.status(400).json({ error: otpError });

  // This creates auth.users. The SQL trigger in the Android project's Supabase
  // migration then creates public.profiles with this user's UUID and name.
  const { data, error } = await supabase.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { name },
  });

  if (error) {
    console.error("Supabase user creation error:", error.message);
    const alreadyExists = /already (registered|exists)/i.test(error.message);
    return res.status(alreadyExists ? 409 : 500).json({
      error: alreadyExists ? "User already exists" : "Could not create user",
    });
  }

  otpStore.delete(email);
  return res.status(201).json({
    status: "USER_CREATED",
    uid: data.user.id,
  });
});

/* ---------- IMAGE UPLOAD ---------- */

app.post("/upload-image", upload.single("file"), async (req, res) => {
  try {
    if (!req.file) return res.status(400).json({ error: "File not received" });
    if (!req.file.mimetype.startsWith("image/")) {
      return res.status(400).json({ error: "Invalid image type" });
    }

    const result = await new Promise((resolve, reject) => {
      const stream = cloudinary.uploader.upload_stream(
        { folder: "profile_pics" },
        (error, uploadResult) => (error ? reject(error) : resolve(uploadResult))
      );
      stream.end(req.file.buffer);
    });
    return res.json({ success: true, url: result.secure_url });
  } catch (error) {
    console.error("Upload error:", error);
    return res.status(500).json({ error: "Upload failed" });
  }
});

const port = process.env.PORT || 3000;
app.listen(port, () => console.log(`Server running on port ${port}`));
