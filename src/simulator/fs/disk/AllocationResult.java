package simulator.fs.disk;

import java.util.List;

public class AllocationResult {
    private final boolean success;
    private final int inodeIdx;
    private final List<Integer> dataBlocks;

    public AllocationResult(boolean success, int inodeIdx, List<Integer> dataBlocks) {
        this.success = success;
        this.inodeIdx = inodeIdx;
        this.dataBlocks = dataBlocks;
    }

    public boolean isSuccess() { return success; }
    public int getInodeIdx() { return inodeIdx; }
    public List<Integer> getDataBlocks() { return dataBlocks; }
}