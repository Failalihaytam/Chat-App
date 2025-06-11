package com.chatapp.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.chatapp.security.CryptoManager;
import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:chat.db";
    private static final String SECRET_KEY = "ThisIsASecretKey123"; // In production, use a secure key management system
    private static DatabaseManager instance;
    private Connection connection;
    private CryptoManager cryptoManager;

    private DatabaseManager() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(DB_URL);
            connection.setAutoCommit(true); // Set auto-commit to true by default
            cryptoManager = new CryptoManager();
            createTables();
            updateExistingUsers(); // Update existing users with public keys
        } catch (Exception e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
            connection.setAutoCommit(true);
        }
        return connection;
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            // Check if users table exists and add public_key column if needed
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                "email TEXT PRIMARY KEY, " +
                "password TEXT NOT NULL, " +
                "salt TEXT NOT NULL, " +
                "public_key TEXT)");

            // Add salt column if it doesn't exist
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN salt TEXT");
            } catch (SQLException e) {
                // Column might already exist, ignore the error
            }

            // Add public_key column if it doesn't exist
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN public_key TEXT");
            } catch (SQLException e) {
                // Column might already exist, ignore the error
            }

            // Create messages table with signature
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender_email TEXT NOT NULL, " +
                "receiver_email TEXT NOT NULL, " +
                "message TEXT NOT NULL, " +
                "signature TEXT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "is_read BOOLEAN DEFAULT 0, " +
                "FOREIGN KEY (sender_email) REFERENCES users(email), " +
                "FOREIGN KEY (receiver_email) REFERENCES users(email))");

            // Add signature column if it doesn't exist
            try {
                stmt.execute("ALTER TABLE messages ADD COLUMN signature TEXT");
            } catch (SQLException e) {
                // Column might already exist, ignore the error
            }

            // Create contacts table
            stmt.execute("CREATE TABLE IF NOT EXISTS contacts (" +
                "user_email TEXT NOT NULL, " +
                "contact_email TEXT NOT NULL, " +
                "PRIMARY KEY (user_email, contact_email), " +
                "FOREIGN KEY (user_email) REFERENCES users(email), " +
                "FOREIGN KEY (contact_email) REFERENCES users(email))");

            // Create groups table
            stmt.execute("CREATE TABLE IF NOT EXISTS groups (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "created_by TEXT NOT NULL, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (created_by) REFERENCES users(email))");

            // Create group_members table
            stmt.execute("CREATE TABLE IF NOT EXISTS group_members (" +
                "group_id INTEGER NOT NULL, " +
                "member_email TEXT NOT NULL, " +
                "PRIMARY KEY (group_id, member_email), " +
                "FOREIGN KEY (group_id) REFERENCES groups(id), " +
                "FOREIGN KEY (member_email) REFERENCES users(email))");

            // Create group_messages table with signature
            stmt.execute("CREATE TABLE IF NOT EXISTS group_messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "group_id INTEGER NOT NULL, " +
                "sender_email TEXT NOT NULL, " +
                "message TEXT NOT NULL, " +
                "signature TEXT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (group_id) REFERENCES groups(id), " +
                "FOREIGN KEY (sender_email) REFERENCES users(email))");

            // Add signature column if it doesn't exist
            try {
                stmt.execute("ALTER TABLE group_messages ADD COLUMN signature TEXT");
            } catch (SQLException e) {
                // Column might already exist, ignore the error
            }

            // Create files table
            stmt.execute("CREATE TABLE IF NOT EXISTS files (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "file_name TEXT NOT NULL, " +
                "file_type TEXT NOT NULL, " +
                "file_data BLOB NOT NULL, " +
                "uploaded_by TEXT NOT NULL, " +
                "uploaded_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (uploaded_by) REFERENCES users(email))");

            // Create file_messages table
            stmt.execute("CREATE TABLE IF NOT EXISTS file_messages (" +
                "message_id INTEGER NOT NULL, " +
                "file_id INTEGER NOT NULL, " +
                "PRIMARY KEY (message_id, file_id), " +
                "FOREIGN KEY (message_id) REFERENCES messages(id), " +
                "FOREIGN KEY (file_id) REFERENCES files(id))");

            // Create group_file_messages table
            stmt.execute("CREATE TABLE IF NOT EXISTS group_file_messages (" +
                "message_id INTEGER NOT NULL, " +
                "file_id INTEGER NOT NULL, " +
                "PRIMARY KEY (message_id, file_id), " +
                "FOREIGN KEY (message_id) REFERENCES group_messages(id), " +
                "FOREIGN KEY (file_id) REFERENCES files(id))");

            // Create contact_requests table
            stmt.execute("CREATE TABLE IF NOT EXISTS contact_requests (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender_email TEXT NOT NULL, " +
                "receiver_email TEXT NOT NULL, " +
                "status TEXT DEFAULT 'PENDING', " + // PENDING, ACCEPTED, DECLINED
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (sender_email) REFERENCES users(email), " +
                "FOREIGN KEY (receiver_email) REFERENCES users(email), " +
                "UNIQUE(sender_email, receiver_email))");
        } catch (SQLException e) {
            System.err.println("Error creating tables: " + e.getMessage());
        }
    }

    public boolean registerUser(String email, String password) {
        try {
            // Generate salt and hash password
            String salt = generateSalt();
            String hashedPassword = hashPassword(password, salt);
            
            // Get public key from crypto manager
            String publicKey = cryptoManager.getPublicKey();
            
            String sql = "INSERT INTO users (email, password, salt, public_key) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, email);
                pstmt.setString(2, hashedPassword);
                pstmt.setString(3, salt);
                pstmt.setString(4, publicKey);
                pstmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error registering user: " + e.getMessage());
            return false;
        }
    }

    public boolean authenticateUser(String email, String password) {
        try {
            String sql = "SELECT password, salt FROM users WHERE email = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, email);
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    String storedPassword = rs.getString("password");
                    String salt = rs.getString("salt");
                    if (salt == null) {
                        // If salt is null, this is an old user record - update it
                        salt = generateSalt();
                        String hashedPassword = hashPassword(password, salt);
                        String updateSql = "UPDATE users SET salt = ?, password = ? WHERE email = ?";
                        try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                            updateStmt.setString(1, salt);
                            updateStmt.setString(2, hashedPassword);
                            updateStmt.setString(3, email);
                            updateStmt.executeUpdate();
                        }
                        return true;
                    }
                    String hashedPassword = hashPassword(password, salt);
                    return storedPassword.equals(hashedPassword);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error authenticating user: " + e.getMessage());
        }
        return false;
    }

    private boolean userExists(String email) {
        try {
            String sql = "SELECT 1 FROM users WHERE email = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, email);
                ResultSet rs = pstmt.executeQuery();
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking user existence: " + e.getMessage());
            return false;
        }
    }

    private String generateSalt() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] salt = new byte[16];
            new java.security.SecureRandom().nextBytes(salt);
            return Base64.getEncoder().encodeToString(salt);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating salt", e);
        }
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes());
            byte[] hashedPassword = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hashedPassword);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    public boolean addContact(String userEmail, String contactEmail) {
        if (!userExists(contactEmail)) {
            return false;
        }

        try {
            String sql = "INSERT INTO contacts (user_email, contact_email) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, userEmail);
                pstmt.setString(2, contactEmail);
                pstmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error adding contact: " + e.getMessage());
            return false;
        }
    }

    public boolean removeContact(String userEmail, String contactEmail) {
        try {
            String sql = "DELETE FROM contacts WHERE user_email = ? AND contact_email = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, userEmail);
                pstmt.setString(2, contactEmail);
                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error removing contact: " + e.getMessage());
            return false;
        }
    }

    public List<String> getContacts(String userEmail) {
        List<String> contacts = new ArrayList<>();
        try {
            String sql = "SELECT contact_email FROM contacts WHERE user_email = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, userEmail);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    contacts.add(rs.getString("contact_email"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting contacts: " + e.getMessage());
        }
        return contacts;
    }

    public boolean isContact(String userEmail, String contactEmail) {
        try {
            String sql = "SELECT 1 FROM contacts WHERE user_email = ? AND contact_email = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, userEmail);
                pstmt.setString(2, contactEmail);
                ResultSet rs = pstmt.executeQuery();
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking contact: " + e.getMessage());
            return false;
        }
    }

    public boolean saveMessage(String sender, String recipient, String message) {
        try {
            if (message == null || message.isEmpty()) {
                return false;
            }
            
            // Encrypt the message
            String encryptedMessage = cryptoManager.encryptMessage(message);
            if (encryptedMessage == null || encryptedMessage.equals(message)) {
                System.err.println("Failed to encrypt message");
                return false;
            }
            
            // Sign the message
            String signature = cryptoManager.signMessage(message);
            if (signature == null) {
                System.err.println("Failed to sign message");
                return false;
            }
            
            String sql = "INSERT INTO messages (sender_email, receiver_email, message, signature) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, sender);
                pstmt.setString(2, recipient);
                pstmt.setString(3, encryptedMessage);
                pstmt.setString(4, signature);
                pstmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error saving message: " + e.getMessage());
            return false;
        }
    }

    private void updateChatHistory(String user1Email, String user2Email, int messageId) {
        try {
            // Try to update existing chat history
            String updateSql = "UPDATE chat_history SET last_message_id = ?, last_message_time = CURRENT_TIMESTAMP " +
                             "WHERE (user1_email = ? AND user2_email = ?) OR (user1_email = ? AND user2_email = ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(updateSql)) {
                pstmt.setInt(1, messageId);
                pstmt.setString(2, user1Email);
                pstmt.setString(3, user2Email);
                pstmt.setString(4, user2Email);
                pstmt.setString(5, user1Email);
                
                if (pstmt.executeUpdate() == 0) {
                    // If no rows were updated, insert new chat history
                    String insertSql = "INSERT INTO chat_history (user1_email, user2_email, last_message_id) VALUES (?, ?, ?)";
                    try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                        insertStmt.setString(1, user1Email);
                        insertStmt.setString(2, user2Email);
                        insertStmt.setInt(3, messageId);
                        insertStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error updating chat history: " + e.getMessage());
        }
    }

    public List<Message> getChatHistory(String userEmail, String contactEmail) {
        List<Message> messages = new ArrayList<>();
        try {
            String sql = "SELECT m.*, u.public_key FROM messages m " +
                        "JOIN users u ON m.sender_email = u.email " +
                        "WHERE (m.sender_email = ? AND m.receiver_email = ?) OR " +
                        "(m.sender_email = ? AND m.receiver_email = ?) " +
                        "ORDER BY m.timestamp ASC";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, userEmail);
                pstmt.setString(2, contactEmail);
                pstmt.setString(3, contactEmail);
                pstmt.setString(4, userEmail);
                
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    // Decrypt the message
                    String encryptedMessage = rs.getString("message");
                    String decryptedMessage = cryptoManager.decryptMessage(encryptedMessage);
                    
                    // Verify the signature
                    String signature = rs.getString("signature");
                    String senderPublicKey = rs.getString("public_key");
                    boolean isVerified = false;
                    
                    if (decryptedMessage != null && !decryptedMessage.equals(encryptedMessage) &&
                        signature != null && senderPublicKey != null) {
                        isVerified = cryptoManager.verifySignature(decryptedMessage, signature, senderPublicKey);
                    }
                    
                    messages.add(new Message(
                        rs.getInt("id"),
                        rs.getString("sender_email"),
                        rs.getString("receiver_email"),
                        decryptedMessage != null ? decryptedMessage : encryptedMessage,
                        rs.getTimestamp("timestamp"),
                        rs.getBoolean("is_read"),
                        isVerified
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting chat history: " + e.getMessage());
        }
        return messages;
    }

    public List<ChatSummary> getChatSummaries(String userEmail) {
        List<ChatSummary> summaries = new ArrayList<>();
        try {
            String sql = "SELECT ch.*, m.message as last_message, m.timestamp as last_message_time, " +
                        "u.email as contact_email " +
                        "FROM chat_history ch " +
                        "JOIN messages m ON ch.last_message_id = m.id " +
                        "JOIN users u ON (ch.user2_email = u.email AND ch.user1_email = ?) OR " +
                        "                (ch.user1_email = u.email AND ch.user2_email = ?) " +
                        "WHERE ch.user1_email = ? OR ch.user2_email = ? " +
                        "ORDER BY m.timestamp DESC";
            
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, userEmail);
                pstmt.setString(2, userEmail);
                pstmt.setString(3, userEmail);
                pstmt.setString(4, userEmail);
                
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    summaries.add(new ChatSummary(
                        rs.getString("contact_email"),
                        rs.getString("last_message"),
                        rs.getTimestamp("last_message_time")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting chat summaries: " + e.getMessage());
        }
        return summaries;
    }

    public boolean createGroup(String groupName, String creatorEmail, List<String> memberEmails) {
        try {
            connection.setAutoCommit(false);
            
            // Create the group
            String createGroupSQL = "INSERT INTO groups (name, created_by) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(createGroupSQL, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, groupName);
                pstmt.setString(2, creatorEmail);
                pstmt.executeUpdate();
                
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    int groupId = rs.getInt(1);
                    
                    // Add members to the group
                    String addMemberSQL = "INSERT INTO group_members (group_id, member_email) VALUES (?, ?)";
                    try (PreparedStatement memberStmt = connection.prepareStatement(addMemberSQL)) {
                        // Add creator as member
                        memberStmt.setInt(1, groupId);
                        memberStmt.setString(2, creatorEmail);
                        memberStmt.executeUpdate();
                        
                        // Add other members
                        for (String memberEmail : memberEmails) {
                            memberStmt.setInt(1, groupId);
                            memberStmt.setString(2, memberEmail);
                            memberStmt.executeUpdate();
                            
                            // Add the group to the member's contacts
                            if (!isContact(memberEmail, creatorEmail)) {
                                addContact(memberEmail, creatorEmail);
                            }
                            
                            // Add all other members as contacts to each other
                            for (String otherMember : memberEmails) {
                                if (!otherMember.equals(memberEmail) && !isContact(memberEmail, otherMember)) {
                                    addContact(memberEmail, otherMember);
                                }
                            }
                        }
                    }
                }
            }
            
            connection.commit();
            return true;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                System.err.println("Error rolling back transaction: " + ex.getMessage());
            }
            System.err.println("Error creating group: " + e.getMessage());
            return false;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println("Error resetting auto-commit: " + e.getMessage());
            }
        }
    }

    public List<Group> getUserGroups(String userEmail) {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT g.id, g.name, g.created_by, g.created_at " +
                    "FROM groups g " +
                    "JOIN group_members gm ON g.id = gm.group_id " +
                    "WHERE gm.member_email = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userEmail);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                groups.add(new Group(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("created_by"),
                    rs.getTimestamp("created_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting user groups: " + e.getMessage());
        }
        return groups;
    }

    public List<String> getGroupMembers(int groupId) {
        List<String> members = new ArrayList<>();
        String sql = "SELECT member_email FROM group_members WHERE group_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                members.add(rs.getString("member_email"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting group members: " + e.getMessage());
        }
        return members;
    }

    public boolean saveGroupMessage(int groupId, String senderEmail, String message) {
        String sql = "INSERT INTO group_messages (group_id, sender_email, message) VALUES (?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setString(2, senderEmail);
            pstmt.setString(3, message);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error saving group message: " + e.getMessage());
            return false;
        }
    }

    public List<GroupMessage> getGroupChatHistory(int groupId) {
        List<GroupMessage> messages = new ArrayList<>();
        String sql = "SELECT * FROM group_messages WHERE group_id = ? ORDER BY timestamp";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                messages.add(new GroupMessage(
                    rs.getInt("id"),
                    rs.getInt("group_id"),
                    rs.getString("sender_email"),
                    rs.getString("message"),
                    rs.getTimestamp("timestamp")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting group chat history: " + e.getMessage());
        }
        return messages;
    }

    public List<String> removeGroup(int groupId) {
        List<String> groupMembers = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            // Start transaction
            conn.setAutoCommit(false);
            
            try {
                // First, get all group members
                String getMembersSQL = "SELECT member_email FROM group_members WHERE group_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(getMembersSQL)) {
                    pstmt.setInt(1, groupId);
                    ResultSet rs = pstmt.executeQuery();
                    while (rs.next()) {
                        groupMembers.add(rs.getString("member_email"));
                    }
                }
                
                // Then, remove all group messages
                String deleteMessagesSQL = "DELETE FROM group_messages WHERE group_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteMessagesSQL)) {
                    pstmt.setInt(1, groupId);
                    pstmt.executeUpdate();
                }
                
                // Then, remove all group members
                String deleteMembersSQL = "DELETE FROM group_members WHERE group_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteMembersSQL)) {
                    pstmt.setInt(1, groupId);
                    pstmt.executeUpdate();
                }
                
                // Finally, remove the group itself
                String deleteGroupSQL = "DELETE FROM groups WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteGroupSQL)) {
                    pstmt.setInt(1, groupId);
                    pstmt.executeUpdate();
                }
                
                // Commit transaction
                conn.commit();
                return groupMembers;
            } catch (SQLException e) {
                // Rollback transaction on error
                conn.rollback();
                System.err.println("Error removing group: " + e.getMessage());
                return null;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error removing group: " + e.getMessage());
            return null;
        }
    }

    public int saveFile(String fileName, String fileType, byte[] fileData, String uploadedBy) {
        String sql = "INSERT INTO files (file_name, file_type, file_data, uploaded_by) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, fileName);
            pstmt.setString(2, fileType);
            pstmt.setBytes(3, fileData);
            pstmt.setString(4, uploadedBy);
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error saving file: " + e.getMessage());
        }
        return -1;
    }

    public FileData getFile(int fileId) {
        String sql = "SELECT * FROM files WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, fileId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new FileData(
                    rs.getInt("id"),
                    rs.getString("file_name"),
                    rs.getString("file_type"),
                    rs.getBytes("file_data"),
                    rs.getString("uploaded_by"),
                    rs.getTimestamp("uploaded_at")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error getting file: " + e.getMessage());
        }
        return null;
    }

    public boolean linkFileToMessage(int messageId, int fileId, boolean isGroupMessage) {
        String sql = isGroupMessage ? 
            "INSERT INTO group_file_messages (message_id, file_id) VALUES (?, ?)" :
            "INSERT INTO file_messages (message_id, file_id) VALUES (?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            pstmt.setInt(2, fileId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error linking file to message: " + e.getMessage());
            return false;
        }
    }

    public List<Integer> getMessageFiles(int messageId, boolean isGroupMessage) {
        List<Integer> fileIds = new ArrayList<>();
        String sql = isGroupMessage ?
            "SELECT file_id FROM group_file_messages WHERE message_id = ?" :
            "SELECT file_id FROM file_messages WHERE message_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                fileIds.add(rs.getInt("file_id"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting message files: " + e.getMessage());
        }
        return fileIds;
    }

    public static class Message {
        private final int id;
        private final String senderEmail;
        private final String receiverEmail;
        private final String message;
        private final Timestamp timestamp;
        private final boolean isRead;
        private final boolean isVerified;

        public Message(int id, String senderEmail, String receiverEmail, String message, Timestamp timestamp, boolean isRead, boolean isVerified) {
            this.id = id;
            this.senderEmail = senderEmail;
            this.receiverEmail = receiverEmail;
            this.message = message;
            this.timestamp = timestamp;
            this.isRead = isRead;
            this.isVerified = isVerified;
        }

        // Getters
        public int getId() { return id; }
        public String getSenderEmail() { return senderEmail; }
        public String getReceiverEmail() { return receiverEmail; }
        public String getMessage() { return message; }
        public Timestamp getTimestamp() { return timestamp; }
        public boolean isRead() { return isRead; }
        public boolean isVerified() { return isVerified; }
    }

    public static class ChatSummary {
        private final String contactEmail;
        private final String lastMessage;
        private final Timestamp lastMessageTime;

        public ChatSummary(String contactEmail, String lastMessage, Timestamp lastMessageTime) {
            this.contactEmail = contactEmail;
            this.lastMessage = lastMessage;
            this.lastMessageTime = lastMessageTime;
        }

        // Getters
        public String getContactEmail() { return contactEmail; }
        public String getLastMessage() { return lastMessage; }
        public Timestamp getLastMessageTime() { return lastMessageTime; }
    }

    public static class Group {
        private final int id;
        private final String name;
        private final String createdBy;
        private final Timestamp createdAt;

        public Group(int id, String name, String createdBy, Timestamp createdAt) {
            this.id = id;
            this.name = name;
            this.createdBy = createdBy;
            this.createdAt = createdAt;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getCreatedBy() { return createdBy; }
        public Timestamp getCreatedAt() { return createdAt; }
    }

    public static class GroupMessage {
        private final int id;
        private final int groupId;
        private final String senderEmail;
        private final String message;
        private final Timestamp timestamp;

        public GroupMessage(int id, int groupId, String senderEmail, String message, Timestamp timestamp) {
            this.id = id;
            this.groupId = groupId;
            this.senderEmail = senderEmail;
            this.message = message;
            this.timestamp = timestamp;
        }

        public int getId() { return id; }
        public int getGroupId() { return groupId; }
        public String getSenderEmail() { return senderEmail; }
        public String getMessage() { return message; }
        public Timestamp getTimestamp() { return timestamp; }
    }

    public static class FileData {
        private final int id;
        private final String fileName;
        private final String fileType;
        private final byte[] fileData;
        private final String uploadedBy;
        private final Timestamp uploadedAt;

        public FileData(int id, String fileName, String fileType, byte[] fileData, String uploadedBy, Timestamp uploadedAt) {
            this.id = id;
            this.fileName = fileName;
            this.fileType = fileType;
            this.fileData = fileData;
            this.uploadedBy = uploadedBy;
            this.uploadedAt = uploadedAt;
        }

        public int getId() { return id; }
        public String getFileName() { return fileName; }
        public String getFileType() { return fileType; }
        public byte[] getFileData() { return fileData; }
        public String getUploadedBy() { return uploadedBy; }
        public Timestamp getUploadedAt() { return uploadedAt; }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }

    private void updateExistingUsers() {
        try {
            // Get all users without public keys
            String sql = "SELECT email FROM users WHERE public_key IS NULL";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String email = rs.getString("email");
                    // Generate a new key pair for the user
                    String publicKey = cryptoManager.getPublicKey();
                    
                    // Update the user with the public key
                    String updateSql = "UPDATE users SET public_key = ? WHERE email = ?";
                    try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                        updateStmt.setString(1, publicKey);
                        updateStmt.setString(2, email);
                        updateStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error updating existing users: " + e.getMessage());
        }
    }

    public boolean exportChatHistory(String userEmail, String contactEmail, String filePath) {
        try {
            List<Message> messages = getChatHistory(userEmail, contactEmail);
            StringBuilder history = new StringBuilder();
            history.append("Chat History between ").append(userEmail).append(" and ").append(contactEmail).append("\n\n");
            
            for (Message message : messages) {
                String timestamp = message.getTimestamp().toString();
                String sender = message.getSenderEmail();
                String content = message.getMessage();
                String verification = message.isVerified() ? "[Verified]" : "[Unverified]";
                
                history.append("[").append(timestamp).append("] ")
                      .append(sender).append(": ")
                      .append(content).append(" ")
                      .append(verification).append("\n");
            }
            
            // Create parent directories if they don't exist
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            
            // Write to file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(history.toString());
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error exporting chat history: " + e.getMessage());
            return false;
        }
    }

    public boolean exportGroupChatHistory(int groupId, String filePath) {
        try {
            List<GroupMessage> messages = getGroupChatHistory(groupId);
            StringBuilder history = new StringBuilder();
            history.append("Group Chat History for Group ID: ").append(groupId).append("\n\n");
            
            for (GroupMessage message : messages) {
                String timestamp = message.getTimestamp().toString();
                String sender = message.getSenderEmail();
                String content = message.getMessage();
                
                history.append("[").append(timestamp).append("] ")
                      .append(sender).append(": ")
                      .append(content).append("\n");
            }
            
            // Create parent directories if they don't exist
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            
            // Write to file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(history.toString());
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error exporting group chat history: " + e.getMessage());
            return false;
        }
    }

    public boolean exportAllChatHistory(String userEmail, String baseDir) {
        try {
            // Create base directory
            File baseDirectory = new File(baseDir);
            baseDirectory.mkdirs();

            // Export individual chats
            List<String> contacts = getContacts(userEmail);
            for (String contact : contacts) {
                String contactDir = baseDir + File.separator + contact;
                String filePath = contactDir + File.separator + "history.txt";
                exportChatHistory(userEmail, contact, filePath);
            }

            // Export group chats
            List<Group> groups = getUserGroups(userEmail);
            String groupDir = baseDir + File.separator + "Groups";
            for (Group group : groups) {
                String filePath = groupDir + File.separator + "group_" + group.getId() + "_history.txt";
                exportGroupChatHistory(group.getId(), filePath);
            }

            return true;
        } catch (Exception e) {
            System.err.println("Error exporting all chat history: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteMessage(int messageId) {
        String sql = "DELETE FROM messages WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error deleting message: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteGroupMessage(int messageId) {
        String sql = "DELETE FROM group_messages WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error deleting group message: " + e.getMessage());
            return false;
        }
    }

    // Add new methods for contact requests
    public boolean sendContactRequest(String senderEmail, String receiverEmail) {
        if (!userExists(receiverEmail)) {
            return false;
        }

        try {
            String sql = "INSERT INTO contact_requests (sender_email, receiver_email) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, senderEmail);
                pstmt.setString(2, receiverEmail);
                pstmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error sending contact request: " + e.getMessage());
            return false;
        }
    }

    public boolean acceptContactRequest(String senderEmail, String receiverEmail) {
        try {
            connection.setAutoCommit(false);
            
            // Update request status
            String updateSql = "UPDATE contact_requests SET status = 'ACCEPTED' " +
                             "WHERE sender_email = ? AND receiver_email = ? AND status = 'PENDING'";
            try (PreparedStatement pstmt = connection.prepareStatement(updateSql)) {
                pstmt.setString(1, senderEmail);
                pstmt.setString(2, receiverEmail);
                int rowsAffected = pstmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    // Add contact for both users
                    String addContactSql = "INSERT INTO contacts (user_email, contact_email) VALUES (?, ?)";
                    try (PreparedStatement contactStmt = connection.prepareStatement(addContactSql)) {
                        // Add contact for receiver
                        contactStmt.setString(1, receiverEmail);
                        contactStmt.setString(2, senderEmail);
                        contactStmt.executeUpdate();
                        
                        // Add contact for sender
                        contactStmt.setString(1, senderEmail);
                        contactStmt.setString(2, receiverEmail);
                        contactStmt.executeUpdate();
                    }
                }
            }
            
            connection.commit();
            return true;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                System.err.println("Error rolling back transaction: " + ex.getMessage());
            }
            System.err.println("Error accepting contact request: " + e.getMessage());
            return false;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println("Error resetting auto-commit: " + e.getMessage());
            }
        }
    }

    public boolean declineContactRequest(String senderEmail, String receiverEmail) {
        try {
            String sql = "UPDATE contact_requests SET status = 'DECLINED' " +
                        "WHERE sender_email = ? AND receiver_email = ? AND status = 'PENDING'";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, senderEmail);
                pstmt.setString(2, receiverEmail);
                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error declining contact request: " + e.getMessage());
            return false;
        }
    }

    public List<ContactRequest> getPendingContactRequests(String userEmail) {
        List<ContactRequest> requests = new ArrayList<>();
        String sql = "SELECT * FROM contact_requests WHERE receiver_email = ? AND status = 'PENDING'";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userEmail);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                requests.add(new ContactRequest(
                    rs.getInt("id"),
                    rs.getString("sender_email"),
                    rs.getString("receiver_email"),
                    rs.getString("status"),
                    rs.getTimestamp("timestamp")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting pending contact requests: " + e.getMessage());
        }
        return requests;
    }

    public static class ContactRequest {
        private final int id;
        private final String senderEmail;
        private final String receiverEmail;
        private final String status;
        private final Timestamp timestamp;

        public ContactRequest(int id, String senderEmail, String receiverEmail, String status, Timestamp timestamp) {
            this.id = id;
            this.senderEmail = senderEmail;
            this.receiverEmail = receiverEmail;
            this.status = status;
            this.timestamp = timestamp;
        }

        public int getId() { return id; }
        public String getSenderEmail() { return senderEmail; }
        public String getReceiverEmail() { return receiverEmail; }
        public String getStatus() { return status; }
        public Timestamp getTimestamp() { return timestamp; }
    }
} 