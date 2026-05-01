# Plan 001: Scaffold demo-scalardl-app

- Status: **Executed 2026-05-01 — scaffold rendered, manifest written, smoke compile BUILD SUCCESSFUL**
- Created: 2026-05-01
- Skill: `scalardl-skills:scaffold-scalardl-springboot-app v0.3.0`
- Working directory: `/home/yu2/IdeaProjects/demo-scalardl-skills-03/`

## 1. Goal

Run the `scaffold-scalardl-springboot-app` skill to produce a Spring Boot + ScalarDL Java Client SDK project shell. The project will target a remote Ledger + Auditor pair at `192.168.214.129`. Contract / Function definitions are out of scope for this plan; they will be added later via `add-scalardl-contract` and `add-scalardl-function`.

## 2. Inputs (Q&A answers)

| # | Field | Value | Source |
|---|---|---|---|
| A1 | Project name | `demo-scalardl-app` | default |
| A2 | Java package | `com.example.demoscalardl` | default |
| A2a | Project description | `Spring Boot demo for the ScalarDL Java Client SDK` | default |
| A3 | ScalarDL SDK version | `3.13.0` | default |
| A4 | Spring Boot version | `3.5.7` | default |
| A5 | Java toolchain | `17` | default |
| A6 | Build tool | Gradle (Groovy DSL) | fixed |
| A7 | Output directory | `./demo-scalardl-app/` | default |
| A8 | Pull cert / properties from `generate-scalardl-config` | **no** | user — will overwrite properties manually |
| A9 | Ledger host | **`192.168.214.129`** | user |
| A10 | Ledger gRPC port | `50051` | default |
| A11 | Ledger privileged port | `50052` | default |
| A12 | Ledger TLS enabled | `false` | default |
| A13 | Client entity id | **`client`** | user |
| A14 | Ledger deployment shape | **Ledger + Auditor** | user |
| A14a | Auditor host | **`192.168.214.129`** | user |
| A14b | Auditor gRPC port | `40051` | default |
| A14c | Auditor privileged port | `40052` | default |
| A14d | Auditor TLS enabled | `false` | default (matches A12) |

## 3. What will be generated

Final layout under `/home/yu2/IdeaProjects/demo-scalardl-skills-03/demo-scalardl-app/`:

```
demo-scalardl-app/
├─ .scalardl-skills.json              ← project-marker manifest (consumed by add-* skills)
├─ build.gradle, settings.gradle, gradlew, gradlew.bat
├─ gradle/wrapper/
├─ src/main/java/com/example/demoscalardl/
│  ├─ Application.java
│  ├─ cli/RenderCli.java
│  ├─ config/ScalarDLProperties.java
│  ├─ controller/{ContractPreview,ContractRegister,ContractRegisterFromSource,ContractExecute,FunctionPreview,FunctionRegister,FunctionRegisterFromSource,Certificate}Controller.java
│  ├─ service/{ScalarDL,Contract,Function,JavaCompiler,CodeManagement}*Service|Generator.java
│  ├─ dto/{Contract,Function}Definition.java
│  └─ util/VersionUtil.java
├─ src/main/resources/
│  ├─ application.properties           ← server-host=192.168.214.129, auditor wiring on
│  ├─ static/index.html
│  ├─ contract-templates/{READ_ASSET,PUT_ASSET,PUT_ASSETS}.java.mustache
│  └─ function-templates/{UPSERT_RECORD,UPSERT_RECORDS}.java.mustache
├─ src/test/java/...                   (ApplicationTests, MustacheRenderSmokeTest)
├─ definitions/{contracts,functions}/
├─ generated/{contracts,functions}/
├─ compiled/{contracts,functions}/
├─ examples/.gitkeep                   ← empty; populated later via add-* skills
├─ cert/.gitkeep                       ← empty (A8 = no — user will drop client.scalar.local.cert/.key here)
└─ README.md
```

`.scalardl-skills.json` content:

```json
{
  "projectName": "demo-scalardl-app",
  "groupId": "com.example",
  "packageName": "com.example.demoscalardl",
  "projectDescription": "Spring Boot demo for the ScalarDL Java Client SDK",
  "scalardlSdkVersion": "3.13.0",
  "targetScalardlMinVersion": "3.13.0",
  "springBootVersion": "3.5.7",
  "javaVersion": "17",
  "clientEntityId": "client",
  "auditorEnabled": true,
  "createdBy": "scaffold-scalardl-springboot-app v0.3.0",
  "createdAt": "<UTC ISO-8601 at scaffold time>"
}
```

`application.properties` will set:

```
scalardl.server-host=192.168.214.129
scalardl.server-port=50051
scalardl.server-privileged-port=50052
scalardl.tls-enabled=false
scalardl.cert-holder-id=client
scalar.dl.client.entity.id=client
scalar.dl.client.cert_path=cert/client.scalar.local.cert
scalar.dl.client.private_key_path=cert/client.scalar.local.key
# Auditor (A14 = Ledger + Auditor)
scalardl.auditor-enabled=true
scalardl.auditor-host=192.168.214.129
scalardl.auditor-port=40051
scalardl.auditor-privileged-port=40052
scalardl.auditor-tls-enabled=false
```

(Exact key names follow the skill's `references/scalardl-properties-keys.md`. The values above are the source of truth from this plan; the skill's renderer is responsible for spelling each key correctly.)

## 4. Out of scope (deferred)

- **Cert / private key files**: A8 = no. `cert/` will only contain `.gitkeep`. Before any Contract `register` call succeeds, the user must place `cert/client.scalar.local.cert` and `cert/client.scalar.local.key` into the project. The plan does **not** generate these.
- **Contract / Function JSON definitions**: `examples/` stays empty. Run `/add-scalardl-contract` and/or `/add-scalardl-function` from inside `demo-scalardl-app/` to add them.
- **Server-side Auditor enablement**: this plan only configures the *client* to sign/verify Auditor. The Ledger at `192.168.214.129` must already be running with Auditor wired up — otherwise client requests will fail with `DL-LEDGER-407003`.

## 5. Steps

1. Render the project shell into `/home/yu2/IdeaProjects/demo-scalardl-skills-03/demo-scalardl-app/`.
2. Write `.scalardl-skills.json` manifest at the project root.
3. Run smoke compile: `cd demo-scalardl-app && ./gradlew compileJava --no-daemon`.
   - On failure: surface the compiler output verbatim, do not auto-fix.
4. Print the skill's completion message (next-step hints).

## 6. Risks / known caveats

- **Contract immutability**: any Contract registered against the remote Ledger at `192.168.214.129` is permanent. The skill warns; recorded here for traceability.
- **Auditor mismatch**: if the remote Ledger is *not* in fact configured with Auditor, every client call will fail. User confirmed A14 = Ledger + Auditor; we trust that.
- **Java 17 bytecode vs Ledger JRE**: SDK 3.13.0 ships with Ledger 3.13.0+ which runs on JRE 21 — Java 17 bytecode loads fine. If the operator runs Ledger ≤ 3.12.x, the Contract register will fail with `UnsupportedClassVersionError`. The skill blocks SDK < 3.13.0 at scaffold time, so this can only happen if SDK / server versions drift.
- **Smoke compile** requires network access for Gradle to fetch dependencies (Spring Boot 3.5.7, ScalarDL SDK 3.13.0, etc.). If offline, step 3 will fail.

## 7. Approval

Awaiting user "go" / "approve" before executing. No files outside this `docs/` directory have been written yet.
