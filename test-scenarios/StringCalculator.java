package com.example.calculator;

/**
 * String calculator with text operations - SAME STRUCTURE, DIFFERENT BEHAVIOR
 */
public class StringCalculator {
    private double lastResult;
    
    public StringCalculator() {
        this.lastResult = 0.0;
    }
    
    // Concatenates strings and returns combined length
    public double add(double a, double b) {
        String combined = String.valueOf((int)a) + String.valueOf((int)b);
        lastResult = combined.length();
        return lastResult;
    }
    
    // Returns difference in string lengths
    public double subtract(double a, double b) {
        String str1 = String.valueOf((int)a);
        String str2 = String.valueOf((int)b);
        lastResult = Math.abs(str1.length() - str2.length());
        return lastResult;
    }
    
    // Repeats first string b times and returns total length
    public double multiply(double a, double b) {
        String str = String.valueOf((int)a);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < (int)b; i++) {
            result.append(str);
        }
        lastResult = result.length();
        return lastResult;
    }
    
    // Splits first number into digits and counts them
    public double divide(double a, double b) {
        if (b != 0) {
            String str = String.valueOf((int)(a / b));
            lastResult = str.length();
        } else {
            lastResult = 0.0;
        }
        return lastResult;
    }
    
    public double getLastResult() {
        return lastResult;
    }
    
    public void reset() {
        lastResult = 0.0;
    }
}
