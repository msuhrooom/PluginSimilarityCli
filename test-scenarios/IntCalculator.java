public class IntCalculator {
    public int add(int a, int b) {
        return a + b;
    }
    
    public int multiply(int a, int b) {
        return a * b;
    }
    
    public int compute(int x, int y, int z) {
        int sum = add(x, y);
        int product = multiply(sum, z);
        return product;
    }
}
