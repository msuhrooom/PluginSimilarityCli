package com.example.test;

public class TestPlugin {
    private String name;
    private int version;
    
    public TestPlugin(String name, int version) {
        this.name = name;
        this.version = version;
    }
    
    public String getName() {
        return name;
    }
    
    public int getVersion() {
        return version;
    }
    
    public void execute() {
        System.out.println("Plugin " + name + " v" + version + " executing");
    }
}

class Helper {
    public static void log(String message) {
        System.out.println("[LOG] " + message);
    }
}
