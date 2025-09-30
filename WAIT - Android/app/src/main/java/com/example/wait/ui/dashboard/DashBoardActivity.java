package com.example.wait.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wait.MainActivity;
import com.example.wait.R;
import com.example.wait.databinding.FragmentDashboardBinding;
import com.example.wait.ui.AuthManager;
import com.example.wait.ui.notifications.NotificationsActivity;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

public class DashBoardActivity extends AppCompatActivity implements OnMapReadyCallback {

    private FragmentDashboardBinding binding;
    BottomNavigationView bottomNavigationView;

    private Double latitude = 0.0;
    private Double longitude = 0.0;
    private GoogleMap googleMap;
    private boolean locationLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = FragmentDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Inicializar mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Configurar navegación
        setupBottomNavigation();

        // Verificar token y obtener ubicación
        checkAndLoadLocation();
    }

    private void setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.nav_viewL);
        bottomNavigationView.setSelectedItemId(R.id.navigation_dashboard);

        bottomNavigationView.setOnItemSelectedListener(new BottomNavigationView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                int id = item.getItemId();

                if (id == R.id.navigation_home) {
                    Intent intent = new Intent(DashBoardActivity.this, MainActivity.class);
                    startActivity(intent);
                    return true;
                } else if (id == R.id.navigation_notifications) {
                    Intent intent = new Intent(DashBoardActivity.this, NotificationsActivity.class);
                    startActivity(intent);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.googleMap = map;



        if (locationLoaded && latitude != 0.0 && longitude != 0.0) {
            addMarkerToMap(latitude, longitude);
        } else {
            
            LatLng defaultLocation = new LatLng(37.7749, -122.4194); // San Francisco por defecto
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10));
        }
    }

    private void checkAndLoadLocation() {


        AuthManager authManager = new AuthManager(this);
        String token = authManager.getToken();

        if (token == null || token.isEmpty()) {
            redirectToLogin();
            return;
        }

        try {
            // Decodificar el token Base64
            String decoded = new String(Base64.decode(token, Base64.DEFAULT));

            String[] parts = decoded.split(":");

            if (parts.length >= 3) {
                long tokenTimestamp = Long.parseLong(parts[2]);
                long currentTime = System.currentTimeMillis();
                long diffMinutes = (currentTime - tokenTimestamp) / (1000 * 60);


                // Si el token tiene más de 4 minutos, considerarlo expirado
                if (diffMinutes > 4) {
                    Log.w("TOKEN_CHECK", "Token expirado, forzando nuevo login");
                    Toast.makeText(this, "Sesión expirada. Redirigiendo al login...", Toast.LENGTH_LONG).show();
                    redirectToLogin();
                    return;
                } else {
                    Log.d("TOKEN_CHECK", "Token válido, verificando permisos del dispositivo...");
                    // Primero verificar permisos, luego obtener ubicación
                    checkDevicePermissions();
                }
            } else {
                Log.e("TOKEN_CHECK", "Formato de token inválido");
                redirectToLogin();
            }
        } catch (Exception e) {
            Log.e("TOKEN_CHECK", "Error verificando token", e);
            redirectToLogin();
        }
    }

    private void checkDevicePermissions() {
        Intent intent = getIntent();
        String deviceIdString = intent.getStringExtra("device_id");

        AuthManager authManager = new AuthManager(this);
        String authToken = authManager.getToken();
        int userId = authManager.getUserId();

        String apiUrl = "http://172.20.10.2:8080/api/users/" + userId + "/devices";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e("PERMISSIONS_CHECK", "Error verificando permisos", e);
                runOnUiThread(() -> {
                    Toast.makeText(getApplicationContext(),
                            "Error verificando permisos del dispositivo",
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "No body";


                if (response.isSuccessful()) {
                    try {
                        JSONArray devicesArray = new JSONArray(responseBody);
                        boolean hasPermission = false;

                        for (int i = 0; i < devicesArray.length(); i++) {
                            JSONObject device = devicesArray.getJSONObject(i);
                            String deviceId = device.optString("device_id", device.optString("idDevice", ""));


                            if (deviceId.equals(deviceIdString)) {
                                hasPermission = true;
                                break;
                            }
                        }

                        if (hasPermission) {
                            runOnUiThread(() -> {
                                getLocationFromAPI();
                            });
                        } else {
                            Log.e("PERMISSIONS_CHECK", "❌ Usuario NO tiene permisos para dispositivo " + deviceIdString);
                            runOnUiThread(() -> {
                                Toast.makeText(getApplicationContext(),
                                        "No tienes permisos para acceder a este dispositivo",
                                        Toast.LENGTH_LONG).show();
                            });
                        }

                    } catch (Exception e) {
                        Log.e("PERMISSIONS_CHECK", "Error parseando dispositivos", e);
                        runOnUiThread(() -> {
                            Toast.makeText(getApplicationContext(),
                                    "Error verificando permisos",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                } else if (response.code() == 401) {
                    Log.e("PERMISSIONS_CHECK", "Token inválido al verificar permisos");
                    runOnUiThread(() -> redirectToLogin());
                } else {
                    Log.e("PERMISSIONS_CHECK", "Error del servidor: " + response.code());
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(),
                                "Error del servidor al verificar permisos: " + response.code(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void getLocationFromAPI() {
        Intent intent = getIntent();
        String deviceIdString = intent.getStringExtra("device_id");
        String deviceName = intent.getStringExtra("device_name");

        if (deviceIdString == null || deviceIdString.isEmpty()) {
            Log.e("GPS_DEBUG", "Device ID es null o vacío");
            Toast.makeText(this, "Error: No se pudo identificar el dispositivo", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthManager authManager = new AuthManager(this);
        String authToken = authManager.getToken();
        int userId = authManager.getUserId();

        if (authToken == null) {
            Log.e("GPS_DEBUG", "No hay token de autenticación");
            redirectToLogin();
            return;
        }

        String apiUrl = "http://172.20.10.2:8080/api/devices/" + deviceIdString + "/sensorsGpsStates";
        Log.d("GPS_DEBUG", "API URL: " + apiUrl);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("Content-Type", "application/json")
                .build();

        for (String name : request.headers().names()) {
            String value = request.header(name);
            if (name.equals("Authorization")) {
                Log.d("DEBUG_REQUEST", name + ": Bearer " + authToken.substring(0, Math.min(20, authToken.length())) + "...");
            } else {
                Log.d("DEBUG_REQUEST", name + ": " + value);
            }
        }

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e("API_ERROR", "Error en la petición", e);
                runOnUiThread(() -> {
                    Toast.makeText(getApplicationContext(),
                            "Error de conexión: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "No body";


                if (response.isSuccessful()) {

                    try {
                        JSONArray jsonArray = new JSONArray(responseBody);

                        if (jsonArray.length() > 0) {
                            JSONObject jsonObject = jsonArray.getJSONObject(0);

                            double newLatitude, newLongitude;

                            if (jsonObject.has("valueLat") && jsonObject.has("valueLong")) {
                                newLatitude = jsonObject.getDouble("valueLong");
                                newLongitude = jsonObject.getDouble("valueLat");
                            } else if (jsonObject.has("latitude") && jsonObject.has("longitude")) {
                                newLatitude = jsonObject.getDouble("latitude");
                                newLongitude = jsonObject.getDouble("longitude");
                            } else {
                                Log.e("API_ERROR", "Campos de coordenadas no encontrados en: " + jsonObject.toString());
                                runOnUiThread(() -> {
                                    Toast.makeText(getApplicationContext(),
                                            "Error: Formato de datos GPS inválido",
                                            Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }


                            // Actualizar coordenadas
                            latitude = newLatitude;
                            longitude = newLongitude;
                            locationLoaded = true;

                            runOnUiThread(() -> {
                                // Si el mapa ya está listo, agregar marcador
                                if (googleMap != null) {
                                    addMarkerToMap(latitude, longitude);
                                }

                                Toast.makeText(getApplicationContext(),
                                        "Ubicación obtenida: " + (deviceName != null ? deviceName : "Dispositivo"),
                                        Toast.LENGTH_SHORT).show();
                            });

                        } else {
                            Log.w("API_WARNING", "No hay datos GPS para este dispositivo");
                            runOnUiThread(() -> {
                                Toast.makeText(getApplicationContext(),
                                        "No se encontraron datos de ubicación para este dispositivo",
                                        Toast.LENGTH_LONG).show();
                            });
                        }

                    } catch (Exception e) {
                        Log.e("API_ERROR", "Error parseando respuesta JSON", e);
                        runOnUiThread(() -> {
                            Toast.makeText(getApplicationContext(),
                                    "Error procesando datos de ubicación",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                } else if (response.code() == 401) {
                    Log.e("API_ERROR", "ERROR 401 - Token inválido o expirado");
                    Log.e("API_ERROR", "Response body: " + responseBody);

                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(),
                                "Sesión expirada. Redirigiendo al login...",
                                Toast.LENGTH_LONG).show();
                        redirectToLogin();
                    });

                } else if (response.code() == 404) {
                    Log.e("API_ERROR", "ERROR 404 - Dispositivo no encontrado");
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(),
                                "Dispositivo no encontrado o sin datos de ubicación",
                                Toast.LENGTH_LONG).show();
                    });

                } else {
                    Log.e("API_ERROR", "Error del servidor: " + response.code() + " - " + responseBody);
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(),
                                "Error del servidor: " + response.code(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void addMarkerToMap(double latitude, double longitude) {
        if (googleMap != null) {

            // Obtener nombre del dispositivo para el marcador
            Intent intent = getIntent();
            String deviceName = intent.getStringExtra("device_name");
            if (deviceName == null) deviceName = "Dispositivo";

            // Limpiar marcadores anteriores
            googleMap.clear();

            // Crear nueva ubicación
            LatLng location = new LatLng(latitude, longitude);

            // Agregar marcador
            googleMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title("Ubicación de " + deviceName)
                    .snippet("Lat: " + String.format("%.6f", latitude) +
                            ", Lng: " + String.format("%.6f", longitude)));

            // Mover cámara al marcador
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15));

            Log.d("MAP_UPDATE", "Marcador agregado exitosamente");
        } else {
            Log.w("MAP_UPDATE", "GoogleMap no está inicializado aún");
        }
    }

    private void redirectToLogin() {
        Log.d("REDIRECT", "Redirigiendo al login...");

        AuthManager authManager = new AuthManager(this);
        authManager.logout();

        Intent intent = new Intent(this, com.example.wait.ui.Login.LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("LIFECYCLE", "DashBoardActivity onResume");

        // Verificar token cada vez que se reanuda la actividad
        if (!locationLoaded) {
            checkAndLoadLocation();
        }
    }
}