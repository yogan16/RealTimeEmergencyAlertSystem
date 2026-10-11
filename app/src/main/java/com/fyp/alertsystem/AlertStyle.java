package com.fyp.alertsystem;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateUtils;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;

/** Colours, icons and time labels shared by the alert screens. */
public final class AlertStyle {
    public static final String PRIORITY_HIGH   = "High";
    public static final String PRIORITY_MEDIUM = "Medium";

    private AlertStyle() {}

    /** Icon for an alert type. Custom ("Others") messages get the generic warning icon. */
    @DrawableRes
    public static int iconFor(String type) {
        if (type == null) return R.drawable.ic_warning;
        switch (type) {
            case "Fire":              return R.drawable.ic_fire;
            case "Chemical Exposure": return R.drawable.ic_chemical;
            case "Medical Emergency": return R.drawable.ic_medical;
            default:                  return R.drawable.ic_warning;
        }
    }

    /** Strong colour for a priority level (text, icons, card outline). */
    @ColorRes
    public static int colorFor(String priority) {
        if (PRIORITY_HIGH.equals(priority))   return R.color.priority_high;
        if (PRIORITY_MEDIUM.equals(priority)) return R.color.priority_medium;
        return R.color.priority_low;
    }

    /** Soft background colour for a priority level (chips, icon circles). */
    @ColorRes
    public static int containerFor(String priority) {
        if (PRIORITY_HIGH.equals(priority))   return R.color.priority_high_container;
        if (PRIORITY_MEDIUM.equals(priority)) return R.color.priority_medium_container;
        return R.color.priority_low_container;
    }

    /** Styles a chip as a coloured priority badge. */
    public static void styleChip(Chip chip, String priority, CharSequence label) {
        Context c = chip.getContext();
        chip.setText(label);
        chip.setTextColor(ContextCompat.getColor(c, colorFor(priority)));
        chip.setChipBackgroundColor(ColorStateList.valueOf(
                ContextCompat.getColor(c, containerFor(priority))));
    }

    /** "Just now", "5 minutes ago", "Yesterday", ... */
    public static CharSequence timeAgo(Context c, long timestamp) {
        long now = System.currentTimeMillis();
        if (timestamp <= 0 || now - timestamp < DateUtils.MINUTE_IN_MILLIS) {
            return c.getString(R.string.time_now);
        }
        return DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS);
    }
}
