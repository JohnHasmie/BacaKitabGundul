import { buildSystemPrompt } from "./policy.js";
import { analysisResponseSchema } from "./schema.js";

const DEFAULT_MODEL = "gemini-2.5-flash";
const API_BASE = "https://generativelanguage.googleapis.com/v1beta";

/**
 * Streams a structured analysis from Gemini. Async generator yielding the
 * accumulated JSON text after every chunk; the caller decides what counts as
 * a partial update. Model is configurable per deployment (plan §7: cheapest
 * viable first, model-agnostic layer).
 */
export async function* streamGeminiAnalysis(request, env = process.env, fetchImpl = fetch) {
  const apiKey = env.GEMINI_API_KEY;
  if (!apiKey) throw new Error("GEMINI_API_KEY is not configured");
  const model = env.MODEL_ID || DEFAULT_MODEL;

  const { image, selectionBbox, bookContext, options } = request;
  const userPrompt = buildUserPrompt({ selectionBbox, bookContext, options });

  const response = await fetchImpl(
    `${API_BASE}/models/${model}:streamGenerateContent?alt=sse`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
      },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: buildSystemPrompt() }] },
        contents: [
          {
            role: "user",
            parts: [
              { text: userPrompt },
              { inlineData: { mimeType: "image/jpeg", data: image } },
            ],
          },
        ],
        generationConfig: {
          responseMimeType: "application/json",
          responseSchema: analysisResponseSchema,
          temperature: 0.2,
        },
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
