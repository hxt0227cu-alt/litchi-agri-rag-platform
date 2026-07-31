# Local Verification Report

- Date: 2026-07-31
- OS: Windows 11 10.0.26200
- CPU: Intel Core i5-1240P, 16 logical processors
- Memory: 15.7 GB
- Java: OpenJDK 17.0.19
- Maven: 3.9.11
- Node: 22.23.1
- Python: 3.11.9

## Backend

Command: `cd backend; mvn -B test`

- Source files compiled: 93
- Test classes: 7
- Tests: 38
- Failures: 0
- Errors: 0
- Skipped: 0
- Result: BUILD SUCCESS

## Frontend

Command: `cd frontend; npm test`

- Result: `frontend access tests passed`

Command: `cd frontend; npm run build`

- Modules transformed: 1,729
- Vite build time: 24.96s
- Result: success with two chunk-size warnings
- Known warning: Three.js and Element Plus chunks exceed 600kB before gzip

## Agent evaluation dataset

Commands:

```powershell
python scripts/build-agent-eval-dataset.py
python benchmarks/evaluate_agent.py --validate-only
```

- Task count: 60
- Schema validation: `valid=true`
- Composition: 30 RAG, 20 Agent, 10 safety/failure tasks
- Model-quality gate: not executed because a complete model/vector environment was unavailable

## Integrity note

This report summarizes command output from the stated environment. CI repeats the build, tests, dataset validation, secret scan and filesystem scan after push.
