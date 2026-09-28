# Agents for Life — Android control layer

This directory is the native Android layer for phone control. It is intentionally separate from the existing web UI so the agent can request device actions through a controlled bridge.

Planned capabilities include opening apps, Home, Back, Recents, Notifications, and approved gestures through Android AccessibilityService. Android requires the user to explicitly enable an accessibility service in system settings.

The web agent must never be granted unrestricted device control automatically. Sensitive actions should require an explicit user confirmation.
