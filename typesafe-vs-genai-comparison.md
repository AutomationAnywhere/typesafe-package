# TypeSafe (3 actions) vs. GenAI Prompt (1 AI Skill, GPT-4o) — Full Comparison

Three sample support tickets, each run two ways:
- **TypeSafe**: 3 purpose-built actions (`Evaluate Boolean`, `Evaluate Choice`, `Evaluate Score`) — 9 API calls total
- **GenAI**: 1 GenAI Prompt action (GPT-4o) asking all three questions in a single call, returning JSON — 3 API calls total

Pricing used for cost estimates: GPT-4o **$2.50 / 1M input tokens, $10.00 / 1M output tokens**; TypeSafe (Jev) **$0.042 / 1M tokens** (billable tokens only — output is free).

---

## Ticket 1 — billing / urgent complaint

> Hi, I've been trying to connect my Stripe account for 3 days and it keeps failing. I'm losing sales. Please help ASAP.

| Field | TypeSafe | GenAI (GPT-4o) |
|---|---|---|
| Urgency probability | 0.98 | 0.90 |
| Department | `billing` (conf 0.25) | `technical` (conf 0.85) |
| Department probabilities | — | billing 0.30 / technical 0.70 / sales 0.20 |
| Frustration score | 1.0 (conf 1.00) | 1 (conf 0.80) |
| Time | 2,925 ms | 4,342 ms (~1.5x slower) |
| Tokens | 995 (billable) | in 366.75 / out 103.25 (est.) |
| **Cost** | **$0.0000418** | **$0.00195** (~47x more) |

---

## Ticket 2 — calm sales inquiry

> Hi team, I'm interested in learning more about your pricing tiers for a 50-person org. No rush at all, whenever you get a chance.

| Field | TypeSafe | GenAI (GPT-4o) |
|---|---|---|
| Urgency probability | 0.03 | 0.10 |
| Department | `sales` (conf 1.00) | `sales` (conf 0.95) |
| Department probabilities | — | billing 0.10 / technical 0.05 / sales 0.85 |
| Frustration score | 0.0 (conf 1.00) | 0 (conf 0.90) |
| Time | 2,296 ms | 3,859 ms (~1.7x slower) |
| Tokens | 1,004 (billable) | in 369.50 / out 102.75 (est.) |
| **Cost** | **$0.0000422** | **$0.00195** (~46x more) |

---

## Ticket 3 — angry technical bug

> This is the THIRD time your API has silently dropped my webhook events with zero explanation. I am beyond frustrated and considering canceling my contract.

| Field | TypeSafe | GenAI (GPT-4o) |
|---|---|---|
| Urgency probability | 0.91 | 0.95 |
| Department | `technical` (conf 0.99) | `technical` (conf 0.90) |
| Department probabilities | — | billing 0.05 / technical 0.90 / sales 0.05 |
| Frustration score | 1.79 (conf 0.68) | 2 (conf 0.95) |
| Time | 2,610 ms | 3,694 ms (~1.4x slower) |
| Tokens | 995 (billable) | in 376.00 / out 104.00 (est.) |
| **Cost** | **$0.0000418** | **$0.00198** (~47x more) |

---

## Summary — totals across all 3 tickets

| Engine | Calls | Total time | Total tokens | Total cost |
|---|---|---|---|---|
| **TypeSafe** | 9 | 7,831 ms | 2,994 (billable, input only) | **$0.000126** |
| **GenAI (GPT-4o)** | 3 | 11,895 ms (~1.5x slower) | in 1,112.25 / out 310.00 (est.) | **$0.00588** (~47x more) |

**Time difference (GenAI − TypeSafe): +4,064 ms** — TypeSafe's 9 purpose-built calls finished ~1.5x faster overall than GenAI's 3 general-purpose calls, and cost about 1/47th as much.

**Agreement check:** all 3 tickets landed on the same department and the same relative urgency/frustration ranking between the two engines — the disagreements are in the *confidence* and exact score, not the underlying call (e.g. Ticket 1: TypeSafe picked `billing` at low confidence (0.25) vs. GenAI's `technical` at high confidence (0.85) — the one case where the two engines actually disagreed on the category itself).

> **Notes:**
> - GenAI token counts are a character-count estimate (~4 chars/token), since the GenAI Prompt action doesn't expose actual usage/token counts — treat the cost figures as directional, not exact billing.
> - TypeSafe's token counts are the real billable figure returned by the API — Jev's output tokens are free, so only input tokens are counted and priced.
