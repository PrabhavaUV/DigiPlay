package com.example.digiplay.models;

public class Device {
    private String id;
    private String name;
    private String currentContent;
    private boolean isOnline;

    public Device(String id, String name, String currentContent, boolean isOnline) {
        this.id = id;
        this.name = name;
        this.currentContent = currentContent;
        this.isOnline = isOnline;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCurrentContent() { return currentContent; }
    public boolean isOnline() { return isOnline; }
}
