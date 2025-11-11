package com.validation.monolithic;

/**
 * User validation - MONOLITHIC style (all logic in one method)
 * Identical logic to refactored version, just different structure
 */
public class UserValidatorMonolithic {
    
    public boolean validateUser(String username, String email, int age) {
        // Validate username
        if (username == null || username.isEmpty()) {
            return false;
        }
        if (username.length() < 3 || username.length() > 20) {
            return false;
        }
        boolean hasValidChars = true;
        for (char c : username.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                hasValidChars = false;
                break;
            }
        }
        if (!hasValidChars) {
            return false;
        }
        
        // Validate email
        if (email == null || email.isEmpty()) {
            return false;
        }
        if (!email.contains("@")) {
            return false;
        }
        int atIndex = email.indexOf("@");
        if (atIndex == 0 || atIndex == email.length() - 1) {
            return false;
        }
        String domain = email.substring(atIndex + 1);
        if (!domain.contains(".")) {
            return false;
        }
        
        // Validate age
        if (age < 13 || age > 120) {
            return false;
        }
        
        return true;
    }
}
