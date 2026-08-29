import test from "node:test";
import assert from "node:assert/strict";
import {
  validateAnalyzeRequest,
  extractPartialVocalizedText,
  runAnalysis,
} from "../src/analyze.js";
import { buildUserPrompt, sseDataLines } from "../src/gemini.js";
import { buildSystemPrompt, referenceBooks } from "../src/policy.js";

const validBody = {
  image: "aGVsbG8=",
  selectionBbox: { x: 10, y: 20, w: 100, h: 40 },
  bookContext: { title: "Jurumiyah", page: 4 },
  options: { transliteration: true, glossLanguage: "id" },
};

test("validateAnalyzeRequest accepts a well-formed body", () => {
  assert.equal(validateAnalyzeRequest(validBody), null);
});

test("validateAnalyzeRequest rejects missing image and empty bbox", () => {
  assert.match(validateAnalyzeRequest({ ...validBody, image: "" }), /image/);
  assert.match(
    validateAnalyzeRequest({ ...validBody, selectionBbox: { x: 0, y: 0, w: 0, h: 10 } }),
    /empty/,
  );
  assert.match(validateAnalyzeRequest(null), /object/);
});

test("extractPartialVocalizedText reads a still-open JSON string", () => {
  const partial = '{"selectedText":"الكلام","vocalizedText":"الْكَلَا';
  assert.equal(extractPartialVocalizedText(partial), "الْكَلَا");
  assert.equal(extractPartialVocalizedText('{"selectedText":"x"'), null);
});

test("runAnalysis streams partials then the parsed result", async () => {
  const chunks = [
    '{"selectedText":"الكلام","vocalizedText":"الْكَ',
    '{"selectedText":"الكلام","vocalizedText":"الْكَلَامُ","transliteration":"al-kalamu",' +
      '"contextBefore":"","contextAfter":"","words":[],"phraseGloss":"kalam","confidence":0.9}',
  ];
  async function* fakeStreamer() {
    for (const chunk of chunks) yield chunk;
  }

  const events = [];
  await runAnalysis(
    validBody,
    {
      partial: (v) => events.push(["partial", v]),
      complete: (r) => events.push(["complete", r]),
      error: (m) => events.push(["error", m]),
    },
    fakeStreamer,
  );

  assert.deepEqual(events[0], ["partial", "الْكَ"]);
  assert.equal(events.at(-1)[0], "complete");
  assert.equal(events.at(-1)[1].vocalizedText, "الْكَلَامُ");
  assert.ok(!events.some(([kind]) => kind === "error"));
});

test("runAnalysis surfaces validation and stream errors", async () => {
  const events = [];
  const sink = {
    partial: () => {},
    complete: () => events.push(["complete"]),
    error: (m) => events.push(["error", m]),
  };

  await runAnalysis({ image: "" }, sink);
  assert.equal(events.at(-1)[0], "error");

  async function* failing() {
    yield "{not-json";
  }
  await runAnalysis(validBody, sink, failing);
  assert.equal(events.at(-1)[0], "error");
  assert.ok(!events.some(([kind]) => kind === "complete"));
});

test("sseDataLines splits data payloads and skips [DONE]", async () => {
  async function* stream() {
    yield Buffer.from('data: {"a":1}\n\ndata: {"b":');
    yield Buffer.from('2}\n\ndata: [DONE]\n\n');
  }
  const payloads = [];
  for await (const data of sseDataLines(stream())) payloads.push(data);
  assert.deepEqual(payloads, ['{"a":1}', '{"b":2}']);
});

test("system prompt embeds the manhaj policy and curated references", () => {
  const prompt = buildSystemPrompt();
  assert.match(prompt, /Ahlus Sunnah wal Jama'ah/);
  assert.match(prompt, /Al-Ajurumiyyah/);
  assert.match(prompt, /Bahasa Indonesia/);
  assert.ok(referenceBooks.fields.tafsir.includes("Tafsir Ibnu Katsir"));
});

test("user prompt carries bbox, book context, and gloss language", () => {
  const prompt = buildUserPrompt(validBody);
  assert.match(prompt, /x=10, y=20, w=100, h=40/);
  assert.match(prompt, /"Jurumiyah", page 4/);
  assert.match(prompt, /Gloss language: id/);
});
