package simulator.fs.memory;

public class FrameEntry {
    private final int frameNumber;
    private boolean occupied = false;
    private int pid = -1;
    private String processName = "";
    private int pageNumber = -1;
    private long lastAccessTimestamp = 0;
    private long idleMilliseconds = 0;

    public FrameEntry(int frameNumber) {
        this.frameNumber = frameNumber;
    }

    public int getFrameNumber() { return frameNumber; }
    public boolean isOccupied() { return occupied; }
    public void setOccupied(boolean occupied) { this.occupied = occupied; }
    public int getPid() { return pid; }
    public void setPid(int pid) { this.pid = pid; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
    public long getLastAccessTimestamp() { return lastAccessTimestamp; }
    public void setLastAccessTimestamp(long lastAccessTimestamp) { this.lastAccessTimestamp = lastAccessTimestamp; }
    public long getIdleMilliseconds() { return idleMilliseconds; }
    public void setIdleMilliseconds(long idleMilliseconds) { this.idleMilliseconds = idleMilliseconds; }
}