package com.fyp.alertsystem;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AdminActivity extends AppCompatActivity {
    private static final long MERGE_WINDOW_MS = 5_000;

    private final Lock mergeLock = new ReentrantLock(true);
    private final ExecutorService sendPool = Executors.newFixedThreadPool(8);

    private DatabaseReference alertsRef, mergedRef;
    private Spinner spinnerAlertType, spinnerArea, spinnerPriority;
    private TextInputLayout tilCustomMessage, tilCustomArea;
    private TextInputEditText etCustomAlertMessage, etCustomArea;
    private MaterialButton btnCreateAlert;
    private final CopyOnWriteArrayList<Alert> historyList = new CopyOnWriteArrayList<>();
    private AlertHistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // RecyclerView setup
        RecyclerView rv = findViewById(R.id.rvHistory);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlertHistoryAdapter(historyList);
        rv.setAdapter(adapter);

        // Firebase references
        alertsRef = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).getReference("alerts");
        mergedRef = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).getReference("mergedAlerts");

        // Load alert history
        alertsRef.orderByChild("timestamp").addChildEventListener(new ChildEventListener() {
            @Override public void onChildAdded(DataSnapshot ds, String p) {
                Alert a = ds.getValue(Alert.class);
                if (a != null) {
                    historyList.add(0, a);
                    adapter.notifyItemInserted(0);
                }
            }
            @Override public void onChildChanged(DataSnapshot ds, String p) {}
            @Override public void onChildRemoved(DataSnapshot ds) {}
            @Override public void onChildMoved(DataSnapshot ds, String p) {}
            @Override public void onCancelled(DatabaseError e) {
                Toast.makeText(AdminActivity.this, "History load error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Bind views
        spinnerAlertType     = findViewById(R.id.spinnerAlertType);
        spinnerArea          = findViewById(R.id.spinnerArea);
        spinnerPriority      = findViewById(R.id.spinnerPriorityVisible);
        tilCustomMessage     = findViewById(R.id.tilCustomMessage);
        etCustomAlertMessage = findViewById(R.id.etCustomAlertMessage);
        tilCustomArea        = findViewById(R.id.tilCustomArea);
        etCustomArea         = findViewById(R.id.etCustomArea);
        btnCreateAlert       = findViewById(R.id.btnCreateAlert);

        // Show/hide custom message input
        spinnerAlertType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                boolean isOther = "Others".equals(spinnerAlertType.getSelectedItem().toString());
                tilCustomMessage.setVisibility(isOther ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Show/hide custom area input
        spinnerArea.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                boolean isOther = "Others".equals(spinnerArea.getSelectedItem().toString());
                tilCustomArea.setVisibility(isOther ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Button click: create/send alert
        btnCreateAlert.setOnClickListener(v -> {
            String type = spinnerAlertType.getSelectedItem().toString();
            String msg = "Others".equals(type)
                    ? etCustomAlertMessage.getText().toString().trim()
                    : type;

            String areaInput = spinnerArea.getSelectedItem().toString();
            String area = "Others".equals(areaInput)
                    ? etCustomArea.getText().toString().trim()
                    : areaInput;

            String prio = spinnerPriority.getSelectedItem().toString();

            if (type.startsWith("Select") || area.startsWith("Select") ||
                    prio.startsWith("Select") || msg.isEmpty()) {
                Toast.makeText(this, "Please complete all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            Future<?> sent = sendPool.submit(() -> sendOrMerge(msg, area, prio));

            new Thread(() -> {
                try { sent.get(); }
                catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
                runOnUiThread(() -> {
                    // Reset UI (hide the whole input layout, not just the EditText,
                    // so it reappears when "Others" is picked again)
                    spinnerAlertType.setSelection(0);
                    spinnerArea.setSelection(0);
                    spinnerPriority.setSelection(0);
                    etCustomAlertMessage.setText("");
                    tilCustomMessage.setVisibility(View.GONE);
                    etCustomArea.setText("");
                    tilCustomArea.setVisibility(View.GONE);
                    Toast.makeText(this, "Alert sent successfully", Toast.LENGTH_SHORT).show();
                });
            }).start();
        });
    }

    /**
     * @param mergeKey  the actual text to merge on (either spinner value or custom msg)
     */
    private void sendOrMerge(String mergeKey, String area, String priority) {
        // raw alert still uses mergeKey as the message
        Map<String,Object> data = new HashMap<>();
        data.put("message", mergeKey);
        data.put("area",     area);
        data.put("priority", priority);
        data.put("timestamp", ServerValue.TIMESTAMP);
        alertsRef.push().setValue(data);

        // Merge repeated alerts for the same message + area within MERGE_WINDOW_MS
        try {
            if (mergeLock.tryLock(2, TimeUnit.SECONDS)) {
                try {
                    mergedRef
                            .child(AppConfig.toSafeKey(mergeKey))
                            .child(AppConfig.toSafeKey(area))
                            .runTransaction(new Transaction.Handler() {
                                @Override
                                public Transaction.Result doTransaction(MutableData cur) {
                                    long now = System.currentTimeMillis();
                                    MergedAlert m = cur.getValue(MergedAlert.class);
                                    if (m == null || now - m.timestamp >= MERGE_WINDOW_MS) {
                                        m = new MergedAlert(mergeKey, area, priority, now, 1);
                                    } else {
                                        m.count++;
                                        m.timestamp = now;
                                    }
                                    cur.setValue(m);
                                    return Transaction.success(cur);
                                }
                                @Override
                                public void onComplete(DatabaseError e, boolean committed, DataSnapshot ds) {
                                    if (e != null) {
                                        runOnUiThread(() ->
                                                Toast.makeText(AdminActivity.this,
                                                        "Merge error: "+e.getMessage(),
                                                        Toast.LENGTH_SHORT).show()
                                        );
                                    }
                                }
                            });
                } finally {
                    mergeLock.unlock();
                }
            } else {
                runOnUiThread(() ->
                        Toast.makeText(this, "Could not acquire merge lock", Toast.LENGTH_SHORT).show()
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sendPool.shutdownNow();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.admin_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            SharedPreferences prefs = getSharedPreferences(AppConfig.PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putBoolean(AppConfig.KEY_LOGGED_IN, false).remove(AppConfig.KEY_ROLE).apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class MergedAlert {
        public String type, area, priority;
        public long timestamp;
        public int count;
        public MergedAlert() {}
        public MergedAlert(String t, String a, String p, long ts, int c) {
            type = t; area = a; priority = p; timestamp = ts; count = c;
        }
    }
}
