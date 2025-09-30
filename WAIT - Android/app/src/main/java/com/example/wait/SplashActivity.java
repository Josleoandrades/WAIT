package com.example.wait;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wait.ui.AuthManager;
import com.example.wait.ui.Login.LoginActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 1000; // 2 segundos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAuthenticationAndRedirect();
            }
        }, SPLASH_DELAY);
    }

    private void checkAuthenticationAndRedirect() {
        AuthManager authManager = new AuthManager(this);

        Intent intent;
        if (authManager.isLoggedIn()) {
            // Usuario ya autenticado, ir a MainActivity
            intent = new Intent(this, MainActivity.class);
        } else {
            // Usuario no autenticado, ir a LoginActivity
            intent = new Intent(this, LoginActivity.class);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(intent);
        finish();
    }
}