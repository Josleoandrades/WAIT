package com.example.wait.ui.Register;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wait.MainActivity;
import com.example.wait.ui.AuthManager;
import com.google.android.material.textfield.TextInputEditText;
import com.example.wait.R;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText usernameEditText;
    private TextInputEditText emailEditText;
    private TextInputEditText phoneEditText;
    private TextInputEditText passwordEditText;
    private TextInputEditText confirmPasswordEditText;
    private Button registerButton;
    private TextView loginText;
    private ProgressBar progressBar;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_activity);

        // Inicializa el AuthManager
        authManager = new AuthManager(this);

        // Referencias a los elementos de la UI
        usernameEditText = findViewById(R.id.username_edit_text);
        emailEditText = findViewById(R.id.email_edit_text);
        phoneEditText = findViewById(R.id.phone_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        confirmPasswordEditText = findViewById(R.id.confirm_password_edit_text);
        registerButton = findViewById(R.id.register_button);
        loginText = findViewById(R.id.login_text);
        progressBar = findViewById(R.id.register_progress);

        // Configurar botón de registro
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptRegister();
            }
        });

        // Configurar texto para ir a la pantalla de login
        loginText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Volver a la pantalla de login
            }
        });
    }

    private void attemptRegister() {
        // Resetear errores
        usernameEditText.setError(null);
        emailEditText.setError(null);
        phoneEditText.setError(null);
        passwordEditText.setError(null);
        confirmPasswordEditText.setError(null);

        // Obtener valores
        String username = usernameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String phone = phoneEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        String confirmPassword = confirmPasswordEditText.getText().toString();

        // Validar campos
        boolean cancel = false;
        View focusView = null;

        // Validar confirmación de contraseña
        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError(getString(R.string.error_passwords_dont_match));
            focusView = confirmPasswordEditText;
            cancel = true;
        }

        // Validar contraseña
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError(getString(R.string.error_field_required));
            focusView = passwordEditText;
            cancel = true;
        } else if (password.length() < 6) {
            passwordEditText.setError(getString(R.string.error_invalid_password));
            focusView = passwordEditText;
            cancel = true;
        }

        // Validar teléfono
        if (TextUtils.isEmpty(phone)) {
            phoneEditText.setError(getString(R.string.error_field_required));
            focusView = phoneEditText;
            cancel = true;
        }

        // Validar email
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError(getString(R.string.error_field_required));
            focusView = emailEditText;
            cancel = true;
        } else if (!isEmailValid(email)) {
            emailEditText.setError(getString(R.string.error_invalid_email));
            focusView = emailEditText;
            cancel = true;
        }

        // Validar nombre de usuario
        if (TextUtils.isEmpty(username)) {
            usernameEditText.setError(getString(R.string.error_field_required));
            focusView = usernameEditText;
            cancel = true;
        }

        if (cancel) {
            focusView.requestFocus();
        } else {
            showProgress(true);

            // Llamar al AuthManager para registrar
            authManager.registerFromAPI(username, email, phone, password);

        }
    }

    private boolean isEmailValid(String email) {
        return email.contains("@") && email.contains(".");
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!show);
    }
}
