# Algorithms & Intelligent Features

All algorithms are implemented from scratch in Java (no ML libraries) so every step can be explained in the
viva. Each one is behind an interface so it can be replaced — for example by an LLM-based classifier — without
touching the incident workflow.

## 1. Priority determination

### Default: weighted scoring (`WeightedScoringPriorityStrategy`)

```
score = Impact × Urgency × Business criticality × UserMultiplier

Impact / Urgency / Criticality : LOW 1 · MEDIUM 2 · HIGH 3 · CRITICAL 4
UserMultiplier                 : ≤10 users 1.0 · ≤100 1.25 · ≤1000 1.5 · >1000 2.0

P1 Critical  score ≥ 48
P2 High      score ≥ 24
P3 Medium    score ≥ 10
P4 Low       otherwise                (maximum possible score: 4×4×4×2 = 128)
```

**Rationale**
- *Multiplicative*, as suggested in the brief: one low factor pulls the result down sharply, so a total
  outage of the low-criticality printing service is not treated like a payment outage.
- The user multiplier is *logarithmic* (steps per ×10 users) so 5,000 affected users raises priority without
  dwarfing impact and urgency.
- Thresholds were calibrated on the brief's own example: "payment application unavailable" (CRITICAL impact,
  CRITICAL urgency, CRITICAL service, 500 users) scores 96 → P1; a single user with a minor problem on a
  medium service scores 4 → P4.
- **Business rule BR-PRI-3:** security-sensitive categories are never below P2.
- Every assessment returns its factors ("Impact HIGH (3); Urgency CRITICAL (4); …; Score 72.0"), shown in the
  UI as *why is this P1?* (explainable AI).

| Example | I × U × C × M | Score | Priority |
|---|---|---|---|
| Payment gateway down, 500 users | 4 × 4 × 4 × 1.5 | 96 | P1 |
| HR portal slow, 150 users | 3 × 3 × 2 × 1.5 | 27 | P2 |
| VPN drops for one user (critical network) | 2 × 2 × 4 × 1.0 | 16 | P3 |
| Printer jam, 20 users | 1 × 2 × 1 × 1.25 | 2.5 | P4 |

### Alternative: ITIL matrix (`ItilMatrixPriorityStrategy`)

Classic 4×4 impact/urgency matrix, raised one level for business-critical services and lowered one level for
low-criticality ones. The administrator can switch between the two at runtime (`PUT /api/admin/settings`).

## 2. Technician assignment (5 strategies)

All strategies share a **template method** (`AbstractAssignmentStrategy.rank`): exclude inactive, off-duty and
at-capacity technicians → apply the strategy's eligibility rule → score → sort (ties broken by lower workload,
then name). Workload is always *derived* from the incidents actually assigned (never a counter that can drift).

| Strategy | Eligible | Score |
|---|---|---|
| Skill-based | skilled in the incident category | 100 − 50 × utilisation |
| Load-based | everyone available | (1 − utilisation) × 100 |
| Round-robin | everyone available | minutes since last assignment (never assigned = first) |
| Experience-based | everyone available | 20 × seniority + 4 × min(incidents resolved on this service, 10) |
| **Smart** (default) | everyone available | see below |

**Smart recommendation (intelligent feature D)**

```
score = 40 × skillMatch            (1 if skilled in the category)
      + 25 × (1 − utilisation)
      + 20 × min(resolvedOnService + resolvedInCategory, 10) / 10
      + 15 × seniorityFit          (P1/P2 favour seniors; P3/P4 favour juniors so seniors stay free)
```

Each candidate comes with reasons, e.g. *"Skilled in DATABASE · 1/3 active incidents · 1 resolved on this
service, 2 in this category · EXPERT for a P2 incident"*. If the active strategy finds nobody (no one has the
skill), assignment falls back to load-based so a critical incident is never left unowned while someone is free;
if everyone is at capacity the incident is queued and supervisors are notified.

## 3. SLA management

- **Policy resolution (BR-SLA-1):** a service-specific policy for the priority wins over the default policy.
  Default targets are the brief's table (P1 5 min / 30 min … P4 4 h / 24 h); Payment Gateway and Core
  Database have stricter P1 targets.
- **Snapshot:** targets are copied into the incident's `SlaRecord` when the clock starts, so editing a policy
  never silently moves deadlines of incidents already in flight. Re-prioritisation re-targets the clock but
  keeps the original start time.
- **Response** clock stops at acknowledgement; **resolution** clock stops at resolution.
- **Pause:** while `AWAITING_INFO` the resolution clock is paused and the deadline is pushed back by the paused
  duration (ITIL "pending customer").
