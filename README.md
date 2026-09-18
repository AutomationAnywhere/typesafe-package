# TypeSafe Package

An Automation Anywhere custom package that wraps [TypeSafe](https://typesafe.ai)'s Jev model, letting a bot ask a **typed, structured question** about a piece of text and get back a precise, machine-usable answer — a probability, a category, or a score — instead of a paragraph of prose to parse.

Full reference documentation (parameters, return fields, troubleshooting): [`TypeSafe-Package-Documentation.docx`](TypeSafe-Package-Documentation.docx).

## How it works

TypeSafe's API (`POST https://api.typesafe.ai/v1/systemone`) accepts a block of text plus one or more "questions," each of one of three types — `noul` (yes/no), `choice` (categorical), or `score` (numeric scale) — and returns a typed answer per question, plus token usage.

This package exposes **one Automation Anywhere action per question type**, rather than a single action taking a mixed list of questions:

| Action | Question type | Key output |
|---|---|---|
| `EvaluateBoolean` | `noul` — yes/no | `probability` |
| `EvaluateChoice` | `choice` — categorical | `choice`, `probabilities`, `confidence` |
| `EvaluateScore` | `score` — numeric scale | `score`, `legend`, `confidence` |

Each action makes exactly one API call for exactly one question. This was a deliberate design choice over a single combined action: it's self-documenting on the Control Room canvas, keeps each action's input fields strictly typed to its question shape (a `DICTIONARY` of categories for `Choice`, a `LIST` of scale levels for `Score`), and lets a bot author drop in exactly the one decision they need.

Every action returns a `DICTIONARY` envelope — never throws on an API failure, always returns a result the bot can branch on:

```json
{
  "probability": "0.98",
  "model": "jev-latest",
  "input_tokens": "301",
  "output_tokens": "12",
  "billable_tokens": "301",
  "elapsed_ms": "2925",
  "status": "success",
  "error_message": ""
}
```

- **`billable_tokens`** — Jev's output tokens are not billed, so this always equals `input_tokens`. Provided so a bot can sum real cost without having to know that rule.
- **`elapsed_ms`** — wall-clock duration of the actual HTTP call, measured in the package's own Java code (`System.nanoTime()` around the request) — not estimated.
- **`status`/`error_message`** — on any failure (bad credential, network error, non-200 response), the action returns `status: "error"` with a message, rather than throwing an exception into the bot.

## Project layout

```
src/main/java/com/automationanywhere/botcommand/
  EvaluateBoolean.java / EvaluateChoice.java / EvaluateScore.java   — the three actions
  utils/TypeSafeClient.java     — HTTP client: builds the request, calls the API, times it
  utils/DictionaryHelper.java   — builds the shared success/error envelope

src/test/java/com/automationanywhere/botcommand/
  TestEvaluateBoolean.java / TestEvaluateChoice.java / TestEvaluateScore.java
  TestSupport.java              — loads the API key from .env for integration tests

src/main/resources/
  package.template, locales/en_US.json, icons/pkg.png
```

## Building

Requires **JDK 11** (the AA SDK's annotation processor breaks under JDK 17+ with this Gradle version):

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home
./gradlew clean build
```

Produces `build/libs/TypeSafePackage-<version>.jar`, ready to upload via Control Room → Manage → Packages.

## Testing

`./gradlew test` runs the TestNG suite. **These are real integration tests** — they call the live TypeSafe API using the key in a local, gitignored `.env` file at the repo root (format: `API_KEY: <your key>`). No mocks: each test asserts on the actual `DictionaryValue` returned, including one test that intentionally sends an invalid API key to confirm the error envelope (not an exception) comes back.

## Platform support

Runs on `WINDOWS` and `MAC_OS` Bot Agents.

## Demo automations

Two example TaskBots built against this package (not part of this repo's build) exercise all three actions on sample support tickets — categorizing urgency, department routing, and frustration — including one that benchmarks TypeSafe's 3-actions-per-ticket approach against a single general-purpose GenAI prompt call for the same information (see [`typesafe-vs-genai-comparison.md`](typesafe-vs-genai-comparison.md) for the results: TypeSafe came out roughly 1.5x faster and ~47x cheaper per ticket).
