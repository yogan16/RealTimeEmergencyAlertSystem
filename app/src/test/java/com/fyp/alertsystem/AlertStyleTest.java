package com.fyp.alertsystem;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AlertStyleTest {

    @Test
    public void iconMatchesAlertType() {
        assertEquals(R.drawable.ic_fire, AlertStyle.iconFor("Fire"));
        assertEquals(R.drawable.ic_chemical, AlertStyle.iconFor("Chemical Exposure"));
        assertEquals(R.drawable.ic_medical, AlertStyle.iconFor("Medical Emergency"));
    }

    @Test
    public void customMessagesUseWarningIcon() {
        assertEquals(R.drawable.ic_warning, AlertStyle.iconFor("Gas leak near library"));
        assertEquals(R.drawable.ic_warning, AlertStyle.iconFor(null));
    }

    @Test
    public void colourMatchesPriority() {
        assertEquals(R.color.priority_high, AlertStyle.colorFor("High"));
        assertEquals(R.color.priority_medium, AlertStyle.colorFor("Medium"));
        assertEquals(R.color.priority_low, AlertStyle.colorFor("Low"));
        assertEquals(R.color.priority_high_container, AlertStyle.containerFor("High"));
    }
}
