package com.grandon.mockgpspro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.Random;

public class MockLocationService extends Service {
    public static final String ACTION_START = "com.grandon.mockgpspro.START";
    public static final String ACTION_STOP = "com.grandon.mockgpspro.STOP";
    public static final String BROADCAST_LOCATION_UPDATE = "com.grandon.mockgpspro.LOCATION_UPDATE";

    public static final String EXTRA_LAT = "extra_lat";
    public static final String EXTRA_LNG = "extra_lng";
    public static final String EXTRA_RADIUS = "extra_radius";
    public static final String EXTRA_WARMUP = "extra_warmup";

    public static final String EXTRA_CURRENT_LAT = "current_lat";
    public static final String EXTRA_CURRENT_LNG = "current_lng";
    public static final String EXTRA_CURRENT_ACC = "current_acc";
    public static final String EXTRA_CURRENT_WARMUP_REMAINING = "warmup_remaining";

    private static final String CHANNEL_ID = "mock_gps_channel_v3";
    private static final int NOTIFICATION_ID = 1001;

    private LocationManager locationManager;
    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private final Random random = new Random();
    private boolean isRunning = false;

    // Coordenadas base
    private double baseLat = -36.82699;
    private double baseLng = -73.04977;
    private double maxRadiusMeters = 15.0;
    private int warmupSeconds = 15;

    // Estado cinemático realista
    private long startTimeMillis = 0;
    private double currentLat = -36.82699;
    private double currentLng = -73.04977;
    private double currentAlt = 15.0;
    private float currentBearing = 0.0f;
    private float currentSpeed = 0.0f;

    private static final double METERS_PER_DEGREE_LAT = 111320.0;

    private final String[] PROVIDERS = new String[] {
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        "fused"
    };

    private final Runnable mockRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            stepAndInjectLocation();
            handler.postDelayed(this, 1000); // 1 Hz estándar GPS
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();

        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MockGPSPro:ServiceWakeLock");
                wakeLock.setReferenceCounted(false);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            baseLat = intent.getDoubleExtra(EXTRA_LAT, baseLat);
            baseLng = intent.getDoubleExtra(EXTRA_LNG, baseLng);
            maxRadiusMeters = intent.getDoubleExtra(EXTRA_RADIUS, maxRadiusMeters);
            warmupSeconds = intent.getIntExtra(EXTRA_WARMUP, warmupSeconds);

