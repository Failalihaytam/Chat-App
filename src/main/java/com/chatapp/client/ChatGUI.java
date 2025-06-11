package com.chatapp.client;

import com.chatapp.db.DatabaseManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.List;
import com.chatapp.db.DatabaseManager.Message;
import com.chatapp.db.DatabaseManager.ChatSummary;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import java.util.ArrayList;
import com.chatapp.db.DatabaseManager.Group;
import com.chatapp.db.DatabaseManager.GroupMessage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.io.File;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.stage.FileChooser;
import java.io.IOException;
import java.nio.file.Files;
import com.chatapp.db.DatabaseManager.FileData;
import java.util.Map;
import java.util.HashMap;

public class ChatGUI extends Application {
    private VBox chatArea;
    private TextField messageInput;
    private ListView<String> contactsList;
    private ListView<String> groupsList;
    private DatabaseManager dbManager;
    private ChatClient chatClient;
    private Label statusLabel;
    private String userEmail;
    private Integer currentGroupId;
    private String currentContact;
    private ObservableList<String> contacts;
    private ScrollPane chatScrollPane;
    private MediaPlayer notificationPlayer;
    private Stage notificationStage;
    private Label notificationLabel;
    private List<Group> userGroups;
    private boolean shouldAutoScroll = true;
    private Map<Integer, Integer> unreadGroupMessages = new HashMap<>(); // Track unread messages for each group

    @Override
    public void start(Stage primaryStage) {
        // Initialize notification sound
        try {
            Media sound = new Media(new File("src/main/resources/notification.mp3").toURI().toString());
            notificationPlayer = new MediaPlayer(sound);
        } catch (Exception e) {
            System.err.println("Error loading notification sound: " + e.getMessage());
        }

        // Initialize notification window
        notificationStage = new Stage(StageStyle.UTILITY);
        notificationStage.setAlwaysOnTop(true);
        notificationLabel = new Label();
        notificationLabel.setStyle("-fx-padding: 10px; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        notificationStage.setScene(new Scene(notificationLabel));
        
        dbManager = DatabaseManager.getInstance();
        chatClient = ChatClient.getInstance();
        showLoginRegisterDialog(primaryStage);
    }

    private void showLoginRegisterDialog(Stage primaryStage) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Login or Register");
        dialog.setHeaderText("Choose an option");

        ButtonType loginButton = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        ButtonType registerButton = new ButtonType("Register", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButton, registerButton, cancelButton);

        dialog.showAndWait().ifPresent(buttonType -> {
            if (buttonType == loginButton) {
                showLoginDialog(primaryStage);
            } else if (buttonType == registerButton) {
                showRegisterDialog(primaryStage);
            } else {
                Platform.exit();
            }
        });
    }

