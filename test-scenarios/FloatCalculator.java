public class FloatCalculator {
    public float add(float a, float b) {
        return a + b;
    }
    
    public float multiply(float a, float b) {
        return a * b;
    }
    
    public float compute(float x, float y, float z) {
        float sum = add(x, y);
        float product = multiply(sum, z);
        return product;
    }
}
