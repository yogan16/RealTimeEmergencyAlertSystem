package com.fyp.alertsystem;

import android.view.*;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AlertHistoryAdapter
        extends RecyclerView.Adapter<AlertHistoryAdapter.VH> {
    private final List<Alert> data;
    public AlertHistoryAdapter(List<Alert> data){this.data=data;}

    @Override public VH onCreateViewHolder(ViewGroup p,int vt){
        View v=LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_history,p,false);
        return new VH(v);
    }
    @Override public void onBindViewHolder(VH h,int pos){
        Alert a=data.get(pos);
        h.tvMsg.setText(a.message);
        h.tvArea.setText(a.area);
        h.tvPriority.setText(a.priority);
        h.tvTime.setText(timeAgo(a.timestamp));
    }
    @Override public int getItemCount(){return data.size();}
    static class VH extends RecyclerView.ViewHolder {
        TextView tvMsg,tvArea,tvTime,tvPriority;
        VH(View v){
            super(v);
            tvMsg      =v.findViewById(R.id.tvMsg);
            tvArea     =v.findViewById(R.id.tvArea);
            tvTime     =v.findViewById(R.id.tvTime);
            tvPriority =v.findViewById(R.id.tvPriority);
        }
    }
    private String timeAgo(long ts){
        long d=System.currentTimeMillis()-ts;
        if(d<TimeUnit.MINUTES.toMillis(1)) return "Now";
        if(d<TimeUnit.HOURS.toMillis(1))
            return d/TimeUnit.MINUTES.toMillis(1)+" min ago";
        if(d<TimeUnit.DAYS.toMillis(1))
            return d/TimeUnit.HOURS.toMillis(1)+" hr ago";
        return d/TimeUnit.DAYS.toMillis(1)+" day ago";
    }
}
