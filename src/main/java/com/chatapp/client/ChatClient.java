package com.chatapp.client;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import com.chatapp.db.DatabaseManager;
import com.chatapp.db.DatabaseManager.Message;
import com.chatapp.db.DatabaseManager.Group;
import com.chatapp.db.DatabaseManager.GroupMessage;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class ChatClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;
    private static ChatClient instance;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String userEmail;
    private MessageListener messageListener;
    private ScheduledExecutorService messageChecker;
    private DatabaseManager dbManager;
    private Map<String, Boolean> onlineStatus = new ConcurrentHashMap<>();
    private List<Group> userGroups = new ArrayList<>();
    private Map<String, Integer> lastMessageIds = new ConcurrentHashMap<>();
    private Map<Integer, Integer> lastGroupMessageIds = new ConcurrentHashMap<>();

    private ChatClient() {
        dbManager = DatabaseManager.getInstance();
    }

    public static ChatClient getInstance() {
        if (instance == null) {
            instance = new ChatClient();
        }
        return instance;
    }

    public void start(String email) {
        this.userEmail = email;
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send user email to server
            out.println(email);

            // Load user groups immediately when connecting
            userGroups = dbManager.getUserGroups(email);
            if (messageListener != null) {
                Platform.runLater(() -> messageListener.onGroupsUpdated(userGroups));
            }

            // Start message listener thread
            new Thread(this::listenForMessages).start();

            // Start periodic message checker
            messageChecker = Executors.newSingleThreadScheduledExecutor();
            messageChecker.scheduleAtFixedRate(this::checkForNewMessages, 1, 1, TimeUnit.SECONDS);

            // Send initial status update
            out.println("STATUS:" + email + ":ONLINE");

        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
        }
    }

    private void listenForMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("CONTACT_REQUEST:")) {
                    String sender = message.substring("CONTACT_REQUEST:".length());
                    Platform.runLater(() -> {
                        showContactRequestDialog(sender);
                    });
                } else if (message.startsWith("CONTACT_ACCEPTED:")) {
                    String accepter = message.substring("CONTACT_ACCEPTED:".length());
                    Platform.runLater(() -> {
                        showNotification("Contact Request Accepted", accepter + " accepted your contact request.");
                        updateContactsList();
                    });
                } else if (message.startsWith("CONTACT_DECLINED:")) {
                    String decliner = message.substring("CONTACT_DECLINED:".length());
                    Platform.runLater(() -> {
                        showNotification("Contact Request Declined", decliner + " declined your contact request.");
                    });
                } else if (message.startsWith("MSG:")) {
                    String[] parts = message.substring(4).split(":", 2);
                    if (parts.length == 2) {
                        String sender = parts[0];
                        String content = parts[1];
                        if (messageListener != null) {
                            Platform.runLater(() -> {
                                messageListener.onMessageReceived(sender, content);
                                // Only update messages if this is the current chat
                                String currentContact = messageListener.getCurrentContact();
                                if (currentContact != null) {
                                    String cleanContact = currentContact.replace(" (Online)", "");
                                    List<Message> messages = dbManager.getChatHistory(userEmail, cleanContact);
                                    if (!messages.isEmpty()) {
                                        int lastId = messages.get(messages.size() - 1).getId();
                                        Integer previousLastId = lastMessageIds.get(cleanContact);
                                        if (previousLastId == null || lastId > previousLastId) {
                                            lastMessageIds.put(cleanContact, lastId);
                                            messageListener.onMessagesUpdated(messages);
                                        }
                                    }
                                }
                            });
                        }
                    }
                } else if (message.startsWith("FILE_MSG:")) {
                    String[] parts = message.substring(9).split(":", 3);
                    if (parts.length == 3) {
                        String sender = parts[0];
                        String content = parts[1];
                        int fileId = Integer.parseInt(parts[2]);
                        if (messageListener != null) {
                            Platform.runLater(() -> {
                                messageListener.onMessageReceived(sender, content);
                                // Only update messages if this is the current chat
                                String currentContact = messageListener.getCurrentContact();
                                if (currentContact != null) {
                                    String cleanContact = currentContact.replace(" (Online)", "");
                                    List<Message> messages = dbManager.getChatHistory(userEmail, cleanContact);
                                    if (!messages.isEmpty()) {
                                        int lastId = messages.get(messages.size() - 1).getId();
                                        Integer previousLastId = lastMessageIds.get(cleanContact);
                                        if (previousLastId == null || lastId > previousLastId) {
                                            lastMessageIds.put(cleanContact, lastId);
                                            messageListener.onMessagesUpdated(messages);
                                        }
                                    }
                                }
                            });
                        }
                    }
                } else if (message.startsWith("GROUP_MSG:")) {
                    String[] parts = message.substring(10).split(":", 3);
                    if (parts.length == 3) {
                        int groupId = Integer.parseInt(parts[0]);
                        String sender = parts[1];
                        String content = parts[2];
                        if (messageListener != null) {
                            Platform.runLater(() -> {
                                messageListener.onGroupMessageReceived(groupId, sender, content);
                                // Only update messages if this is the current group
                                Integer currentGroupId = messageListener.getCurrentGroupId();
                                if (currentGroupId != null && currentGroupId == groupId) {
                                    List<GroupMessage> messages = dbManager.getGroupChatHistory(groupId);
                                    if (!messages.isEmpty()) {
                                        int lastId = messages.get(messages.size() - 1).getId();
                                        Integer previousLastId = lastGroupMessageIds.get(groupId);
                                        if (previousLastId == null || lastId > previousLastId) {
                                            lastGroupMessageIds.put(groupId, lastId);
                                            messageListener.onGroupMessagesUpdated(messages);
                                        }
                                    }
                                }
                            });
                        }
                    }
                } else if (message.startsWith("GROUP_FILE_MSG:")) {
                    String[] parts = message.substring(15).split(":", 4);
                    if (parts.length == 4) {
                        int groupId = Integer.parseInt(parts[0]);
                        String sender = parts[1];
                        String content = parts[2];
                        int fileId = Integer.parseInt(parts[3]);
                        if (messageListener != null) {
                            Platform.runLater(() -> {
                                messageListener.onGroupMessageReceived(groupId, sender, content);
                                // Only update messages if this is the current group
                                Integer currentGroupId = messageListener.getCurrentGroupId();
                                if (currentGroupId != null && currentGroupId == groupId) {
                                    List<GroupMessage> messages = dbManager.getGroupChatHistory(groupId);
                                    if (!messages.isEmpty()) {
                                        int lastId = messages.get(messages.size() - 1).getId();
                                        Integer previousLastId = lastGroupMessageIds.get(groupId);
                                        if (previousLastId == null || lastId > previousLastId) {
                                            lastGroupMessageIds.put(groupId, lastId);
                                            messageListener.onGroupMessagesUpdated(messages);
                                        }
                                    }
                                }
                            });
                        }
                    }
                } else if (message.startsWith("NOTIFY:")) {
                    String[] parts = message.substring(7).split(":", 2);
                    if (parts.length == 2) {
                        String notification = parts[1];
                        if (notification.startsWith("NEW_GROUP:")) {
                            // Reload groups when a new group is created
                            userGroups = dbManager.getUserGroups(userEmail);
                            if (messageListener != null) {
                                Platform.runLater(() -> messageListener.onGroupsUpdated(userGroups));
                            }
                        } else if (notification.startsWith("GROUP_DELETED:")) {
                            // Handle group deletion notification
                            int deletedGroupId = Integer.parseInt(notification.substring(14));
                            userGroups = dbManager.getUserGroups(userEmail);
                            if (messageListener != null) {
                                Platform.runLater(() -> {
                                    messageListener.onGroupsUpdated(userGroups);
                                    // If the deleted group was currently selected, clear the chat
                                    if (messageListener.getCurrentGroupId() != null && 
                                        messageListener.getCurrentGroupId() == deletedGroupId) {
                                        messageListener.onGroupMessagesUpdated(new ArrayList<>());
                                    }
                                });
                            }
                        } else if (notification.startsWith("MESSAGE_DELETED:")) {
                            // Handle message deletion notification
                            String[] deleteParts = notification.substring(15).split(":");
                            if (deleteParts.length == 2) {
                                String sender = deleteParts[0];
                                int messageId = Integer.parseInt(deleteParts[1]);
                                
                                // Delete message from local database
                                dbManager.deleteMessage(messageId);
                                
                                if (messageListener != null) {
                                    Platform.runLater(() -> {
                                        String currentContact = messageListener.getCurrentContact();
                                        if (currentContact != null) {
                                            String cleanContact = currentContact.replace(" (Online)", "");
                                            if (cleanContact.equals(sender)) {
                                                // Update the chat history to remove the deleted message
                                                List<Message> messages = dbManager.getChatHistory(userEmail, cleanContact);
                                                messages.removeIf(msg -> msg.getId() == messageId);
                                                messageListener.onMessagesUpdated(messages);
                                            }
                                        }
                                    });
                                }
                            }
                        } else if (notification.startsWith("GROUP_MESSAGE_DELETED:")) {
                            // Handle group message deletion notification
                            String[] deleteParts = notification.substring(21).split(":");
                            if (deleteParts.length == 2) {
                                int groupId = Integer.parseInt(deleteParts[0]);
                                int messageId = Integer.parseInt(deleteParts[1]);
                                
                                if (messageListener != null) {
                                    Platform.runLater(() -> {
                                        Integer currentGroupId = messageListener.getCurrentGroupId();
                                        if (currentGroupId != null && currentGroupId == groupId) {
                                            // Update the group chat history to remove the deleted message
                                            List<GroupMessage> messages = dbManager.getGroupChatHistory(groupId);
                                            messages.removeIf(msg -> msg.getId() == messageId);
                                            messageListener.onGroupMessagesUpdated(messages);
                                        }
                                    });
                                }
                            }
                        }
                    }
                } else if (message.startsWith("STATUS:")) {
                    String[] parts = message.substring(7).split(":", 2);
                    if (parts.length == 2) {
                        String user = parts[0];
                        boolean isOnline = parts[1].equals("ONLINE");
                        onlineStatus.put(user, isOnline);
                        if (messageListener != null) {
                            Platform.runLater(() -> messageListener.onStatusChanged(user, isOnline));
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading from server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkForNewMessages() {
        try {
            if (messageListener != null) {
                String currentContact = messageListener.getCurrentContact();
                if (currentContact != null) {
                    String cleanContact = currentContact.replace(" (Online)", "");
                    List<Message> messages = dbManager.getChatHistory(userEmail, cleanContact);
                    
                    // Always update the UI with the latest messages
                    if (!messages.isEmpty()) {
                        Platform.runLater(() -> messageListener.onMessagesUpdated(messages));
                    }
                }
                
                Integer currentGroupId = messageListener.getCurrentGroupId();
                if (currentGroupId != null) {
                    List<GroupMessage> messages = dbManager.getGroupChatHistory(currentGroupId);
                    
                    // Always update the UI with the latest messages
                    if (!messages.isEmpty()) {
                        Platform.runLater(() -> messageListener.onGroupMessagesUpdated(messages));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking for new messages: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendMessage(String recipient, String message) {
        if (out != null) {
            out.println("TO:" + recipient + ":" + message);
        }
    }

    public void sendGroupMessage(int groupId, String message) {
        if (out != null) {
            out.println("GROUP:" + groupId + ":" + message);
        }
    }

    public void sendMessageWithFile(String recipient, String message, int fileId) {
        if (out != null) {
            out.println("FILE_MSG:" + recipient + ":" + message + ":" + fileId);
            // Save the message locally to ensure it persists
            dbManager.saveMessage(userEmail, recipient, message);
        }
    }

    public void sendGroupMessageWithFile(int groupId, String message, int fileId) {
        if (out != null) {
            out.println("GROUP_FILE_MSG:" + groupId + ":" + message + ":" + fileId);
            // Save the message locally to ensure it persists
            dbManager.saveGroupMessage(groupId, userEmail, message);
        }
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public boolean isUserOnline(String userEmail) {
        return onlineStatus.getOrDefault(userEmail, false);
    }

    public List<Group> getUserGroups() {
        return userGroups;
    }

    public void createGroup(String groupName, List<String> memberEmails) {
        try {
            // Create group in database
            boolean success = dbManager.createGroup(groupName, userEmail, memberEmails);
            if (success) {
                // Notify all members about the new group
                String notification = "NEW_GROUP:" + groupName;
                for (String member : memberEmails) {
                    if (!member.equals(userEmail)) { // Don't notify self
                        out.println("NOTIFY:" + member + ":" + notification);
                    }
                }
                
                // Update local groups list
                userGroups = dbManager.getUserGroups(userEmail);
                if (messageListener != null) {
                    Platform.runLater(() -> messageListener.onGroupsUpdated(userGroups));
                }
            }
        } catch (Exception e) {
            System.err.println("Error creating group: " + e.getMessage());
        }
    }

    public void stop() {
        try {
            if (messageChecker != null) {
                messageChecker.shutdown();
            }
            if (out != null) {
                out.println("STATUS:" + userEmail + ":OFFLINE");
                out.close();
            }
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }

    public void notifyGroupDeletion(String memberEmail, int groupId) {
        try {
            out.println("NOTIFY:" + memberEmail + ":GROUP_DELETED:" + groupId);
            out.flush();
        } catch (Exception e) {
            System.err.println("Error notifying group deletion: " + e.getMessage());
        }
    }

    public void notifyMessageDeletion(String recipient, int messageId) {
        if (out != null) {
            // Delete message from database immediately
            dbManager.deleteMessage(messageId);
            
            // Send deletion notification to recipient
            out.println("NOTIFY:" + recipient + ":MESSAGE_DELETED:" + userEmail + ":" + messageId);
            out.flush(); // Ensure immediate delivery
            
            // Update local UI immediately for the sender
            if (messageListener != null) {
                Platform.runLater(() -> {
                    String currentContact = messageListener.getCurrentContact();
                    if (currentContact != null && currentContact.equals(recipient)) {
                        // Get updated chat history
                        List<Message> messages = dbManager.getChatHistory(userEmail, recipient);
                        // Update UI
                        messageListener.onMessagesUpdated(messages);
                    }
                });
            }
        }
    }

    public void notifyGroupMessageDeletion(int groupId, int messageId) {
        if (out != null) {
            // Get all group members except the current user
            List<String> groupMembers = dbManager.getGroupMembers(groupId);
            for (String member : groupMembers) {
                if (!member.equals(userEmail)) {
                    out.println("NOTIFY:" + member + ":GROUP_MESSAGE_DELETED:" + groupId + ":" + messageId);
                    out.flush(); // Ensure immediate delivery
                }
            }
            
            // Update local UI immediately for the sender
            if (messageListener != null) {
                Platform.runLater(() -> {
                    Integer currentGroupId = messageListener.getCurrentGroupId();
                    if (currentGroupId != null && currentGroupId == groupId) {
                        List<GroupMessage> messages = dbManager.getGroupChatHistory(groupId);
                        messageListener.onGroupMessagesUpdated(messages);
                    }
                });
            }
        }
    }

    public void sendContactRequest(String receiverEmail) {
        if (out != null) {
            out.println("CONTACT_REQUEST:" + receiverEmail);
            out.flush();
        }
    }

    public void acceptContactRequest(String senderEmail) {
        if (out != null) {
            out.println("CONTACT_ACCEPT:" + senderEmail);
            out.flush();
        }
    }

    public void declineContactRequest(String senderEmail) {
        if (out != null) {
            out.println("CONTACT_DECLINE:" + senderEmail);
            out.flush();
        }
    }

    private void showContactRequestDialog(String sender) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("New Contact Request");
        alert.setHeaderText("Contact Request from " + sender);
        alert.setContentText("Would you like to accept this contact request?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == ButtonType.OK) {
                // Accept the contact request
                sendMessage("CONTACT_ACCEPTED", userEmail + ":" + sender);
                updateContactsList();
            } else {
                // Decline the contact request
                sendMessage("CONTACT_DECLINED", userEmail + ":" + sender);
            }
        }
    }

    private void showNotification(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void updateContactsList() {
        // Refresh the contacts list in the UI
        Platform.runLater(() -> {
            if (messageListener != null) {
                messageListener.onGroupsUpdated(userGroups);
            }
        });
    }

    public interface MessageListener {
        void onMessageReceived(String sender, String message);
        void onMessagesUpdated(List<Message> messages);
        void onGroupMessageReceived(int groupId, String sender, String message);
        void onGroupMessagesUpdated(List<GroupMessage> messages);
        void onStatusChanged(String user, boolean isOnline);
        void onGroupsUpdated(List<Group> groups);
        void onContactRequestReceived(String sender);
        void onContactRequestAccepted(String contact);
        void onContactRequestDeclined(String contact);
        String getCurrentContact();
        Integer getCurrentGroupId();
    }
} 