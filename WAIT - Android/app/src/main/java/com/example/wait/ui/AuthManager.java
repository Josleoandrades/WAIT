package com.example.wait.ui;

import static android.content.ContentValues.TAG;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.example.wait.MainActivity;
import com.example.wait.ui.Login.LoginActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import android.content.Context;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

import okhttp3.Request;
import okhttp3.Response;

public class AuthManager {
    private static final String PREF_TOKEN = "jwt_token";
    private static final String PREF_USER_ID = "user_id";
    private static final String PREF_USERNAME = "username";


    private SharedPreferences prefs;
    private Context context;
    private RequestQueue requestQueue;

    public AuthManager(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE);
        requestQueue = Volley.newRequestQueue(context);
    }

    public void saveToken(String token, int userId, String username) {
        prefs.edit()
                .putString(PREF_TOKEN, token)
                .putInt(PREF_USER_ID, userId)
                .putString(PREF_USERNAME, username)
                .apply();
    }



    public String getToken() {
        return prefs.getString(PREF_TOKEN, null);
    }

    public int getUserId() {
        return prefs.getInt(PREF_USER_ID, -1);
    }

    public String getUsername() {
        return prefs.getString(PREF_USERNAME, "");
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public void logout() {
        prefs.edit().clear().apply();
    }

    public void registerFromAPI(String username, String email, String phone, String password) {
        String apiUrl = "http://172.20.10.2:8080/api/register";

        // Crear Handler para ejecutar en UI thread
        Handler mainHandler = new Handler(Looper.getMainLooper());

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("username", username);
            jsonBody.put("email", email);
            jsonBody.put("phone", phone);
            jsonBody.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
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
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                e.printStackTrace();
                Log.e("API_REQUEST", "Error en la petición: " + e.getMessage());

                // AÑADIR: Mostrar error en UI thread
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context,
                                "Error de conexión: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String responseBody = response.body().string();

                if (response.isSuccessful()) {
                    // Registro exitoso
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String message = jsonResponse.optString("message", "Usuario registrado exitosamente");
                        int userId = jsonResponse.optInt("user_id", -1);



                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context,
                                        "¡Registro exitoso!",
                                        Toast.LENGTH_SHORT).show();

                                // Cambiar a LoginActivity
                                Intent intent = new Intent(context, LoginActivity.class);
                                intent.putExtra("registered_email", email); // Para prellenar el email
                                context.startActivity(intent);

                                if (context instanceof Activity) {
                                    ((Activity) context).finish();
                                }
                            }
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("API_RESPONSE", "Error parseando respuesta JSON: " + e.getMessage());

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context,
                                        "Usuario registrado exitosamente",
                                        Toast.LENGTH_SHORT).show();

                                Intent intent = new Intent(context, LoginActivity.class);
                                context.startActivity(intent);

                                if (context instanceof Activity) {
                                    ((Activity) context).finish();
                                }
                            }
                        });
                    }

                } else {
                    // Error del servidor
                    try {
                        JSONObject errorJson = new JSONObject(responseBody);
                        String errorMessage = errorJson.optString("error", "Error desconocido");
                        String errorDetails = errorJson.optString("details", "");

                        Log.e("API_RESPONSE", "Error del servidor: " + errorMessage);
                        Log.e("API_RESPONSE", "Detalles: " + errorDetails);

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                String fullMessage = errorMessage;
                                if (!errorDetails.isEmpty()) {
                                    fullMessage += "\n\nDetalles: " + errorDetails;
                                }
                                Toast.makeText(context, fullMessage, Toast.LENGTH_LONG).show();
                            }
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("API_RESPONSE", "Error del servidor - Response: " + responseBody);

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context,
                                        "Error del servidor: " + response.code(),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        });
    }


    public void loginFromAPI(String email, String password) {
        // URL
        String apiUrl = "http://172.20.10.2:8080/api/login";
        Log.d("API_REQUEST", "Llamando a: " + apiUrl);

        // Crear Handler para ejecutar en UI thread
        Handler mainHandler = new Handler(Looper.getMainLooper());

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("email", email);
            jsonBody.put("password", password);
            Log.d("API_REQUEST", "JSON Body: " + jsonBody.toString());
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)    // 60 segundos para conectar
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)       // 60 segundos para leer respuesta
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)      // 60 segundos para escribir
                .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS)      // 120 segundos timeout total
                .build();

        RequestBody requestBody = RequestBody.create(
                jsonBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                e.printStackTrace();
                Log.e("API_REQUEST", "Error en la petición: " + e.getMessage());

                // Mostrar error en UI thread
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context,
                                "Error de conexión: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();

                        // Ocultar progress bar si existe
                        if (context instanceof LoginActivity) {
                            ((LoginActivity) context).hideProgress();
                        }
                    }
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String responseBody = response.body().string();

                if (response.isSuccessful()) {
                    // Login exitoso
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String token = jsonResponse.getString("token");
                        int userId = jsonResponse.getInt("user_id");
                        String username = jsonResponse.getString("username");


                        // Guardar datos de sesión
                        saveToken(token, userId, username);

                        // Mostrar mensaje y navegar
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context,
                                        "¡Bienvenido " + username + "!",
                                        Toast.LENGTH_SHORT).show();

                                if (context instanceof LoginActivity) {
                                    Intent intent = new Intent(context, MainActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    context.startActivity(intent);
                                    ((Activity) context).finish();
                                }
                            }
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("API_RESPONSE", "Error parseando respuesta JSON: " + e.getMessage());

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context,
                                        "Error en la respuesta del servidor",
                                        Toast.LENGTH_LONG).show();

                                if (context instanceof LoginActivity) {
                                    ((LoginActivity) context).hideProgress();
                                }
                            }
                        });
                    }

                } else {
                    // Error del servidor
                    try {
                        JSONObject errorJson = new JSONObject(responseBody);
                        String errorMessage = errorJson.optString("error", "Error desconocido");

                        Log.e("API_RESPONSE", "Error del servidor: " + errorMessage);

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                String userFriendlyMessage;

                                if (response.code() == 401) {
                                    userFriendlyMessage = "Email o contraseña incorrectos";
                                } else if (response.code() == 404) {
                                    userFriendlyMessage = "Usuario no encontrado";
                                } else if (response.code() >= 500) {
                                    userFriendlyMessage = "Error del servidor. Intenta más tarde.";
                                } else {
                                    userFriendlyMessage = errorMessage;
                                }

                                Toast.makeText(context, userFriendlyMessage, Toast.LENGTH_LONG).show();

                                if (context instanceof LoginActivity) {
                                    ((LoginActivity) context).hideProgress();
                                }
                            }
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("API_RESPONSE", "Error del servidor - Response: " + responseBody);

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context,
                                        "Error del servidor: " + response.code(),
                                        Toast.LENGTH_LONG).show();

                                // Ocultar el progress bar si el contexto es LoginActivity
                                if (context instanceof LoginActivity) {
                                    ((LoginActivity) context).hideProgress();
                                }
                            }
                        });
                    }
                }
            }
        });
    }


}