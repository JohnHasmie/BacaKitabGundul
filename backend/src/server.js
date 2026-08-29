import { createServer } from "node:http";
import { pathToFileURL } from "node:url";
import { runAnalysis } from "./analyze.js";

const PORT = Number(process.env.PORT || 8787);
const MAX_BODY_BYTES = 4 * 1024 * 1024;

// Simple per-client rate limit: N requests per minute keyed by bearer token
// (falling back to remote address). In-memory — fine for a single instance.
const RATE_LIMIT_PER_MINUTE = Number(process.env.RATE_LIMIT_PER_MINUTE || 10);
const usage = new Map();

function allowRequest(key) {
  const now = Date.now();
  const window = usage.get(key)?.filter((t) => now - t < 60_000) ?? [];
  if (window.length >= RATE_LIMIT_PER_MINUTE) return false;
  window.push(now);
  usage.set(key, window);
  return true;
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(new Error("Body too large"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", reject);
  });
}

function sendJson(res, status, payload) {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(payload));
}

export function createAppServer() {
  return createServer(async (req, res) => {
    if (req.method === "GET" && req.url === "/healthz") {
      sendJson(res, 200, { ok: true });
      return;
    }
    if (req.method !== "POST" || req.url !== "/v1/analyze") {
      sendJson(res, 404, { error: "Not found" });
      return;
    }

    const client =
      req.headers.authorization?.replace(/^Bearer\s+/i, "") ||
      req.socket.remoteAddress ||
      "unknown";
    if (!allowRequest(client)) {
      sendJson(res, 429, { error: "Rate limit exceeded" });
      return;
    }

    let body;
    try {
      body = JSON.parse(await readBody(req));
    } catch {
      sendJson(res, 400, { error: "Invalid JSON body" });
      return;
    }

    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
    });
    const send = (payload) => res.write(`data: ${JSON.stringify(payload)}\n\n`);

    await runAnalysis(body, {
      partial: (vocalizedText) => send({ type: "partial", vocalizedText }),
      complete: (result) => send({ type: "complete", result }),
      error: (message) => send({ type: "error", message }),
    });
    res.end();
  });
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  createAppServer().listen(PORT, () => {
    console.log(`classic-book-reader backend listening on :${PORT}`);
  });
}
