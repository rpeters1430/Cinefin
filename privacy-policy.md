# Privacy Policy for Cinefin

**Unofficial Jellyfin Android Client**

**Effective Date:** January 27, 2026
**Last Updated:** January 27, 2026

This Privacy Policy describes how Cinefin ("the App", "we", "us", or "our") collects, uses, shares, and protects information when you use our Android application. By using the App, you agree to the collection and use of information in accordance with this policy.

**Application Scope:** This Privacy Policy applies to the Cinefin application distributed on Google Play. Cinefin is an unofficial, third-party Jellyfin Android client and is not affiliated with or endorsed by the Jellyfin project.

## 1. Overview

Cinefin is an unofficial third-party client for Jellyfin media servers. The App operates as a bridge between your device and your personal Jellyfin server. We are committed to protecting your privacy and being transparent about our data practices.

**Key Privacy Principles:**
- We do **not** collect, store, or have access to your personal information
- We do **not** collect, store, or have access to your Jellyfin server credentials
- We do **not** sell or share your data with third parties for advertising or marketing purposes
- We collect only anonymous diagnostic data to improve app stability and performance

## 2. Information We Collect

### 2.1 Information We Do NOT Collect

The App does **not** collect:
- Personal identification information (names, email addresses, phone numbers)
- Jellyfin server URLs or addresses
- Authentication tokens or login credentials
- User IDs or account information
- Media viewing history or preferences
- Payment information
- Location data
- Contact lists or other device data

### 2.2 Automatically Collected Anonymous Data

To maintain and improve the App's performance, we collect limited anonymous technical data through Google Firebase services:

**Crash Reports (via Firebase Crashlytics):**
- Stack traces and error logs when the app crashes
- Device model and manufacturer
- Android OS version
- App version code and name
- Timestamp of the crash
- Device state at time of crash (memory usage, orientation, etc.)

**Performance Metrics (via Firebase Performance Monitoring):**
- App startup time
- Screen rendering performance
- Network request latency
- HTTP response codes
- App foreground/background duration

**Anonymous Usage Analytics (via Firebase Analytics):**
- Screen views and navigation patterns
- Feature usage frequency
- Session duration
- Device characteristics (screen size, language, timezone)
- App installation and update events

**Important:** All data collected is anonymous and cannot be used to personally identify individual users. Firebase automatically assigns a random identifier to your app installation, which is not linked to your identity.

### 2.3 Data Stored Locally on Your Device

The following information is stored **only on your device** and is **never transmitted to us**:

- Jellyfin server connection details (URLs, ports)
- Authentication tokens and session credentials (encrypted using Android Keystore)
- User preferences and app settings
- Downloaded media files (if offline mode is used)
- Playback position and watch history (synced with your Jellyfin server only)

This data remains on your device until you clear app data, uninstall the App, or manually delete it.

## 3. How We Use Information

Anonymous data collected through Firebase is used exclusively to:

- **Diagnose and fix crashes** to improve app stability
- **Monitor performance issues** to optimize speed and responsiveness
- **Understand feature usage** to prioritize development efforts
- **Ensure device compatibility** across different Android versions and device models
- **Track app installation trends** to understand user growth (anonymous only)

We do **not** use collected data to:
- Track individual users across apps or services
- Build user profiles or behavioral patterns
- Serve advertisements or marketing content
- Sell or rent data to third parties

## 4. How We Share Information

### 4.1 Third-Party Service Providers

The App uses the following third-party services that may collect and process anonymous data:

