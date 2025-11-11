package com.network.connection;

import java.util.HashMap;
import java.util.Map;

/**
 * Network connection manager plugin
 */
public class NetworkManager {
    private Map<String, Integer> activeConnections;
    private int maxConnections;
    private long totalBytesTransferred;
    
    public NetworkManager(int maxConnections) {
        this.maxConnections = maxConnections;
        this.activeConnections = new HashMap<>();
        this.totalBytesTransferred = 0;
    }
    
    public boolean openConnection(String host, int port) {
        if (activeConnections.size() >= maxConnections) {
            return false;
        }
        String key = host + ":" + port;
        activeConnections.put(key, port);
        return true;
    }
    
    public void closeConnection(String host, int port) {
        String key = host + ":" + port;
        activeConnections.remove(key);
    }
    
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
    
    public void sendData(String host, int port, byte[] data) {
        String key = host + ":" + port;
        if (activeConnections.containsKey(key)) {
            totalBytesTransferred += data.length;
        }
    }
    
    public long getTotalBytesTransferred() {
        return totalBytesTransferred;
    }
    
    public void resetStatistics() {
        totalBytesTransferred = 0;
    }
    
    public boolean isConnected(String host, int port) {
        String key = host + ":" + port;
        return activeConnections.containsKey(key);
    }
}
