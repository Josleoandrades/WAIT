package com.example.wait.ui.notifications;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wait.MainActivity;
import com.example.wait.R;
import com.example.wait.ui.AuthManager;
import com.example.wait.ui.dashboard.DashBoardActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import android.content.SharedPreferences;
import android.widget.Switch;
import android.widget.CompoundButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class NotificationsActivity extends AppCompatActivity implements LocationListener {

    private static final String TAG = "NotificationsActivity";

    // Umbrales para detección de accidentes
    private static final int ACCELEROMETER_THRESHOLD = 15;
    private static final int GYROSCOPE_THRESHOLD = 10;

    private static final double DYNAMIC_SAFE_ZONE_RADIUS_METERS = 2000.0; // 2 km de radio
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private static final String PREFS_NAME = "NotificationPrefs";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_GEOFENCING_ENABLED = "geofencing_enabled";

    private LocationManager locationManager;
    private double phoneLatitude = 0.0;
    private double phoneLongitude = 0.0;
    private boolean isPhoneLocationAvailable = false;
    private boolean isGeofencingEnabled = true;

    // Elementos de UI adicionales
    private Button toggleGeofencingButton;
    private TextView geofencingStatusText;

    // Intervalo de consulta
    private static final long NOTIFICATION_CHECK_INTERVAL = 60000; // 1 minuto

    // Elementos de UI
    private BottomNavigationView bottomNavigationView;
    private LinearLayout notificationContainer;
    private LinearLayout devicesContainer; // Contenedor dinámico para dispositivos
    private Button emergencyButton;

    private SharedPreferences sharedPreferences;
    private boolean areNotificationsEnabled = false;

    // Datos
    private List<DeviceInfo> userDevices;
    private Map<String, NotificationInfo> activeNotifications;
    private Handler notificationHandler;
    private Runnable notificationChecker;

    private Switch notificationSwitch;

    private static class DeviceInfo {
        String deviceId;
        String name;
        String nickname;
        String emergencyContact;
        double lastLatitude = 0.0;
        double lastLongitude = 0.0;
        boolean isInsideSafeZone = true;

        DeviceInfo(String deviceId, String name, String nickname) {
            this.deviceId = deviceId;
            this.name = name;
            this.nickname = nickname;
            this.emergencyContact = "112";
        }
    }

    private static class NotificationInfo {
        String deviceId;
        String deviceName;
        String message;
        long timestamp;
        String severity;
        String alertType;
        int valueAc;
        int valueGir;
        double latitude;
        double longitude;

        // Constructor para alertas de accidente
        NotificationInfo(String deviceId, String deviceName, String message, String severity, int valueAc, int valueGir) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.message = message;
            this.severity = severity;
            this.alertType = "ACCIDENT";
            this.valueAc = valueAc;
            this.valueGir = valueGir;
            this.timestamp = System.currentTimeMillis();
        }

        // Constructor para alertas de geofencing
        NotificationInfo(String deviceId, String deviceName, String message, String severity, double latitude, double longitude) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.message = message;
            this.severity = severity;
            this.alertType = "GEOFENCE";
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_notifications);


        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Recuperar el estado guardado de las notificaciones
        areNotificationsEnabled = sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, false);
        isGeofencingEnabled = sharedPreferences.getBoolean(KEY_GEOFENCING_ENABLED, true);

        Log.d(TAG, "Estado de notificaciones recuperado: " + areNotificationsEnabled);
        Log.d(TAG, "Estado de geofencing recuperado: " + isGeofencingEnabled);

        userDevices = new ArrayList<>();
        activeNotifications = new HashMap<>();

        if (!checkUserSession()) {
            return;
        }

        initializeViews();

        loadUserDevices();

        if (areNotificationsEnabled) {
            initializeNotificationSystem();
        }
    }

    private boolean checkUserSession() {
        AuthManager authManager = new AuthManager(this);
        boolean isLoggedIn = authManager.isLoggedIn();

        if (!isLoggedIn) {
            Log.e(TAG, "No hay sesión válida - redirigiendo al login");
            redirectToLogin();
            return false;
        }

        return true;
    }

    private void initializeViews() {
        bottomNavigationView = findViewById(R.id.nav_viewN);
        bottomNavigationView.setSelectedItemId(R.id.navigation_notifications);


        notificationContainer = findViewById(R.id.notificationContainer);
        devicesContainer = findViewById(R.id.devicesContainer); // Contenedor dinámico para dispositivos

        // Botón de emergencia
        emergencyButton = findViewById(R.id.emergencyButton);
        emergencyButton.setOnClickListener(v -> callEmergency("112"));


        toggleGeofencingButton = findViewById(R.id.toggleGeofencingButton);
        geofencingStatusText = findViewById(R.id.geofencingStatusText);

        updateGeofencingButton();
        updateGeofencingStatus();
        toggleGeofencingButton.setOnClickListener(v -> toggleGeofencing());

        ImageButton btnPerfilWAIT = findViewById(R.id.btnPerfilWAIT);
        btnPerfilWAIT.setOnClickListener(v -> {
            Intent intent = new Intent(NotificationsActivity.this, MainActivity.class);
            startActivity(intent);
        });

        // Navigation listener
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.navigation_home) {
                Intent intent = new Intent(NotificationsActivity.this, MainActivity.class);
                startActivity(intent);
                return true;
            } else if (id == R.id.navigation_dashboard) {
                Intent intent = new Intent(NotificationsActivity.this, DashBoardActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });
        initializeLocationServices();
    }


    private void loadUserDevices() {
        AuthManager authManager = new AuthManager(this);
        int userIdInt = authManager.getUserId();
        String authToken = authManager.getToken();

        if (userIdInt == -1 || authToken == null) {
            Log.e(TAG, "No user ID found en loadUserDevices");
            redirectToLogin();
            return;
        }

        String userId = String.valueOf(userIdInt);
        String apiUrl = "http://172.20.10.2:8080/api/users/" + userId + "/devices";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + authToken)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "Error loading devices", e);
                runOnUiThread(() -> {
                    showNoDevicesMessage();
                    Toast.makeText(NotificationsActivity.this,
                            "Error cargando dispositivos", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                Log.d(TAG, "Respuesta de dispositivos: " + response.code());

                if (response.isSuccessful()) {
                    try {
                        String jsonResponse = response.body().string();
                        Log.d(TAG, "Dispositivos obtenidos: " + jsonResponse);

                        JSONArray devicesArray = new JSONArray(jsonResponse);
                        userDevices.clear();

                        for (int i = 0; i < devicesArray.length(); i++) {
                            JSONObject device = devicesArray.getJSONObject(i);
                            String deviceId = device.getString("device_id");
                            String deviceName = device.optString("name", "Dispositivo");
                            String nickname = device.optString("nickname", deviceName);

                            userDevices.add(new DeviceInfo(deviceId, deviceName, nickname));
                        }

                        runOnUiThread(() -> updateDeviceUI());

                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing devices response", e);
                        runOnUiThread(() -> showNoDevicesMessage());
                    }
                } else if (response.code() == 401) {
                    Log.e(TAG, "Token expirado (401) - redirigiendo al login");
                    runOnUiThread(() -> redirectToLogin());
                } else {
                    Log.e(TAG, "Error response: " + response.code());
                    runOnUiThread(() -> {
                        showNoDevicesMessage();
                        Toast.makeText(NotificationsActivity.this,
                                "Error del servidor: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void updateDeviceUI() {
        devicesContainer.removeAllViews();

        if (userDevices.isEmpty()) {
            showNoDevicesMessage();
            return;
        }

        for (DeviceInfo device : userDevices) {
            View deviceView = createDeviceView(device);
            devicesContainer.addView(deviceView);
        }
    }

    private View createDeviceView(DeviceInfo device) {
        // Crear el LinearLayout principal para el dispositivo
        LinearLayout deviceLayout = new LinearLayout(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(0, 0, 0, 32);
        deviceLayout.setLayoutParams(layoutParams);
        deviceLayout.setOrientation(LinearLayout.VERTICAL);
        deviceLayout.setBackgroundResource(R.drawable.rounded_background);
        deviceLayout.setPadding(32, 32, 32, 32);
        deviceLayout.setElevation(4);
        deviceLayout.setClickable(true);
        deviceLayout.setFocusable(true);
        deviceLayout.setForeground(getDrawable(android.R.drawable.list_selector_background));

        // LinearLayout horizontal para imagen y textos
        LinearLayout horizontalLayout = new LinearLayout(this);
        horizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
        horizontalLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // ImageView para el icono del dispositivo
        ImageView deviceImage = new ImageView(this);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(96, 96);
        imageParams.setMargins(0, 0, 32, 0);
        deviceImage.setLayoutParams(imageParams);
        deviceImage.setImageResource(R.drawable.localizador);

        // LinearLayout vertical para los textos
        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textLayout.setLayoutParams(textParams);

        // TextView para el nombre del dispositivo
        TextView deviceName = new TextView(this);
        deviceName.setText(device.nickname);
        deviceName.setTextSize(16);
        deviceName.setTextColor(getResources().getColor(R.color.black));
        deviceName.setTypeface(deviceName.getTypeface(), android.graphics.Typeface.BOLD);

        // TextView para el estado
        TextView deviceStatus = new TextView(this);
        deviceStatus.setText("Estado: Activo");
        deviceStatus.setTextSize(14);
        deviceStatus.setTextColor(getResources().getColor(R.color.green));

        textLayout.addView(deviceName);
        textLayout.addView(deviceStatus);

        // ImageView para el icono de navegación
        ImageView navIcon = new ImageView(this);
        navIcon.setLayoutParams(new LinearLayout.LayoutParams(48, 48));
        navIcon.setImageResource(android.R.drawable.ic_menu_more);
        navIcon.setColorFilter(getResources().getColor(R.color.gray));

        horizontalLayout.addView(deviceImage);
        horizontalLayout.addView(textLayout);
        horizontalLayout.addView(navIcon);


        LinearLayout emergencyLayout = new LinearLayout(this);
        LinearLayout.LayoutParams emergencyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        emergencyParams.setMargins(0, 24, 0, 0);
        emergencyLayout.setLayoutParams(emergencyParams);
        emergencyLayout.setOrientation(LinearLayout.HORIZONTAL);
        emergencyLayout.setPadding(16, 16, 16, 16);
        emergencyLayout.setBackgroundResource(R.drawable.emergency_contact_background);

        ImageView phoneIcon = new ImageView(this);
        LinearLayout.LayoutParams phoneParams = new LinearLayout.LayoutParams(48, 48);
        phoneParams.setMargins(0, 0, 16, 0);
        phoneIcon.setLayoutParams(phoneParams);
        phoneIcon.setImageResource(android.R.drawable.ic_menu_call);
        phoneIcon.setColorFilter(getResources().getColor(R.color.red));

        TextView emergencyText = new TextView(this);
        LinearLayout.LayoutParams emergencyTextParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        emergencyText.setLayoutParams(emergencyTextParams);
        emergencyText.setText("Contacto de Emergencia:");
        emergencyText.setTextSize(14);
        emergencyText.setTextColor(getResources().getColor(R.color.black));

        TextView phoneNumber = new TextView(this);
        phoneNumber.setText(device.emergencyContact);
        phoneNumber.setTextSize(16);
        phoneNumber.setTextColor(getResources().getColor(R.color.red));
        phoneNumber.setPadding(8, 8, 8, 8);
        phoneNumber.setClickable(true);
        phoneNumber.setFocusable(true);
        phoneNumber.setBackgroundResource(android.R.drawable.list_selector_background);

        phoneNumber.setOnClickListener(v -> callEmergency(device.emergencyContact));

        emergencyLayout.addView(phoneIcon);
        emergencyLayout.addView(emergencyText);
        emergencyLayout.addView(phoneNumber);
        deviceLayout.addView(horizontalLayout);
        deviceLayout.addView(emergencyLayout);

        deviceLayout.setOnClickListener(v -> {
            Intent intent = new Intent(NotificationsActivity.this, DashBoardActivity.class);
            intent.putExtra("device_name", device.nickname);
            intent.putExtra("device_id", device.deviceId);
            startActivity(intent);
        });

        return deviceLayout;
    }

    private void showNoDevicesMessage() {
        devicesContainer.removeAllViews();

        TextView noDevicesText = new TextView(this);
        noDevicesText.setText("No tienes dispositivos asociados.\n\nContacta con el administrador para añadir dispositivos.");
        noDevicesText.setTextSize(16);
        noDevicesText.setTextColor(getResources().getColor(android.R.color.darker_gray));
        noDevicesText.setGravity(android.view.Gravity.CENTER);
        noDevicesText.setPadding(32, 64, 32, 64);

        devicesContainer.addView(noDevicesText);
    }

    private void initializeLocationServices() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (checkLocationPermissions()) {
            requestLocationUpdates();
        } else {
            requestLocationPermissions();
        }
    }

    private void initializeNotificationSystem() {
        notificationHandler = new Handler(Looper.getMainLooper());

        notificationChecker = new Runnable() {
            @Override
            public void run() {
                checkForNewNotifications();
                notificationHandler.postDelayed(this, NOTIFICATION_CHECK_INTERVAL);
            }
        };

        notificationHandler.post(notificationChecker);
    }

    private void checkForNewNotifications() {
        if (!areNotificationsEnabled) {
            return;
        }

        for (DeviceInfo device : userDevices) {
            checkDeviceForAccidents(device);
            checkDeviceForGeofencing(device);
        }
    }

    private void checkDeviceForAccidents(DeviceInfo device) {
        AuthManager authManager = new AuthManager(this);
        String authToken = authManager.getToken();

        if (authToken == null) {
            Log.e(TAG, "No hay token para verificar accidentes");
            return;
        }

        String apiUrl = "http://172.20.10.2:8080/api/devices/" + device.deviceId + "/sensorsAcStates";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + authToken)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "Error checking device " + device.deviceId + " for accidents", e);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String responseBody = null;
                try {
                    responseBody = response.body().string();

                    if (response.isSuccessful()) {
                        JSONArray sensorsArray = new JSONArray(responseBody);

                        if (sensorsArray.length() > 0) {
                            JSONObject latestSensor = sensorsArray.getJSONObject(0);

                            int valueAc = latestSensor.optInt("valueAc", 0);
                            int valueGir = latestSensor.optInt("valueGir", 0);

                            if (valueAc > ACCELEROMETER_THRESHOLD || valueGir > GYROSCOPE_THRESHOLD) {
                                final String severity = determineSeverity(valueAc, valueGir);
                                final String message = createAccidentMessage(valueAc, valueGir, severity);
                                final int finalValueAc = valueAc;
                                final int finalValueGir = valueGir;

                                runOnUiThread(() -> {
                                    showAccidentNotification(device, message, severity, finalValueAc, finalValueGir);
                                });
                            } else {
                                runOnUiThread(() -> {
                                    String notificationKey = device.deviceId + "_ACCIDENT";
                                    if (activeNotifications.containsKey(notificationKey)) {
                                        activeNotifications.remove(notificationKey);
                                        updateNotificationUI();
                                    }
                                });
                            }
                        } else {
                            Log.w(TAG, "No sensor data found for device " + device.deviceId);
                        }

                    } else if (response.code() == 401) {
                        Log.e(TAG, "Token expired for device " + device.deviceId);
                        runOnUiThread(() -> redirectToLogin());
                    } else if (response.code() == 404) {
                        Log.w(TAG, "No sensors found for device " + device.deviceId);
                    } else if (response.code() == 500) {
                        Log.e(TAG, "Server error (500) for device " + device.deviceId + ": " + responseBody);
                    } else {
                        Log.e(TAG, "Error getting sensor data for device " + device.deviceId + ": " + response.code() + " - " + responseBody);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing sensor data for device " + device.deviceId + ": " + responseBody, e);
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
    }

    private boolean validateToken() {
        AuthManager authManager = new AuthManager(this);
        String token = authManager.getToken();

        if (token == null || token.isEmpty()) {
            Log.e(TAG, "No hay token disponible");
            return false;
        }

        try {
            String decoded = new String(Base64.decode(token, Base64.DEFAULT));
            String[] parts = decoded.split(":");

            if (parts.length >= 3) {
                long tokenTimestamp = Long.parseLong(parts[2]);
                long currentTime = System.currentTimeMillis();
                long diffMinutes = (currentTime - tokenTimestamp) / (1000 * 60);

                if (diffMinutes > 4) {
                    Log.w(TAG, "Token expirado, forzando nuevo login");
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Sesión expirada. Redirigiendo al login...", Toast.LENGTH_LONG).show();
                        redirectToLogin();
                    });
                    return false;
                } else {
                    return true;
                }
            } else {
                Log.e(TAG, "Formato de token inválido");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verificando token", e);
            return false;
        }
    }

    private void checkDevicePermissions(DeviceInfo device, Runnable onSuccess) {
        AuthManager authManager = new AuthManager(this);
        String authToken = authManager.getToken();
        int userId = authManager.getUserId();

        String apiUrl = "http://172.20.10.2:8080/api/users/" + userId + "/devices";
        Log.d(TAG, "Verificando permisos para dispositivo " + device.deviceId);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "Error verificando permisos para device " + device.deviceId, e);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "No body";

                if (response.isSuccessful()) {
                    try {
                        JSONArray devicesArray = new JSONArray(responseBody);
                        boolean hasPermission = false;

                        for (int i = 0; i < devicesArray.length(); i++) {
                            JSONObject deviceObj = devicesArray.getJSONObject(i);
                            String deviceId = deviceObj.optString("device_id", deviceObj.optString("idDevice", ""));

                            if (deviceId.equals(device.deviceId)) {
                                hasPermission = true;
                                Log.d(TAG, "Usuario tiene permisos para dispositivo " + device.deviceId);
                                break;
                            }
                        }

                        if (hasPermission) {
                            runOnUiThread(onSuccess);
                        } else {
                            Log.e(TAG, "Usuario NO tiene permisos para dispositivo " + device.deviceId);
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error parseando dispositivos para permisos", e);
                    }
                } else if (response.code() == 401) {
                    Log.e(TAG, "Token inválido al verificar permisos para device " + device.deviceId);
                    runOnUiThread(() -> redirectToLogin());
                }
            }
        });
    }

    private void checkDeviceForGeofencing(DeviceInfo device) {
        if (!isGeofencingEnabled) {
            return;
        }

        if (!isPhoneLocationAvailable) {
            return;
        }

        if (!validateToken()) {
            return;
        }

        checkDevicePermissions(device, () -> {
            getLocationForGeofencing(device);
        });
    }

    private boolean checkLocationPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocationUpdates();
            } else {
                Log.w(TAG, "Permisos de ubicación denegados - Geofencing deshabilitado");
                Toast.makeText(this, "Permisos de ubicación necesarios para geofencing", Toast.LENGTH_LONG).show();
                isGeofencingEnabled = false;
                updateGeofencingButton();
                updateGeofencingStatus();
            }
        }
    }

    private void requestLocationUpdates() {
        if (!checkLocationPermissions()) {
            return;
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        30000, // 30 segundos
                        50,    // 50 metros
                        this);

                Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocation != null) {
                    onLocationChanged(lastKnownLocation);
                }
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        60000, // 1 minuto
                        100,   // 100 metros
                        this);

                if (!isPhoneLocationAvailable) {
                    Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if (networkLocation != null) {
                        onLocationChanged(networkLocation);
                    }
                }

            }

        } catch (SecurityException e) {
            Log.e(TAG, "Error al solicitar actualizaciones de ubicación", e);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        phoneLatitude = location.getLatitude();
        phoneLongitude = location.getLongitude();
        isPhoneLocationAvailable = true;


        updateGeofencingStatus();

        runOnUiThread(() -> {
            Toast.makeText(this, "Zona segura actualizada a ubicación actual", Toast.LENGTH_SHORT).show();
        });
    }

    private void toggleGeofencing() {
        isGeofencingEnabled = !isGeofencingEnabled;

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_GEOFENCING_ENABLED, isGeofencingEnabled);
        editor.apply();

        updateGeofencingButton();
        updateGeofencingStatus();

        String status = isGeofencingEnabled ? "activado" : "desactivado";
        Toast.makeText(this, "Geofencing " + status, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Geofencing " + status + " por el usuario");

        if (!isGeofencingEnabled) {
            removeAllGeofenceNotifications();
        }
    }

    private void updateGeofencingButton() {
        if (toggleGeofencingButton == null) return;

        if (isGeofencingEnabled) {
            toggleGeofencingButton.setText("🛡️ DESACTIVAR GEOFENCING");
            toggleGeofencingButton.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            toggleGeofencingButton.setText("🚫 ACTIVAR GEOFENCING");
            toggleGeofencingButton.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        }
        toggleGeofencingButton.setTextColor(getResources().getColor(android.R.color.white));
        toggleGeofencingButton.setTextSize(12);
    }

    private void updateGeofencingStatus() {
        if (geofencingStatusText == null) return;

        String statusText;
        if (!isGeofencingEnabled) {
            statusText = "Geofencing desactivado";
        } else if (!isPhoneLocationAvailable) {
            statusText = "Esperando ubicación del móvil...";
        } else {
            statusText = String.format("Zona segura: %.6f, %.6f (Radio: %.0fm)",
                    phoneLatitude, phoneLongitude, DYNAMIC_SAFE_ZONE_RADIUS_METERS);
        }

        geofencingStatusText.setText(statusText);
    }

    private void removeAllGeofenceNotifications() {
        boolean removed = false;

        java.util.Iterator<Map.Entry<String, NotificationInfo>> iterator = activeNotifications.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, NotificationInfo> entry = iterator.next();
            if (entry.getValue().alertType.equals("GEOFENCE")) {
                iterator.remove();
                removed = true;
                Log.d(TAG, "Removed geofence notification: " + entry.getKey());
            }
        }

        if (removed) {
            updateNotificationUI();
            Toast.makeText(this, "Notificaciones de geofencing eliminadas", Toast.LENGTH_SHORT).show();
        }
    }

    private void getLocationForGeofencing(DeviceInfo device) {
        AuthManager authManager = new AuthManager(this);
        String authToken = authManager.getToken();

        if (authToken == null) {
            Log.e(TAG, "No hay token para verificar geofencing");
            return;
        }

        String apiUrl = "http://172.20.10.2:8080/api/devices/" + device.deviceId + "/sensorsGpsStates";
        Log.d(TAG, "Obteniendo ubicación GPS para device " + device.deviceId + " - URL: " + apiUrl);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("Content-Type", "application/json")
                .build();

        Log.d(TAG, "=== REQUEST HEADERS para GPS ===");
        for (String name : request.headers().names()) {
            String value = request.header(name);
            if (name.equals("Authorization")) {
                Log.d(TAG, name + ": Bearer " + authToken.substring(0, Math.min(20, authToken.length())) + "...");
            } else {
                Log.d(TAG, name + ": " + value);
            }
        }

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "Error obteniendo GPS para device " + device.deviceId, e);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "No body";

                try {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "¡Éxito GPS! Procesando datos para device " + device.deviceId);

                        JSONArray jsonArray = new JSONArray(responseBody);

                        if (jsonArray.length() > 0) {
                            JSONObject jsonObject = jsonArray.getJSONObject(0);

                            double currentLatitude, currentLongitude;

                            if (jsonObject.has("valueLat") && jsonObject.has("valueLong")) {
                                currentLatitude = jsonObject.getDouble("valueLong");
                                currentLongitude = jsonObject.getDouble("valueLat");
                            } else if (jsonObject.has("latitude") && jsonObject.has("longitude")) {
                                currentLatitude = jsonObject.getDouble("latitude");
                                currentLongitude = jsonObject.getDouble("longitude");
                            } else {
                                Log.e(TAG, "Campos de coordenadas no encontrados para device " + device.deviceId + ": " + jsonObject.toString());
                                return;
                            }

                            Log.d(TAG, "GPS obtenido para device " + device.deviceId + ": " + currentLatitude + ", " + currentLongitude);

                            if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                                processGeofencing(device, currentLatitude, currentLongitude);
                            } else {
                                Log.w(TAG, "Coordenadas GPS vacías para device " + device.deviceId);
                            }

                        } else {
                            Log.w(TAG, "No hay datos GPS para device " + device.deviceId);
                        }

                    } else if (response.code() == 401) {
                        Log.e(TAG, "ERROR 401 GPS - Token inválido para device " + device.deviceId);
                        runOnUiThread(() -> {
                            Toast.makeText(NotificationsActivity.this,
                                    "Sesión expirada. Redirigiendo al login...",
                                    Toast.LENGTH_LONG).show();
                            redirectToLogin();
                        });

                    } else if (response.code() == 404) {
                        Log.e(TAG, "ERROR 404 GPS - Dispositivo " + device.deviceId + " no encontrado");

                    } else {
                        Log.e(TAG, "Error GPS del servidor para device " + device.deviceId + ": " + response.code() + " - " + responseBody);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error parseando GPS para device " + device.deviceId + ": " + responseBody, e);
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
    }

    private void processGeofencing(DeviceInfo device, double currentLatitude, double currentLongitude) {
        final double distance = calculateDistance(
                phoneLatitude, phoneLongitude,
                currentLatitude, currentLongitude
        );

        boolean currentlyInSafeZone = distance <= DYNAMIC_SAFE_ZONE_RADIUS_METERS;

        final double finalLatitude = currentLatitude;
        final double finalLongitude = currentLongitude;

        if (device.isInsideSafeZone && !currentlyInSafeZone) {
            final String message = "🚨 DISPOSITIVO FUERA DE ZONA SEGURA";
            final String severity = "HIGH";

            Log.d(TAG, "¡GEOFENCE ALERT! Device " + device.deviceId + " left safe zone. Distance: " + Math.round(distance) + "m");

            runOnUiThread(() -> {
                showGeofenceNotification(device, message, severity, finalLatitude, finalLongitude, distance);
            });
        } else if (!device.isInsideSafeZone && currentlyInSafeZone) {
            Log.d(TAG, "Device " + device.deviceId + " returned to safe zone");

            runOnUiThread(() -> {
                removeGeofenceNotification(device);
            });
        }

        device.lastLatitude = finalLatitude;
        device.lastLongitude = finalLongitude;
        device.isInsideSafeZone = currentlyInSafeZone;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radio de la Tierra en kilómetros

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // Convertir a metros

        return distance;
    }

    private String determineSeverity(int valueAc, int valueGir) {
        int maxValue = Math.max(valueAc, valueGir);

        if (maxValue > 30) {
            return "HIGH";
        } else if (maxValue > 20) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private String createAccidentMessage(int valueAc, int valueGir, String severity) {
        switch (severity) {
            case "HIGH":
                return "🚨 ACCIDENTE GRAVE DETECTADO";
            case "MEDIUM":
                return "⚠️ Posible accidente detectado";
            default:
                return "ℹ️ Movimiento inusual detectado";
        }
    }

    private void showAccidentNotification(DeviceInfo device, String message, String severity, int valueAc, int valueGir) {
        String notificationKey = device.deviceId + "_ACCIDENT";

        NotificationInfo notification = new NotificationInfo(device.deviceId, device.nickname, message, severity, valueAc, valueGir);
        activeNotifications.put(notificationKey, notification);

        updateNotificationUI();

        Log.d(TAG, "Mostrando notificación de accidente para " + device.nickname + ": " + message);
        Toast.makeText(this, "ACCIDENTE DETECTADO: " + device.nickname, Toast.LENGTH_LONG).show();
    }

    private void showGeofenceNotification(DeviceInfo device, String message, String severity,
                                          double latitude, double longitude, double distance) {
        String notificationKey = device.deviceId + "_GEOFENCE";

        NotificationInfo notification = new NotificationInfo(device.deviceId, device.nickname,
                message, severity, latitude, longitude);
        activeNotifications.put(notificationKey, notification);

        updateNotificationUI();

        Log.d(TAG, "Mostrando notificación de geofencing para " + device.nickname + ": " + message);
        Toast.makeText(this, "FUERA DE ZONA SEGURA: " + device.nickname +
                " (" + Math.round(distance) + "m de ubicación)", Toast.LENGTH_LONG).show();
    }

    private void removeGeofenceNotification(DeviceInfo device) {
        String notificationKey = device.deviceId + "_GEOFENCE";

        if (activeNotifications.containsKey(notificationKey)) {
            activeNotifications.remove(notificationKey);
            updateNotificationUI();

            Toast.makeText(this, device.nickname + " volvió a zona segura", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNotificationUI() {
        notificationContainer.removeAllViews();

        for (NotificationInfo notification : activeNotifications.values()) {
            View notificationView = createNotificationView(notification);
            notificationContainer.addView(notificationView);
        }
    }

    private View createNotificationView(NotificationInfo notification) {
        LinearLayout notificationLayout = new LinearLayout(this);
        notificationLayout.setOrientation(LinearLayout.VERTICAL);
        notificationLayout.setPadding(16, 16, 16, 16);
        notificationLayout.setBackgroundResource(getNotificationBackground(notification.severity));

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(0, 0, 0, 16);
        notificationLayout.setLayoutParams(layoutParams);

        TextView deviceTitle = new TextView(this);
        deviceTitle.setText(notification.deviceName);
        deviceTitle.setTextSize(16);
        deviceTitle.setTextColor(getResources().getColor(android.R.color.black));
        deviceTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        notificationLayout.addView(deviceTitle);

        TextView messageText = new TextView(this);
        messageText.setText(notification.message);
        messageText.setTextSize(14);
        messageText.setTextColor(getResources().getColor(android.R.color.black));
        notificationLayout.addView(messageText);

        TextView detailsText = new TextView(this);
        if ("ACCIDENT".equals(notification.alertType)) {
            detailsText.setText("Acelerómetro: " + notification.valueAc + " | Giroscopio: " + notification.valueGir);
        } else if ("GEOFENCE".equals(notification.alertType)) {
            detailsText.setText("Ubicación: " + String.format("%.6f", notification.latitude) +
                    ", " + String.format("%.6f", notification.longitude));
        }
        detailsText.setTextSize(12);
        detailsText.setTextColor(getResources().getColor(android.R.color.darker_gray));
        notificationLayout.addView(detailsText);

        TextView timestampText = new TextView(this);
        long timeAgo = System.currentTimeMillis() - notification.timestamp;
        String timeAgoStr = formatTimeAgo(timeAgo);
        timestampText.setText("Hace " + timeAgoStr);
        timestampText.setTextSize(12);
        timestampText.setTextColor(getResources().getColor(android.R.color.darker_gray));
        notificationLayout.addView(timestampText);

        LinearLayout buttonContainer = new LinearLayout(this);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(android.view.Gravity.CENTER);
        buttonContainer.setPadding(0, 8, 0, 0);

        Button callButton = new Button(this);
        callButton.setText("LLAMAR EMERGENCIAS");
        callButton.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
        callButton.setTextColor(getResources().getColor(android.R.color.white));
        callButton.setTextSize(12);
        callButton.setOnClickListener(v -> callEmergency("112"));

        LinearLayout.LayoutParams callButtonParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        callButtonParams.setMargins(0, 0, 8, 0);
        callButton.setLayoutParams(callButtonParams);

        Button closeButton = new Button(this);
        closeButton.setText("CERRAR");
        closeButton.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        closeButton.setTextColor(getResources().getColor(android.R.color.white));
        closeButton.setTextSize(12);

        LinearLayout.LayoutParams closeButtonParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        closeButtonParams.setMargins(8, 0, 0, 0);
        closeButton.setLayoutParams(closeButtonParams);

        closeButton.setOnClickListener(v -> {
            String notificationKey = notification.deviceId + "_" + notification.alertType;
            activeNotifications.remove(notificationKey);
            updateNotificationUI();
            Toast.makeText(this, "Notificación de " + notification.deviceName + " cerrada",
                    Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Notificación cerrada manualmente: " + notificationKey);
        });

        buttonContainer.addView(callButton);
        buttonContainer.addView(closeButton);
        notificationLayout.addView(buttonContainer);

        return notificationLayout;
    }

    private int getNotificationBackground(String severity) {
        switch (severity) {
            case "HIGH":
                return android.R.color.holo_red_light;
            case "MEDIUM":
                return android.R.color.holo_orange_dark;
            default:
                return android.R.color.holo_orange_light;
        }
    }

    private String formatTimeAgo(long timeAgo) {
        long minutes = timeAgo / (1000 * 60);
        long hours = timeAgo / (1000 * 60 * 60);

        if (hours > 0) {
            return hours + " hora(s)";
        } else if (minutes > 0) {
            return minutes + " minuto(s)";
        } else {
            return "menos de 1 minuto";
        }
    }

    private void callEmergency(String phoneNumber) {
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + phoneNumber));

        try {
            startActivity(callIntent);
        } catch (SecurityException e) {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            dialIntent.setData(Uri.parse("tel:" + phoneNumber));
            startActivity(dialIntent);
        }
    }

    private void redirectToLogin() {
        AuthManager authManager = new AuthManager(this);
        authManager.logout();

        Intent intent = new Intent(NotificationsActivity.this, com.example.wait.ui.Login.LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (areNotificationsEnabled && notificationHandler != null && notificationChecker != null) {
            notificationHandler.removeCallbacks(notificationChecker);
            notificationHandler.post(notificationChecker);
        }

        // Recargar dispositivos al volver a la actividad
        if (userDevices != null) {
            loadUserDevices();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (notificationHandler != null && notificationChecker != null) {
            notificationHandler.removeCallbacks(notificationChecker);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "Location provider status changed: " + provider + " status: " + status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Location provider enabled: " + provider);
        if (checkLocationPermissions()) {
            requestLocationUpdates();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Location provider disabled: " + provider);
        Toast.makeText(this, "Proveedor de ubicación " + provider + " deshabilitado", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() llamado");

        if (notificationHandler != null && notificationChecker != null) {
            notificationHandler.removeCallbacks(notificationChecker);
        }

        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
                Log.d(TAG, "Actualizaciones de ubicación detenidas");
            } catch (SecurityException e) {
                Log.e(TAG, "Error al detener actualizaciones de ubicación", e);
            }
        }

        if (activeNotifications != null) {
            activeNotifications.clear();
        }
    }
}