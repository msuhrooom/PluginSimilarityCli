package com.utils.files;

import java.util.ArrayList;
import java.util.List;

/**
 * File utility plugin with common file operations
 */
public class FileUtility {
    private List<String> fileList;
    private long totalSize;
    
    public FileUtility() {
        this.fileList = new ArrayList<>();
        this.totalSize = 0;
    }
    
    // Common functionality: file listing
    public void addFile(String filename, long size) {
        fileList.add(filename);
        totalSize += size;
    }
    
    public void removeFile(String filename, long size) {
        if (fileList.remove(filename)) {
            totalSize -= size;
        }
    }
    
    public int getFileCount() {
        return fileList.size();
    }
    
    public long getTotalSize() {
        return totalSize;
    }
    
    // Unique to FileUtility: sorting
    public void sortFiles() {
        fileList.sort(String::compareTo);
    }
    
    // Unique to FileUtility: filtering
    public List<String> filterByExtension(String ext) {
        List<String> filtered = new ArrayList<>();
        for (String file : fileList) {
            if (file.endsWith(ext)) {
                filtered.add(file);
            }
        }
        return filtered;
    }
}
