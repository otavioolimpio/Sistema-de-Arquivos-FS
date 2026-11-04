package simulator.fs.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FSNode {
    private final String name;
    private final boolean isDir;
    private FSNode parent;
    private final Map<String, FSNode> children = new LinkedHashMap<>();
    private int sizeBytes = 0;
    private int inodeIdx = -1;
    private List<Integer> dataBlocks = new ArrayList<>();

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