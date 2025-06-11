package com.chatapp.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.chatapp.db.DatabaseManager;
import javafx.application.Platform;

public class ChatServer {
    private static final int PORT = 5000;
    private static final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();
    private static final DatabaseManager dbManager = DatabaseManager.getInstance();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Chat Server is running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private String userEmail;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                // First message should be the user's email
                userEmail = in.readLine();
                if (userEmail != null) {
                    // Send current online status to the new client
                    for (String onlineUser : clients.keySet()) {
                        out.println("STATUS:" + onlineUser + ":ONLINE");
                    }
                    
                    clients.put(userEmail, out);
                    System.out.println(userEmail + " connected");
                    // Notify all clients about the new online user
                    broadcastUserStatus(userEmail, true);
                }

                String message;
                while ((message = in.readLine()) != null) {
                    // Message format: "TO:recipient@email.com:message" or "GROUP:groupId:message" or "NOTIFY:user@email.com:notification"
                    if (message.startsWith("TO:")) {
                        String[] parts = message.substring(3).split(":", 2);
                        if (parts.length == 2) {
                            String recipient = parts[0];
                            String content = parts[1];
                            
                            // Save message to database
                            if (dbManager.saveMessage(userEmail, recipient, content)) {
                                // Send message to recipient if online
                                PrintWriter recipientWriter = clients.get(recipient);
                                if (recipientWriter != null) {
                                    recipientWriter.println("MSG:" + userEmail + ":" + content);
                                }
                                // Send confirmation to sender
                                out.println("MSG_SENT:" + recipient);
                            }
                        }
                    } else if (message.startsWith("GROUP:")) {
                        String[] parts = message.substring(6).split(":", 2);
                        if (parts.length == 2) {
                            int groupId = Integer.parseInt(parts[0]);
                            String content = parts[1];
                            
                            // Save group message to database
                            if (dbManager.saveGroupMessage(groupId, userEmail, content)) {
                                // Get all group members
                                List<String> members = dbManager.getGroupMembers(groupId);
                                
                                // Send message to all online group members
                                for (String member : members) {
                                    if (!member.equals(userEmail)) { // Don't send to sender
                                        PrintWriter memberWriter = clients.get(member);
                                        if (memberWriter != null) {
                                            memberWriter.println("GROUP_MSG:" + groupId + ":" + userEmail + ":" + content);
                                        }
                                    }
                                }
                                // Send confirmation to sender
                                out.println("GROUP_MSG_SENT:" + groupId);
                            }
                        }
                    } else if (message.startsWith("NOTIFY:")) {
                        String[] parts = message.substring(7).split(":", 2);
                        if (parts.length == 2) {
                            String recipient = parts[0];
                            String notification = parts[1];
                            
                            // Forward notification to recipient if online
                            PrintWriter recipientWriter = clients.get(recipient);
                            if (recipientWriter != null) {
                                if (notification.startsWith("MESSAGE_DELETED:") || 
                                    notification.startsWith("GROUP_MESSAGE_DELETED:")) {
                                    // For message deletion notifications, forward them immediately
                                    recipientWriter.println("NOTIFY:" + userEmail + ":" + notification);
                                    recipientWriter.flush(); // Ensure immediate delivery
                                } else {
                                    recipientWriter.println("NOTIFY:" + userEmail + ":" + notification);
                                }
                            }
                        }
                    } else if (message.startsWith("STATUS:")) {
                        String[] parts = message.substring(7).split(":", 2);
                        if (parts.length == 2) {
                            String recipient = parts[0];
                            String status = parts[1];
                            
                            // Forward status to recipient if online
                            PrintWriter recipientWriter = clients.get(recipient);
                            if (recipientWriter != null) {
                                recipientWriter.println(message);
                            }
                        }
                    } else if (message.startsWith("CONTACT_REQUEST:")) {
                        String[] parts = message.split(":", 3);
                        if (parts.length == 3) {
                            String sender = parts[1];
                            String receiver = parts[2];
                            // Forward the contact request to the receiver
                            PrintWriter recipientWriter = clients.get(receiver);
                            if (recipientWriter != null) {
                                recipientWriter.println("CONTACT_REQUEST:" + sender);
                                recipientWriter.flush();
                            }
                        }
                    } else if (message.startsWith("CONTACT_ACCEPTED:")) {
                        String[] parts = message.split(":", 3);
                        if (parts.length == 3) {
                            String accepter = parts[1];
                            String sender = parts[2];
                            // Notify the original sender that their request was accepted
                            PrintWriter senderWriter = clients.get(sender);
                            if (senderWriter != null) {
                                senderWriter.println("CONTACT_ACCEPTED:" + accepter);
                                senderWriter.flush();
                            }
                        }
                    } else if (message.startsWith("CONTACT_DECLINED:")) {
                        String[] parts = message.split(":", 3);
                        if (parts.length == 3) {
                            String decliner = parts[1];
                            String sender = parts[2];
                            // Notify the original sender that their request was declined
                            PrintWriter senderWriter = clients.get(sender);
                            if (senderWriter != null) {
                                senderWriter.println("CONTACT_DECLINED:" + decliner);
                                senderWriter.flush();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                if (userEmail != null) {
                    clients.remove(userEmail);
                    System.out.println(userEmail + " disconnected");
                    // Notify all clients about the user going offline
                    broadcastUserStatus(userEmail, false);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
            }
        }
    }

    private static void broadcastUserStatus(String userEmail, boolean isOnline) {
        String statusMessage = "STATUS:" + userEmail + ":" + (isOnline ? "ONLINE" : "OFFLINE");
        for (PrintWriter writer : clients.values()) {
            writer.println(statusMessage);
        }
    }

    public static boolean isUserOnline(String userEmail) {
        return clients.containsKey(userEmail);
    }

    private static void handleMessageDeletion(String sender, String recipient, int messageId) {
        // Delete message from database
        dbManager.deleteMessage(messageId);
        
        // Notify recipient immediately
        PrintWriter recipientWriter = clients.get(recipient);
        if (recipientWriter != null) {
            recipientWriter.println("NOTIFY:" + sender + ":MESSAGE_DELETED:" + sender + ":" + messageId);
            recipientWriter.flush(); // Ensure immediate delivery
        }
    }
} 