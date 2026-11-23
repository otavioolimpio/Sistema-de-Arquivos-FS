package simulator.fs.memory;

import java.util.ArrayList;
import java.util.List;

public class ProcessControlBlock {
    private final int pid;
    private final String name;
    private final ProcessType type;
    private final int sizeBytes;
    private final List<PageTableEntry> pageTable = new ArrayList<>();

    public ProcessControlBlock(int pid, String name, ProcessType type, int sizeBytes) {
        this.pid = pid;
        this.name = name;
        this.type = type;
        this.sizeBytes = sizeBytes;
    }

    public int getPid() { return pid; }
    public String getName() { return name; }
    public ProcessType getType() { return type; }
    public int getSizeBytes() { return sizeBytes; }
    public List<PageTableEntry> getPageTable() { return pageTable; }
}