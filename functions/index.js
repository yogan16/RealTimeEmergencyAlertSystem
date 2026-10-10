const { onValueCreated } = require("firebase-functions/v2/database");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

// Must match AppConfig.ALERTS_TOPIC in the Android app
const ALERTS_TOPIC = "alerts";

// Set this to your Realtime Database location (e.g. "asia-southeast1" or "us-central1")
const DATABASE_REGION = "asia-southeast1";

/**
 * Sends a push notification to every subscribed student when an admin or
 * lecturer creates a new alert. Data-only messages so the app builds the
 * notification itself (custom channel, sound, tap action).
 */
exports.notifyNewAlert = onValueCreated(
  { ref: "/alerts/{alertId}", region: DATABASE_REGION },
  async (event) => {
    const alert = event.data.val() || {};
    await getMessaging().send({
      topic: ALERTS_TOPIC,
      data: {
        message: String(alert.message || "Emergency alert"),
        area: String(alert.area || ""),
        priority: String(alert.priority || ""),
      },
      android: { priority: "high" },
    });
  }
);
