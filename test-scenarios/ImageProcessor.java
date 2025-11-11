package com.graphics.imaging;

/**
 * Image processing plugin with filters and transformations
 */
public class ImageProcessor {
    private int width;
    private int height;
    private byte[] imageData;
    
    public ImageProcessor(int width, int height) {
        this.width = width;
        this.height = height;
        this.imageData = new byte[width * height * 3];
    }
    
    public void applyGrayscale() {
        for (int i = 0; i < imageData.length; i += 3) {
            int avg = (imageData[i] + imageData[i+1] + imageData[i+2]) / 3;
            imageData[i] = (byte)avg;
            imageData[i+1] = (byte)avg;
            imageData[i+2] = (byte)avg;
        }
    }
    
    public void rotate90Degrees() {
        byte[] rotated = new byte[imageData.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int srcIndex = (y * width + x) * 3;
                int dstIndex = (x * height + (height - 1 - y)) * 3;
                rotated[dstIndex] = imageData[srcIndex];
                rotated[dstIndex+1] = imageData[srcIndex+1];
                rotated[dstIndex+2] = imageData[srcIndex+2];
            }
        }
        imageData = rotated;
        int temp = width;
        width = height;
        height = temp;
    }
    
    public void adjustBrightness(int delta) {
        for (int i = 0; i < imageData.length; i++) {
            int value = imageData[i] + delta;
            if (value > 255) value = 255;
            if (value < 0) value = 0;
            imageData[i] = (byte)value;
        }
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public byte[] getImageData() {
        return imageData;
    }
}
