# Execution Sensei Android architecture

## System goal
Build a local-first Android app that helps retrain thought under pressure: keep major commitments visible, detect drift early, confront weak patterns, and add friction when the user starts escaping into distraction.

## Current implementation status
This repository contains a working Android prototype originally built around execution enforcement. In v2, the architectural framing shifts toward a sensei-style discipline coach while retaining the existing local-first implementation.

## Tech stack
- Kotlin
- Jetpack Compose
- AndroidX ViewModel
- Room
- SharedPreferences
- AlarmManager
- BroadcastReceiver
- AccessibilityService
- Material 3 UI

## High-level module layout
Single-app module prototype.
Package root:
com.dustinnstroud.executionenforcer

Key source files:
- MainActivity.kt
- Data.kt
- Repository.kt
- ThreatEngine.kt
- Reminders.kt
- ShieldAccessibilityService.kt

## Architectural style
Pragmatic MVVM with a local repository layer.
Not yet a fully split multi-module architecture.
Chosen for speed, clarity, and a working prototype on a constrained environment.

## Runtime layers

### 1. UI layer
Implemented in Compose.
Responsibilities:
- render tabs and screens
- collect reactive state from MainViewModel
- gather user input for check-ins, tasks, and settings
- route users into Android settings for permissions
- surface intervention prompts and corrective language

Primary screen groups:
- Command / Today
- Goals
- Enforce
- Review

V2 direction:
- evolve Command into Today / Focus Frame
- add clearer coaching prompts and next-action compression
- show readiness state for enforcement capabilities

### 2. ViewModel layer
MainViewModel is the orchestration point.
Responsibilities:
- seed initial data on first run
- combine repository snapshot with UI-only state
- compute threat assessment and ranked next task
- expose event handlers for task completion, check-ins, settings changes, and intervention context

Reactive state:
- AppUiState
- selected tab
- current intervention package

V2 direction:
- add explicit coaching state
- track current target, correction prompt, and recommitment flow
- expose readiness/status for accessibility and notifications

### 3. Repository layer
ExecutionRepository mediates local storage and settings access.
Responsibilities:
- expose goal, task, and check-in flows
- expose combined AppSnapshot flow
- seed initial prototype data
- persist new tasks
- persist task completion
- persist check-ins
- persist settings changes

V2 direction:
- store coaching-specific entities such as distortions, recommitments, and life-domain snapshots
- move settings to reactive storage

### 4. Storage layer
Two persistence mechanisms are used.

#### Room database
Database: ExecutionDatabase
Entities:
- GoalEntity
- TaskEntity
- CheckInEntity

Reason for Room:
Structured local data with simple queries and low operational overhead.

#### SharedPreferences settings store
SettingsStore persists:
- manual threat override
- blocked package list
- harsh mode toggle
- reminders enabled toggle

Reason for SharedPreferences:
Simple key-value state with no need for relational modeling in the first prototype.

V2 direction:
- migrate settings to DataStore or a reactive equivalent
- optionally encrypt sensitive reflections / coaching history

## Domain model

### Current entities
GoalEntity:
- id
- title
- category
- deadlineEpochDay
- priority
- notes
- active

TaskEntity:
- id
- goalId
- title
- notes
- urgency
- impact
- threatReduction
- dueEpochMillis
- completed
- completedAtEpochMillis

CheckInEntity:
- id
- segment
- createdAtEpochMillis
- reflection
- alignmentScore

AppSettings:
- manualThreat
- blockedPackages
- harshMode
- remindersEnabled

### Proposed v2 entities
Not yet implemented, but architecturally appropriate:
- DistortionEvent
- Recommitment
- LifeDomainState
- DailyStandard
- FocusBlock

## Core decision engines

### ThreatEngine.assess
Inputs:
- current snapshot
- current time

Logic:
- determine how many required check-ins were missed today
- detect whether completed progress has been logged recently
- produce reasons for escalation
- calculate recommended threat score
- apply manual threat floor

Outputs:
- recommended threat
- effective threat
- human-readable reasons
- count of missed check-ins

V2 direction:
Threat should represent life drift, not only task drift.
Additional signals should eventually include:
- repeated distraction attempts
- repeated self-negotiation patterns
- neglected high-stakes life domains
- recommitment failure loops

### ThreatEngine.rankNextTask
Inputs:
- current snapshot
- current time

Scoring formula:
- urgency * 0.40
- impact * 0.35
- threatReduction * 0.25
- overdue bonus up to a capped value

Output:
- single RankedTask with linked goal title

V2 direction:
Use this as the backbone for “next clean move” selection, not just task ranking.

## Android services and system integrations

### Accessibility service
Class:
ShieldAccessibilityService

Purpose:
Add friction when the user attempts to open a blocked package.

Flow:
1. Observe TYPE_WINDOW_STATE_CHANGED events
2. Read blocked packages from SharedPreferences
3. Ignore self package and select system packages
4. If blocked package is foregrounded:
   - perform global HOME action
   - launch MainActivity with the blocked package in extras

This creates interruption without irreversible lockout.

V2 framing:
This service is a behavioral interruption layer, not the full product identity.
It supports the sensei function by creating a moment for correction.

### Reminder scheduling
Classes:
- ReminderScheduler
- CheckInReminderReceiver
- BootReceiver

Purpose:
Maintain the three daily check-in prompts.

Flow:
1. MainActivity schedules alarms
2. AlarmManager posts phase-specific reminders
3. BroadcastReceiver builds and posts notification
4. Boot receiver reschedules after restart

V2 framing:
Reminders should support ritualized discipline, not generic nudges.

## Screen map

### Today / Command tab
Purpose:
Immediate focus frame.

Current displays:
- threat state
- drift reasons
- intervention warning if opened from blocked app
- top-ranked next task card
- active goals with countdown text

V2 target:
- current target
- next physical action
- one correction prompt
- standard for this block

### Goals tab
Purpose:
Task and goal operational control.

Current displays:
- current tasks
- completion status
- weighted dimensions

V2 target:
- maintain planning here, but keep it subordinate to execution

### Enforce tab
Purpose:
Behavioral control center.

Current displays and actions:
- manual threat selector
- harsh mode toggle
- reminders toggle
- editable blocked package list
- permission launch button

V2 target:
- readiness state
- honest capability messaging
- interruption intensity controls

### Review tab
Purpose:
Retrospective visibility.

Current displays:
- recent check-ins
- active tasks snapshot
- latest reflection trail

V2 target:
- recurring distortion patterns
- where discipline held
- where excuses repeated
- next correction to practice

## Constraints and honesty boundary
- Android permissions are user-controlled
- Accessibility can be disabled by the user
- the current implementation cannot guarantee hard lockout
- the system should be described as focused guidance + friction, not absolute control

## Recommended next architectural steps
1. Make settings reactive
2. Add readiness checks for notifications and accessibility
3. Add validation guards for user input
4. Add alarm cancellation and exact-alarm capability handling
5. Introduce coaching-specific data structures
6. Evolve threat assessment into broader drift assessment
7. Add domain-level weekly review support
