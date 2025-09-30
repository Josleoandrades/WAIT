package com.example.wait;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wait.ui.AuthManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.app.AlertDialog;
import android.widget.EditText;

import com.example.wait.ui.dashboard.DashBoardActivity;
import com.example.wait.ui.notifications.NotificationsActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.example.wait.databinding.ActivityMainBinding;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    BottomNavigationView bottomNavigationView;
    private List<DeviceInfo> userDevices;
    private LinearLayout devicesContainer; // Contenedor principal para todos los dispositivos
    private ImageButton btnLogout;

    private static final String TAG = "MainActivity";
    private boolean isRedirecting = false;

    // Clase para almacenar información del dispositivo
    private static class DeviceInfo {
        String deviceId;
        String name;
        String nickname;

        DeviceInfo(String deviceId, String name, String nickname) {
            this.deviceId = deviceId;
            this.name = name;
            this.nickname = nickname;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userDevices = new ArrayList<>();

        if (!checkUserSession()) {
            return;
        }

        initializeViews();

        loadUserDevices();
    }

    private boolean checkUserSession() {
        if (isRedirecting) {
            return false;
        }

        AuthManager authManager = new AuthManager(this);
        boolean isLoggedIn = authManager.isLoggedIn();


        if (!isLoggedIn) {
            Log.e(TAG, "No hay sesión válida según AuthManager - redirigiendo al login");
            redirectToLogin();
            return false;
        }

        return true;
    }

    private void initializeViews() {
        bottomNavigationView = findViewById(R.id.nav_view);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        bottomNavigationView.setSelectedItemId(R.id.navigation_home);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        // Inicializar el contenedor de dispositivos
        devicesContainer = findViewById(R.id.devicesContainer);

        // Botón de perfil WAIT (centrado)
        ImageButton btnPerfilWAIT = findViewById(R.id.btnPerfilWAIT);
        btnPerfilWAIT.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MainActivity.class);
            startActivity(intent);
        });

        // Botón de cerrar sesión
        btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> showLogoutDialog());

        // Botón flotante para añadir dispositivo
        FloatingActionButton fabAddDevice = findViewById(R.id.fabAddDevice);
        fabAddDevice.setOnClickListener(v -> showAddDeviceDialog());

        // Navigation listener
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.navigation_dashboard) {
                Intent intent = new Intent(MainActivity.this, DashBoardActivity.class);
                startActivity(intent);
                clearNavigationSelection();
                return true;
            } else if (id == R.id.navigation_notifications) {
                Intent intent = new Intent(MainActivity.this, NotificationsActivity.class);
                startActivity(intent);
                clearNavigationSelection();
                return true;
            }
            return false;
        });

    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Cerrar Sesión")
                .setMessage("¿Estás seguro de que deseas cerrar sesión?")
                .setPositiveButton("Sí", (dialog, which) -> logout())
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void logout() {
        Log.d(TAG, "Logout iniciado por el usuario");

        AuthManager authManager = new AuthManager(this);
        authManager.logout();

        performServerLogout();
        redirectToLogin();
    }

    private void performServerLogout() {
        AuthManager authManager = new AuthManager(this);
        String authToken = authManager.getToken();

        if (authToken != null) {
            String apiUrl = "http://172.20.10.2:8080/api/auth/logout";

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + authToken)
                    .delete()
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    Log.e(TAG, "Error en logout del servidor", e);
                }

                @Override
                public void onResponse(okhttp3.Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Logout del servidor exitoso");
                    } else {
                        Log.e(TAG, "Error en logout del servidor: " + response.code());
                    }
                }
            });
        }
    }

    private void redirectToLogin() {
        if (isRedirecting) {
            return;
        }

        isRedirecting = true;
        AuthManager authManager = new AuthManager(this);
        authManager.logout();

        Intent intent = new Intent(MainActivity.this, com.example.wait.ui.Login.LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void clearNavigationSelection() {
        bottomNavigationView.getMenu().findItem(R.id.navigation_home).setChecked(false);
        bottomNavigationView.getMenu().findItem(R.id.navigation_dashboard).setChecked(false);
        bottomNavigationView.getMenu().findItem(R.id.navigation_notifications).setChecked(false);
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
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String jsonResponse = response.body().string();
                        Log.d(TAG, "Dispositivos obtenidos: " + jsonResponse);

                        JSONArray devicesArray = new JSONArray(jsonResponse);

                        List<DeviceInfo> newDevices = new ArrayList<>();

                        for (int i = 0; i < devicesArray.length(); i++) {
                            JSONObject device = devicesArray.getJSONObject(i);
                            String deviceId = device.getString("device_id");
                            String deviceName = device.optString("name", "Dispositivo");
                            String nickname = device.optString("nickname", deviceName);

                            newDevices.add(new DeviceInfo(deviceId, deviceName, nickname));
                        }

                        runOnUiThread(() -> {
                            userDevices.clear();
                            userDevices.addAll(newDevices);
                            updateDeviceUI();
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing devices response", e);
                        runOnUiThread(() -> showNoDevicesMessage());
                    }
                } else if (response.code() == 401) {
                    Log.e(TAG, "Token expirado (401) - redirigiendo al login");
                    runOnUiThread(() -> redirectToLogin());
                } else {
                    Log.e(TAG, "Error response: " + response.code());

                    if (response.code() == 500) {
                        Log.e(TAG, "Error 500 - Problema en el servidor");
                        runOnUiThread(() -> {
                            showNoDevicesMessage();
                            Toast.makeText(MainActivity.this,
                                    "No se pudieron cargar los dispositivos. Intenta más tarde.",
                                    Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        runOnUiThread(() -> showNoDevicesMessage());
                    }
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

        List<DeviceInfo> devicesCopy = new ArrayList<>(userDevices);

        for (DeviceInfo device : devicesCopy) {
            View deviceView = createDeviceView(device);
            devicesContainer.addView(deviceView);
        }
    }

    private View createDeviceView(DeviceInfo device) {
        CardView cardView = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(16, 16, 16, 16);
        cardView.setLayoutParams(cardParams);
        cardView.setCardElevation(8);
        cardView.setRadius(16);
        cardView.setCardBackgroundColor(getResources().getColor(android.R.color.white));

        // LinearLayout dentro del CardView
        LinearLayout deviceLayout = new LinearLayout(this);
        deviceLayout.setOrientation(LinearLayout.VERTICAL);
        deviceLayout.setPadding(32, 32, 32, 32);

        // LinearLayout horizontal para imagen y texto
        LinearLayout horizontalLayout = new LinearLayout(this);
        horizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
        horizontalLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // ImageView para el icono del dispositivo
        ImageView deviceImage = new ImageView(this);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(100, 100);
        imageParams.setMargins(0, 0, 32, 0);
        deviceImage.setLayoutParams(imageParams);
        deviceImage.setImageResource(R.drawable.localizador);

        // TextView para el nombre del dispositivo
        TextView deviceName = new TextView(this);
        deviceName.setText(device.nickname);
        deviceName.setTextSize(18);
        deviceName.setTextColor(getResources().getColor(R.color.colorWAIT));
        deviceName.setTypeface(deviceName.getTypeface(), android.graphics.Typeface.BOLD);

        // Añadir vistas al layout horizontal
        horizontalLayout.addView(deviceImage);
        horizontalLayout.addView(deviceName);

        // Añadir el layout horizontal al layout principal
        deviceLayout.addView(horizontalLayout);

        // Añadir el layout al CardView
        cardView.addView(deviceLayout);

        // Hacer el CardView clickeable
        cardView.setClickable(true);
        cardView.setFocusable(true);
        cardView.setForeground(getDrawable(android.R.drawable.list_selector_background));

        // Configurar el click listener
        cardView.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DashBoardActivity.class);
            intent.putExtra("device_name", device.nickname);
            intent.putExtra("device_id", device.deviceId);
            startActivity(intent);
        });

        return cardView;
    }

    private void showNoDevicesMessage() {
        devicesContainer.removeAllViews();

        TextView noDevicesText = new TextView(this);
        noDevicesText.setText("No tienes dispositivos asociados.\n\nPulsa el botón + para añadir uno.");
        noDevicesText.setTextSize(16);
        noDevicesText.setTextColor(getResources().getColor(android.R.color.darker_gray));
        noDevicesText.setGravity(android.view.Gravity.CENTER);
        noDevicesText.setPadding(32, 64, 32, 64);

        devicesContainer.addView(noDevicesText);
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() llamado");
        isRedirecting = false;

        if (userDevices != null) {
            loadUserDevices();
        }
    }

    private void showAddDeviceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        EditText serialInput = new EditText(this);
        serialInput.setHint("Serial del dispositivo (ej: WAIT001)");
        layout.addView(serialInput);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Nombre del dispositivo");
        layout.addView(nameInput);

        EditText nicknameInput = new EditText(this);
        nicknameInput.setHint("Apodo (opcional)");
        layout.addView(nicknameInput);

        builder.setView(layout);
        builder.setTitle("Añadir Nuevo Dispositivo");

        builder.setPositiveButton("Añadir", (dialog, which) -> {
            String serial = serialInput.getText().toString().trim();
            String name = nameInput.getText().toString().trim();
            String nickname = nicknameInput.getText().toString().trim();

            if (serial.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "Serial y nombre son obligatorios", Toast.LENGTH_SHORT).show();
                return;
            }

            if (nickname.isEmpty()) {
                nickname = name;
            }

            addDeviceToAPI(serial, name, nickname);
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void addDeviceToAPI(String deviceSerial, String deviceName, String nickname) {
        String apiUrl = "http://172.20.10.2:8080/api/add-device";

        AuthManager authManager = new AuthManager(this);
        String authToken = authManager.getToken();

        if (authToken == null) {
            Toast.makeText(this, "Error: No hay sesión válida", Toast.LENGTH_SHORT).show();
            redirectToLogin();
            return;
        }

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("deviceSerialId", deviceSerial);
            jsonBody.put("name", deviceName);
            jsonBody.put("nickname", nickname);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error creando datos", Toast.LENGTH_SHORT).show();
            return;
        }

        OkHttpClient client = new OkHttpClient();
        RequestBody requestBody = RequestBody.create(
                jsonBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + authToken)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "Error de conexión: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String responseBody = response.body().string();

                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String message = jsonResponse.getString("message");
                            int deviceId = jsonResponse.getInt("device_id");

                            Toast.makeText(MainActivity.this,
                                    "¡Dispositivo añadido! ID: " + deviceId,
                                    Toast.LENGTH_SHORT).show();

                            loadUserDevices();

                        } catch (JSONException e) {
                            Toast.makeText(MainActivity.this,
                                    "Dispositivo añadido correctamente",
                                    Toast.LENGTH_SHORT).show();
                            loadUserDevices();
                        }
                    } else if (response.code() == 401) {
                        Toast.makeText(MainActivity.this,
                                "Sesión expirada. Por favor, inicia sesión nuevamente",
                                Toast.LENGTH_LONG).show();
                        redirectToLogin();
                    } else {
                        Toast.makeText(MainActivity.this,
                                "Error del servidor: " + response.code(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}