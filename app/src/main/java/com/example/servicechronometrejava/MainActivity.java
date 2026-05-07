package com.example.servicechronometrejava;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int NOTIFICATION_PERMISSION_CODE = 100;

    private TextView tvTemps, tvStatus;
    private Button btnStart, btnStop;

    private ChronometreService chronometreService;
    private boolean isBound = false;

    private Handler handler;
    private Runnable updateRunnable;

    // Connexion au service
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ChronometreService.LocalBinder binder = (ChronometreService.LocalBinder) service;
            chronometreService = binder.getService();
            isBound = true;
            tvStatus.setText("Service connecté");
            startUpdatingUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            chronometreService = null;
            tvStatus.setText("Service déconnecté");
            stopUpdatingUI();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTemps = findViewById(R.id.tvTemps);
        tvStatus = findViewById(R.id.tvStatus);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        handler = new Handler(Looper.getMainLooper());

        // Mise à jour de l'interface
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isBound && chronometreService != null) {
                    tvTemps.setText(chronometreService.getCurrentTime());
                }
                handler.postDelayed(this, 500);
            }
        };

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkNotificationPermissionAndStart();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopChronometre();
            }
        });
    }

    private void checkNotificationPermissionAndStart() {
        // Pour Android 13+ (API 33+), permission notification requise
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
                return;
            }
        }
        startChronometre();
    }

    private void startChronometre() {
        Intent intent = new Intent(this, ChronometreService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        tvStatus.setText("Démarrage du service...");
    }

    private void stopChronometre() {
        Intent intent = new Intent(this, ChronometreService.class);
        intent.setAction("STOP");
        startService(intent);

        if (isBound) {
            unbindService(connection);
            isBound = false;
            chronometreService = null;
            stopUpdatingUI();
        }
        tvTemps.setText("00:00");
        tvStatus.setText("Service arrêté");
        Toast.makeText(this, "Chronomètre arrêté", Toast.LENGTH_SHORT).show();
    }

    private void startUpdatingUI() {
        handler.post(updateRunnable);
    }

    private void stopUpdatingUI() {
        handler.removeCallbacks(updateRunnable);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startChronometre();
            } else {
                Toast.makeText(this, "Permission notification requise pour le service", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
        }
        stopUpdatingUI();
    }
}