package com.chatapp.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final ConcurrentHashMap<String, ClientHandler> connectedClients;
    private BufferedReader in;
    private PrintWriter out;
    private String userEmail;
    private boolean isRunning;

    public ClientHandler(Socket socket, ConcurrentHashMap<String, ClientHandler> connectedClients) {
        this.clientSocket = socket;
        this.connectedClients = connectedClients;
        this.isRunning = true;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // First message should be the user's email for authentication
            userEmail = in.readLine();
            if (userEmail != null && isValidEmail(userEmail)) {
                connectedClients.put(userEmail, this);
                System.out.println("User connected: " + userEmail);
                out.println("CONNECTED");
            } else {
                out.println("INVALID_EMAIL");
                closeConnection();
                return;
            }

            String message;
            while (isRunning && (message = in.readLine()) != null) {
                processMessage(message);
            }
        } catch (IOException e) {
            System.err.println("Error handling client " + userEmail + ": " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void processMessage(String message) {
        if (message.startsWith("TO:")) {
            String[] parts = message.substring(3).split(":", 2);
            if (parts.length == 2) {
                String recipient = parts[0];
                String content = parts[1];
                routeMessage(recipient, content);
            }
        }
    }

    private void routeMessage(String recipient, String content) {
        ClientHandler recipientHandler = connectedClients.get(recipient);
        if (recipientHandler != null) {
            String formattedMessage = String.format("FROM:%s:%s", userEmail, content);
            recipientHandler.sendMessage(formattedMessage);
            System.out.println("Message routed from " + userEmail + " to " + recipient);
        } else {
            // TODO: Store message for offline users
            System.out.println("Recipient " + recipient + " is offline. Message will be stored.");
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void closeConnection() {
        isRunning = false;
        if (userEmail != null) {
            connectedClients.remove(userEmail);
            System.out.println("User disconnected: " + userEmail);
        }
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
} 