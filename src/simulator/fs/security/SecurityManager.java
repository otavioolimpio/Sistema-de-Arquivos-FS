package simulator.fs.security;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

public class SecurityManager {
    private final Map<String, User> users = new LinkedHashMap<>();
    private final List<String> securityLogs = new ArrayList<>();
    private User currentUser;

    private static final List<String> MALWARE_SIGNATURES = Arrays.asList(
            "MALWARE", "TROJAN", "RANSOMWARE", "EICAR", "0xDEAD"
    );

    public SecurityManager() {
        // Usuários padrão do sistema
        registerUser("root", "admin", "root123", ExecutionMode.MODO_KERNEL);
        registerUser("carlos", "devs", "senha12345", ExecutionMode.MODO_USUARIO);
        this.currentUser = users.get("root"); // Inicializa em modo kernel para montagem do sistema
    }

    public void registerUser(String username, String group, String rawPassword, ExecutionMode mode) {
        String salt = generateSalt();
        String hash = hashPassword(rawPassword, salt);
        users.put(username, new User(username, group, hash, salt, mode));
    }

    public boolean authenticate(String username, String rawPassword) {
        User user = users.get(username);
        if (user == null) return false;

        String candidateHash = hashPassword(rawPassword, user.getSalt());
        if (candidateHash.equals(user.getPasswordHash())) {
            this.currentUser = user;
            return true;
        }
        logSecurityEvent("Falha de autenticação para o usuário '" + username + "'.");
        return false;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isKernelMode() {
        return currentUser != null && currentUser.getMode() == ExecutionMode.MODO_KERNEL;
    }

    public void logSecurityEvent(String event) {
        securityLogs.add(event);
    }

    public List<String> getSecurityLogs() {
        return Collections.unmodifiableList(securityLogs);
    }

    // --- HASHING COM SALT (SHA-256) ---
    private String generateSalt() {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        return bytesToHex(saltBytes);
    }

    public String hashPassword(String password, String salt) {
        return sha256Hex((salt + password).getBytes(StandardCharsets.UTF_8));
    }

    public String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao calcular SHA-256", e);
        }
    }

    // --- CRIPTOGRAFIA EM REPOUSO (AES-128) ---
    public byte[] encrypt(byte[] plainData, String secretKey) {
        try {
            SecretKeySpec keySpec = deriveAesKey(secretKey);
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return cipher.doFinal(plainData);
        } catch (Exception e) {
            throw new RuntimeException("Erro na criptografia dos dados", e);
        }
    }

    public byte[] decrypt(byte[] cipherData, String secretKey) {
        try {
            SecretKeySpec keySpec = deriveAesKey(secretKey);
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return cipher.doFinal(cipherData);
        } catch (Exception e) {
            throw new RuntimeException("Erro na descriptografia: chave inválida ou dados corrompidos", e);
        }
    }

    private SecretKeySpec deriveAesKey(String secretKey) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = Arrays.copyOf(sha.digest(secretKey.getBytes(StandardCharsets.UTF_8)), 16);
        return new SecretKeySpec(keyBytes, "AES");
    }

    // --- VERIFICAÇÃO DE ASSINATURAS MALICIOSAS ---
    public boolean containsMalwareSignature(byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8).toUpperCase();
        for (String sig : MALWARE_SIGNATURES) {
            if (content.contains(sig)) {
                return true;
            }
        }
        return false;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}