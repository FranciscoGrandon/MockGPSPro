package com.grandon.mockgpspro;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQ_CODE = 2001;

    private EditText etLat;
    private EditText etLng;
    private EditText etRadius;
    private EditText etWarmup;
    private TextView tvStatus;
    private Button btnStart;
    private Button btnStop;
    private View btnDevSettings;
    private View btnBatterySettings;
    private Button btnPresetConcepcion;
    private Button btnPresetSantiago;
    private Button btnCenterMap;
    private WebView wvMap;

    private boolean isMapLoaded = false;

    // Receptor de actualización de coordenadas en tiempo real desde el servicio
    private final BroadcastReceiver locationUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            double curLat = intent.getDoubleExtra(MockLocationService.EXTRA_CURRENT_LAT, 0.0);
            double curLng = intent.getDoubleExtra(MockLocationService.EXTRA_CURRENT_LNG, 0.0);
            float curAcc = intent.getFloatExtra(MockLocationService.EXTRA_CURRENT_ACC, 0.0f);
            int warmupRemaining = intent.getIntExtra(MockLocationService.EXTRA_CURRENT_WARMUP_REMAINING, 0);

            if (isMapLoaded && wvMap != null) {
                wvMap.post(new Runnable() {
                    @Override
                    public void run() {
                        wvMap.evaluateJavascript("if (typeof updateLocation === 'function') { updateLocation(" + curLat + ", " + curLng + ", " + curAcc + "); }", null);
                    }
                });
            }

            if (warmupRemaining > 0) {
                tvStatus.setText("⏳  Calentamiento TTFF (" + warmupRemaining + "s restantes)\nPosición: " + String.format("%.5f", curLat) + ", " + String.format("%.5f", curLng) + " (±" + String.format("%.1fm", curAcc) + ")");
                tvStatus.setTextColor(0xFFD32F2F);
            } else {
                tvStatus.setText("🟢  SIMULACIÓN ACTIVA EN SEGUNDO PLANO\nPosición actual: " + String.format("%.5f", curLat) + ", " + String.format("%.5f", curLng) + " (±" + String.format("%.1fm", curAcc) + ")\nGNSS: 14+ satélites sintetizados con deriva física.");
                tvStatus.setTextColor(0xFF087A5B);
            }
        }
    };

    // Puente Javascript para interactuar con el mapa
    public class WebAppInterface {
        @JavascriptInterface
        public void onMapClicked(final double lat, final double lng) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    etLat.setText(String.format(java.util.Locale.US, "%.5f", lat));
                    etLng.setText(String.format(java.util.Locale.US, "%.5f", lng));
                    Toast.makeText(MainActivity.this, "Punto fijado: " + String.format("%.5f", lat) + ", " + String.format("%.5f", lng), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etLat = (EditText) findViewById(R.id.et_lat);
        etLng = (EditText) findViewById(R.id.et_lng);
        etRadius = (EditText) findViewById(R.id.et_radius);
        etWarmup = (EditText) findViewById(R.id.et_warmup);
        tvStatus = (TextView) findViewById(R.id.tv_status);

        btnStart = (Button) findViewById(R.id.btn_start);
        btnStop = (Button) findViewById(R.id.btn_stop);
        btnDevSettings = findViewById(R.id.btn_dev_settings);
        btnBatterySettings = findViewById(R.id.btn_battery_settings);
        btnPresetConcepcion = (Button) findViewById(R.id.btn_preset_concepcion);
        btnPresetSantiago = (Button) findViewById(R.id.btn_preset_santiago);
        btnCenterMap = (Button) findViewById(R.id.btn_center_map);
        wvMap = (WebView) findViewById(R.id.wv_map);

        etLat.setText("-36.82699");
        etLng.setText("-73.04977");

        setupWebView();

        btnDevSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDeveloperSettings();
            }
        });

        btnBatterySettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestIgnoreBatteryOptimizations();
            }
        });

        btnPresetConcepcion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCoordinates("-36.82699", "-73.04977");
            }
        });

        btnPresetSantiago.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCoordinates("-33.44889", "-70.66927");
            }
        });

        btnCenterMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncMapWithInputs();
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionsAndStart();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMockService();
            }
        });

        requestRuntimePermissions();
    }

    private void setupWebView() {
        WebSettings settings = wvMap.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Política oficial OSM: User-Agent descriptivo con identificación de app y contacto del desarrollador (sin spoofing genérico)
        settings.setUserAgentString("GeoLabGPS/1.6.0 (com.grandon.mockgpspro; grandonpanxo@gmail.com) Android");

        wvMap.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");
        wvMap.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isMapLoaded = true;
                syncMapWithInputs();
            }
        });

        loadLeafletMapHtml(-36.82699, -73.04977, 15.0);
    }

    private void loadLeafletMapHtml(double lat, double lng, double radius) {
        // Implementación con diseño Light UI Concept y cumplimiento de OSM Tile Usage Policy
        String html = "<!DOCTYPE html>"
            + "<html>"
            + "<head>"
            + "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />"
            + "<link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css' />"
            + "<script src='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js'></script>"
            + "<style>"
            + "html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #edf1ec; }"
            + ".leaflet-control-attribution { font-size: 8px; background: rgba(255,255,255,0.85) !important; color: #6d7b84 !important; border-radius: 4px; padding: 2px 4px; }"
            + ".leaflet-control-attribution a { color: #00a878 !important; text-decoration: none; font-weight: bold; }"
            + ".leaflet-bar { border: 1px solid #dce5e9 !important; border-radius: 10px !important; overflow: hidden; box-shadow: 0 4px 12px rgba(35,60,70,0.08) !important; }"
            + ".leaflet-bar a { background-color: #ffffff !important; color: #51656e !important; border-bottom: 1px solid #e0e8eb !important; }"
            + ".pin-marker { width: 24px; height: 24px; border-radius: 50% 50% 50% 0; background: #1688c7; transform: rotate(-45deg); box-shadow: 0 0 0 6px rgba(22,136,199,0.22), 0 0 0 14px rgba(22,136,199,0.1); position: relative; }"
            + ".pin-marker:after { content: ''; position: absolute; width: 8px; height: 8px; border-radius: 50%; background: white; left: 8px; top: 8px; }"
            + ".map-legend { position: absolute; right: 10px; bottom: 10px; z-index: 1000; background: rgba(255,255,255,0.93); border: 1px solid #d9e2e4; border-radius: 12px; padding: 8px 10px; font-size: 9px; font-family: sans-serif; box-shadow: 0 4px 14px rgba(0,0,0,0.08); color: #52616a; pointer-events: none; }"
            + ".legend-row { display: flex; align-items: center; gap: 6px; margin: 2px 0; }"
            + ".legend-dot { width: 8px; height: 8px; border-radius: 50%; background: #1688c7; }"
            + ".legend-line { width: 15px; height: 3px; border-radius: 2px; background: #00b987; }"
            + "</style>"
            + "</head>"
            + "<body>"
            + "<div id='map'></div>"
            + "<div class='map-legend'>"
            + "  <div class='legend-row'><span class='legend-dot'></span> Tu ubicación (simulada)</div>"
            + "  <div class='legend-row'><span class='legend-line'></span> Rango de deriva</div>"
            + "</div>"
            + "<script>"
            + "var map = L.map('map', {zoomControl: true}).setView([" + lat + ", " + lng + "], 16);"
            + "L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {"
            + "  maxZoom: 19,"
            + "  attribution: '&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>'"
            + "}).addTo(map);"
            + "var pinIcon = L.divIcon({"
            + "  className: 'pin-wrap',"
            + "  html: '<div class=\"pin-marker\"></div>',"
            + "  iconSize: [24, 24],"
            + "  iconAnchor: [12, 24]"
            + "});"
            + "var marker = L.marker([" + lat + ", " + lng + "], {icon: pinIcon}).addTo(map);"
            + "var circle = L.circle([" + lat + ", " + lng + "], {"
            + "  color: '#00a878',"
            + "  fillColor: '#00c98b',"
            + "  fillOpacity: 0.18,"
            + "  weight: 2,"
            + "  radius: " + radius
            + "}).addTo(map);"
            + "map.on('click', function(e) {"
            + "  var cLat = e.latlng.lat;"
            + "  var cLng = e.latlng.lng;"
            + "  marker.setLatLng([cLat, cLng]);"
            + "  circle.setLatLng([cLat, cLng]);"
            + "  if (window.AndroidBridge) {"
            + "    window.AndroidBridge.onMapClicked(cLat, cLng);"
            + "  }"
            + "});"
            + "function updateLocation(lat, lng, acc) {"
            + "  marker.setLatLng([lat, lng]);"
            + "}"
            + "function setCenter(lat, lng, rad) {"
            + "  map.setView([lat, lng], 16);"
            + "  marker.setLatLng([lat, lng]);"
            + "  if (circle) {"
            + "    circle.setLatLng([lat, lng]);"
            + "    circle.setRadius(rad);"
            + "  }"
            + "}"
            + "</script>"
            + "</body>"
            + "</html>";

        wvMap.loadDataWithBaseURL("https://grandon.dev", html, "text/html", "UTF-8", null);
    }

    private void syncMapWithInputs() {
        if (!isMapLoaded) return;
        try {
            double lat = Double.parseDouble(etLat.getText().toString().trim());
            double lng = Double.parseDouble(etLng.getText().toString().trim());
            double rad = Double.parseDouble(etRadius.getText().toString().trim());
            wvMap.evaluateJavascript("if (typeof setCenter === 'function') { setCenter(" + lat + ", " + lng + ", " + rad + "); }", null);
        } catch (Exception ignored) {}
    }

    private void setCoordinates(String latStr, String lngStr) {
        etLat.setText(latStr);
        etLng.setText(lngStr);
        syncMapWithInputs();
        Toast.makeText(this, "Coordenadas fijadas", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMockPermissionStatus();
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(locationUpdateReceiver, new IntentFilter(MockLocationService.BROADCAST_LOCATION_UPDATE), 4);
            } else {
                registerReceiver(locationUpdateReceiver, new IntentFilter(MockLocationService.BROADCAST_LOCATION_UPDATE));
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(locationUpdateReceiver);
        } catch (Exception ignored) {}
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions;
            if (Build.VERSION.SDK_INT >= 33) {
                permissions = new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    "android.permission.POST_NOTIFICATIONS"
                };
            } else {
                permissions = new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                };
            }

            boolean needsRequest = false;
            for (String perm : permissions) {
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    needsRequest = true;
                    break;
                }
            }

            if (needsRequest) {
                requestPermissions(permissions, PERMISSION_REQ_CODE);
            }
        }
    }

    private boolean isMockLocationAppSelected() {
        try {
            AppOpsManager opsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (opsManager == null) return false;
            int mode = opsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                getPackageName()
            );
            return (mode == AppOpsManager.MODE_ALLOWED);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                return pm.isIgnoringBatteryOptimizations(getPackageName());
            }
        }
        return true;
    }

    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent();
                if (!isIgnoringBatteryOptimizations()) {
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                } else {
                    intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                }
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent appInfo = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    appInfo.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(appInfo);
                } catch (Exception ex) {
                    Toast.makeText(this, "Ajustes no disponibles", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void updateMockPermissionStatus() {
        boolean hasMock = isMockLocationAppSelected();
        boolean noBatteryOpt = isIgnoringBatteryOptimizations();

        StringBuilder sb = new StringBuilder();
        if (hasMock) {
            sb.append("Ubicación Simulada: ✅ HABILITADA en desarrollador.\n");
        } else {
            sb.append("Ubicación Simulada: ⚠️ Pulsa 'Desarrollador' y selecciona GeoLab GPS.\n");
        }

        if (noBatteryOpt) {
            sb.append("Batería: ✅ Sin restricciones (segundo plano activo).");
        } else {
            sb.append("Batería: ⚠️ Pulsa 'Sin Batería Opt' para evitar corte en segundo plano.");
        }

        tvStatus.setText(sb.toString());
        tvStatus.setTextColor(hasMock ? 0xFF087A5B : 0xFFD32F2F);
    }

    private void openDeveloperSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                startActivity(fallback);
                Toast.makeText(this, "Abre Opciones de desarrollador manualmente", Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                Toast.makeText(this, "Ajustes no disponibles", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkPermissionsAndStart() {
        if (!isMockLocationAppSelected()) {
            Toast.makeText(this, "Selecciona GeoLab GPS en Opciones de desarrollador", Toast.LENGTH_LONG).show();
            openDeveloperSettings();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestRuntimePermissions();
                Toast.makeText(this, "Concede los permisos solicitados", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (!isIgnoringBatteryOptimizations()) {
            requestIgnoreBatteryOptimizations();
        }

        startMockService();
    }

    private void startMockService() {
        try {
            double lat = Double.parseDouble(etLat.getText().toString().trim());
            double lng = Double.parseDouble(etLng.getText().toString().trim());
            double radius = Double.parseDouble(etRadius.getText().toString().trim());
            int warmup = Integer.parseInt(etWarmup.getText().toString().trim());

            Intent intent = new Intent(this, MockLocationService.class);
            intent.setAction(MockLocationService.ACTION_START);
            intent.putExtra(MockLocationService.EXTRA_LAT, lat);
            intent.putExtra(MockLocationService.EXTRA_LNG, lng);
            intent.putExtra(MockLocationService.EXTRA_RADIUS, radius);
            intent.putExtra(MockLocationService.EXTRA_WARMUP, warmup);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            syncMapWithInputs();

            tvStatus.setText("Estado: 🟢 SIMULACIÓN INICIADA\nObjetivo: " + lat + ", " + lng + " (Radio: ±" + radius + "m)\nCalentando satélites...");
            tvStatus.setTextColor(0xFF087A5B);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Valores numéricos inválidos", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopMockService() {
        Intent intent = new Intent(this, MockLocationService.class);
        intent.setAction(MockLocationService.ACTION_STOP);
        startService(intent);

        updateMockPermissionStatus();
    }
}
