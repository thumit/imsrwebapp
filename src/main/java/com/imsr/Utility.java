package com.imsr;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import com.imsr.core.FilesHandle;

public class Utility {

    public static void convert_pdf_to_text_files(File[] files, String convertOption) {
        if (files != null && files.length > 0) {
            String inputFolder = files[0].getParentFile().getAbsolutePath();
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            File pdftotext_exe_target_file = null;

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

            if (pdftotext_exe_target_file != null && pdftotext_exe_target_file.exists()) {
                pdftotext_exe_target_file.delete();
            }
        }
    }

    public static void run_command(String inputFolder, File[] files, String convertOption) {
        File outDirectory = new File(inputFolder, convertOption);
        if (!outDirectory.exists()) {
            outDirectory.mkdirs();
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        
        // Resolve absolute binary path for Windows (extracted local exe) vs Linux (installed poppler utility)
        String executable = isWindows 
                ? new File(inputFolder, "pdftotext.exe").getAbsolutePath() 
                : "pdftotext";

        for (File pdfFile : files) {
            String fileName = pdfFile.getName();
            String txtName = fileName.replaceAll("(?i)\\.pdf$", ".txt");
            File outputFile = new File(outDirectory, txtName);

            List<String> command = new ArrayList<>();
            command.add(executable);

            // pdftotext CLI options mapping:
            // "simple2" uses layout preservation; "raw" uses raw layout/stream order
            if ("simple2".equals(convertOption)) {
                command.add("-layout");
            } else if ("raw".equals(convertOption)) {
                command.add("-raw");
            }

            command.add(pdfFile.getAbsolutePath());
            command.add(outputFile.getAbsolutePath());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);

            try {
                Process p = builder.start();
                
                // Read and output stream to capture errors in Render logs
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[pdftotext]: " + line);
                    }
                }

                int exitCode = p.waitFor();
                if (exitCode != 0) {
                    System.err.println("pdftotext failed for " + pdfFile.getName() + " with exit code: " + exitCode);
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Execution exception during conversion of: " + pdfFile.getName());
                e.printStackTrace();
            }
        }
    }
}