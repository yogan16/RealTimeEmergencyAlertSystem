package com.fyp.alertsystem;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

public class MainActivity extends AppCompatActivity {
    private static final String KEY_REMEMBER   = "rememberMe";
    private static final String KEY_SAVED_USER = "savedUsername";
    private static final String KEY_SAVED_PASS = "savedPassword";  // only used to delete old saved passwords

    private TextInputEditText etUsername, etPassword;
    private AppCompatButton   btnLogin;
    private CheckBox          cbRememberMe;
    private FirebaseAuth      auth;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(AppConfig.PREFS_NAME, MODE_PRIVATE);
        prefs.edit().remove(KEY_SAVED_PASS).apply();
        auth  = FirebaseAuth.getInstance();

        etUsername   = findViewById(R.id.etUsername);
        etPassword   = findViewById(R.id.etPassword);
        btnLogin     = findViewById(R.id.btnLogin);
        cbRememberMe = findViewById(R.id.cbRememberMe);

        // Firebase keeps the session, so a signed-in user goes straight to their dashboard
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            loadRoleAndContinue(current);
            return;
        }

        // Pre-fill the email if "Remember Me" was checked
        if (prefs.getBoolean(KEY_REMEMBER, false)) {
            etUsername.setText(prefs.getString(KEY_SAVED_USER, ""));
            cbRememberMe.setChecked(true);
        }

        btnLogin.setOnClickListener(v -> {
            String email = etUsername.getText().toString().trim();
            String pass  = etPassword.getText().toString();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
                Toast.makeText(this, "Enter both fields", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences.Editor edit = prefs.edit();
            if (cbRememberMe.isChecked()) {
                edit.putBoolean(KEY_REMEMBER, true).putString(KEY_SAVED_USER, email);
            } else {
                edit.remove(KEY_REMEMBER).remove(KEY_SAVED_USER);
            }
            edit.apply();

            btnLogin.setEnabled(false);
            auth.signInWithEmailAndPassword(email, pass)
                    .addOnSuccessListener(result -> loadRoleAndContinue(result.getUser()))
                    .addOnFailureListener(e -> {
                        btnLogin.setEnabled(true);
                        Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show();
                    });
        });
    }

    // Role is stored at users/{uid}/role and can only be read by that user (see database.rules.json)
    private void loadRoleAndContinue(FirebaseUser user) {
        AppConfig.database().getReference("users").child(user.getUid()).child("role")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        String role = snap.getValue(String.class);
                        if (role == null) {
                            auth.signOut();
                            btnLogin.setEnabled(true);
                            Toast.makeText(MainActivity.this,
                                    "This account has no role assigned", Toast.LENGTH_LONG).show();
                            return;
                        }
                        prefs.edit().putString(AppConfig.KEY_ROLE, role).apply();
                        navigateToRole(role);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        btnLogin.setEnabled(true);
                        Toast.makeText(MainActivity.this,
                                "Login error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Students get the alert feed; admins and lecturers get the alert console
    private void navigateToRole(String role) {
        Intent i = AppConfig.ROLE_STUDENT.equals(role)
                ? new Intent(this, StudentActivity.class)
                : new Intent(this, AdminActivity.class);
        startActivity(i);
        finish();
    }
}