**Google Firebase (Crashlytics, Performance Monitoring, Analytics)**
- **Purpose:** Crash reporting, performance diagnostics, anonymous usage analytics
- **Data Collected:** As described in Section 2.2
- **Privacy Policy:** [https://policies.google.com/privacy](https://policies.google.com/privacy)
- **Data Processing:** Google processes data in accordance with their privacy policy and is certified under privacy frameworks

**Jellyfin Server (User-Operated)**
- **Purpose:** Media streaming and playback
- **Data Shared:** Authentication tokens, playback requests, media metadata requests
- **Important:** Your Jellyfin server is operated by you or a third party you trust. We do not operate, control, or have access to any Jellyfin servers. All communication with your server is direct between your device and the server. Please review your server administrator's privacy policy.

### 4.2 Legal Requirements

We may disclose information if required to do so by law or in response to valid requests by public authorities (e.g., court orders, government agencies), including to meet national security or law enforcement requirements. However, given that we do not collect personally identifiable information, such disclosures would be limited to anonymous diagnostic data.

### 4.3 Business Transfers

In the event of a merger, acquisition, or sale of assets, anonymous usage data may be transferred to the acquiring entity. You will be notified via this Privacy Policy of any such change.

## 5. Data Security

We take data security seriously and implement industry-standard measures to protect information:

### 5.1 Local Data Protection
- **Android Keystore Encryption:** All server credentials and authentication tokens are encrypted using the Android Keystore system, providing hardware-backed encryption on supported devices
- **Secure Storage:** Sensitive data is stored in encrypted shared preferences
- **Certificate Pinning:** Dynamic Trust-on-First-Use (TOFU) model protects server communications from man-in-the-middle attacks
- **HTTPS Only:** All network communications use encrypted HTTPS connections

### 5.2 Anonymous Data Protection
- Firebase data is transmitted over encrypted connections (TLS/SSL)
- Data is stored securely on Google's infrastructure with industry-standard security measures
- Access to Firebase data is restricted to authorized developers only

### 5.3 Your Responsibilities
- Keep your device secure with lock screen protection
- Only connect to Jellyfin servers you trust
- Do not share your Jellyfin server credentials with untrusted parties
- Keep the App updated to receive security patches

**Note:** While we implement strong security measures, no method of electronic storage or transmission is 100% secure. We cannot guarantee absolute security.

## 6. Data Retention

### 6.1 Anonymous Diagnostic Data
- **Crash Reports:** Retained for up to 90 days by Firebase Crashlytics
- **Performance Metrics:** Retained for up to 90 days by Firebase Performance Monitoring
- **Analytics Data:** Retained for up to 14 months by Firebase Analytics (configurable)
- Data is automatically deleted after retention periods expire

### 6.2 Locally Stored Data
- Data stored on your device (server credentials, preferences, downloads) is retained until:
  - You manually clear app data in Android settings
  - You uninstall the App
  - You use the App's logout or clear data features
- You have full control over when to delete this data

## 7. Your Privacy Rights and Choices

### 7.1 Control Over Local Data
- **Access:** You can view your stored server connections and preferences within the App's settings
- **Delete:** You can remove server connections, clear preferences, or delete all app data through Android settings
- **Export:** You can manually record your server connection details before uninstalling

### 7.2 Control Over Anonymous Analytics
While we do not offer an in-app opt-out (as data is already anonymous), you can:
- **Disable Crashlytics:** Firebase Crashlytics respects the Android opt-out settings
- **Use Privacy-Focused Alternatives:** Consider using open-source builds without Firebase integration
- **Contact Us:** Request information about your anonymous data (though we cannot identify individual users)

### 7.3 Regional Privacy Rights

**For Users in the European Economic Area (EEA), UK, and Switzerland (GDPR):**
- Right to access: Request information about anonymous data we collect
- Right to erasure: Request deletion of anonymous data (where technically feasible)
- Right to object: Object to data processing (can uninstall app or use builds without Firebase)
- Right to data portability: Limited applicability due to anonymous nature of data
- Right to withdraw consent: Uninstall the App to cease data collection

**For California Residents (CCPA):**
- Right to know what personal information is collected (see Section 2)
- Right to delete personal information (we do not collect personal information)
- Right to opt-out of sale of personal information (we do not sell data)
- Right to non-discrimination (we do not discriminate based on privacy rights)

**For Other Regions:**
- We respect all applicable data protection laws and regulations
- Contact us to exercise any rights available under your local laws

To exercise any of these rights, please contact us at the email address listed in Section 11.

## 8. Children's Privacy

The App is not directed to children under the age of 13 (or the applicable age of digital consent in your jurisdiction).

We do **not** knowingly collect personal information from children under 13. The anonymous diagnostic data we collect through Firebase does not include age information and cannot be used to identify children.

If you are a parent or guardian and believe your child has provided us with personal information, please contact us immediately at rpeters1430@gmail.com, and we will take steps to investigate and address the issue.

## 9. International Data Transfers

The anonymous data collected through Firebase may be transferred to and processed in countries other than your country of residence, including the United States, where Google operates data centers.

These countries may have data protection laws different from those in your jurisdiction. By using the App, you consent to the transfer of anonymous information to these countries. Google Firebase complies with applicable data protection frameworks, including:
- EU-U.S. Data Privacy Framework
- Standard Contractual Clauses (SCCs) for GDPR compliance
- Security measures equivalent to those required in your jurisdiction

Your Jellyfin server communication is direct and does not pass through our systems. The location of data processing depends on where your Jellyfin server is hosted.

## 10. Third-Party Links and Services

The App may contain links to third-party websites, services, or content (e.g., links within your Jellyfin media library, external movie databases).

**Important:** We are not responsible for the privacy practices of third-party services. This Privacy Policy applies only to the App. We encourage you to review the privacy policies of any third-party services you access.

Specifically:
- **Jellyfin Server:** Operated independently; review your server administrator's privacy policy
- **External Links:** May be present in media descriptions; we do not control these destinations
- **Third-Party SDKs:** Firebase is the only third-party SDK; see Section 4.1

## 11. Changes to This Privacy Policy

We may update this Privacy Policy from time to time to reflect changes in our practices, legal requirements, or app functionality.

**We will notify you of material changes by:**
- Updating the "Last Updated" date at the top of this policy
- Posting the new Privacy Policy at this URL
- Displaying an in-app notice upon first launch after an update (for significant changes)

**Your continued use of the App after changes become effective constitutes acceptance of the updated Privacy Policy.** We encourage you to review this policy periodically.

**Policy Version History:**
- v1.0 - January 27, 2026: Initial privacy policy for Google Play submission

## 12. Contact Us

If you have questions, concerns, or requests regarding this Privacy Policy or our data practices, please contact us:

**Email:** rpeters1430@gmail.com
**GitHub Repository:** [https://github.com/rpeters1430/JellyfinAndroid](https://github.com/rpeters1430/JellyfinAndroid)
**Privacy Policy URL:** [https://rpeters1430.github.io/JellyfinAndroid/privacy-policy.html](https://rpeters1430.github.io/JellyfinAndroid/privacy-policy.html)

We will respond to privacy inquiries within 30 days.

## 13. Governing Law

This Privacy Policy is governed by and construed in accordance with the laws of the United States and the State of [Your State], without regard to conflict of law principles.

For users in the European Economic Area, UK, or Switzerland, GDPR and local data protection laws also apply.

---

**Acknowledgment:** By installing, accessing, or using Cinefin, you acknowledge that you have read, understood, and agree to be bound by this Privacy Policy.
