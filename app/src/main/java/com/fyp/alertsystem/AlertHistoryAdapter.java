package com.fyp.alertsystem;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.List;

public class AlertHistoryAdapter extends RecyclerView.Adapter<AlertHistoryAdapter.VH> {
    private final List<Alert> data;

    public AlertHistoryAdapter(List<Alert> data) { this.data = data; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Alert a = data.get(pos);
        Context c = h.itemView.getContext();

        h.tvMsg.setText(a.message);
        h.tvArea.setText(a.area);
        h.tvTime.setText(c.getString(R.string.history_time, AlertStyle.timeAgo(c, a.timestamp)));
        AlertStyle.styleChip(h.chipPriority, a.priority, a.priority);

        h.ivType.setImageResource(AlertStyle.iconFor(a.message));
        h.ivType.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(c, AlertStyle.colorFor(a.priority))));
        h.iconBg.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(c, AlertStyle.containerFor(a.priority))));
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvMsg, tvArea, tvTime;
        final Chip chipPriority;
        final ImageView ivType;
        final View iconBg;

        VH(View v) {
            super(v);
            tvMsg        = v.findViewById(R.id.tvMsg);
            tvArea       = v.findViewById(R.id.tvArea);
            tvTime       = v.findViewById(R.id.tvTime);
            chipPriority = v.findViewById(R.id.tvPriority);
            ivType       = v.findViewById(R.id.ivType);
            iconBg       = v.findViewById(R.id.iconBg);
        }
    }
}
