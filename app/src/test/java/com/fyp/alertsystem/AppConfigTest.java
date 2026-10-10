package com.fyp.alertsystem;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppConfigTest {
    @Test
    public void toSafeKey_replacesCharactersFirebaseRejects() {
        assertEquals("a_b_c_d_e_f_g", AppConfig.toSafeKey("a.b#c$d[e]f/g"));
        assertEquals("Gas leak_ evacuate lab 3", AppConfig.toSafeKey("Gas leak. evacuate lab 3"));
    }

    @Test
    public void toSafeKey_leavesNormalTextUnchanged() {
        assertEquals("Fire", AppConfig.toSafeKey("Fire"));
        assertEquals("Medical Emergency", AppConfig.toSafeKey("Medical Emergency"));
    }
}
