package simulator.fs.disk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiskManager {
    private final int totalBlocks;
    private final int blockSize;
    private final DiskBlock[] blocks;

    public DiskManager(int totalBlocks, int blockSize) {
        this.totalBlocks = totalBlocks;
        this.blockSize = blockSize;
        this.blocks = new DiskBlock[totalBlocks];
        for (int i = 0; i < totalBlocks; i++) {
            blocks[i] = new DiskBlock(i);
        }
    }

    public int getTotalBlocks() { return totalBlocks; }
    public int getBlockSize() { return blockSize; }
    public DiskBlock[] getBlocks() { return blocks; }

    public List<Integer> getFreeBlocks() {
        List<Integer> free = new ArrayList<>();
        for (DiskBlock b : blocks) {
            if (b.getType() == BlockType.LIVRE) {
                free.add(b.getIndex());
            }
        }
        return free;
    }

    public AllocationResult allocateIndexed(String path, int sizeBytes, boolean isDir) {
        List<Integer> free = getFreeBlocks();
        String baseName = path.equals("/") ? "RAIZ" : path.substring(path.lastIndexOf('/') + 1);
        String formattedLabel = "INODE_" + baseName.toUpperCase().replace(".", "_");

        if (isDir) {
            if (free.isEmpty()) return new AllocationResult(false, -1, Collections.emptyList());
            int inodeIdx = free.get(0);
            DiskBlock b = blocks[inodeIdx];
            b.setType(BlockType.INODE);
            b.setLabel(formattedLabel);
            b.setOwnerPath(path);
            return new AllocationResult(true, inodeIdx, Collections.emptyList());
        }

        int neededDataBlocks = (int) Math.ceil((double) sizeBytes / blockSize);
        if (neededDataBlocks == 0) neededDataBlocks = 1;
        int neededTotal = 1 + neededDataBlocks;

        if (free.size() < neededTotal) {
            return new AllocationResult(false, -1, Collections.emptyList());
        }

        int inodeIdx = free.get(0);
        List<Integer> dataIndices = new ArrayList<>(free.subList(1, neededTotal));

        // Configura Bloco i-node
        DiskBlock inodeBlock = blocks[inodeIdx];
        inodeBlock.setType(BlockType.INODE);
        inodeBlock.setLabel(formattedLabel);
        inodeBlock.setOwnerPath(path);

        // Configura Blocos de Dados
        int remaining = sizeBytes;
        for (int idx : dataIndices) {
            DiskBlock b = blocks[idx];
            b.setType(BlockType.DADO);
            b.setOwnerPath(path);
            int bytesInBlock = Math.min(blockSize, remaining);
            b.setBytesUsed(bytesInBlock);
            remaining -= bytesInBlock;
        }

        return new AllocationResult(true, inodeIdx, dataIndices);
    }

    public void freeBlocks(int inodeIdx, List<Integer> dataBlocks) {
        List<Integer> toFree = new ArrayList<>();
        if (inodeIdx >= 0) toFree.add(inodeIdx);
        if (dataBlocks != null) toFree.addAll(dataBlocks);

        for (int idx : toFree) {
            if (idx >= 0 && idx < totalBlocks) {
                DiskBlock b = blocks[idx];
                b.setType(BlockType.LIVRE);
                b.setLabel("");
                b.setOwnerPath("");
                b.setBytesUsed(0);
            }
        }
    }
}