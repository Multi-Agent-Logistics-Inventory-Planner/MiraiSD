# Phase 4–6 exit gate — native validation

## Tier

Standard

## Problem and outcome

Local JDK 21 execution of Mockito-backed inventory tests fails because Mockito cannot
self-attach its Byte Buddy agent. Restore reliable local native-gate execution and
record current validation evidence for the Phase 4–6 closeout.

## Durable context

- Specs: [multi-site data and API](../../docs/specs/multi-site-data-and-api.md)
- Plans: [Phase 6 gap closure](../../docs/plans/phase-6-gap-closure.md), [Phase 4–6 closeout](../../docs/plans/phase-4-6-closeout.md)

## Acceptance criteria

- AC-1: Maven Surefire starts test JVMs with the JaCoCo-provided agent argument intact and enables dynamic agent loading for Mockito on JDK 21.
- AC-2: Required Maven, contract, web, and Python native gates are run and their actual results are recorded.
- AC-3: The analytics/forecasting H2 order-fragility risk is reproduced and either fixed or formally retired without excluding either security IT.

## Tasks

- T-1: Reproduce the JDK 21 Mockito self-attachment failure and add the minimal Surefire configuration.
- T-2: Run the Maven unit/ArchUnit and IT suites; investigate the analytics/forecasting security ITs if they fail.
- T-3: Run and record contract freshness, web, contracts, and E2E gates.
