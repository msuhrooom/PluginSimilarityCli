package com.shop.cart;

import java.util.ArrayList;
import java.util.List;

/**
 * Shopping Cart Plugin - Version 2.0
 * Added: discount support, item listing
 * Removed: clearCart (use removeAll instead)
 * Modified: tracking quantities
 */
public class ShoppingCartV2 {
    private List<String> items;
    private List<Integer> quantities;
    private double totalPrice;
    private double discountPercent;
    
    public ShoppingCartV2() {
        this.items = new ArrayList<>();
        this.quantities = new ArrayList<>();
        this.totalPrice = 0.0;
        this.discountPercent = 0.0;
    }
    
    public void addItem(String item, double price) {
        int index = items.indexOf(item);
        if (index >= 0) {
            quantities.set(index, quantities.get(index) + 1);
        } else {
            items.add(item);
            quantities.add(1);
        }
        totalPrice += price;
    }
    
    public void removeItem(String item, double price) {
        int index = items.indexOf(item);
        if (index >= 0) {
            int qty = quantities.get(index);
            if (qty > 1) {
                quantities.set(index, qty - 1);
            } else {
                items.remove(index);
                quantities.remove(index);
            }
            totalPrice -= price;
        }
    }
    
    public int getItemCount() {
        int total = 0;
        for (int qty : quantities) {
            total += qty;
        }
        return total;
    }
    
    public double getTotalPrice() {
        return totalPrice * (1.0 - discountPercent / 100.0);
    }
    
    // NEW: Apply discount
    public void setDiscount(double percent) {
        this.discountPercent = percent;
    }
    
    // NEW: Get discount
    public double getDiscount() {
        return discountPercent;
    }
    
    // NEW: List all items
    public List<String> listItems() {
        return new ArrayList<>(items);
    }
    
    // NEW: Get quantity for specific item
    public int getQuantity(String item) {
        int index = items.indexOf(item);
        if (index >= 0) {
            return quantities.get(index);
        }
        return 0;
    }
}
