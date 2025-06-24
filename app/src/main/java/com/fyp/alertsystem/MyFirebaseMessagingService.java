package com.fyp.alertsystem;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Receives FCM messages even when the app is closed.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "alert_channel";

    @Override
    public void onNewToken(String token) {
        // TODO: send this token to your server so you can address messages to this device.
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // 1) Extract data payload
        String message  = remoteMessage.getData().get("message");
        String area     = remoteMessage.getData().get("area");
        String priority = remoteMessage.getData().get("priority");

        // 2) Build and fire a notification
        sendNotification(message, area, priority);
    }

    private void sendNotification(String msg, String area, String pr) {
        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Create channel on Oreo+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    "Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            chan.setDescription("Real-time emergency alerts");
            nm.createNotificationChannel(chan);
        }

        // Intent when user taps the notification:
        Intent intent = new Intent(this, StudentActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder nb =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.log)   // your notification icon
                        .setContentTitle("Emergency Alert")
                        .setContentText(msg + " @ " + area + " (" + pr + ")")
                        .setAutoCancel(true)
                        .setSound(defaultSound)
                        .setContentIntent(pi)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        nm.notify((int) System.currentTimeMillis(), nb.build());
    }
}
