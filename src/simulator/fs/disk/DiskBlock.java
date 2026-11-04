package simulator.fs.disk;

public class DiskBlock {
    private final int index;
    private BlockType type = BlockType.LIVRE;
    private String label = "";
    private String ownerPath = "";
    private int bytesUsed = 0;

    public DiskBlock(int index) {
        this.index = index;
    }

    public int getIndex() { return index; }
    public BlockType getType() { return type; }
    public void setType(BlockType type) { this.type = type; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getOwnerPath() { return ownerPath; }
    public void setOwnerPath(String ownerPath) { this.ownerPath = ownerPath; }
    public int getBytesUsed() { return bytesUsed; }
    public void setBytesUsed(int bytesUsed) { this.bytesUsed = bytesUsed; }
}