package com.utils.filesystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Directory manager plugin - shares some functionality with FileUtility
 */
public class DirectoryManager {
    private List<String> fileList;
    private long totalSize;
    private Map<String, String> fileMetadata;
    
    public DirectoryManager() {
        this.fileList = new ArrayList<>();
        this.totalSize = 0;
        this.fileMetadata = new HashMap<>();
    }
    
    // Common functionality: same as FileUtility
    public void addFile(String filename, long size) {
        fileList.add(filename);
        totalSize += size;
    }
    
    public void removeFile(String filename, long size) {
        if (fileList.remove(filename)) {
            totalSize -= size;
            fileMetadata.remove(filename);
        }
    }
    
    public int getFileCount() {
        return fileList.size();
    }
    
    public long getTotalSize() {
        return totalSize;
    }
    
    // Unique to DirectoryManager: metadata
    public void setFileMetadata(String filename, String metadata) {
        if (fileList.contains(filename)) {
            fileMetadata.put(filename, metadata);
        }
    }
    
    public String getFileMetadata(String filename) {
        return fileMetadata.get(filename);
    }
    
    // Unique to DirectoryManager: search
    public List<String> searchFiles(String pattern) {
        List<String> results = new ArrayList<>();
        for (String file : fileList) {
            if (file.contains(pattern)) {
                results.add(file);
            }
        }
        return results;
    }
}
