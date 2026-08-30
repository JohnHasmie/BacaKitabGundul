/**
 * Structured-output schema for /v1/analyze (plan §5), in Gemini's
 * responseSchema dialect (OpenAPI-flavored JSON schema).
 */
/**
 * Structured-output schema for /v1/page-translate (plan §5, §Fase 3):
 * one lines[] entry per physical line, words in reading order (RTL),
 * bbox normalized 0..1 and advisory only.
 */
export const pageTranslationResponseSchema = {
  type: "OBJECT",
  properties: {
    lines: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          words: {
            type: "ARRAY",
            items: {
              type: "OBJECT",
              properties: {
                arabic: { type: "STRING" },
                gloss: { type: "STRING" },
                bbox: {
                  type: "OBJECT",
                  properties: {
                    x: { type: "NUMBER" },
                    y: { type: "NUMBER" },
                    w: { type: "NUMBER" },
                    h: { type: "NUMBER" },
                  },
                  required: ["x", "y", "w", "h"],
                },
              },
              required: ["arabic", "gloss", "bbox"],
            },
          },
        },
        required: ["words"],
      },
    },
    confidence: { type: "NUMBER" },
  },
  required: ["lines", "confidence"],
};

export const analysisResponseSchema = {
  type: "OBJECT",
  properties: {
    selectedText: { type: "STRING" },
    vocalizedText: { type: "STRING" },
    transliteration: { type: "STRING" },
    contextBefore: { type: "STRING" },
    contextAfter: { type: "STRING" },
    words: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          arabic: { type: "STRING" },
          vocalized: { type: "STRING" },
          transliteration: { type: "STRING" },
          gloss: { type: "STRING" },
          irab: {
            type: "OBJECT",
            properties: {
              role: { type: "STRING" },
              reasoning: { type: "STRING" },
              caseMarker: { type: "STRING" },
            },
            required: ["role", "reasoning", "caseMarker"],
          },
          sarf: {
            type: "OBJECT",
            properties: {
              root: { type: "STRING" },
              pattern: { type: "STRING" },
              form: { type: "STRING" },
            },
            required: ["root", "pattern", "form"],
          },
        },
        required: ["arabic", "vocalized", "transliteration", "gloss", "irab", "sarf"],
      },
    },
    phraseGloss: { type: "STRING" },
    confidence: { type: "NUMBER" },
  },
  required: [
    "selectedText",
    "vocalizedText",
    "transliteration",
    "contextBefore",
    "contextAfter",
    "words",
    "phraseGloss",
    "confidence",
  ],
};
