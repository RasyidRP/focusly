# focusly.

A hyper-focused, offline-first task manager engineered to solve the "activation energy" problem for neurodivergent brains. 

Built with Kotlin and Jetpack Compose, **focusly.** abandons the guilt of traditional to-do lists. Instead, it forces users to prioritize the signal over the noise through Daily Highlights, Micro-Commitments, and deep-work timers. 

<p align="center">
  <img src="https://github.com/user-attachments/assets/defca04c-9bea-4365-85df-662bf7465b22" width="220" />
  <img src="https://github.com/user-attachments/assets/53d4f519-e876-4f6a-9eb2-ad233606d3c8" width="220" />
  <img src="https://github.com/user-attachments/assets/c9f9a89e-612d-4e0c-be0e-ab49ffa6fea8" width="220" />
  <img src="https://github.com/user-attachments/assets/0328c224-a74d-4e0e-9436-18e2c1cc1ae1" width="220" />
</p>

### [Download Latest Release (v2.0.0)](https://github.com/RasyidRP/focusly/releases/tag/v2.0.0)

## The Mission
Traditional productivity apps assume you have the activation energy to start working. Staring at a massive wall of pending tasks often leads to paralysis rather than productivity. **focusly.** is designed to cut through mental fog by enforcing a strict framework:
1. **Brain Dumps:** Clear your mind of chaotic thoughts.
2. **The Signal (Daily Highlight):** Pick exactly one task that makes the day a win.
3. **The Noise:** Everything else is secondary.
4. **Kaizen:** Master your workflow 1% at a time.

## Tech Stack
* **Language:** 100% Kotlin
* **UI:** Jetpack Compose (Material 3)
* **Local Database:** Room (SQLite)
* **Asynchronous Operations:** Coroutines & Kotlin Flow
* **Architecture:** MVVM (Model-View-ViewModel)
* **Native APIs:** AlarmManager, MediaPlayer, Storage Access Framework (SAF), Foreground Services

## Engineering Challenges Solved

### 1. Breaking the Lock Screen (Full-Screen Intents)
To ensure the app acts as a strict productivity enforcer, timers need to demand attention exactly at zero. 
* **Challenge:** Android's Doze mode and background execution limits often delay or silence notifications when the device is locked.
* **Solution:** Engineered a robust Foreground Service using `NotificationCompat.PRIORITY_HIGH` combined with an explicit `setFullScreenIntent`. This allows the background timer to successfully bypass the lock screen and wake the device directly into the app's Full Screen UI the millisecond a deep-work sprint ends.

### 2. Custom Audio Pipeline & Persistable Permissions
* **Challenge:** Relying on Android's default `RingtoneManager` for custom user-selected audio files (e.g., MP3s from the Downloads folder) is notoriously brittle and prone to silent failures. Furthermore, URI access is revoked once the app closes.
* **Solution:** Implemented `takePersistableUriPermission` via the Storage Access Framework to permanently lock file access. Built a custom audio engine using native `MediaPlayer` routed explicitly through `AudioAttributes.USAGE_ALARM`, guaranteeing custom alarms play reliably even if the user's media volume is muted.

### 3. The Data Vault (Zero-Dependency Serialization)
* **Challenge:** Creating a robust export/import backup system without bloating the app with heavy third-party parsing libraries (like Gson/Moshi) or triggering dependency resolution conflicts.
* **Solution:** Leveraged Android's native `org.json` library to build a lightweight, custom serialization engine. The `TaskViewModel` asynchronously reads and writes beautifully formatted JSON directly to the user's local storage or Google Drive via Android's `ActivityResultContracts.CreateDocument`, keeping the Kaizen history safe and strictly offline.

### 4. Safe State Hoisting in Drag-and-Drop UI
* **Challenge:** Implementing complex, visually smooth drag-and-drop reordering in Jetpack Compose's `LazyColumn` while simultaneously observing live database streams (`Flow`) can cause severe UI jitter and index out-of-bounds crashes.
* **Solution:** Decoupled the visual drag state from the database update logic. Reordering operations are handled via a local UI state map first, providing instant 60fps visual feedback, and only pushed to the Room database via a Coroutine once the `onDragEnd` event fires.

## Core Features
* **Timer Service:** Drift-free countdowns via Foreground Services.
* **Picture-in-Picture (PiP):** Seamless transition to a floating timer when leaving the app.
* **Morning Primer:** A daily `AlarmManager` BroadcastReceiver that nudges the user to set their Daily Highlight.
* **Kaizen Analytics:** Tracks streaks, calculates time-estimation accuracy, and visualizes task distribution over time.
* **Weekly Review:** Automatically detects "stale" tasks that have been sitting for 72+ hours, forcing the user to either recommit or let them go.

## Developer
Developed by **Rasyid Rafi Pamuji**
