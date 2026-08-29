import { streamGeminiAnalysis } from "./gemini.js";

const MAX_IMAGE_BASE64_CHARS = 1_500_000; // ~1.1MB decoded; the app sends ≤300KB

/**
 * Validates an /v1/analyze request body. Returns a string error message, or
 * null when the body is acceptable.
 */
export function validateAnalyzeRequest(body) {
  if (!body || typeof body !== "object") return "Body must be a JSON object";
  if (typeof body.image !== "string" || body.image.length === 0) {
    return "image (base64 JPEG) is required";
  }
  if (body.image.length > MAX_IMAGE_BASE64_CHARS) return "image is too large";
  const bbox = body.selectionBbox;
  if (!bbox || typeof bbox !== "object") return "selectionBbox is required";
  for (const key of ["x", "y", "w", "h"]) {
    if (!Number.isFinite(bbox[key]) || bbox[key] < 0) {
      return `selectionBbox.${key} must be a non-negative number`;
    }
  }
  if (bbox.w === 0 || bbox.h === 0) return "selectionBbox must not be empty";
  return null;
}

/**
 * Extracts the (possibly still growing) vocalizedText value from a partial
 * JSON stream, so the app can show harakat before the full analysis lands.
 */
export function extractPartialVocalizedText(accumulatedJson) {
  const match = /"vocalizedText"\s*:\s*"((?:[^"\\]|\\.)*)/.exec(accumulatedJson);
  if (!match) return null;
  try {
    return JSON.parse(`"${match[1]}"`);
  } catch {
    return null;
  }
}

/**
 * Runs one analysis and reports progress through the sink — an object with
 * `partial(vocalizedText)`, `complete(result)`, and `error(message)`. The
 * model streamer is injectable for tests.
 */
export async function runAnalysis(body, sink, streamer = streamGeminiAnalysis) {
  const validationError = validateAnalyzeRequest(body);
  if (validationError) {
    sink.error(validationError);
    return;
  }

  let lastJson = "";
  let lastPartial = "";
  let result;
  try {
    for await (const accumulated of streamer(body)) {
      lastJson = accumulated;
      const vocalized = extractPartialVocalizedText(accumulated);
      if (vocalized && vocalized !== lastPartial) {
        lastPartial = vocalized;
        sink.partial(vocalized);
      }
    }
    result = JSON.parse(lastJson);
  } catch (error) {
    sink.error(error instanceof Error ? error.message : "Analysis failed");
    return;
  }
  // Outside the try: a throwing complete-callback must not be reported to
  // the client as an analysis failure.
  sink.complete(result);
}
