package simulator.fs.security;

public class User {
    private final String username;
    private final String group;
    private final String passwordHash;
    private final String salt;
    private final ExecutionMode mode;

    public User(String username, String group, String passwordHash, String salt, ExecutionMode mode) {
        this.username = username;
        this.group = group;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.mode = mode;
    }

    public String getUsername() { return username; }
    public String getGroup() { return group; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt() { return salt; }
    public ExecutionMode getMode() { return mode; }
}