package com.validation.refactored;

/**
 * User validation - REFACTORED style (logic split into helper methods)
 * Identical logic to monolithic version, just better structured
 */
public class UserValidatorRefactored {
    
    public boolean validateUser(String username, String email, int age) {
        return validateUsername(username) && 
               validateEmail(email) && 
               validateAge(age);
    }
    
    private boolean validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        if (username.length() < 3 || username.length() > 20) {
            return false;
        }
        return hasValidUsernameChars(username);
    }
    
    private boolean hasValidUsernameChars(String username) {
        for (char c : username.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
    
    private boolean validateEmail(String email) {
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
        return validateEmailDomain(email, atIndex);
    }
    
    private boolean validateEmailDomain(String email, int atIndex) {
        String domain = email.substring(atIndex + 1);
        if (!domain.contains(".")) {
            return false;
        }
        return true;
    }
    
    private boolean validateAge(int age) {
        if (age < 13 || age > 120) {
            return false;
        }
        return true;
    }
}
