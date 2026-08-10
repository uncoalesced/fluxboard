Key Dimensions Covered in the Memo:
Auto Backup Under the Hood:
Trigger Lifecycle: Backups are run by the system-level BackupManagerService only when a device is simultaneously idle, plugged into power, connected to Wi-Fi, and at least 24 hours have elapsed since the last backup
. The application notifies the OS of data changes via BackupManager.dataChanged()
.
The 25MB Ceiling & Silent Failures: Cloud backups are capped at 25MB per user
. If your sticker or GIF database pushes FluxBoard past this threshold, the OS silently aborts future backups and preserves the outdated snapshot without notifying the user
.
XML Scoping: Using data_extraction_rules.xml (Android 12+) and backup_rules.xml (Android 11-), we can target storage domains to include configurations and exclude sensitive database paths
.
Privacy and Ecosystem Mapping:
The "Unlocked Device" Vulnerability: Standard Android backups are end-to-end encrypted starting in Android 9, using the lock screen passcode to wrap the encryption key before escrowing it to Titan HSM enclaves
. However, if the user has no screen lock, the backup payload is decrypted and accessible to Google
. This report explains how setting disableIfNoEncryptionCapabilities="true" acts as a necessary defense
.
FOSS Keyboards and Privacy-First Apps: We benchmarked the designs of comparable privacy-centric apps:
FlorisBoard opts into standard backup but excludes personal user data, framing the system-managed transfer as distinct from the app's zero-network status
.
HeliBoard runs 100% offline
, completely rejecting cloud backups in favor of manual file exports
 and utilizing device-protected storage (/data/user_de/) for boot accessibility
.
Aegis Authenticator excludes raw TOTP secrets from the standard OS cloud pipeline, utilizing local encrypted exports instead
.
Signal Messenger explicitly disables system backups (allowBackup="false") to prevent any unencrypted leaks
.
Manual Local Export Architecture:
Storage & Encryption: Using Scoped Storage and the Storage Access Framework (SAF) via system intents
 allows FluxBoard to write backup ZIPs locally or to USBs without requiring legacy storage permissions
. The report outlines a zero-knowledge symmetric pipeline utilizing AES-256-GCM with a key stretched via PBKDF2 or Argon2id
.
The Zip-Slip Hazard: Manual archive extractions are highly vulnerable to path traversal exploits
. An attacker-crafted archive with ../ characters can overwrite critical system files
. The report details a strict canonical path validation pattern (target.canonicalPath.startsWith(destination.canonicalPath)) to eliminate this attack vector
.
The Recommended Architecture for FluxBoard
The memo recommends a hybrid backup strategy
:
Enable Scoped Auto Backup (allowBackup="true")
 but enforce the disableIfNoEncryptionCapabilities="true" constraint
.
Strictly Exclude sensitive databases (specifically, the custom typing dictionary and clipboard history Room databases) from all backup and transfer scopes
.
Optimize Asset Transfers: Exclude rich media folders (stickers and GIFs) from cloud-backup paths to keep payloads far below the 25MB cloud quota
, but allow them during direct device-to-device (D2D) migrations (where the 25MB cap does not apply)
.
Build a Zero-Knowledge Manual Export Tool: Implement an on-demand, AES-256-GCM encrypted ZIP backup utility under FluxBoard's advanced settings to let advanced users securely export and migrate their entire state (including dictionary history) using SAF
. Include canonical path checks in the unzip pipeline to prevent Zip-Slip vulnerabilities
.