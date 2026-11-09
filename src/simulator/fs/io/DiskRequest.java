package simulator.fs.io;

public class DiskRequest {
    private final int track;
    private final String operation; // "READ" ou "WRITE"
    private final String description;

    public DiskRequest(int track, String operation, String description) {
        this.track = track;
        this.operation = operation;
        this.description = description;
    }

    public int getTrack() { return track; }
    public String getOperation() { return operation; }
    public String getDescription() { return description; }
}