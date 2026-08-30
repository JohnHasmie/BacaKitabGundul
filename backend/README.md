# Classic Book Reader — Analysis Backend

Minimal, zero-dependency Node service implementing `POST /v1/analyze`
(plan §5): the app sends a cropped JPEG of the circled region plus its
bounding box, the service streams back vocalization, i'rab, sarf, and
Indonesian glosses over SSE.

The religious-content policy (plan §5b — Ahlus Sunnah wal Jama'ah with the
salaf understanding, curated reference whitelist) is enforced **here**, in
the system prompt and `config/reference-books.json`. It is deliberately not
surfaced as UI text in the app.

## Running

```bash
GEMINI_API_KEY=...   # required — the only secret; never ships in the APK
MODEL_ID=gemini-2.5-flash        # optional, this is the default
RATE_LIMIT_PER_MINUTE=10         # optional, per bearer token / IP
PORT=8787                        # optional

npm start
```

Requires Node 20+. No `npm install` needed — there are no runtime
dependencies. Deploy anywhere a Node process runs (VPS, Railway, Render,
Fly.io, Cloud Run). Put the resulting URL into the app: **Atur → Server AI**.

## Endpoints

- `POST /v1/analyze` — request per plan §5; responds `text/event-stream`
  where each `data:` line is one of:
  - `{"type":"partial","vocalizedText":"…"}` — early harakat, sent as the
    model streams;
  - `{"type":"complete","result":{…}}` — the full schema result;
  - `{"type":"error","message":"…"}`.
- `POST /v1/page-translate` — full-page interlinear translation (plan
  §Fase 3): body `{ image: <base64 jpeg>, bookContext? }`; SSE frames are
  `{"type":"progress","wordCount":N}` while the page streams (a full page
  takes 20-60s), then `complete` with
  `{ lines: [ { words: [ { arabic, gloss, bbox } ] } ], confidence }`,
  or `error`.
- `GET /healthz` — liveness probe.

## Tests

```bash
npm test
```

Pure-unit: request validation, partial-JSON harakat extraction, stream
envelope shaping, SSE parsing, and the policy prompt — the model client is
injected, so no network or API key is needed.

## Later phases

`/v1/page-translate`, `/v1/enrich` (with mushaf-validated Qur'an quotes and
whitelist-only cross references), and `/v1/detect` land in Phases 3-4 on the
same skeleton.
