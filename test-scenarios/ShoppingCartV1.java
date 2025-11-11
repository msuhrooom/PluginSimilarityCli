package com.shop.cart;

import java.util.ArrayList;
import java.util.List;

/**
 * Shopping Cart Plugin - Version 1.0
 * Basic cart functionality
 */
public class ShoppingCartV1 {
    private List<String> items;
    private double totalPrice;
    
    public ShoppingCartV1() {
        this.items = new ArrayList<>();
        this.totalPrice = 0.0;
    }
    
    public void addItem(String item, double price) {
        items.add(item);
        totalPrice += price;
    }
    
    public void removeItem(String item, double price) {
        if (items.remove(item)) {
            totalPrice -= price;
        }
    }
    
    public int getItemCount() {
        return items.size();
    }
    
    public double getTotalPrice() {
        return totalPrice;
    }
    
    public void clearCart() {
        items.clear();
        totalPrice = 0.0;
    }
}
