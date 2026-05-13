# Architecture Plan: Cinefin Companion Plugin for Jellyfin

## Overview
Currently, integrating external services like Overseerr, Sonarr, and Radarr directly into the Android client presents several UX and security challenges:
1. **Configuration Fatigue:** Users must manually enter URLs and API keys for each service on their mobile devices.
2. **Security Risks:** Users must expose their *arr stack to the public internet to use these features away from home.
3. **API Limitations:** Direct communication with Overseerr limits functionality (e.g., unable to request specific episodes of partially available seasons).

The **Cinefin Companion Plugin** will be a server-side C# (.NET) plugin installed directly on the Jellyfin server. It will act as a secure proxy and orchestration layer between the Cinefin Android app and the user's self-hosted infrastructure.

## Key Benefits
- **Zero Client Configuration:** The Android app only needs to communicate with the Jellyfin server (which it already does). No additional API keys are needed on the phone.
- **Enhanced Security:** Sonarr, Radarr, and Overseerr do not need to be exposed to the internet. The plugin communicates with them locally on the server's network.
- **Advanced Capabilities:** The plugin can orchestrate complex requests. For example, if a user requests a specific missing episode, the plugin can bypass Overseerr and speak directly to Sonarr to trigger the search.

## Proposed Architecture

### 1. The Plugin (C# / .NET)
Developed using the `MediaBrowser.Controller.Plugins` SDK, this plugin will run inside the Jellyfin server process.

**Features:**
- **Configuration Page:** A UI within the Jellyfin web dashboard for admins to enter their Overseerr, Sonarr, and Radarr API keys and local URLs.
- **Custom API Endpoints:** Exposes new REST endpoints on the Jellyfin server specifically for the Cinefin app. Example: `POST /Cinefin/Request/Episode`
- **Orchestration Logic:** 
  - Translate Jellyfin item IDs to TVDB/TMDB IDs.
  - Route standard requests to Overseerr.
  - Route granular/specific episode requests directly to Sonarr.

### 2. The Android Client (Kotlin)
The Cinefin app will be updated to check if the companion plugin is installed on the connected server.

**Features:**
- Detect plugin presence via a custom `/Cinefin/Info` endpoint.
- If present, route all requests (Search, Trending, Request) through the Jellyfin server instead of requiring local Overseerr configuration.
- Surface granular episode request buttons when the plugin confirms Sonarr integration is available.

## Repository Structure Recommendation
It is highly recommended that this plugin be developed in a **separate GitHub repository**.

**Why separate repositories?**
1. **Different Languages & Tooling:** The Android app uses Kotlin, Gradle, and Android SDKs. The plugin will use C#, MSBuild, and the Jellyfin .NET SDK. Mixing them in a monorepo drastically complicates CI/CD pipelines (GitHub Actions) and IDE setups (Android Studio vs. Visual Studio/Rider).
2. **Independent Lifecycles:** A plugin update to fix a Sonarr API change should not require an Android app release, and vice versa.
3. **Ecosystem Standards:** Jellyfin plugins are typically distributed via custom repository URLs (manifest JSON files) that point to GitHub releases. Having a dedicated repository makes managing these releases and the manifest file much cleaner.

## Future Expansion
Once the proxy infrastructure is in place, the plugin could be expanded to handle other heavy server-side tasks:
- Pre-caching NPU AI summaries on the server.
- Aggregating server health diagnostics specifically formatted for the mobile app.
- Advanced notification management (pushing Sonarr download completion alerts through Jellyfin to the app).
