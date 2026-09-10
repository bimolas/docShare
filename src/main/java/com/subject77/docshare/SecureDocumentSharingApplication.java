package com.subject77.docshare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SecureDocumentSharingApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecureDocumentSharingApplication.class, args);
    }
}
