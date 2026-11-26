# Benchmark harness

The harness lives in the `orchestrator` module (`dev.blockwright.orchestrator.bench`). It starts N clients of a
profile, measures them and compares the numbers with the versioned budgets in `perf/budgets.json`.

## Running

```
./gradlew :orchestrator:bench                         # local, report only
./gradlew :orchestrator:bench -Pbench.strict=true     # CI mode
./gradlew :orchestrator:bench -Pbench.profile=stub    # one profile
```

Output: `orchestrator/build/reports/bench/bench-report.json` (machine readable) and `bench-summary.md`. Both are
overwritten per run and written even when the gate fails. The CI job `bench` runs strict on Linux and uploads the
folder as the `bench-report` artefact.

Exit codes: `0` pass, `1` a budget is exceeded (or a required profile has no target, or a metric failed), `2`
configuration or usage error (unknown metric key, non-positive budget, bad `schemaVersion`, a warm budget without a
warm sample). A breach prints `FAIL profile=P metric=M value=V budget=B unit=U`.

## Metrics

| Key | Meaning |
|---|---|
| `coldStartMs` | Process start to `READY` of the first client of the run (fresh JVM; the OS page cache is not dropped). |
| `warmStartMs` | Median start of the later launches (clients 2..N plus `warmRuns` relaunches). |
| `pssMiB` | Proportional set size of a client and its descendants, worst client, measured with all clients up. |
| `cpuIdlePercent` / `cpuActivePercent` | CPU time of a client and descendants over the idle / active window, percent of one core, worst client. Active = `startLoad()` is on. |
| `screenshotP95Ms` | 95th percentile (nearest rank) of the `screenshot()` round trip. |

PSS is read from `/proc/<pid>/smaps_rollup` (fallback: the sum of `/proc/<pid>/smaps`) and exists on Linux only. On
Windows and macOS it is reported `NOT_RUN` with a reason; there is no RSS substitute. A `NOT_RUN` metric is not
gated, except with `--strict`, where a budgeted `NOT_RUN` metric fails (CI runs strict on Linux, so a broken probe
cannot pass silently).

## Budgets file

```json
{ "schemaVersion": 1,
  "profiles": { "stub": { "required": true, "clients": 2, "warmRuns": 2, "idleSeconds": 2, "activeSeconds": 2,
                          "screenshots": 10, "startTimeoutSeconds": 60, "budgets": { "pssMiB": 256 } } } }
```

Budgets are absolute ceilings per profile. A required profile without a bench target fails the gate; a non-required
one is reported `NOT_RUN`. The `stub` profile gates the harness itself (a stand-in child JVM), not the product; its
ceilings are generous. `logic`, `snapshot` and `live` are listed without budgets until a real target exists.

## Adding a profile

1. Implement `BenchTarget` (and `BenchClient`) for the real client; `ProcessBenchTarget` covers anything that speaks
   the line protocol `READY`, `SHOT` -> `SHOT ok`, `LOAD on|off`, `QUIT`.
2. Register it in `BenchTargets.forProfile`.
3. Add its budgets to `perf/budgets.json`.

## Report schema

`BenchReport` (`schemaVersion` 1): `startedAt` (informational), `gitSha` (`GITHUB_SHA` or null), `os`, `arch`, `jdk`,
`cpus`, `strict`, `passed`, and per profile its configuration plus one entry per metric with `status`
(`PASS|FAIL|NOT_RUN`), `samples`, `aggregate`, `budget` and `reason`.
