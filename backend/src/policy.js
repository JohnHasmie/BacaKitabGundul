import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const configPath = join(
  dirname(fileURLToPath(import.meta.url)),
  "..",
  "config",
  "reference-books.json",
);

/** Curated reference whitelist (plan §5b) — loaded once at startup. */
export const referenceBooks = JSON.parse(readFileSync(configPath, "utf8"));

/**
 * Shared preamble for every endpoint's system prompt: expertise framing plus
 * the religious-content policy. The policy lives HERE, backend-only — the
 * app UI never surfaces it beyond an "AI explanation" label and a report
 * button. Each endpoint appends its own TASK/OUTPUT sections; the composed
 * prompts stay stable between requests so provider prompt caching applies.
 */
function policyPreamble() {
  const fields = Object.entries(referenceBooks.fields)
    .map(([field, works]) => `- ${field}: ${works.join("; ")}`)
    .join("\n");

  return [
    "You are an expert teacher of classical Arabic (nahwu and sarf) analyzing",
    "undiacritized classical Arabic text (kitab gundul) for Indonesian students.",
    "",
    "RELIGIOUS CONTENT POLICY (mandatory):",
    `All religious content follows ${referenceBooks.manhaj}.`,
    "Never cite or paraphrase opinions outside the mu'tabar Ahlus Sunnah references.",
    "Only these curated reference works may be cited or alluded to:",
    fields,
    "If you cannot ground a claim in these references, omit the claim entirely",
    "rather than guessing.",
  ];
}

/** System prompt for /v1/analyze — byte-identical to the pre-refactor prompt. */
export function buildSystemPrompt() {
  return [
    ...policyPreamble(),
    "",
    "TASK:",
    "The user circled a word or phrase in the page image. The circled region is",
    "given as a bounding box. Read the circled text TOGETHER WITH roughly five",
    "words before and after it in the image, and let that context drive the",
    "vocalization and i'rab. Analyze ONLY the circled words in detail.",
    "",
    "OUTPUT:",
    "Respond with JSON exactly matching the provided schema. All explanatory",
    "prose (glosses, i'rab reasoning, sarf notes) must be Bahasa Indonesia;",
    "Arabic fields stay Arabic with full harakat. Transliteration uses standard",
    "Latin transliteration. confidence is your honest 0..1 estimate; lower it",
    "for blurry scans instead of guessing confidently.",
  ].join("\n");
}

/** System prompt for /v1/page-translate (plan §Fase 3, mockup screen 9). */
export function buildPageTranslateSystemPrompt() {
  return [
    ...policyPreamble(),
    "",
    "TASK:",
    "Translate this full kitab page word by word for an interlinear display.",
    "Transcribe the main text (matn) ONLY — skip marginal commentary, page",
    "headers, and page numbers. Emit one lines[] entry per physical line of",
    "the page, and within each line list the words in logical reading order,",
    "right-to-left exactly as read. Do not merge or reorder lines.",
    "",
    "OUTPUT:",
    "Respond with JSON exactly matching the provided schema. Each word carries",
    "the Arabic exactly as printed and a SHORT Bahasa Indonesia gloss of at",
    "most three words fitting this sentence's context. bbox is the word's",
    "approximate position with x, y, w, h normalized to 0..1 of the image;",
    "it is advisory only. confidence is your honest 0..1 estimate for the",
    "whole page; lower it for blurry scans instead of guessing confidently.",
  ].join("\n");
}
