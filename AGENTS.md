# Execution Sensei — repository instructions

## Purpose and current state
Personal Android discipline assistant prototype built with Kotlin and Jetpack Compose, Room persistence, and Android accessibility/reminder features. Read `README.md` and the v2 product documents before planning work. This is a prototype, not an unbypassable device-control system.

## Constraints
- Be precise about Android accessibility, notification, and background-execution limitations.
- Do not claim guaranteed lockout or bypass prevention.
- Preserve explicit user control over permissions, settings, schedules, and emergency access.
- Protect locally stored goals, behavioral logs, and sensitive personal notes.
- Keep the sensei-style guidance user-directed; avoid deceptive or harmful coercion.
- Treat unimplemented v2 roadmap items as plans, not completed features.

## Working and testing
Inspect actual Gradle scripts and Android module structure before choosing build commands. Run targeted unit/Gradle tests and an emulator or device UI test where available. Document what was and was not verified, especially platform-specific behavior.