- **Monitor:** `SlaMonitorService` runs every 30 s. For every active incident it raises an *approaching*
  warning once at the policy's threshold (default 75 % of the window) and records each breach exactly once
  (flags on the record make the monitor idempotent).
- Boundary rule: exactly at the deadline is still within SLA; one second later is a breach.

## 4. Automatic escalation (intelligent feature C) — Chain of Responsibility

| Level | Triggered when | Notified |
|---|---|---|
| Team Lead | a P1/P2 is not acknowledged by its response deadline, or any resolution breach | the assignee's team lead |
| System Administrator | still unacknowledged one response window later, or a P1 resolution breach | system administrator |
| IT Manager | still unacknowledged two windows later, or a P1 overdue by > 50 % of its window | IT manager |

A cool-down of one response window between levels prevents jumping several levels in consecutive monitor runs.
Technicians and supervisors can also escalate manually one level at a time.

## 5. Duplicate detection (intelligent feature A)

1. **Text analysis** (`TextAnalyzer`): lower-case → tokenise → remove stop-words → light stemming → **synonym
   normalisation** (e.g. *unavailable / down / failing / unable* → `outage`; *transaction / pay / checkout* →
   `payment`).
2. **TF-IDF vectors** (`TfIdfVectorizer`): `tf = 1 + ln(count)`, `idf = ln((1+N)/(1+df)) + 1`, L2-normalised.
3. **Cosine similarity** between the new report and every open incident from the last 72 h.
4. Combined score `= 0.75 × text similarity + 0.25 × service affinity` (1 same service, 0.5 dependent service).

| Score | Action |
|---|---|
| ≥ 0.45 | flagged as *possible duplicate*, technician/lead notified, shown in the UI |
| ≥ 0.85 **and same reporter** | submission refused with `DUPLICATE_INCIDENT` (409) listing the matches; the reporter may resubmit with `acknowledgeDuplicates=true` |

The brief's example — *"Payment application unavailable"*, *"Payment transaction failing"*, *"Unable to make
payment"* — is a unit test: all three match each other while "Printer paper jam" does not. While the user types,
the form calls `POST /api/intelligence/duplicates` to warn them **before** submitting. When a parent incident is
resolved, its flagged duplicates are closed automatically and their reporters notified.

## 6. Incident correlation (intelligent feature B)

Uses the **service dependency graph** (e.g. Payment Gateway → Identity → Core Database → Core Network).

1. Take open incidents reported in the last 60 minutes.
2. For each service *R*, collect incidents whose service is *R* or depends on *R* (breadth-first traversal).
3. *Explanatory power* = share of *R*'s dependent services that are currently failing, + a bonus if *R* itself
   has direct reports. Single-service clusters count half.
4. Greedily pick the best root, remove the incidents it explains, repeat.

This prefers the most specific common ancestor: in the demo, Payment, HR Portal and ERP failures plus a
database alert are attributed to **Core Database** (not to the core network, which everything depends on).

## 7. Automatic categorisation (intelligent feature F) — Multinomial Naive Bayes

- **Training data:** each category's keywords (weight ×3), its name and description, plus every historical
  incident with a category. New categories work immediately from their keywords; the model improves as
  incidents are resolved (the `KnowledgeBaseObserver` marks it for retraining).
- `P(c | words) ∝ P(c) · Π P(w | c)` with Laplace smoothing `(count + 1) / (total + |V|)`, computed in log space.
- **Calibration:** raw Naive Bayes is over-confident because it treats every word as independent evidence, so
  log-scores are divided by √(number of tokens) (a temperature) before the softmax.
- Returns the category, a confidence, runner-ups and the words that drove the decision ("signals").
- If the reporter does not pick a category, the suggestion is used when confidence ≥ 20 %, otherwise
  APPLICATION is used and the incident is visibly marked "AI-classified" for a human to correct.

## 8. Predictive SLA risk (intelligent feature E)

```
risk = 0.40 × fraction of the resolution window already used
     + 0.30 × prediction pressure  = expected remaining work ÷ time remaining (capped at 1)
                                     where expected work = median resolution time of the category
     + 0.15 × ownership gap         (1 unassigned, 0.5 not acknowledged)
     + 0.15 × assignee utilisation

HIGH ≥ 0.70 · MEDIUM ≥ 0.45 · LOW otherwise · BREACHED / PAUSED reported as such
```

Unlike the 75 % warning, this flags an incident that is only 40 % through its window when similar incidents
usually take much longer than the time left. Shown on technician queues and the manager's "at risk" list.

## 9. Suggested resolutions (intelligent feature G)

Every resolved incident is a knowledge-base article. For an open incident the same TF-IDF engine ranks resolved
incidents: `relevance = 0.8 × text similarity + 0.1 × same service + 0.1 × same category`, returning the top
three fixes with their root causes.
