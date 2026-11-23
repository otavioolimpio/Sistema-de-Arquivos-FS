package simulator.fs.memory;

public class MemoryBlock {
    private int startKb;
    private int endKb;
    private boolean free;
    private String ownerName;

    public MemoryBlock(int startKb, int endKb, boolean free, String ownerName) {
        this.startKb = startKb;
        this.endKb = endKb;
        this.free = free;
        this.ownerName = ownerName;
    }

    public int getStartKb() { return startKb; }
    public void setStartKb(int startKb) { this.startKb = startKb; }
    public int getEndKb() { return endKb; }
    public void setEndKb(int endKb) { this.endKb = endKb; }
    public int getSizeKb() { return endKb - startKb; }
    public boolean isFree() { return free; }
    public void setFree(boolean free) { this.free = free; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
}