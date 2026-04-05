# <img src="img/miku.jpg" width="64" height="64" align="center"> MikuBackup 📦

**MikuBackup** is a production-ready Minecraft Paper/Spigot plugin written in Kotlin. It provides automated, scheduled backups of your server worlds, compresses them into ZIP archives, and securely uploads them to Google Drive.

## ✨ Features

*   **📅 Robust Scheduling**: Three flexible modes to fit your needs:
    *   `FIXED_INTERVAL`: Run every X minutes.
    *   `DAILY_TIME`: Run at specific times every day (e.g., 03:00, 15:00).
    *   `CRON`: Full Quartz cron expression support for complex schedules.
*   **☁️ Google Drive Integration**:
    *   Uses **Google Drive API v3**.
    *   Secure authentication via **Service Account** JSON.
    *   Uploads to a specific folder ID.
*   **🧹 Automatic Cleanup**: Keep your storage tidy by automatically deleting old backups both locally and on Google Drive.
*   **⚡ Performance Optimized**:
    *   Uses **Kotlin Coroutines** for non-blocking I/O (zipping and uploading).
    *   Synchronous world saving on the main thread ensures data integrity before backup.
*   **🛠️ Admin Commands**: Manual trigger, configuration reload, and next-run preview.

## 🚀 Requirements

*   **Minecraft**: Paper or Spigot 1.20 or newer.
*   **Java**: 21.
*   **Google Cloud**: A Service Account with Drive API enabled.

## ⚙️ Setup

1.  **Google Drive API**:
    *   Go to the [Google Cloud Console](https://console.cloud.google.com/).
    *   Create a new project and enable the **Google Drive API**.
    *   Create a **Service Account**, generate a **JSON key**, and download it.
    *   Rename the file to `service-account.json` and place it in `/plugins/MikuBackup/`.
    *   **Crucial**: Share your target Google Drive folder with the Service Account's email address (give it "Editor" permissions).

2.  **Configuration**:
    *   Edit `config.yml` in the plugin folder.
    *   Set `folder-id` to the ID of your Google Drive folder.
    *   Configure your preferred `schedule` mode.

## 💻 Commands

*   `/backup run`: Manually start a backup process immediately.
*   `/backup reload`: Reload the `config.yml` and restart the scheduler.
*   `/backup next`: Display the exact time for the next scheduled backup.

**Permission**: `mikubackup.admin` (Default: OP)

## 🛠️ Build

To build the plugin from source, ensure you have Java 21 and Gradle installed:

```bash
./gradlew shadowJar
```

The compiled JAR will be located in `build/libs/`.

---
Developed with ❤️ by Miku.
