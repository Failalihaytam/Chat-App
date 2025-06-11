package com.chatapp.client;

import java.util.Scanner;

public class ClientUI {
    private final Scanner scanner;
    private String currentUserEmail;
    private String currentRecipient;

    public ClientUI() {
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        System.out.println("Welcome to Email Chat!");
        login();
        startChat();
    }

    private void login() {
        while (true) {
            System.out.print("Enter your email address: ");
            String email = scanner.nextLine().trim();
            
            if (isValidEmail(email)) {
                this.currentUserEmail = email;
                System.out.println("Logged in as: " + email);
                ChatClient.getInstance().start(email);
                break;
            } else {
                System.out.println("Invalid email format. Please try again.");
            }
        }
    }

    private void startChat() {
        System.out.println("\nChat started! Type 'exit' to quit.");
        
        while (true) {
            // Get recipient
            System.out.print("\nEnter recipient's email: ");
            String recipient = scanner.nextLine().trim();
            
            if ("exit".equalsIgnoreCase(recipient)) {
                break;
            }
            
            if (!isValidEmail(recipient)) {
                System.out.println("Invalid email format. Please try again.");
                continue;
            }
            
            this.currentRecipient = recipient;
            
            // Get message
            System.out.print("Enter your message: ");
            String message = scanner.nextLine().trim();
            
            if ("exit".equalsIgnoreCase(message)) {
                break;
            }
            
            if (!message.isEmpty()) {
                ChatClient.getInstance().sendMessage(recipient, message);
                System.out.println("Message sent to " + recipient);
            }
        }
        
        System.out.println("Goodbye!");
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    public String getCurrentUserEmail() {
        return currentUserEmail;
    }

    public String getCurrentRecipient() {
        return currentRecipient;
    }
} 