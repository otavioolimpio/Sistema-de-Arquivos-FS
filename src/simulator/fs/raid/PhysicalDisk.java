package simulator.fs.raid;

import java.util.HashMap;
import java.util.Map;

public class PhysicalDisk {
    private final int id;
    private boolean healthy = true;
    private final Map<Integer, byte[]> blocks = new HashMap<>();

    public PhysicalDisk(int id) {
        this.id = id;
    }

    public int getId() { return id; }
    public boolean isHealthy() { return healthy; }
    public void setHealthy(boolean healthy) { this.healthy = healthy; }

    public void writeBlock(int blockNum, byte[] data) {
        if (!healthy) throw new IllegalStateException("Disco " + id + " está inoperante (falha de hardware).");
        blocks.put(blockNum, data.clone());
    }

    public byte[] readBlock(int blockNum) {
        if (!healthy) throw new IllegalStateException("Disco " + id + " está inoperante (falha de hardware).");
        return blocks.getOrDefault(blockNum, new byte[0]);
    }

    public boolean hasBlock(int blockNum) {
        return healthy && blocks.containsKey(blockNum);
    }
}