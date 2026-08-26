package com.imsr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        // This starts the embedded web server and keeps it running on port 8080
    	System.setProperty("java.awt.headless", "false"); // Fix HeadlessException happens because Vaadin runs as a web server on a machine that Java thinks is "headless"
        SpringApplication.run(Application.class, args);
    }
}