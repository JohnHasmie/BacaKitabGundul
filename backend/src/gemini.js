import { buildSystemPrompt, buildPageTranslateSystemPrompt } from "./policy.js";
import { analysisResponseSchema, pageTranslationResponseSchema } from "./schema.js";

const DEFAULT_MODEL = "gemini-2.5-flash";
const API_BASE = "https://generativelanguage.googleapis.com/v1beta";

/**
 * Generic structured-output streamer: async generator yielding the
 * accumulated JSON text after every chunk; the caller decides what counts
 * as a partial update. Model is configurable per deployment (plan §7:
 * cheapest viable first, model-agnostic layer).
 */
export async function* streamGeminiJson(
  { systemPrompt, userParts, responseSchema, maxOutputTokens },
  env = process.env,
  fetchImpl = fetch,
) {
  const apiKey = env.GEMINI_API_KEY;
  if (!apiKey) throw new Error("GEMINI_API_KEY is not configured");
  const model = env.MODEL_ID || DEFAULT_MODEL;

  const generationConfig = {
    responseMimeType: "application/json",
    responseSchema,
    temperature: 0.2,
  };
  if (maxOutputTokens) generationConfig.maxOutputTokens = maxOutputTokens;

  const response = await fetchImpl(
    `${API_BASE}/models/${model}:streamGenerateContent?alt=sse`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
      },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: systemPrompt }] },
        contents: [{ role: "user", parts: userParts }],
        generationConfig,
      }),
    },
  );

  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    throw new Error(`Model call failed (${response.status}): ${detail.slice(0, 300)}`);
  }

  let accumulated = "";
  for await (const data of sseDataLines(response.body)) {
    let parsed;
    try {
      parsed = JSON.parse(data);
    } catch {
      continue;
    }
    // A streamed candidate may split its text across several parts.
    const parts = parsed?.candidates?.[0]?.content?.parts ?? [];
    const text = parts
      .map((part) => (typeof part?.text === "string" ? part.text : ""))
      .join("");
    if (text.length > 0) {
      accumulated += text;
      yield accumulated;
    }
  }
}

/** /v1/analyze streamer — thin wrapper over [streamGeminiJson]. */
export async function* streamGeminiAnalysis(request, env = process.env, fetchImpl = fetch) {
  const { image, selectionBbox, bookContext, options } = request;
  yield* streamGeminiJson(
    {
      systemPrompt: buildSystemPrompt(),
      userParts: [
        { text: buildUserPrompt({ selectionBbox, bookContext, options }) },
        { inlineData: { mimeType: "image/jpeg", data: image } },
      ],
      responseSchema: analysisResponseSchema,
    },
    env,
    fetchImpl,
  );
}

/** /v1/page-translate streamer — a full page is the largest generation. */
export async function* streamGeminiPageTranslation(request, env = process.env, fetchImpl = fetch) {
  const { image, bookContext } = request;
  yield* streamGeminiJson(
    {
      systemPrompt: buildPageTranslateSystemPrompt(),
      userParts: [
        { text: buildPageTranslatePrompt({ bookContext }) },
        { inlineData: { mimeType: "image/jpeg", data: image } },
      ],
      responseSchema: pageTranslationResponseSchema,
      maxOutputTokens: 16384,
    },
    env,
    fetchImpl,
  );
}

export function buildUserPrompt({ selectionBbox, bookContext, options }) {
  const lines = [
    "Circled region bounding box in image pixels: " +
      `x=${selectionBbox.x}, y=${selectionBbox.y}, w=${selectionBbox.w}, h=${selectionBbox.h}.`,
  ];
  if (bookContext?.title) {
    lines.push(
      `The page is from the book "${bookContext.title}"` +
        (bookContext.page ? `, page ${bookContext.page}.` : "."),
    );
  }
  lines.push(`Gloss language: ${options?.glossLanguage || "id"}.`);
  if (options?.transliteration === false) {
    lines.push("Transliteration fields may be left empty.");
  }
  return lines.join("\n");
}

export function buildPageTranslatePrompt({ bookContext }) {
  const lines = ["Translate this page word by word for interlinear display."];
  if (bookContext?.title) {
    lines.push(
      `The page is from the book "${bookContext.title}"` +
        (bookContext.page ? `, page ${bookContext.page}.` : "."),
    );
  }
  return lines.join("\n");
}

/** Splits a byte stream into SSE `data:` payloads. */
export async function* sseDataLines(stream) {
  const decoder = new TextDecoder();
  let buffer = "";
  const parse = (rawLine) => {
    const line = rawLine.replace(/\r$/, "");
    if (!line.startsWith("data:")) return null;
    const data = line.slice(5).trim();
    return data && data !== "[DONE]" ? data : null;
  };
  for await (const chunk of stream) {
    buffer += decoder.decode(chunk, { stream: true });
    let index;
    while ((index = buffer.indexOf("\n")) >= 0) {
      const data = parse(buffer.slice(0, index));
      buffer = buffer.slice(index + 1);
      if (data) yield data;
    }
  }
  // Flush a final frame that arrived without a trailing newline.
  const data = parse(buffer);
  if (data) yield data;
}
