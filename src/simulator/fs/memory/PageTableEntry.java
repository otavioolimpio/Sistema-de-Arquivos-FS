package simulator.fs.memory;

public class PageTableEntry {
    private final int pageNumber;
    private final int usedBytes;
    private int frameNumber = -1;
    private boolean present = false;
    private boolean inSwap = true;
    private long lastAccessOrder = 0;

    public PageTableEntry(int pageNumber, int usedBytes) {
        this.pageNumber = pageNumber;
        this.usedBytes = usedBytes;
    }

    public int getPageNumber() { return pageNumber; }
    public int getUsedBytes() { return usedBytes; }
    public int getFrameNumber() { return frameNumber; }
    public void setFrameNumber(int frameNumber) { this.frameNumber = frameNumber; }
    public boolean isPresent() { return present; }
    public void setPresent(boolean present) { this.present = present; }
    public boolean isInSwap() { return inSwap; }
    public void setInSwap(boolean inSwap) { this.inSwap = inSwap; }
    public long getLastAccessOrder() { return lastAccessOrder; }
    public void setLastAccessOrder(long lastAccessOrder) { this.lastAccessOrder = lastAccessOrder; }
}