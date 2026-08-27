package com.imsr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.imsr.core.FilesHandle;

public class Utility {
    
    // Converted to accept files directly from your Vaadin web upload
    public static void convert_pdf_to_text_files(File[] files, String convertOption) {
        if (files != null && files.length > 0) {
            String inputFolder = files[0].getParentFile().toString();
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            File pdftotext_exe_target_file = null;
            
            // Only extract local pdftotext.exe if running on Windows
            if (isWindows) {
                Path targetDirectory = Paths.get(inputFolder + File.separator + "pdftotext.exe");
                pdftotext_exe_target_file = FilesHandle.getResourceFile("pdftotext.exe", targetDirectory);
            }
            
            if ("both".equals(convertOption)) {
                run_command(inputFolder, files, "simple2");
                run_command(inputFolder, files, "raw");
            } else {
                run_command(inputFolder, files, convertOption);
            }
            
            // Clean up temporary Windows executable if it was extracted
            if (pdftotext_exe_target_file != null && pdftotext_exe_target_file.exists()) {
                pdftotext_exe_target_file.delete();
            }
        }
    }
    
    public static void run_command(String inputFolder, File[] file, String corvert_option) {
        final File directory = new File(inputFolder);
        final File out_directory = new File(inputFolder + File.separator + corvert_option);
        if (!out_directory.exists()) {
            out_directory.mkdirs();
        }
        
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        int number_files_per_thread = 30;
        int number_of_splits = file.length / number_files_per_thread;
        
        for (int i = 0; i <= number_of_splits; i++) {
            String batch_command = "cd " + inputFolder;
            for (int j = 0; j < number_files_per_thread; j++) {
                if (number_files_per_thread * i + j < file.length) {
                    String fileName = file[number_files_per_thread * i + j].getName();
                    String txtName = fileName.replace(".pdf", ".txt");
                    
                    // Cross-platform binary and path separator handling
                    String executable = isWindows ? "pdftotext" : "pdftotext";
                    String outputPath = corvert_option + "/" + txtName;
                    
                    batch_command = String.join(" && ", batch_command, 
                        executable + " -" + corvert_option + " " + fileName + " " + outputPath);
                }
            }
            
            final String cmd = batch_command;
            ProcessBuilder builder = new ProcessBuilder();
            
            // Branch process execution based on OS
            if (isWindows) {
                builder.command("cmd.exe", "/c", cmd);
            } else {
                builder.command("sh", "-c", cmd);
            }
            
            builder = builder.directory(directory);
            builder.redirectErrorStream(true);
            try {
                Process p = builder.start();
                p.waitFor();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}