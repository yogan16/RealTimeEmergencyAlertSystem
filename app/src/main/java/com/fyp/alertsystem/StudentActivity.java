package com.fyp.alertsystem;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class StudentActivity extends AppCompatActivity {
    private static final String PREFS_NAME = AppConfig.PREFS_NAME;

    // Firebase refs
    private DatabaseReference alertsRef;
    private DatabaseReference mergedRef;

    // UI
    private CardView    cardCurrent;
    private TextView    tvMsg, tvArea, tvTime, tvPriority;
    private Button      btnAck;
    private RecyclerView rvHistory;
    private AlertHistoryAdapter adapter;

    // Raw-alert history
    private final List<Alert> historyList = new ArrayList<>();

    // Merged-alert queue for current card
    private final Map<String, MergedAlert> mergedMap   = new ConcurrentHashMap<>();
    private final Deque<MergedAlert>       pendingQueue = new ArrayDeque<>();

    // Single-thread executor for queue processing
    private final ExecutorService queueExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "queue-processor");
                t.setDaemon(true);
                return t;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Current-alert card
        cardCurrent = findViewById(R.id.cardCurrentAlert);
        tvMsg       = findViewById(R.id.tvCurrentMsg);
        tvArea      = findViewById(R.id.tvCurrentArea);
        tvTime      = findViewById(R.id.tvCurrentTime);
        tvPriority  = findViewById(R.id.tvCurrentPriority);
        btnAck      = findViewById(R.id.btnAcknowledge);

        // Push notifications: ask for permission (Android 13+) and listen on the alerts topic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        FirebaseMessaging.getInstance().subscribeToTopic(AppConfig.ALERTS_TOPIC);

        // RecyclerView + adapter for raw history
        rvHistory = findViewById(R.id.rvHistory);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlertHistoryAdapter(historyList);
        rvHistory.setAdapter(adapter);

        // 1) Listen to RAW alerts (identical to Admin)
        alertsRef = AppConfig.database().getReference("alerts");

        alertsRef.orderByChild("timestamp")
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot ds, String p) {
                        Alert a = ds.getValue(Alert.class);
                        if (a != null) {
                            historyList.add(0, a);
                            adapter.notifyItemInserted(0);
                        }
                    }
                    @Override public void onChildChanged(DataSnapshot ds, String p) { }
                    @Override public void onChildRemoved(DataSnapshot ds) { }
                    @Override public void onChildMoved(DataSnapshot ds, String p) { }
                    @Override public void onCancelled(DatabaseError e) { }
                });

        // 2) Listen to MERGED alerts for current-alert card
        mergedRef = AppConfig.database().getReference("mergedAlerts");

        // Initial load of all merged entries
        mergedRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot root) {
                for (DataSnapshot typeSnap : root.getChildren()) {
                    for (DataSnapshot areaSnap : typeSnap.getChildren()) {
                        enqueueMerged(areaSnap);
                    }
                    typeSnap.getRef().addChildEventListener(areaListener);
                }
                queueExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        showNextPending();
                    }
                });
            }
            @Override public void onCancelled(DatabaseError e) { }
        });

        // Watch for new merged alerts
        mergedRef.addChildEventListener(new ChildEventListener() {
            @Override public void onChildAdded(DataSnapshot ts, String p) {
                for (DataSnapshot a : ts.getChildren()) {
                    enqueueMerged(a);
                }
                ts.getRef().addChildEventListener(areaListener);
            }
            @Override public void onChildRemoved(DataSnapshot ds) {
                String type = ds.getKey();
                // remove all keys with this type
                for (String key : new ArrayList<>(mergedMap.keySet())) {
                    if (key.startsWith(type + "|")) {
                        mergedMap.remove(key);
                    }
                }
            }
            @Override public void onChildChanged(DataSnapshot ds, String p) {
                enqueueMerged(ds);
            }
            @Override public void onChildMoved(DataSnapshot ds, String p) { }
            @Override public void onCancelled(DatabaseError e) { }
        });

        // Acknowledge button
        btnAck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queueExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (pendingQueue) {
                            MergedAlert removed = pendingQueue.pollFirst();
                            if (removed != null) {
                                String ackKey = "ack_" + removed.type + "_" + removed.area;
                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                        .edit()
                                        .putLong(ackKey, removed.timestamp)
                                        .apply();
                            }
                        }
                        showNextPending();
                    }
                });
            }
        });
    }

    // Listener for each merged-area node
    private final ChildEventListener areaListener = new ChildEventListener() {
        @Override public void onChildAdded(DataSnapshot snap, String p)    {
            enqueueMerged(snap);
        }
        @Override public void onChildChanged(DataSnapshot snap, String p)  {
            enqueueMerged(snap);
        }
        @Override public void onChildRemoved(DataSnapshot snap) {
            // no-op for individual removal
        }
        @Override public void onChildMoved(DataSnapshot snap, String p)    { }
        @Override public void onCancelled(DatabaseError e)                 { }
    };

    // Enqueue for “current alert” card only
    private void enqueueMerged(DataSnapshot snap) {
        if (!snap.hasChild("count")) return;
        MergedAlert m = snap.getValue(MergedAlert.class);
        if (m == null) return;

        String key = m.type + "|" + m.area;
        mergedMap.put(key, m);

        long lastAck = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getLong("ack_" + m.type + "_" + m.area, 0L);

        if (m.timestamp > lastAck) {
            synchronized (pendingQueue) {
                pendingQueue.removeIf(x ->
                        x.type.equals(m.type) && x.area.equals(m.area)
                );
                pendingQueue.addLast(m);
            }
            showNextPending();
        }
    }

    // Update the card with the next pending merged alert
    private void showNextPending() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized (pendingQueue) {
                    MergedAlert next = pendingQueue.peekFirst();
                    if (next == null) {
                        cardCurrent.setVisibility(View.GONE);
                        return;
                    }
                    tvMsg.setText(next.type);
                    tvArea.setText(next.area);
                    tvTime.setText("Now");
                    tvPriority.setText(next.priority + (next.count > 1 ? " (×" + next.count + ")" : ""));
                    vibrate();
                    cardCurrent.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    // Simple vibrate feedback
    private void vibrate() {
        Vibrator v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(
                        300, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                //noinspection deprecation
                v.vibrate(300);
            }
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        queueExecutor.shutdownNow();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.student_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            AppConfig.logout(this);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class MergedAlert {
        public String type, area, priority;
        public long   timestamp;
        public int    count;
        public MergedAlert() {}
    }
}
