import { streamGeminiPageTranslation } from "./gemini.js";

// A full page at ≤500KB JPEG is ~680K base64 chars; leave headroom.
const MAX_IMAGE_BASE64_CHARS = 2_000_000;

/**
 * Validates a /v1/page-translate request body. Returns a string error
 * message, or null when the body is acceptable.
 */
export function validatePageTranslateRequest(body) {
  if (!body || typeof body !== "object") return "Body must be a JSON object";
  if (typeof body.image !== "string" || body.image.length === 0) {
    return "image (base64 JPEG) is required";
  }
  if (body.image.length > MAX_IMAGE_BASE64_CHARS) return "image is too large";
  return null;
}

/**
 * Counts words emitted so far in the partial JSON stream — cheap progress
 * signal for the 20-60s a full page takes.
 */
export function countEmittedWords(accumulatedJson) {
  return accumulatedJson.match(/"arabic"/g)?.length ?? 0;
}

/**
 * Runs one page translation, reporting through the sink — an object with
 * `progress(wordCount)`, `complete(result)`, and `error(message)`. The
 * model streamer is injectable for tests.
 */
export async function runPageTranslation(body, sink, streamer = streamGeminiPageTranslation) {
  const validationError = validatePageTranslateRequest(body);
  if (validationError) {
    sink.error(validationError);
    return;
  }

  let lastJson = "";
  let lastCount = 0;
  let result;
  try {
    for await (const accumulated of streamer(body)) {
      lastJson = accumulated;
      const count = countEmittedWords(accumulated);
      if (count > lastCount) {
        lastCount = count;
        sink.progress(count);
      }
    }
    result = JSON.parse(lastJson);
  } catch (error) {
    sink.error(error instanceof Error ? error.message : "Translation failed");
    return;
  }
  // Outside the try: a throwing complete-callback must not be reported to
  // the client as a translation failure.
  sink.complete(result);
}
