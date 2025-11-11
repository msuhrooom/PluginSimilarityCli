package com.example.usermanager;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages user data with CRUD operations
 */
public class UserDataManager {
    private Map<String, String> userDatabase;
    
    public UserDataManager() {
        this.userDatabase = new HashMap<>();
    }
    
    public void storeUser(String userId, String userData) {
        if (userId != null && userData != null) {
            userDatabase.put(userId, userData);
        }
    }
    
    public String retrieveUser(String userId) {
        if (userId == null) {
            return null;
        }
        return userDatabase.get(userId);
    }
    
    public boolean removeUser(String userId) {
        if (userId != null && userDatabase.containsKey(userId)) {
            userDatabase.remove(userId);
            return true;
        }
        return false;
    }
    
    public int countUsers() {
        return userDatabase.size();
    }
    
    public void clearAllUsers() {
        userDatabase.clear();
    }
    
    public boolean hasUser(String userId) {
        if (userId == null) {
            return false;
        }
        return userDatabase.containsKey(userId);
    }
}
