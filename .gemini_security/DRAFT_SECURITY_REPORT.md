### Newly Introduced Vulnerabilities

*   **ID:** VULN-001
*   **Vulnerability:** Google Drive API Query Injection
*   **Vulnerability Type:** Security
*   **Severity:** Low
*   **Source Location:** `src/main/kotlin/me/miku/backup/service/DriveService.kt` line 84
*   **Sink Location:** N/A
*   **Data Type:** N/A
*   **Line Content:** 
    `"'${config.driveFolderId}' in parents and name contains '${config.backupPrefix}' and trashed = false"`
*   **Description:** The plugin concatenates configuration values (`driveFolderId` and `backupPrefix`) directly into a Google Drive API search query string without escaping single quotes in `cleanupOldBackups()`. If a server administrator configures a `backupPrefix` containing single quotes (e.g., `backup-' or '1'='1`), it can alter the logic of the Drive API query. This could lead to the unintended deletion of all files in the Google Drive associated with the OAuth token during the cleanup phase. While exploiting this requires access to modify the plugin's configuration file (which is a high-privilege action), unsanitized concatenation in queries is an insecure pattern and should be avoided. Note that this vulnerability only exists in `cleanupOldBackups()` (line 84), as it is properly escaped in `uploadFile()` (line 46).
*   **Recommendation:** Escape single quotes in the configuration values before using them in the query string in `cleanupOldBackups()`, just like it is done in `uploadFile()`. For example: `val safeFolderId = config.driveFolderId.replace("'", "\\'")` and `val safePrefix = config.backupPrefix.replace("'", "\\'")`.