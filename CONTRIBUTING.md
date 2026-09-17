# Contributing to DonutMail

Thanks for helping improve DonutMail.

## How to contribute

1. Fork the repository and create a branch from `main`.
2. Make a focused change (one concern per PR when practical).
3. Run `mvn -B test` (and `mvn -B package` if you touch packaging).
4. Open a pull request describing the problem and solution.

## Coding expectations

- Match existing package layout under `io.nightbeam.donutmail`.
- Prefer small, readable changes over broad refactors.
- Keep Folia safety in mind: use `SchedulerAdapter` for async/sync work.
- Do not commit secrets, local server configs, or `target/` build output.

## Licensing

By contributing, you agree that your contributions are licensed under the Apache License 2.0 unless you state otherwise in the PR.
