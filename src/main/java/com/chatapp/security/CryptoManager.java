package com.chatapp.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class CryptoManager {
    private static final String DES_ALGORITHM = "DES";
    private static final String ECDSA_ALGORITHM = "EC";
    private static final String ECDSA_SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String CURVE_NAME = "secp256r1";
    private static final String SHARED_SECRET = "ChatAppSecretKey123"; // Shared secret for all clients

    private SecretKey desKey;
    private KeyPair ecdsaKeyPair;

    public CryptoManager() {
        try {
            // Use shared secret key for DES
            byte[] keyBytes = SHARED_SECRET.getBytes();
            desKey = new SecretKeySpec(keyBytes, 0, 8, DES_ALGORITHM);

            // Generate ECDSA key pair
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(ECDSA_ALGORITHM);
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(CURVE_NAME);
            keyPairGen.initialize(ecSpec);
            ecdsaKeyPair = keyPairGen.generateKeyPair();
        } catch (Exception e) {
            System.err.println("Error initializing crypto manager: " + e.getMessage());
        }
    }

    public String encryptMessage(String message) {
        try {
            if (message == null || message.isEmpty()) {
                return message;
            }
            Cipher cipher = Cipher.getInstance(DES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, desKey);
            byte[] encryptedBytes = cipher.doFinal(message.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            System.err.println("Error encrypting message: " + e.getMessage());
            return message;
        }
    }

    public String decryptMessage(String encryptedMessage) {
        try {
            if (encryptedMessage == null || encryptedMessage.isEmpty()) {
                return encryptedMessage;
            }
            Cipher cipher = Cipher.getInstance(DES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, desKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedMessage));
            return new String(decryptedBytes);
        } catch (Exception e) {
            System.err.println("Error decrypting message: " + e.getMessage());
            return encryptedMessage;
        }
    }

    public String signMessage(String message) {
        try {
            if (message == null || message.isEmpty()) {
                return null;
            }
            Signature signature = Signature.getInstance(ECDSA_SIGNATURE_ALGORITHM);
            signature.initSign(ecdsaKeyPair.getPrivate());
            signature.update(message.getBytes());
            byte[] signatureBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            System.err.println("Error signing message: " + e.getMessage());
            return null;
        }
    }

    public boolean verifySignature(String message, String signature, String publicKeyBase64) {
        try {
            if (message == null || signature == null || publicKeyBase64 == null) {
                return false;
            }
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(ECDSA_ALGORITHM);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            Signature sig = Signature.getInstance(ECDSA_SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(message.getBytes());
            return sig.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            System.err.println("Error verifying signature: " + e.getMessage());
            return false;
        }
    }

    public String getPublicKey() {
        return Base64.getEncoder().encodeToString(ecdsaKeyPair.getPublic().getEncoded());
    }

    public String getPrivateKey() {
        return Base64.getEncoder().encodeToString(ecdsaKeyPair.getPrivate().getEncoded());
    }
} 