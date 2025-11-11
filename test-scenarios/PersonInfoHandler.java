package com.acme.personhandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles person information with storage operations
 */
public class PersonInfoHandler {
    private Map<String, String> personRecords;
    
    public PersonInfoHandler() {
        this.personRecords = new HashMap<>();
    }
    
    public void savePersonInfo(String personId, String personInfo) {
        if (personId != null && personInfo != null) {
            personRecords.put(personId, personInfo);
        }
    }
    
    public String fetchPersonInfo(String personId) {
        if (personId == null) {
            return null;
        }
        return personRecords.get(personId);
    }
    
    public boolean deletePerson(String personId) {
        if (personId != null && personRecords.containsKey(personId)) {
            personRecords.remove(personId);
            return true;
        }
        return false;
    }
    
    public int getTotalPersons() {
        return personRecords.size();
    }
    
    public void eraseAllRecords() {
        personRecords.clear();
    }
    
    public boolean existsPerson(String personId) {
        if (personId == null) {
            return false;
        }
        return personRecords.containsKey(personId);
    }
}
