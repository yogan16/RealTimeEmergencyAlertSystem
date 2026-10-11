package com.fyp.alertsystem;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Locale;
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

    private static final String OTHERS = "Others";

    private DatabaseReference alertsRef, mergedRef;
    private TextInputLayout tilAlertType, tilArea, tilPriority, tilCustomMessage, tilCustomArea;
    private MaterialAutoCompleteTextView ddAlertType, ddArea, ddPriority;
    private TextInputEditText etCustomAlertMessage, etCustomArea;
    private MaterialButton btnCreateAlert;
    private View progressHistory, tvEmptyHistory;
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
        progressHistory = findViewById(R.id.progressHistory);
        tvEmptyHistory  = findViewById(R.id.tvEmptyHistory);

        // Firebase references
        alertsRef = AppConfig.database().getReference("alerts");
        mergedRef = AppConfig.database().getReference("mergedAlerts");

        // Load alert history
        alertsRef.orderByChild("timestamp").addChildEventListener(new ChildEventListener() {
            @Override public void onChildAdded(DataSnapshot ds, String p) {
                Alert a = ds.getValue(Alert.class);
                if (a != null) {
                    historyList.add(0, a);
                    adapter.notifyItemInserted(0);
                    updateHistoryState(false);
                }
            }
            @Override public void onChildChanged(DataSnapshot ds, String p) {}
            @Override public void onChildRemoved(DataSnapshot ds) {}
            @Override public void onChildMoved(DataSnapshot ds, String p) {}
            @Override public void onCancelled(DatabaseError e) {
                updateHistoryState(false);
                Toast.makeText(AdminActivity.this,
                        getString(R.string.error_history_load, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
        // Fires once the initial history has loaded, so the spinner gives way to the list or the empty text
        alertsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) { updateHistoryState(false); }
            @Override public void onCancelled(@NonNull DatabaseError e) { updateHistoryState(false); }
        });
        updateHistoryState(true);

        // Bind views
        tilAlertType         = findViewById(R.id.tilAlertType);
        tilArea              = findViewById(R.id.tilArea);
        tilPriority          = findViewById(R.id.tilPriority);
        ddAlertType          = findViewById(R.id.ddAlertType);
        ddArea               = findViewById(R.id.ddArea);
        ddPriority           = findViewById(R.id.ddPriority);
        tilCustomMessage     = findViewById(R.id.tilCustomMessage);
        etCustomAlertMessage = findViewById(R.id.etCustomAlertMessage);
        tilCustomArea        = findViewById(R.id.tilCustomArea);
        etCustomArea         = findViewById(R.id.etCustomArea);
        btnCreateAlert       = findViewById(R.id.btnCreateAlert);

        // Show the free-text fields only when "Others" is picked
        ddAlertType.setOnItemClickListener((parent, view, pos, id) -> {
            tilAlertType.setError(null);
            tilCustomMessage.setVisibility(OTHERS.equals(text(ddAlertType)) ? View.VISIBLE : View.GONE);
        });
        ddArea.setOnItemClickListener((parent, view, pos, id) -> {
            tilArea.setError(null);
            tilCustomArea.setVisibility(OTHERS.equals(text(ddArea)) ? View.VISIBLE : View.GONE);
        });
        ddPriority.setOnItemClickListener((parent, view, pos, id) -> tilPriority.setError(null));

        // Button click: validate, confirm, then send
        btnCreateAlert.setOnClickListener(v -> {
            String type = text(ddAlertType);
            String msg  = OTHERS.equals(type) ? text(etCustomAlertMessage) : type;
            String areaInput = text(ddArea);
            String area = OTHERS.equals(areaInput) ? text(etCustomArea) : areaInput;
            String prio = text(ddPriority);

            boolean ok = require(tilAlertType, type);
            ok &= !OTHERS.equals(type) || require(tilCustomMessage, msg);
            ok &= require(tilArea, areaInput);
            ok &= !OTHERS.equals(areaInput) || require(tilCustomArea, area);
            ok &= require(tilPriority, prio);
            if (!ok) return;

            new MaterialAlertDialogBuilder(this)
                    .setIcon(AlertStyle.iconFor(type))
                    .setTitle(getString(R.string.confirm_send_title, prio.toUpperCase(Locale.ROOT)))
                    .setMessage(getString(R.string.confirm_send_message, msg, area))
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_send, (d, w) -> send(msg, area, prio))
                    .show();
        });
    }

    private void send(String msg, String area, String prio) {
        btnCreateAlert.setEnabled(false);
        Future<?> sent = sendPool.submit(() -> sendOrMerge(msg, area, prio));

        new Thread(() -> {
            try { sent.get(); }
            catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            runOnUiThread(() -> {
                // Reset the form (hide the whole input layout, not just the EditText,
                // so it reappears when "Others" is picked again)
                ddAlertType.setText("", false);
                ddArea.setText("", false);
                ddPriority.setText("", false);
                etCustomAlertMessage.setText("");
                tilCustomMessage.setVisibility(View.GONE);
                etCustomArea.setText("");
                tilCustomArea.setVisibility(View.GONE);
                btnCreateAlert.setEnabled(true);
                Snackbar.make(findViewById(R.id.adminRoot), R.string.alert_sent, Snackbar.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void updateHistoryState(boolean loading) {
        progressHistory.setVisibility(loading ? View.VISIBLE : View.GONE);
        tvEmptyHistory.setVisibility(!loading && historyList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String text(TextView v) {
        return v.getText() == null ? "" : v.getText().toString().trim();
    }

    /** Shows "Required" on the field when value is empty. */
    private boolean require(TextInputLayout til, String value) {
        boolean ok = !value.isEmpty();
        til.setError(ok ? null : getString(R.string.error_required));
        return ok;
    }

    /**
     * @param mergeKey  the actual text to merge on (either the picked alert type or the custom message)
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
                                                        getString(R.string.error_merge, e.getMessage()),
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
                        Toast.makeText(this, R.string.error_merge_lock, Toast.LENGTH_SHORT).show()
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
            AppConfig.logout(this);
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
