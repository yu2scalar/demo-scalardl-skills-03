# Plan 002: Read-Modify-Write Contracts & Functions (V1_0_1)

- Status: **Implemented 2026-05-01 — 4 files written under generated/, javac --release 17 smoke-test BUILD CLEAN (exit 0, all 4 .class produced, major version 61).** No registration / execution against the Ledger (user does E2E themselves).
- Branch: `feature/rmw-contracts-functions`
- Created: 2026-05-01
- Working project: `demo-scalardl-app/`
- Reference: sibling project `~/IdeaProjects/demo-scalardl-skills-02/demo-scalardl-app/generated/{contracts,functions}/`

## 1. Goal

Add **Read-Modify-Write (RMW)** behaviour on top of the existing scaffold-rendered `PutAsset` / `PutAssets` Contracts and Functions, exercising the customisation path that ScalarDL skills produce a base shape and Claude Code adapts it.

- `qty` semantics changes from absolute write → delta-applied (existing + delta).
- The single-asset variant self-bootstraps when no prior asset exists (treats `current.qty` as 0 — same shape as the existing V1_0_0 falsey branch).
- The multi-asset variant adds a **conservation precondition**: sum of per-entry deltas must equal 0 (rejects net value creation/destruction). When violated:
  `sum of deltas across assets must equal 0 (got <n>)`
- The Contract publishes the post-modify `newQty` (single-asset) or per-entry `newQty` array (multi-asset) via `setContext(...)`. The paired Function reads it via `getContractContext()` and projects the same value into a ScalarDB row.

## 2. Confirmed Q&A decisions

