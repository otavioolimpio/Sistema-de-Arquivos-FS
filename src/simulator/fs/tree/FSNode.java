package simulator.fs.tree;

import java.util.*;

public class FSNode {
    private final String name;
    private final boolean isDir;
    private FSNode parent;
    private final Map<String, FSNode> children = new LinkedHashMap<>();
    private int sizeBytes = 0;
    private int inodeIdx = -1;
    private List<Integer> dataBlocks = new ArrayList<>();

    // Metadados de Segurança (Etapa 3)
    private String owner = "root";
    private String group = "admin";
    private int permissions = 0750; // rwxr-x--- por padrão
    private boolean encrypted = false;
    private String officialHash = "";

    public FSNode(String name, boolean isDir, FSNode parent) {
        this.name = name;
        this.isDir = isDir;
        this.parent = parent;
    }

    public String getName() { return name; }
    public boolean isDir() { return isDir; }
    public FSNode getParent() { return parent; }
    public void setParent(FSNode parent) { this.parent = parent; }
    public Map<String, FSNode> getChildren() { return children; }
    public int getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(int sizeBytes) { this.sizeBytes = sizeBytes; }
    public int getInodeIdx() { return inodeIdx; }
    public void setInodeIdx(int inodeIdx) { this.inodeIdx = inodeIdx; }
    public List<Integer> getDataBlocks() { return dataBlocks; }
    public void setDataBlocks(List<Integer> dataBlocks) { this.dataBlocks = dataBlocks; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public int getPermissions() { return permissions; }
    public void setPermissions(int permissions) { this.permissions = permissions; }
    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
    public String getOfficialHash() { return officialHash; }
    public void setOfficialHash(String officialHash) { this.officialHash = officialHash; }

    public String getFullPath() {
        if (parent == null || parent == this) return "/";
        List<String> segments = new ArrayList<>();
        FSNode curr = this;
        while (curr != null && curr.getParent() != curr && curr.getParent() != null) {
            segments.add(curr.getName());
            curr = curr.getParent();
        }
        Collections.reverse(segments);
        return "/" + String.join("/", segments);
    }
}