# Execution Sensei product spec

## Product intent
Execution Sensei is a personal Android-native discipline coach.
Its job is not generic task storage and not soft motivational coaching. Its job is to keep the user mentally aligned, catch drift early, confront weak thinking, and pressure a return to the next right move.

## Product identity
A brutally honest digital sensei for disciplined thought and action.

## Primary user
Single-user personal prototype for the owner of the device.
Not a multi-tenant product in v2.
Not a social accountability platform in v2.
Not a therapist replacement.

## Core outcome
Restructure day-to-day thinking toward clarity, focus, and disciplined execution by making avoidance more visible, self-negotiation harder, and re-entry into aligned action faster.

## Success criteria for the current prototype baseline
1. The app persists the user’s goals, tasks, check-ins, and enforcement settings locally.
2. The app presents one best next action based on weighted urgency, impact, threat reduction, and overdue pressure.
3. The app supports mandatory daily check-ins across morning, midday, and evening.
4. The app computes a drift/threat level from missed check-ins and lack of progress.
5. The app supports manual threat override.
6. The app stores a blocked-package list and harsh-mode preference.
7. The app can interrupt distraction attempts through an AccessibilityService path when enabled by the user.
8. The app can schedule reminder notifications for daily check-ins.
9. The app builds successfully into a debug APK on the current environment.

## V2 direction
V2 shifts the center of gravity from enforcement-first to sensei-first.

The app should train:
- attention control
- anti-escape awareness
- thought compression
- identity-based discipline
- rapid recovery from drift

## Non-goals
- No cloud sync
- No automatic messaging of other people
- No irreversible lockout
- No cross-device account system
- No fake therapy persona
- No external AI dependency required for core use
- No location tracking

## Product principles
- Local-first
- High-friction when drifting, low-friction when aligned
- Short reflections, not essay journaling
- Guidance should be sharp, brief, and corrective
- Visible standards and one clear next move
- Adapt for bad days without collapsing standards
- More dojo, less diary

## Feature set in the implemented prototype

### 1. Focus dashboard
The main dashboard shows:
- current effective threat level
- recommended threat level
- reasons for the threat recommendation
- the top-ranked next action
- active goals with deadline countdowns
- blocked-package intervention message when the app is opened from a distraction bounce

### 2. Goal management
The app stores goals with:
- title
- category
- deadline
- priority
- active flag

### 3. Task management
The app stores tasks with:
- title
- notes
- goal relationship
- urgency score
- impact score
- threat-reduction score
- due date/time
- completion state
- completion timestamp

### 4. Daily sensei check-ins
The app supports three daily check-in segments:
- morning
- midday
- evening

Each check-in records:
- segment
- reflection text
- alignment score
- timestamp

### 5. Threat engine
Threat level is derived from:
- missed required check-ins for the current day
- how long it has been since completed progress was logged
- optional manual threat floor

Threat levels:
- Green
- Yellow
- Orange
- Red

The effective threat is the higher of:
- recommended threat
- manual threat override

### 6. Enforcement settings
The enforce screen supports:
- manual threat override selection
- harsh mode toggle
- reminders toggle
- blocked-package list editing
- quick links to Accessibility settings

### 7. Distraction interception
When the AccessibilityService is enabled and a blocked package comes to the foreground, the service:
- sends the user to the home screen
- launches the app with the blocked package name attached
- surfaces an intervention message in the app

This is friction, not full lockout.

### 8. Reminder scheduling
The app schedules reminders for:
- morning check-in
- midday check-in
- evening check-in

Reminder delivery uses AlarmManager plus a BroadcastReceiver that posts notifications.
A boot receiver reschedules reminders after reboot.

## V2 coaching behaviors to add next
- identify likely avoidance pattern during check-ins
- require the user to name the next physical action
- explicitly distinguish strategy from escape
- track repeated cognitive distortions over time
- show neglected life domains, not just open tasks
- verify whether enforcement services are actually enabled before claiming readiness

## Data persistence
The prototype persists state locally using:
- Room database for goals, tasks, and check-ins
- SharedPreferences for enforcement settings

## Key user flows

### Morning alignment flow
1. Open app
2. Review current threat and next action
3. Submit morning check-in
4. Name the day’s standard and first clean move
5. Execute

### Drift interruption flow
1. User opens a blocked app
2. Accessibility service detects foreground package
3. User is bounced home
4. Execution Sensei opens with intervention context
5. User sees corrective language and must re-enter focus intentionally

### Pattern review flow
1. Open Review tab
2. Inspect recent check-ins
3. Inspect active tasks and completion state
4. Identify where thought drifted
5. Set the next correction to practice

## Constraints
- Android-only prototype
- User-controlled permissions required for Accessibility and notifications
- Enforcement is intentionally annoying but not irreversible
- App must remain functional without network access
- Android cannot guarantee total distraction prevention under current constraints

## Future extensions
- launcher replacement mode
- VPN-based domain blocking
- adaptive timeline rebalancing
- better drift heuristics based on usage stats
- low-capacity recovery mode
- encrypted local storage for sensitive reflections
- domain-based life map and weekly sensei review
