package com.imsr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.imsr.core.FilesHandle;

public class Utility {
    
    // Converted to accept files directly from your Vaadin web upload
    public static void convert_pdf_to_text_files(File[] files, String convertOption) {
        if (files != null && files.length > 0) {
            String inputFolder = files[0].getParentFile().toString();
            Path targetDirectory = Paths.get(inputFolder + "/pdftotext.exe");
            File pdftotext_exe_target_file = FilesHandle.getResourceFile("pdftotext.exe", targetDirectory);
            
            if ("both".equals(convertOption)) {
                run_command(inputFolder, files, "simple2");
                run_command(inputFolder, files, "raw");
            } else {
                run_command(inputFolder, files, convertOption);
            }
            
            // Clean up the temporary executable file
            if (pdftotext_exe_target_file != null && pdftotext_exe_target_file.exists()) {
                pdftotext_exe_target_file.delete();
            }
        }
    }
    
    public static void run_command(String inputFolder, File[] file, String corvert_option) {
        final File directory = new File(inputFolder);
        final File out_directory = new File(inputFolder + "/" + corvert_option);
        if (!out_directory.exists()) {
            out_directory.mkdirs();
        }
        
        int number_files_per_thread = 30;
        int number_of_splits = file.length / number_files_per_thread;
        
        for (int i = 0; i <= number_of_splits; i++) {
            String batch_command = "cd " + inputFolder;
            for (int j = 0; j < number_files_per_thread; j++) {
                if (number_files_per_thread * i + j < file.length) {
                    batch_command = String.join(" && ", batch_command, "pdftotext -" + corvert_option + " " + file[number_files_per_thread * i + j].getName()
                            + " " + corvert_option + "\\" + file[number_files_per_thread * i + j].getName().replace(".pdf", ".txt"));
                }
            }
            final String cmd = batch_command;
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", cmd);
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