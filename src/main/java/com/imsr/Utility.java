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
            File pdftotext_target_file = null;

            if (isWindows) {
                Path targetDirectory = Paths.get(inputFolder + File.separator + "pdftotext.exe");
                pdftotext_target_file = FilesHandle.getResourceFile("pdftotext.exe", targetDirectory);
            } else {
                // Linux / Render environment: Extract custom binary supporting -simple2
                Path targetDirectory = Paths.get(inputFolder + File.separator + "pdftotext_linux");
                pdftotext_target_file = FilesHandle.getResourceFile("pdftotext_linux", targetDirectory);

                // Ensure execution permission is granted on the extracted Linux binary
                if (pdftotext_target_file != null && pdftotext_target_file.exists()) {
                    pdftotext_target_file.setExecutable(true, false);
                }
            }

            if ("both".equals(convertOption)) {
                run_command(inputFolder, files, "simple2", pdftotext_target_file);
                run_command(inputFolder, files, "raw", pdftotext_target_file);
            } else {
                run_command(inputFolder, files, convertOption, pdftotext_target_file);
            }

            if (pdftotext_target_file != null && pdftotext_target_file.exists()) {
                pdftotext_target_file.delete();
            }
        }
    }

    public static void run_command(String inputFolder, File[] files, String convertOption, File customExecutableFile) {
        File outDirectory = new File(inputFolder, convertOption);
        if (!outDirectory.exists()) {
            outDirectory.mkdirs();
        }

        // Use extracted custom binary if available, fallback to system PATH binary if null
        String executable;
        if (customExecutableFile != null && customExecutableFile.exists()) {
            executable = customExecutableFile.getAbsolutePath();
        } else {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            executable = isWindows ? "pdftotext.exe" : "pdftotext";
        }

        for (File pdfFile : files) {
            String fileName = pdfFile.getName();
            String txtName = fileName.replaceAll("(?i)\\.pdf$", ".txt");
            File outputFile = new File(outDirectory, txtName);

            List<String> command = new ArrayList<>();
            command.add(executable);

            if ("simple2".equals(convertOption)) {
                command.add("-simple2");
            } else if ("raw".equals(convertOption)) {
                command.add("-raw");
            }

            command.add(pdfFile.getAbsolutePath());
            command.add(outputFile.getAbsolutePath());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);

            try {
                Process p = builder.start();

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