            currentLat = baseLat;
            currentLng = baseLng;
            currentAlt = 18.0 + (random.nextDouble() * 3.0);
            currentBearing = (float) (random.nextDouble() * 360.0);
            currentSpeed = 0.0f;
            startTimeMillis = System.currentTimeMillis();

            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
            }

            startForeground(NOTIFICATION_ID, buildNotification("Iniciando adquisición de satélites..."));
            setupMockProviders();
            isRunning = true;
            handler.removeCallbacks(mockRunnable);
            handler.post(mockRunnable);

            Toast.makeText(this, "Simulación avanzada activa", Toast.LENGTH_SHORT).show();
        } else if (ACTION_STOP.equals(action)) {
            stopSimulation();
            stopSelf();
        }

        return START_STICKY;
    }

    private void setupMockProviders() {
        for (String provider : PROVIDERS) {
            try {
                locationManager.addTestProvider(
                    provider,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                );
                locationManager.setTestProviderEnabled(provider, true);
            } catch (SecurityException e) {
                Toast.makeText(this, "Permiso denegado: Selecciona Mock GPS Pro en Opciones de desarrollador", Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {
                try {
                    locationManager.setTestProviderEnabled(provider, true);
                } catch (Exception ex) {}
            }
        }
    }

    private void stepAndInjectLocation() {
        long elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000;
        long now = System.currentTimeMillis();
        long elapsedNanos = SystemClock.elapsedRealtimeNanos();

        // 1. Fase de Calentamiento (Warm-up TTFF):
        // Precisión que converge de 45m a 2.5m imitando la adquisición de efemérides
        float accuracy;
        int warmupRemaining = 0;
        if (elapsedSeconds < warmupSeconds) {
            warmupRemaining = (int) (warmupSeconds - elapsedSeconds);
            float progress = (float) elapsedSeconds / (float) warmupSeconds;
            accuracy = 45.0f - (progress * 42.0f) + (float)(random.nextGaussian() * 1.5);
            if (accuracy < 3.2f) accuracy = 3.2f;
        } else {
            // Fluctuación gaussiana realista (1.6m a 3.4m)
            accuracy = 2.1f + (float) Math.abs(random.nextGaussian() * 0.5);
        }

        // 2. Cinemática Peatonal Natural (Brownian Random Walk con inercia de rumbo)
        if (maxRadiusMeters > 0.1) {
            // Variación suave de ángulo: max ±25 grados por segundo para evitar giros imposibles
            float angleDelta = (float) (random.nextGaussian() * 20.0);
            currentBearing = (currentBearing + angleDelta + 360.0f) % 360.0f;

            // Velocidad peatonal: 0.2 a 0.75 m/s con aceleración suave
            double stepMeters = 0.25 + (random.nextDouble() * 0.5);
            currentSpeed = (float) stepMeters;

            double rad = Math.toRadians(currentBearing);
            double dLat = (stepMeters * Math.cos(rad)) / METERS_PER_DEGREE_LAT;
            double metersPerDegreeLng = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(baseLat));
            double dLng = (stepMeters * Math.sin(rad)) / metersPerDegreeLng;

            double candLat = currentLat + dLat;
            double candLng = currentLng + dLng;

            // Límite de radio
            double distFromBase = calculateDistanceMeters(baseLat, baseLng, candLat, candLng);
            if (distFromBase <= maxRadiusMeters) {
                currentLat = candLat;
                currentLng = candLng;
            } else {
                // Reorientar suavemente hacia el centro
                double returnAngle = Math.toDegrees(Math.atan2(
                    baseLng - currentLng,
                    (baseLat - currentLat) * Math.cos(Math.toRadians(baseLat))
                ));
                currentBearing = (float) ((returnAngle + 360.0) % 360.0);
            }
        } else {
            currentLat = baseLat;
            currentLng = baseLng;
            currentSpeed = 0.0f;
        }

        // Micro-fluctuación barométrica
        currentAlt += (random.nextGaussian() * 0.06);

        // 3. Síntesis de Paquetes de Metadatos GNSS (Bundle Extras)
        Bundle gpsExtras = new Bundle();
        int satCount = (elapsedSeconds < warmupSeconds) ? (5 + (int)(elapsedSeconds * 0.7)) : (14 + random.nextInt(8));
        gpsExtras.putInt("satellites", satCount);
        gpsExtras.putFloat("meanCn0", 29.5f + (float)(random.nextDouble() * 3.5));
        gpsExtras.putFloat("maxCn0", 39.0f + (float)(random.nextDouble() * 4.0));
        gpsExtras.putFloat("hdop", 0.8f + (float)(random.nextDouble() * 0.4));
        gpsExtras.putFloat("vdop", 1.2f + (float)(random.nextDouble() * 0.5));
        gpsExtras.putFloat("pdop", 1.5f + (float)(random.nextDouble() * 0.6));

        // 4. Inyección en los Providers
        for (String provider : PROVIDERS) {
            try {
                Location mock = new Location(provider);
                mock.setLatitude(currentLat);
                mock.setLongitude(currentLng);
                mock.setAltitude(currentAlt);
                mock.setTime(now);
                mock.setElapsedRealtimeNanos(elapsedNanos);

                if (LocationManager.GPS_PROVIDER.equals(provider)) {
                    mock.setAccuracy(accuracy);
                    mock.setSpeed(currentSpeed);
                    mock.setBearing(currentBearing);
                    mock.setExtras(gpsExtras);
                } else if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
                    // El proveedor de red simula celdas/Wi-Fi: mayor dispersión (~18m)
                    mock.setAccuracy(accuracy * 5.0f + 10.0f);
                    mock.setSpeed(0.0f);
                    mock.setBearing(0.0f);
                } else {
                    mock.setAccuracy(accuracy);
                    mock.setSpeed(currentSpeed);
                    mock.setBearing(currentBearing);
                }

                if (Build.VERSION.SDK_INT >= 26) {
                    mock.setBearingAccuracyDegrees(12.0f + (float)(random.nextDouble() * 8.0));
                    mock.setSpeedAccuracyMetersPerSecond(0.2f + (float)(random.nextDouble() * 0.3));
                    mock.setVerticalAccuracyMeters(accuracy * 1.5f);
                }

                locationManager.setTestProviderLocation(provider, mock);
            } catch (Exception ignored) {}
        }

        // 5. Notificación y Broadcast para actualización del mapa visual
        if (elapsedSeconds % 4 == 0) {
            String statusMsg = (elapsedSeconds < warmupSeconds)
                ? "Calentando GPS (" + warmupRemaining + "s | Sats: " + satCount + " | ±" + String.format("%.1fm", accuracy) + ")"
                : "Simulando: " + String.format("%.5f", currentLat) + ", " + String.format("%.5f", currentLng) + " (±" + String.format("%.1fm", accuracy) + ")";
            updateNotification(statusMsg);
        }

        // Broadcast local para que la UI del mapa se sincronice si está visible
        sendLocationBroadcast(currentLat, currentLng, accuracy, warmupRemaining);
    }

    private void sendLocationBroadcast(double lat, double lng, float acc, int warmupRemaining) {
        Intent updateIntent = new Intent(BROADCAST_LOCATION_UPDATE);
        updateIntent.putExtra(EXTRA_CURRENT_LAT, lat);
        updateIntent.putExtra(EXTRA_CURRENT_LNG, lng);
        updateIntent.putExtra(EXTRA_CURRENT_ACC, acc);
        updateIntent.putExtra(EXTRA_CURRENT_WARMUP_REMAINING, warmupRemaining);
        sendBroadcast(updateIntent);
    }

    private double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000.0 * c;
    }

    private void stopSimulation() {
        isRunning = false;
        handler.removeCallbacks(mockRunnable);

        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }

        for (String provider : PROVIDERS) {
            try {
                locationManager.clearTestProviderLocation(provider);
                locationManager.clearTestProviderEnabled(provider);
                locationManager.removeTestProvider(provider);
            } catch (Exception ignored) {}
        }
        stopForeground(true);
        Toast.makeText(this, "Simulación finalizada", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        stopSimulation();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "GeoLab GPS Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Emisión continua de telemetría GPS simulada");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            (Build.VERSION.SDK_INT >= 23) ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        return builder
            .setContentTitle("GeoLab GPS Activo")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }
}
