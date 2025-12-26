# focusly.
**A modern, distraction-free focus timer built with Jetpack Compose.**

Focusly is a native Android productivity tool designed to help students and professionals manage their time effectively. It features a custom-built timer engine, background persistence, and seamless Picture-in-Picture (PiP) multitasking.

<p align="center">
  <img src="https://github.com/user-attachments/assets/defca04c-9bea-4365-85df-662bf7465b22" width="220" />
  <img src="https://github.com/user-attachments/assets/53d4f519-e876-4f6a-9eb2-ad233606d3c8" width="220" />
  <img src="https://github.com/user-attachments/assets/c9f9a89e-612d-4e0c-be0e-ab49ffa6fea8" width="220" />
  <img src="https://github.com/user-attachments/assets/0328c224-a74d-4e0e-9436-18e2c1cc1ae1" width="220" />
</p>

## Key Features

* **Smart Task Management:** Create, edit, and reorder tasks with a smooth drag-and-drop interface.
* **Persistent Background Timer:** Timers continue running accurately even when the app is closed, utilizing Android `Foreground Services` to prevent process killing.
* **Picture-in-Picture (PiP):** Keep an eye on your countdown while using other apps.
* **Overtime Tracking:** Automatically tracks time elapsed *after* the timer hits zero, ideal for measuring flow states.
* **Dynamic UI:** Smooth color transitions and animations powered by Jetpack Compose.

## Tech Stack

* **Language:** Kotlin
* **UI Toolkit:** Jetpack Compose (Material3)
* **Architecture:** MVVM (Model-View-ViewModel)
* **Concurrency:** Kotlin Coroutines & Flow
* **Local Data:** Room Database (SQLite)
* **Android APIs:** * `ForegroundService` (for reliable timing)
    * `PictureInPictureParams` (for multitasking)
    * `NotificationManager` (for real-time status bar updates)

## Highlights

### Custom Wheel Picker
Instead of standard widgets, Focusly uses a custom-built, snap-scrolling wheel picker for setting time durations, offering a more tactile and precise user experience.

### Drift-Free Timing Engine
Unlike basic timers that rely on thread sleeping (which causes time drift), Focusly calculates remaining time based on system clock deltas, ensuring 100% accuracy regardless of CPU throttling.

[[Download](https://github.com/RasyidRP/focusly/releases/tag/v1.0)]
