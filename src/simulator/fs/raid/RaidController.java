package simulator.fs.raid;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RaidController {
    private final RaidType raidType;
    private final int numDisks;
    private final int blockSizeBytes;
    private final List<PhysicalDisk> disks;

    public RaidController(RaidType raidType, int numDisks, int blockSizeBytes) {
        if (raidType == RaidType.RAID5 && numDisks < 3) {
            throw new IllegalArgumentException("RAID 5 requer no mínimo 3 discos.");
        }
        this.raidType = raidType;
        this.numDisks = numDisks;
        this.blockSizeBytes = blockSizeBytes;
        this.disks = new ArrayList<>();
        for (int i = 1; i <= numDisks; i++) {
            disks.add(new PhysicalDisk(i));
        }
    }

    public String writeData(String fileName, byte[] data, int stripeIndex) {
        StringBuilder report = new StringBuilder();
        report.append(String.format("Arranjo Ativo: %s (%d Discos Físicos, Blocos de %d KB)\n\n",
                raidType, numDisks, blockSizeBytes / 1024));
        report.append("[E/S - DISTRIBUIÇÃO EM DISCO]\n");

        if (raidType == RaidType.RAID5) {
            int dataDisksCount = numDisks - 1;
            int parityDiskIndex = (numDisks - 1) - (stripeIndex % numDisks); // Rotação da paridade

            // Dividir dados em blocos para os discos de dados
            byte[][] dataPayloads = new byte[dataDisksCount][blockSizeBytes];
            int bytesRemaining = data.length;
            int offset = 0;

            for (int i = 0; i < dataDisksCount; i++) {
                if (bytesRemaining > 0) {
                    int chunkSize = Math.min(blockSizeBytes, bytesRemaining);
                    System.arraycopy(data, offset, dataPayloads[i], 0, chunkSize);
                    offset += chunkSize;
                    bytesRemaining -= chunkSize;
                }
            }

            // Calcular Paridade via XOR
            byte[] parityBlock = new byte[blockSizeBytes];
            for (int b = 0; b < blockSizeBytes; b++) {
                byte xorValue = 0;
                for (int d = 0; d < dataDisksCount; d++) {
                    xorValue ^= dataPayloads[d][b];
                }
                parityBlock[b] = xorValue;
            }

            // Gravar nos discos
            int currentDataIdx = 0;
            for (int diskIdx = 0; diskIdx < numDisks; diskIdx++) {
                PhysicalDisk disk = disks.get(diskIdx);
                if (diskIdx == parityDiskIndex) {
                    disk.writeBlock(stripeIndex, parityBlock);
                    report.append(String.format("Disco %d: [PARIDADE : XOR(Blocos de Dados do Stripe %d)]\n",
                            disk.getId(), stripeIndex));
                } else {
                    byte[] payload = dataPayloads[currentDataIdx];
                    disk.writeBlock(stripeIndex, payload);
                    String contentStr = new String(payload, StandardCharsets.UTF_8).trim();
                    if (contentStr.isEmpty()) contentStr = "Dados do Arquivo";
                    report.append(String.format("Disco %d: [Bloco %02d: %s - Parte %d (%d KB)]\n",
                            disk.getId(), stripeIndex + 1, contentStr, currentDataIdx + 1, blockSizeBytes / 1024));
                    currentDataIdx++;
                }
            }
        }
        return report.toString();
    }

    public void simulateDiskFailure(int diskId) {
        for (PhysicalDisk disk : disks) {
            if (disk.getId() == diskId) {
                disk.setHealthy(false);
                break;
            }
        }
    }

    public String recoverDataFromFailure(int failedDiskId, int stripeIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[TESTE DE FALHA SIMULADO]\n");
        sb.append(String.format("- Falha forçada no Disco %d!\n", failedDiskId));

        int dataDisksCount = numDisks - 1;
        int parityDiskIndex = (numDisks - 1) - (stripeIndex % numDisks);

        byte[] reconstructedBlock = new byte[blockSizeBytes];

        // Reconstrução via XOR com os discos remanescentes
        List<String> xorOperandNames = new ArrayList<>();
        for (PhysicalDisk disk : disks) {
            if (disk.getId() != failedDiskId) {
                byte[] b = disk.readBlock(stripeIndex);
                for (int i = 0; i < blockSizeBytes; i++) {
                    reconstructedBlock[i] ^= b[i];
                }
                xorOperandNames.add("Disco " + disk.getId());
            }
        }

        String operandsStr = String.join(" XOR ", xorOperandNames);
        sb.append(String.format("- Recuperando Bloco do Disco %d via operação XOR: (%s) -> Reconstrução [OK]\n",
                failedDiskId, operandsStr));

        return sb.toString();
    }
}