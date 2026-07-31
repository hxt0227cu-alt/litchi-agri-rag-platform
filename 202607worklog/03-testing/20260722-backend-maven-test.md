# Backend Maven test - 2026-07-22

## Environment

- JDK: Eclipse Temurin 17.0.19
- Maven: 3.9.11
- OS: Windows 11 x64
- Command: `mvn -B test`

## Initial result

- Tests: 37
- Failures: 0
- Errors: 3
- Duration: 14 minutes 58 seconds, including the first dependency download

All three errors occurred in `AgentServiceTest`. The synchronous compatibility entry point generated a run ID and immediately transitioned to `planning`, but unlike the asynchronous entry point it had not persisted the initial `created` state. The state transition therefore could not find the run.

## Fix

Both synchronous and asynchronous entry points now create and persist the same initial response before execution. This keeps compatibility behavior aligned with the V2 persistent state machine instead of weakening persistence checks in tests.

## Verification result

- Tests: 37
- Failures: 0
- Errors: 0
- Skipped: 0
- Result: `BUILD SUCCESS`
- Duration: 3 minutes 26 seconds

The console displayed mojibake for some Chinese log messages because Maven used the Windows GBK console encoding. Source files and test behavior were unaffected; UTF-8 console encoding should be configured separately.
