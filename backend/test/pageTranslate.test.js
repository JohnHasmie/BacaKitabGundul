import test from "node:test";
import assert from "node:assert/strict";
import {
  validatePageTranslateRequest,
  countEmittedWords,
  runPageTranslation,
} from "../src/pageTranslate.js";
import { buildPageTranslateSystemPrompt } from "../src/policy.js";
import { buildPageTranslatePrompt } from "../src/gemini.js";

const validBody = {
  image: "aGVsbG8=",
  bookContext: { title: "Jurumiyah", page: 12 },
};

const completeJson =
  '{"lines":[{"words":[' +
  '{"arabic":"الكلام","gloss":"perkataan","bbox":{"x":0.7,"y":0.1,"w":0.2,"h":0.05}},' +
  '{"arabic":"هو","gloss":"adalah","bbox":{"x":0.6,"y":0.1,"w":0.08,"h":0.05}}' +
  ']}],"confidence":0.9}';

test("validatePageTranslateRequest accepts a well-formed body", () => {
  assert.equal(validatePageTranslateRequest(validBody), null);
});

test("validatePageTranslateRequest rejects a missing or oversized image", () => {
  assert.match(validatePageTranslateRequest({}), /image/);
  assert.match(
    validatePageTranslateRequest({ image: "x".repeat(2_000_001) }),
    /too large/,
  );
  assert.match(validatePageTranslateRequest(null), /object/);
});

test("countEmittedWords counts words in a partial stream", () => {
  assert.equal(countEmittedWords('{"lines":[{"words":[{"arabic":"a"'), 1);
  assert.equal(countEmittedWords(completeJson), 2);
  assert.equal(countEmittedWords('{"lines":['), 0);
});

test("runPageTranslation streams progress then the parsed result", async () => {
  const chunks = [
    '{"lines":[{"words":[{"arabic":"الكلام","gloss":"perka',
    completeJson,
  ];
  async function* fakeStreamer() {
    for (const chunk of chunks) yield chunk;
  }

  const events = [];
  await runPageTranslation(
    validBody,
    {
      progress: (count) => events.push(["progress", count]),
      complete: (result) => events.push(["complete", result]),
      error: (message) => events.push(["error", message]),
    },
    fakeStreamer,
  );

  assert.deepEqual(events[0], ["progress", 1]);
  assert.deepEqual(events[1], ["progress", 2]);
  assert.equal(events.at(-1)[0], "complete");
  assert.equal(events.at(-1)[1].lines[0].words.length, 2);
  assert.ok(!events.some(([kind]) => kind === "error"));
});

test("runPageTranslation surfaces validation and truncated-JSON errors", async () => {
  const events = [];
  const sink = {
    progress: () => {},
    complete: () => events.push(["complete"]),
    error: (m) => events.push(["error", m]),
  };

  await runPageTranslation({}, sink);
  assert.equal(events.at(-1)[0], "error");

  async function* truncated() {
    yield '{"lines":[{"words":[{"arabic":"a"'; // stream ends mid-JSON
  }
  await runPageTranslation(validBody, sink, truncated);
  assert.equal(events.at(-1)[0], "error");
  assert.ok(!events.some(([kind]) => kind === "complete"));
});

test("page-translate prompts carry the policy, RTL ordering, and book context", () => {
  const system = buildPageTranslateSystemPrompt();
  assert.match(system, /Ahlus Sunnah wal Jama'ah/);
  assert.match(system, /right-to-left/);
  assert.match(system, /matn/);
  assert.match(system, /Bahasa Indonesia gloss/);

  const user = buildPageTranslatePrompt(validBody);
  assert.match(user, /"Jurumiyah", page 12/);
});
