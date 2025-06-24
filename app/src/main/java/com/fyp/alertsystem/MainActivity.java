package com.fyp.alertsystem;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.*;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME       = "login_prefs";
    private static final String KEY_LOGGED_IN    = "isLoggedIn";
    private static final String KEY_ROLE         = "role";
    private static final String KEY_REMEMBER     = "rememberMe";
    private static final String KEY_SAVED_USER   = "savedUsername";
    private static final String KEY_SAVED_PASS   = "savedPassword";

    private TextInputEditText etUsername, etPassword;
    private AppCompatButton   btnLogin;
    private CheckBox          cbRememberMe;
    private DatabaseReference usersRef;
    private SharedPreferences prefs;
    private static final String RTDB_URL =
            "https://alertsystem-a08e4-default-rtdb.asia-southeast1.firebasedatabase.app";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // If already logged in, skip to dashboard
        if (prefs.getBoolean(KEY_LOGGED_IN, false)) {
            navigateToRole();
            return;
        }

        etUsername   = findViewById(R.id.etUsername);
        etPassword   = findViewById(R.id.etPassword);
        btnLogin     = findViewById(R.id.btnLogin);
        cbRememberMe = findViewById(R.id.cbRememberMe);

        // Pre-fill if “Remember Me” was checked
        if (prefs.getBoolean(KEY_REMEMBER, false)) {
            etUsername.setText(prefs.getString(KEY_SAVED_USER, ""));
            etPassword.setText(prefs.getString(KEY_SAVED_PASS, ""));
            cbRememberMe.setChecked(true);
        }

        usersRef = FirebaseDatabase
                .getInstance(RTDB_URL)
                .getReference("Users");

        btnLogin.setOnClickListener(v -> {
            String u = etUsername.getText().toString().trim();
            String p = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(u) || TextUtils.isEmpty(p)) {
                Toast.makeText(this, "Enter both fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Handle “Remember Me”
            SharedPreferences.Editor edit = prefs.edit();
            if (cbRememberMe.isChecked()) {
                edit.putBoolean(KEY_REMEMBER, true)
                        .putString(KEY_SAVED_USER, u)
                        .putString(KEY_SAVED_PASS, p);
            } else {
                edit.remove(KEY_REMEMBER)
                        .remove(KEY_SAVED_USER)
                        .remove(KEY_SAVED_PASS);
            }
            edit.apply();

            // Authenticate against Firebase
            usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snap) {
                    String role = null;
                    // 1) admin
                    String au = snap.child("admin/username").getValue(String.class);
                    String ap = snap.child("admin/password").getValue(String.class);
                    if (u.equals(au) && p.equals(ap)) {
                        role = "admin";
                    }
                    // 2) lecturers
                    if (role == null) {
                        for (DataSnapshot lec : snap.child("lecturer").getChildren()) {
                            String lu = lec.child("username").getValue(String.class);
                            String lp = lec.child("password").getValue(String.class);
                            if (u.equals(lu) && p.equals(lp)) {
                                role = "lecturer";
                                break;
                            }
                        }
                    }
                    // 3) students
                    if (role == null) {
                        for (DataSnapshot stu : snap.child("student").getChildren()) {
                            String su = stu.child("username").getValue(String.class);
                            String sp = stu.child("password").getValue(String.class);
                            if (u.equals(su) && p.equals(sp)) {
                                role = "student";
                                break;
                            }
                        }
                    }

                    if (role != null) {
                        // save login state
                        prefs.edit()
                                .putBoolean(KEY_LOGGED_IN, true)
                                .putString(KEY_ROLE, role)
                                .apply();
                        navigateToRole();
                    } else {
                        Toast.makeText(MainActivity.this,
                                "Invalid credentials", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onCancelled(DatabaseError e) {
                    Toast.makeText(MainActivity.this,
                            "Login error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // Send user to StudentActivity or AdminActivity
    private void navigateToRole() {
        String role = prefs.getString(KEY_ROLE, "");
        Intent i = "student".equals(role)
                ? new Intent(this, StudentActivity.class)
                : new Intent(this, AdminActivity.class);
        startActivity(i);
        finish();
    }
}
