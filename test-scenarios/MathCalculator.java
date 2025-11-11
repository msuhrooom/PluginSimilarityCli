package com.example.calculator;

/**
 * Mathematical calculator with basic operations
 */
public class MathCalculator {
    private double lastResult;
    
    public MathCalculator() {
        this.lastResult = 0.0;
    }
    
    public double add(double a, double b) {
        lastResult = a + b;
        return lastResult;
    }
    
    public double subtract(double a, double b) {
        lastResult = a - b;
        return lastResult;
    }
    
    public double multiply(double a, double b) {
        lastResult = a * b;
        return lastResult;
    }
    
    public double divide(double a, double b) {
        if (b != 0) {
            lastResult = a / b;
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
