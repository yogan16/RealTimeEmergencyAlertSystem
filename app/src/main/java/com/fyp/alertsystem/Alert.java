package com.fyp.alertsystem;

public class Alert {
    public String message;
    public String area;
    public String priority;
    public long   timestamp;

    // **Required**: no-arg constructor for Firebase
    public Alert() { }

    public Alert(String message, String area, String priority, long timestamp) {
        this.message   = message;
        this.area      = area;
        this.priority  = priority;
        this.timestamp = timestamp;
    }
}
