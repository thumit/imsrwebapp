package com.imsr.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.core.io.ClassPathResource;

public class FilesHandle {

    public FilesHandle() {
    }
    
    // Gets the system's temporary directory or a local working directory for the web app
    public static File get_temporaryFolder() {
        File temporaryFolder = new File(System.getProperty("java.io.tmpdir"), "imsr_temp");
        if (!temporaryFolder.exists()) {
            temporaryFolder.mkdirs();
        }
        return temporaryFolder;
    }   

    public static File get_file_maequee() {
        File file_maequee = null;
        try {
            file_maequee = new File(get_temporaryFolder(), "maequee.txt");
            file_maequee.deleteOnExit();

            // Use Spring's ClassPathResource to safely read files inside the JAR/classpath
            ClassPathResource resource = new ClassPathResource("maequee.txt");
            try (InputStream initialStream = resource.getInputStream();
                 OutputStream outStream = new FileOutputStream(file_maequee)) {
                byte[] buffer = new byte[initialStream.available()];
                initialStream.read(buffer);
                outStream.write(buffer);
            }
        } catch (IOException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
        } 
        return file_maequee;
    }
    
    public static File getResourceFile(String fileName, Path targetDirectory) {
        try {
            ClassPathResource resource = new ClassPathResource(fileName);
            try (InputStream initialStream = resource.getInputStream()) {
                Files.copy(initialStream, targetDirectory, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
        }
        return targetDirectory.toFile();
    }
}