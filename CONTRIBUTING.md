# Contributing

Thanks for helping with Mintaka. This guide covers building, testing and sending changes.

## Build

Requirements: JDK 17, the Android SDK (platform 37), Python 3.10+ for the tools.

```
cd launcher
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # once; the file is git-ignored
./gradlew testDebugUnitTest assembleDebug
```

## Test

- Launcher and companions: `cd launcher && ./gradlew testDebugUnitTest` (JVM unit tests, no device needed).
- Tools: `cd tools && python3 -m pytest`.

Add or update tests with your change. Logic should be testable without a device; keep Android classes thin.

## Layout audit

A debug-only layout auditor measures overlaps and clipped text on a real TV. With a debug build installed and the TV
reachable over adb:

```
python3 -I tools/audit/run_audit.py --host <tv-ip> \
    --scenario tools/audit/scenarios/home.json --out ./audit-out
```

Scenarios live in `tools/audit/scenarios/`. Exit code 1 means at least one high-severity finding. Run it for any change
that touches layouts.

## Code style

- Kotlin, plain Android Views. Do not add Compose.
- Do not add third-party runtime libraries without discussing it first; keeping the launcher dependency-free is a goal.
- The launcher module must not request the INTERNET permission. Network code belongs in a companion module.
- Keep it light: avoid work on the main thread, avoid large allocations, test on low-memory devices when you can.
- User-visible text should be plain and short, and must never show developer commands.
- Cross-module communication uses signature-permission broadcasts or providers.

## Pull request checklist

- [ ] `./gradlew testDebugUnitTest assembleDebug` passes
- [ ] `python3 -m pytest` passes in `tools/` if tools changed
- [ ] New behaviour has tests
- [ ] Layout audit run if layouts changed
- [ ] No new permissions, or the reason is explained in the PR
- [ ] No personal data, IP addresses or keys in code, docs or screenshots
- [ ] Docs and `CHANGELOG.md` updated

## Reporting issues

Use the issue templates. For security problems, follow [SECURITY.md](SECURITY.md) instead.