| # | Topic | Decision | Reasoning recorded |
|---|---|---|---|
| Q1 | File names | **V1_0_1 lineage** (`PutAssetV1_0_1`, `PutAssetsV1_0_1`, `PutAssetFunctionV1_0_1`, `PutAssetsFunctionV1_0_1`) | This task tests "skill builds base shape → Claude Code customises". Lineage continuity matters more than strict SemVer. The breaking semantic change (absolute → delta) is acknowledged and accepted. |
| Q2 | Bootstrap Contract | **Not needed** | `!existing.isPresent()` branch in V1_0_1 itself handles the first write (treats `current.qty` as 0). The existing V1_0_0 also covers the no-record case via the same `existing.isEmpty()` branch — so the pattern is already in the lineage. |
| Q3 | Java release target | **`--release 17`** (override the original `--release 8` instruction) | The project's `JavaCompilerService.java:82` is hard-coded to `"--release", "17"`, and `PutAssetV1_0_0.java:64` already uses `Optional.isEmpty()` (Java 11+). Forcing `--release 8` would break consistency with the existing V1_0_0. User explicitly approved this override. |
| Q4 | Conservation check timing | **Early** (before `ledger.get`) | `sum(argument.assets[].qty) == 0` is computable from input alone. Running the check before the per-asset reads avoids unnecessary Snapshot reads on the abort path. Same error message as the reference. |
| Q5 | Function context shape | **`{"assets": [{"location","item","newQty"}, ...]}`** consumed by Function (no `contractArgument` access for keys) | Matches the sibling-project reference `MultiAssetReadModifyWriteItemFunctionV1_0_1`. Single-asset Function uses `{"newQty": N}` shape and consumes asset-id keys from `contractArgument` (since they're trivially identical). |
| Q6 | Plan doc location | `docs/plan-002-rmw-contracts-functions.md` | This file. |

## 3. New files (4 total)

All under `demo-scalardl-app/generated/`. **No existing V1_0_0 file is modified.**

### 3.1 `generated/contracts/PutAssetV1_0_1.java` (single-asset RMW Contract)

Behaviour:
1. Validate `argument.{location, item, qty}` are present.
2. Compute `assetId = location + ":" + item`, read `existing = ledger.get(assetId)`.
3. `delta = argument.qty.asInt()`.
4. **Branch (no prior asset)**: write `{location, item, qty: delta}` as a fresh asset; `setContext({"newQty": delta})`; return `{status:"created", assetId, newQty: delta}`.
5. **Branch (prior asset)**: `currentQty = existing.data().qty (or 0); newQty = currentQty + delta`; `ledger.put(assetId, {location, item, qty: newQty})`; `setContext({"newQty": newQty})`; return `{status:"modified", assetId, previousAge, previousQty, delta, newQty}`.

Java idioms:
- Use `!existing.isPresent()` for Java 8 portability (matches reference).
- No precondition rejecting negative resulting `newQty` (intentionally permissive — the user did not request one).

### 3.2 `generated/contracts/PutAssetsV1_0_1.java` (multi-asset RMW Contract with conservation precondition)

Behaviour:
1. Validate `argument.assets[]` is a non-empty array, and every entry has `{location, item, qty}`.
2. **Early conservation check**: `sumOfDeltas = sum(entry.qty for entry in assets)`. If non-zero, throw:
   `sum of deltas across assets must equal 0 (got <n>)`.
3. **First pass**: for each entry, compute `assetId`, read `existing = ledger.get(assetId)`, compute `currentQty (or 0)`, `delta`, `newQty`.
4. **Second pass**: for each entry, `ledger.put(assetId, {location, item, qty: newQty})` + accumulate `{location, item, newQty}` into the per-row context array.
5. `setContext({"assets": [{"location","item","newQty"}, ...]})`.
6. Return `{count, assets: [{assetId, status, previousAge, previousQty, delta, newQty}, ...]}`.

Note vs reference: reference does the conservation check after the first pass. We do it before the first pass per Q4 (b).

### 3.3 `generated/functions/PutAssetFunctionV1_0_1.java` (single-asset Function)

Behaviour:
1. Validate `contractArgument.{location, item}` (asset-id keys).
2. Read `ctx = getContractContext()`. Reject if `null` or missing `newQty`:
   `expected linked Contract to setContext({"newQty": ...})`.
3. `newQty = ctx.get("newQty").asInt()`.
4. Build `Put` against `ns_postgres.inventory` with `partitionKey={location}`, `clusteringKey={item}`, `intValue("qty", newQty)`.
5. `database.put(put)`.
6. Return `{status:"upserted", table, qty: newQty}`.

Pairs with `PutAssetV1_0_1` (linked via the `linkedContractId` in a follow-up Function definition JSON, if/when registered — out of scope for this plan; user does E2E themselves).

### 3.4 `generated/functions/PutAssetsFunctionV1_0_1.java` (multi-asset Function)

Behaviour:
1. Read `ctx = getContractContext()`. Reject if missing `assets` array:
   `expected linked Contract to setContext({"assets": [{location, item, newQty}, ...]})`.
2. For each entry in `ctx.assets[]`, validate `{location, item, newQty}` present, build a `Put` using **context** (not `contractArgument`) for keys + `qty`, and `database.put(put)`.
3. Return `{status:"upserted", count, table}`.

Pairs with `PutAssetsV1_0_1`.

## 4. Out of scope (not done in this plan)

- **No edits to V1_0_0**. The four V1_0_0 files generated by the skill stay untouched (Contract immutability discipline; the project's runtime regenerates them from `examples/*.json` on every `/preview` and `/register`, so any in-place edit would be lost on next render anyway).
- **No new `examples/*.json` definition files**. The user said "prefer hand-editing the generated `.java` over re-rendering". V1_0_1 lives only as `.java`; if/when the user wants to register it, they'll go through the `register-from-source` flow with `{"className":"PutAssetV1_0_1"}` etc.
- **No template changes** under `src/main/resources/contract-templates/` or `function-templates/` — the user explicitly said hand-edit the generated files, not modify templates.
- **No registration / execution** against the Ledger — the user does E2E themselves.

## 5. Smoke test plan

After writing each `.java`, compile each file alone with `javac` against the project's runtime classpath:

```bash
cd demo-scalardl-app
./gradlew compileJava --no-daemon                       # baseline (V1_0_0 must still compile)
CP=$(./gradlew --no-daemon -q dependencies --configuration runtimeClasspath \
       | grep -E '^[+\\\\| ]' | head 0)                 # not actually needed; use Gradle's classpath dump
# Simpler: have Gradle print the classpath, then javac each file
./gradlew --no-daemon -q printRuntimeClasspath > /tmp/cp.txt   # if such task exists; else use shell glob
javac --release 17 -cp "$(cat /tmp/cp.txt)" \
  -d /tmp/rmw-smoke \
  generated/contracts/PutAssetV1_0_1.java \
  generated/contracts/PutAssetsV1_0_1.java \
  generated/functions/PutAssetFunctionV1_0_1.java \
  generated/functions/PutAssetsFunctionV1_0_1.java
```

Implementation note: Gradle 8.14 doesn't ship a `printRuntimeClasspath` task by default. The actual smoke-test will use the simplest classpath-finding strategy that works (likely `./gradlew compileJava` + reading `~/.gradle/caches/modules-2/files-2.1/...` paths via Gradle's internal layout, or running `./gradlew dependencies` and parsing). Final command will be reported in the implementation phase.

Pass criterion: clean exit (no errors, no warnings beyond standard `Note: ... uses unchecked or unsafe operations`).

## 6. Risks / known caveats

- **`Optional.isEmpty()` vs `!isPresent()`**: I will use `!existing.isPresent()` in V1_0_1 to match the sibling-project reference, even though `--release 17` would let me use `isEmpty()`. The reference is the more battle-tested code; deviating would invite drift. The existing V1_0_0 uses `isEmpty()` — that file stays as-is.
- **`isEmpty()` on Jackson `ArrayNode`**: this is a Jackson library method (Jackson ≥ 2.10), not a JDK 11 language feature. Safe to use; the existing project uses it already.
- **Conservation check moved earlier than reference**: per Q4 (b). The reference's "after first pass" placement protects against a hypothetical future case where the read itself influences the deltas (it doesn't, currently). Today the early check is strictly cheaper and behaviourally equivalent.
- **Negative resulting `newQty` is allowed**. The user did not request a precondition. If they want one later (e.g. inventory must stay ≥ 0), it's an additive V1_0_2 — easy.
- **No `examples/*.json` for V1_0_1**: registering V1_0_1 requires the `register-from-source` flow (`POST /api/contracts/register-from-source -d '{"className":"PutAssetV1_0_1"}'`). Note that the project's runtime regenerates `generated/contracts/PutAssetV1_0_0.java` from `examples/PutAsset.json` on every `/preview` or `/register` call — V1_0_1 is unaffected because it's a different class file. **However**, if anyone later reruns `./gradlew render` with a V1_0_0-targeting JSON, only V1_0_0 is overwritten; V1_0_1 stays.

## 7. Approval needed

Awaiting user "go" / "approve" before editing code. Tasks #12-#14 stay in pending until this plan is approved.
