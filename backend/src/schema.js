/**
 * Structured-output schema for /v1/analyze (plan §5), in Gemini's
 * responseSchema dialect (OpenAPI-flavored JSON schema).
 */
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
