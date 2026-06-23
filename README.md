# Execution Sensei

Execution Sensei is a personal Android prototype for disciplined cognition.

It is not a soft productivity app and not a generic life coach.
Its purpose is to catch drift early, narrow attention, confront weak thinking, and return the user to one clean next move.

Current state: v2 product direction on top of the existing working Android prototype.

Core thesis
- sensei-like guidance over generic coaching
- focused discipline over motivation
- short corrective prompts over long journaling
- friction when drifting, clarity when aligned

What exists in this repo
- Kotlin + Jetpack Compose Android app prototype
- local persistence with Room + SharedPreferences
- morning / midday / evening check-ins
- task / goal tracking
- threat scoring and next-action ranking
- accessibility-based distraction bounce
- reminder scheduling
- v2 product docs for the sensei direction

What v2 means here
This branch reframes the product from "execution enforcement" toward a stricter cognitive-discipline coach:
- trains thought compression
- detects avoidance and self-negotiation
- emphasizes return-to-target behavior
- uses enforcement as a support layer, not the whole identity

Important limitation
The current Android implementation is still a friction-based prototype, not a hard lockout system. Android platform limits mean the app should be described honestly: it can interrupt, redirect, pressure, and coach, but it cannot guarantee total prevention of bypass.

Key docs
- docs/product-spec.md
- docs/android-architecture.md
- docs/v2-sensei-direction.md

Build
- ./gradlew testDebugUnitTest assembleDebug

Environment note
This project was built in an ARM64 Crostini environment using a custom AAPT2 override configured in gradle.properties.
