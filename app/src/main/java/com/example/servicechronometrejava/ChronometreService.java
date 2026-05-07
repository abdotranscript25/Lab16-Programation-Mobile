package com.example.servicechronometrejava;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChronometreService extends Service {

    private static final String TAG = "ChronometreService";
    private static final String CHANNEL_ID = "chronometre_channel";
    private static final int NOTIFICATION_ID = 1001;

    // Binder pour permettre à l'Activity de se connecter
    private final IBinder binder = new LocalBinder();

    private int secondes = 0;
    private boolean isRunning = false;
    private ScheduledExecutorService executor;
    private NotificationManager notificationManager;

    // Classe interne pour le binding
    public class LocalBinder extends Binder {
        public ChronometreService getService() {
            return ChronometreService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: Service créé");
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Service démarré");

        String action = (intent != null) ? intent.getAction() : null;

        // Action STOP
        if ("STOP".equals(action)) {
            Log.d(TAG, "onStartCommand: Arrêt demandé");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Démarrer le chronomètre si pas déjà en cours
        if (!isRunning) {
            isRunning = true;
            startForeground(NOTIFICATION_ID, createNotification());
            startChronometre();
        }

        // START_STICKY : redémarre auto si le système tue le service
        return START_STICKY;
    }

    private void startChronometre() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                secondes++;
                updateNotification();
                Log.d(TAG, "Temps: " + formatTime(secondes));
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Chronomètre Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        // Intent pour ouvrir l'app quand on clique sur la notification
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⏱️ Chronomètre actif")
                .setContentText("Temps écoulé : " + formatTime(secondes))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⏱️ Chronomètre actif")
                .setContentText("Temps écoulé : " + formatTime(secondes))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // Méthodes publiques pour l'Activity (via le binder)
    public int getCurrentSeconds() {
        return secondes;
    }

    public String getCurrentTime() {
        return formatTime(secondes);
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: Activity connectée au service");
        return binder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: Service détruit");
        isRunning = false;
        if (executor != null) {
            executor.shutdown();
        }
        stopForeground(true);
        super.onDestroy();
    }
}