    private void showLoginDialog(Stage primaryStage) {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Login");
        dialog.setHeaderText("Enter your credentials");

        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        dialog.getDialogPane().setContent(new VBox(10,
            new Label("Email:"), emailField,
            new Label("Password:"), passwordField
        ));

        ButtonType loginButton = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButton, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButton) {
                return new String[]{emailField.getText(), passwordField.getText()};
            }
            return null;
        });

        dialog.showAndWait().ifPresent(credentials -> {
            String email = credentials[0];
            String password = credentials[1];

            if (dbManager.authenticateUser(email, password)) {
                userEmail = email;
                showMainWindow(primaryStage);
            } else {
                showAlert("Error", "Invalid email or password");
                showLoginDialog(primaryStage);
            }
        });
    }

    private void showRegisterDialog(Stage primaryStage) {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Register");
        dialog.setHeaderText("Create a new account");

        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm Password");

        dialog.getDialogPane().setContent(new VBox(10,
            new Label("Email:"), emailField,
            new Label("Password:"), passwordField,
            new Label("Confirm Password:"), confirmPasswordField
        ));

        ButtonType registerButton = new ButtonType("Register", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(registerButton, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == registerButton) {
                return new String[]{
                    emailField.getText(),
                    passwordField.getText(),
                    confirmPasswordField.getText()
                };
            }
            return null;
        });

        dialog.showAndWait().ifPresent(credentials -> {
            String email = credentials[0];
            String password = credentials[1];
            String confirmPassword = credentials[2];

            if (!isValidEmail(email)) {
                showAlert("Error", "Please enter a valid email address");
                showRegisterDialog(primaryStage);
                return;
            }

            if (!password.equals(confirmPassword)) {
                showAlert("Error", "Passwords do not match");
                showRegisterDialog(primaryStage);
                return;
            }

            if (dbManager.registerUser(email, password)) {
                showAlert("Success", "Registration successful! Please login.");
                showLoginDialog(primaryStage);
            } else {
                showAlert("Error", "Email already exists");
                showRegisterDialog(primaryStage);
            }
        });
    }

    private void showMainWindow(Stage primaryStage) {
        primaryStage.setTitle("Chat Application - " + userEmail);
        
        // Initialize components
        chatArea = new VBox(5);
        chatArea.setPadding(new Insets(10));
        
        messageInput = new TextField();
        contactsList = new ListView<>();
        groupsList = new ListView<>();
        contacts = FXCollections.observableArrayList();
        contactsList.setItems(contacts);
        
        statusLabel = new Label("Status: Disconnected");
        
        // Create layout
        BorderPane mainLayout = new BorderPane();
        
        // Create the left panel with contacts and groups
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setPrefWidth(200);
        
        // Contacts section
        Label contactsLabel = new Label("Contacts");
        contactsList.setPrefHeight(200);
        
        // Groups section
        Label groupsLabel = new Label("Groups");
        groupsList.setPrefHeight(200);
        
        // Add selection listeners
        contactsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentContact = newVal;
                currentGroupId = null;
                groupsList.getSelectionModel().clearSelection();
                loadChatHistory();
                updateStatusLabel();
            }
        });
        
        groupsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentGroupId = getGroupIdFromName(newVal);
                currentContact = null;
                contactsList.getSelectionModel().clearSelection();
                loadGroupChatHistory();
                updateStatusLabel();
                
                // Reset unread count when opening a group chat
                if (currentGroupId != null) {
                    resetGroupUnreadCount(currentGroupId);
                }
            }
        });
        
        // Buttons for contacts and groups
        HBox contactButtons = new HBox(5);
        Button addContactButton = new Button("Add");
        Button removeContactButton = new Button("Remove");
        contactButtons.getChildren().addAll(addContactButton, removeContactButton);
        
        HBox groupButtons = new HBox(5);
        Button createGroupButton = new Button("New Group");
        Button removeGroupButton = new Button("Remove Group");
        groupButtons.getChildren().addAll(createGroupButton, removeGroupButton);
        
        // Add contact request button to the left panel
        Button contactRequestsButton = new Button("Contact Requests");
        contactRequestsButton.setOnAction(e -> showContactRequestsDialog());
        
        // Add components to left panel
        leftPanel.getChildren().addAll(
            contactsLabel, contactsList, contactButtons,
            new Separator(),
            groupsLabel, groupsList, groupButtons,
            new Separator(),
            new Button("Download All History") {{
                setOnAction(e -> downloadAllHistory());
            }},
            contactRequestsButton
        );
        
        // Create the chat area
        VBox chatContainer = new VBox(5);
        chatContainer.setPadding(new Insets(10));
        
        chatScrollPane = new ScrollPane(chatArea);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        
        // Set initial scroll position to bottom
        chatScrollPane.setVvalue(1.0);
        
        // Set up scroll listener
        setupScrollListener();
        
        chatContainer.getChildren().addAll(statusLabel, chatScrollPane);
        
        // Create the message input area
        HBox inputArea = new HBox(5);
        inputArea.setPadding(new Insets(10));
        messageInput.setPrefWidth(400);
        Button sendButton = new Button("Send");
        Button attachButton = new Button("Attach File");
        inputArea.getChildren().addAll(messageInput, attachButton, sendButton);
        
        // Set up the main layout
        mainLayout.setLeft(leftPanel);
        mainLayout.setCenter(chatContainer);
        mainLayout.setBottom(inputArea);
        
        // Set up event handlers
        addContactButton.setOnAction(e -> showAddContactDialog());
        removeContactButton.setOnAction(e -> removeSelectedContact());
        createGroupButton.setOnAction(e -> showCreateGroupDialog());
        removeGroupButton.setOnAction(e -> removeSelectedGroup());
        sendButton.setOnAction(e -> sendMessage());
        messageInput.setOnAction(e -> sendMessage());
        attachButton.setOnAction(e -> attachFile());
        
        // Load initial data
        loadContacts();
        loadGroups();
        
        // Set up chat client
        chatClient.start(userEmail);
        chatClient.setMessageListener(new ChatClient.MessageListener() {
            @Override
            public void onMessageReceived(String sender, String message) {
                Platform.runLater(() -> {
                    if (currentContact != null && currentContact.equals(sender)) {
                        addMessageToChat(sender, message);
                    } else {
                        // Show notification for messages from other contacts
                        showNotification("New message from " + sender + ": " + message);
                    }
                });
            }

            @Override
            public void onMessagesUpdated(List<Message> messages) {
                Platform.runLater(() -> {
                    // Store current scroll position and check if we were at the bottom
                    double scrollPosition = chatScrollPane.getVvalue();
                    boolean wasAtBottom = isScrolledToBottom();
                    
                    // Clear and rebuild the chat area
                    chatArea.getChildren().clear();
                    for (Message message : messages) {
                        addMessageToChat(message.getSenderEmail(), message.getMessage());
                    }
                    
                    // Restore scroll position
                    if (wasAtBottom) {
                        scrollToBottom();
                    } else {
                        chatScrollPane.setVvalue(scrollPosition);
                    }
                });
            }

            @Override
            public void onGroupMessageReceived(int groupId, String sender, String message) {
                Platform.runLater(() -> {
                    if (currentGroupId != null && currentGroupId == groupId) {
                        addGroupMessageToChat(sender, message);
                    } else {
                        // Show notification for messages from other groups
                        String groupName = getGroupNameById(groupId);
                        showNotification("New message in " + groupName + " from " + sender + ": " + message);
                        
                        // Increment unread count for this group
                        int currentCount = unreadGroupMessages.getOrDefault(groupId, 0);
                        updateGroupUnreadCount(groupId, currentCount + 1);
                    }
                });
            }

            @Override
            public void onGroupMessagesUpdated(List<GroupMessage> messages) {
                Platform.runLater(() -> {
                    // Store current scroll position and check if we were at the bottom
                    double scrollPosition = chatScrollPane.getVvalue();
                    boolean wasAtBottom = isScrolledToBottom();
                    
                    // Clear and rebuild the chat area
                    chatArea.getChildren().clear();
                    for (GroupMessage message : messages) {
                        addGroupMessageToChat(message.getSenderEmail(), message.getMessage());
                    }
                    
                    // Restore scroll position
                    if (wasAtBottom) {
                        scrollToBottom();
                    } else {
                        chatScrollPane.setVvalue(scrollPosition);
                    }
                });
            }

            @Override
            public void onStatusChanged(String user, boolean isOnline) {
                Platform.runLater(() -> {
                    // Update online status in contacts list
                    for (int i = 0; i < contacts.size(); i++) {
                        String contact = contacts.get(i);
                        if (contact.equals(user)) {
                            // Just update the contact without adding (Online)
                            contacts.set(i, user);
                            break;
                        }
                    }
                    
                    // Update status label if this is the current contact
                    if (user.equals(currentContact)) {
                        updateStatusLabel();
                    }
                });
            }

            @Override
            public void onGroupsUpdated(List<Group> groups) {
                Platform.runLater(() -> {
                    userGroups = groups;  // Update the userGroups field
                    groupsList.getItems().clear();
                    for (Group group : groups) {
                        // Add group name with unread count if any
                        String groupDisplay = group.getName() + " (" + group.getId() + ")";
                        if (unreadGroupMessages.containsKey(group.getId()) && unreadGroupMessages.get(group.getId()) > 0) {
                            groupDisplay += " [" + unreadGroupMessages.get(group.getId()) + " unread]";
                        }
                        groupsList.getItems().add(groupDisplay);
                    }
                });
            }

            @Override
            public String getCurrentContact() {
                return currentContact;
            }

            @Override
            public Integer getCurrentGroupId() {
                return currentGroupId;
            }

            @Override
            public void onContactRequestReceived(String sender) {
                Platform.runLater(() -> {
                    showNotification("New contact request from " + sender);
                    showContactRequestsDialog();
                });
            }

            @Override
            public void onContactRequestAccepted(String contact) {
                Platform.runLater(() -> {
                    showNotification(contact + " accepted your contact request");
                    loadContacts();
                });
            }

            @Override
            public void onContactRequestDeclined(String contact) {
                Platform.runLater(() -> {
                    showNotification(contact + " declined your contact request");
                });
            }
        });
        
        // Create and show the scene
        Scene scene = new Scene(mainLayout, 800, 500);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void loadContacts() {
        List<String> contactList = dbManager.getContacts(userEmail);
        contacts.clear();
        for (String contact : contactList) {
            // Just add the contact without any status
            contacts.add(contact);
        }
    }

    private void loadGroups() {
        userGroups = dbManager.getUserGroups(userEmail);
        groupsList.getItems().clear();
        for (Group group : userGroups) {
            // Add group name with unread count if any
            String groupDisplay = group.getName() + " (" + group.getId() + ")";
            if (unreadGroupMessages.containsKey(group.getId()) && unreadGroupMessages.get(group.getId()) > 0) {
                groupDisplay += " [" + unreadGroupMessages.get(group.getId()) + " unread]";
            }
            groupsList.getItems().add(groupDisplay);
        }
    }

    private void loadChatHistory() {
        if (currentContact == null) return;
        
        chatArea.getChildren().clear();
        List<Message> messages = dbManager.getChatHistory(userEmail, currentContact);
        
        // Add messages in chronological order
        for (Message message : messages) {
            addMessageToChat(message.getSenderEmail(), message.getMessage());
        }
        
        // Force scroll to bottom after loading messages
        Platform.runLater(() -> {
            chatScrollPane.setVvalue(1.0);
            shouldAutoScroll = true;
        });
    }

    private void loadGroupChatHistory() {
        if (currentGroupId == null) return;
        
        chatArea.getChildren().clear();
        List<GroupMessage> messages = dbManager.getGroupChatHistory(currentGroupId);
        
        // Add messages in chronological order
        for (GroupMessage message : messages) {
            addGroupMessageToChat(message.getSenderEmail(), message.getMessage());
        }
        
        // Force scroll to bottom after loading messages
        Platform.runLater(() -> {
            chatScrollPane.setVvalue(1.0);
            shouldAutoScroll = true;
        });
    }

    private void addMessageToChat(String sender, String message) {
        TextFlow messageFlow = new TextFlow();
        messageFlow.setPadding(new Insets(5));
        
        // Get the message from database to get its timestamp
        List<Message> messages = dbManager.getChatHistory(userEmail, currentContact);
        Message msg = messages.stream()
            .filter(m -> m.getMessage().equals(message))
            .findFirst()
            .orElse(null);
        
        // Create timestamp text using the message's timestamp
        Text timestamp = new Text((msg != null ? msg.getTimestamp().toString() : new java.util.Date().toString()) + "\n");
        timestamp.setStyle("-fx-fill: gray; -fx-font-size: 10px;");
        
        // Create message text
        Text messageText = new Text(message + "\n");
        
        // Style based on sender
        if (sender.equals(userEmail)) {
            messageFlow.setStyle("-fx-background-color: #DCF8C6; -fx-background-radius: 10; -fx-padding: 5;");
            messageFlow.getChildren().addAll(timestamp, messageText);
            messageFlow.setMaxWidth(300);
            messageFlow.setTranslateX(chatArea.getWidth() - messageFlow.getMaxWidth() - 20);
            
            // Add delete button for user's own messages
            Button deleteButton = new Button("Delete");
            deleteButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
            deleteButton.setOnAction(e -> {
                if (msg != null) {
                    if (dbManager.deleteMessage(msg.getId())) {
                        // Remove the message from UI immediately
                        chatArea.getChildren().remove(messageFlow);
                        // Notify the server about message deletion
                        chatClient.notifyMessageDeletion(currentContact, msg.getId());
                        // Update the chat history immediately
                        List<Message> updatedMessages = dbManager.getChatHistory(userEmail, currentContact);
                        Platform.runLater(() -> {
                            chatArea.getChildren().clear();
                            for (Message updatedMsg : updatedMessages) {
                                addMessageToChat(updatedMsg.getSenderEmail(), updatedMsg.getMessage());
                            }
                        });
                    }
                }
            });
            messageFlow.getChildren().add(deleteButton);
        } else {
            messageFlow.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 10; -fx-padding: 5;");
            messageFlow.getChildren().addAll(timestamp, messageText);
            messageFlow.setMaxWidth(300);
        }
        
        // Add file download button if message contains a file
        if (message.startsWith("File: ")) {
            String fileName = message.substring(6);
            Button downloadButton = new Button("Download");
            downloadButton.setOnAction(e -> downloadFile(fileName));
            messageFlow.getChildren().add(downloadButton);
        }
        
        chatArea.getChildren().add(messageFlow);
        
        // Always scroll to bottom for new messages
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    private void addGroupMessageToChat(String sender, String message) {
        TextFlow messageFlow = new TextFlow();
        messageFlow.setPadding(new Insets(5));
        
        // Get the message from database to get its timestamp
        List<GroupMessage> messages = dbManager.getGroupChatHistory(currentGroupId);
        GroupMessage msg = messages.stream()
            .filter(m -> m.getMessage().equals(message))
            .findFirst()
            .orElse(null);
        
        // Create timestamp text using the message's timestamp
        Text timestamp = new Text((msg != null ? msg.getTimestamp().toString() : new java.util.Date().toString()) + "\n");
        timestamp.setStyle("-fx-fill: gray; -fx-font-size: 10px;");
        
        // Create sender text
        Text senderText = new Text(sender + ": ");
        senderText.setStyle("-fx-font-weight: bold;");
        
        // Create message text
        Text messageText = new Text(message + "\n");
        
        // Style based on sender
        if (sender.equals(userEmail)) {
            messageFlow.setStyle("-fx-background-color: #DCF8C6; -fx-background-radius: 10; -fx-padding: 5;");
            messageFlow.getChildren().addAll(timestamp, senderText, messageText);
            messageFlow.setMaxWidth(300);
            messageFlow.setTranslateX(chatArea.getWidth() - messageFlow.getMaxWidth() - 20);
            
            // Add delete button for user's own messages
            Button deleteButton = new Button("Delete");
            deleteButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
            deleteButton.setOnAction(e -> {
                if (msg != null) {
                    if (dbManager.deleteGroupMessage(msg.getId())) {
                        // Remove the message from UI immediately
                        chatArea.getChildren().remove(messageFlow);
                        // Notify the server about message deletion
                        chatClient.notifyGroupMessageDeletion(currentGroupId, msg.getId());
                    }
                }
            });
            messageFlow.getChildren().add(deleteButton);
        } else {
            messageFlow.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 10; -fx-padding: 5;");
            messageFlow.getChildren().addAll(timestamp, senderText, messageText);
            messageFlow.setMaxWidth(300);
        }
        
        // Add file download button if message contains a file
        if (message.startsWith("File: ")) {
            String fileName = message.substring(6);
            Button downloadButton = new Button("Download");
            downloadButton.setOnAction(e -> downloadFile(fileName));
            messageFlow.getChildren().add(downloadButton);
        }
        
        chatArea.getChildren().add(messageFlow);
        
        // Always scroll to bottom for new messages
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    private void showAddContactDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Contact");
        dialog.setHeaderText("Enter contact's email address");
        dialog.setContentText("Email:");

        dialog.showAndWait().ifPresent(email -> {
            if (isValidEmail(email)) {
                if (dbManager.sendContactRequest(userEmail, email)) {
                    chatClient.sendContactRequest(email);
                    showAlert("Success", "Contact request sent to " + email);
                } else {
                    showAlert("Error", "Failed to send contact request. Make sure the user exists.");
                }
            } else {
                showAlert("Invalid Email", "Please enter a valid email address.");
            }
        });
    }

    private void showContactRequestsDialog() {
        List<DatabaseManager.ContactRequest> requests = dbManager.getPendingContactRequests(userEmail);
        if (requests.isEmpty()) {
            showAlert("Contact Requests", "You have no pending contact requests.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Contact Requests");
        dialog.setHeaderText("Pending Contact Requests");

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        for (DatabaseManager.ContactRequest request : requests) {
            HBox requestBox = new HBox(10);
            Label senderLabel = new Label(request.getSenderEmail());
            Button acceptButton = new Button("Accept");
            Button declineButton = new Button("Decline");

            acceptButton.setOnAction(e -> {
                if (dbManager.acceptContactRequest(request.getSenderEmail(), userEmail)) {
                    chatClient.acceptContactRequest(request.getSenderEmail());
                    showAlert("Success", "Contact request accepted.");
                    loadContacts();
                    dialog.close();
                } else {
                    showAlert("Error", "Failed to accept contact request.");
                }
            });

            declineButton.setOnAction(e -> {
                if (dbManager.declineContactRequest(request.getSenderEmail(), userEmail)) {
                    chatClient.declineContactRequest(request.getSenderEmail());
                    showAlert("Success", "Contact request declined.");
                    dialog.close();
                } else {
                    showAlert("Error", "Failed to decline contact request.");
                }
            });

            requestBox.getChildren().addAll(senderLabel, acceptButton, declineButton);
            content.getChildren().add(requestBox);
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void removeSelectedContact() {
        String selected = contactsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (dbManager.removeContact(userEmail, selected)) {
                loadContacts();
                if (selected.equals(currentContact)) {
                    currentContact = null;
                    chatArea.getChildren().clear();
                }
            } else {
                showAlert("Error", "Failed to remove contact.");
            }
        }
    }

    private void showCreateGroupDialog() {
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Create New Group");
        dialog.setHeaderText("Enter group name and select members");

        // Set the button types
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Create the group name field
        TextField groupNameField = new TextField();
        groupNameField.setPromptText("Group Name");

        // Create the member selection list
        ListView<String> memberSelection = new ListView<>(contacts);
        memberSelection.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Add components to dialog
        VBox content = new VBox(10);
        content.getChildren().addAll(
            new Label("Group Name:"),
            groupNameField,
            new Label("Select Members:"),
            memberSelection
        );
        dialog.getDialogPane().setContent(content);

        // Convert the result to a list of selected members
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String groupName = groupNameField.getText().trim();
                if (!groupName.isEmpty() && !memberSelection.getSelectionModel().getSelectedItems().isEmpty()) {
                    List<String> selectedMembers = new ArrayList<>(memberSelection.getSelectionModel().getSelectedItems());
                    chatClient.createGroup(groupName, selectedMembers);
                    loadGroups();
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void sendMessage() {
        String message = messageInput.getText().trim();
        if (message.isEmpty()) return;
        
        // Set auto-scroll to true when sending a new message
        shouldAutoScroll = true;
        
        if (currentContact != null) {
            chatClient.sendMessage(currentContact, message);
        } else if (currentGroupId != null) {
            chatClient.sendGroupMessage(currentGroupId, message);
            // Force update of group messages after sending
            List<GroupMessage> messages = dbManager.getGroupChatHistory(currentGroupId);
            chatArea.getChildren().clear();
            for (GroupMessage msg : messages) {
                addGroupMessageToChat(msg.getSenderEmail(), msg.getMessage());
            }
        }
        
        messageInput.clear();
    }

    private void updateStatusLabel() {
        if (currentContact != null) {
            boolean isOnline = chatClient.isUserOnline(currentContact);
            statusLabel.setText(currentContact + " is " + (isOnline ? "online" : "offline"));
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isOnline ? "green" : "gray") + ";");
        } else if (currentGroupId != null) {
            statusLabel.setText("Group Chat");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
        } else {
            statusLabel.setText("Select a contact or group to chat");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");
        }
    }

    private Integer getGroupIdFromName(String groupNameWithId) {
        try {
            return Integer.parseInt(groupNameWithId.substring(groupNameWithId.lastIndexOf("(") + 1, groupNameWithId.lastIndexOf(")")));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidEmail(String email) {
        // Pattern for something345@domain.something format
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z]+\\.[A-Za-z]+$");
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showNotification(String message) {
        Platform.runLater(() -> {
            // Play sound if available
            if (notificationPlayer != null) {
                notificationPlayer.stop();
                notificationPlayer.play();
            }

            // Show notification window
            notificationLabel.setText(message);
            notificationStage.setX(10);
            notificationStage.setY(10);
            notificationStage.show();

            // Hide notification after 3 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    Platform.runLater(() -> notificationStage.hide());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        });
    }

    private String getGroupNameById(int groupId) {
        for (Group group : userGroups) {
            if (group.getId() == groupId) {
                return group.getName();
            }
        }
        return "Group";
    }

    private void removeSelectedGroup() {
        String selected = groupsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // Extract group ID from the selected item (format: "GroupName (ID)")
            try {
                int groupId = Integer.parseInt(selected.substring(selected.lastIndexOf("(") + 1, selected.lastIndexOf(")")));
                
                // Confirm deletion
                Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                confirmDialog.setTitle("Confirm Group Removal");
                confirmDialog.setHeaderText("Remove Group");
                confirmDialog.setContentText("Are you sure you want to remove this group? This action cannot be undone.");
                
                confirmDialog.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        List<String> groupMembers = dbManager.removeGroup(groupId);
                        if (groupMembers != null) {
                            // Clear chat area if the removed group was currently selected
                            if (currentGroupId != null && currentGroupId == groupId) {
                                currentGroupId = null;
                                chatArea.getChildren().clear();
                                statusLabel.setText("Select a contact or group to chat");
                                statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");
                            }
                            
                            // Notify all group members about the deletion
                            for (String member : groupMembers) {
                                if (!member.equals(userEmail)) {  // Don't notify ourselves
                                    chatClient.notifyGroupDeletion(member, groupId);
                                }
                            }
                            
                            // Reload groups
                            loadGroups();
                            showAlert("Success", "Group removed successfully");
                        } else {
                            showAlert("Error", "Failed to remove group");
                        }
                    }
                });
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid group selection");
            }
        } else {
            showAlert("Error", "Please select a group to remove");
        }
    }

    private void attachFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        File selectedFile = fileChooser.showOpenDialog(null);
        
        if (selectedFile != null) {
            try {
                // Read file data
                byte[] fileData = Files.readAllBytes(selectedFile.toPath());
                String fileName = selectedFile.getName();
                String fileType = Files.probeContentType(selectedFile.toPath());
                if (fileType == null) {
                    fileType = "application/octet-stream";
                }
                
                // Save file to database
                int fileId = dbManager.saveFile(fileName, fileType, fileData, userEmail);
                if (fileId != -1) {
                    // Send message with file
                    if (currentContact != null) {
                        chatClient.sendMessageWithFile(currentContact, "File: " + fileName, fileId);
                        // Add message to chat immediately
                        addMessageToChat(userEmail, "File: " + fileName);
                    } else if (currentGroupId != null) {
                        chatClient.sendGroupMessageWithFile(currentGroupId, "File: " + fileName, fileId);
                        // Add message to chat immediately
                        addGroupMessageToChat(userEmail, "File: " + fileName);
                    }
                    messageInput.clear();
                } else {
                    showAlert("Error", "Failed to save file");
                }
            } catch (IOException e) {
                showAlert("Error", "Failed to read file: " + e.getMessage());
            }
        }
    }

    private void downloadFile(String fileName) {
        // Get the message that contains this file
        List<Message> messages = dbManager.getChatHistory(userEmail, currentContact);
        for (Message message : messages) {
            if (message.getMessage().equals("File: " + fileName)) {
                List<Integer> fileIds = dbManager.getMessageFiles(message.getId(), false);
                if (!fileIds.isEmpty()) {
                    FileData fileData = dbManager.getFile(fileIds.get(0));
                    if (fileData != null) {
                        FileChooser fileChooser = new FileChooser();
                        fileChooser.setTitle("Save File");
                        fileChooser.setInitialFileName(fileName);
                        File saveFile = fileChooser.showSaveDialog(null);
                        
                        if (saveFile != null) {
                            try {
                                Files.write(saveFile.toPath(), fileData.getFileData());
                                showAlert("Success", "File downloaded successfully");
                            } catch (IOException e) {
                                showAlert("Error", "Failed to save file: " + e.getMessage());
                            }
                        }
                    }
                }
                break;
            }
        }
    }

    private void downloadAllHistory() {
        // Create directory structure
        String baseDir = "C:\\Users\\Pc\\Desktop\\final\\Chat_history" + File.separator + userEmail;
        
        if (dbManager.exportAllChatHistory(userEmail, baseDir)) {
            showAlert("Success", "All chat history downloaded to: " + baseDir);
        } else {
            showAlert("Error", "Failed to download chat history");
        }
    }

    // Add scroll listener to detect when user manually scrolls
    private void setupScrollListener() {
        chatScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            // If user scrolls up, disable auto-scroll
            if (newVal.doubleValue() < 1.0) {
                shouldAutoScroll = false;
            }
            // If user scrolls to bottom, enable auto-scroll
            else if (newVal.doubleValue() == 1.0) {
                shouldAutoScroll = true;
            }
        });
    }

    private boolean isScrolledToBottom() {
        double value = chatScrollPane.getVvalue();
        double max = chatScrollPane.getVmax();
        return Math.abs(value - max) < 0.001; // Account for floating-point precision
    }

    private void scrollToBottom() {
        // Use Platform.runLater to ensure this happens after the layout is updated
        Platform.runLater(() -> {
            chatScrollPane.setVvalue(chatScrollPane.getVmax());
        });
    }

    private void updateGroupUnreadCount(int groupId, int count) {
        unreadGroupMessages.put(groupId, count);
        
        loadGroups();
    }

    private void resetGroupUnreadCount(int groupId) {
        unreadGroupMessages.put(groupId, 0);
        // Update the groups list to remove the unread count
        loadGroups();
    }

    public static void main(String[] args) {
        launch(args);
    }
} 