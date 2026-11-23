package simulator.fs.core;

import simulator.fs.disk.AllocationResult;
import simulator.fs.disk.BlockType;
import simulator.fs.disk.DiskBlock;
import simulator.fs.disk.DiskManager;
import simulator.fs.io.DiskScheduler;
import simulator.fs.raid.RaidController;
import simulator.fs.raid.RaidType;
import simulator.fs.security.AccessControl;
import simulator.fs.security.ExecutionMode;
import simulator.fs.security.SecurityManager;
import simulator.fs.security.User;
import simulator.fs.tree.FSNode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VirtualFileSystem {
    private final DiskManager disk;
    private final RaidController raid;
    private final SecurityManager security;
    private final FSNode root;
    private FSNode cwd;
    private int currentHeadTrack = 53;

    public VirtualFileSystem(int totalBlocks, int blockSize) {
        this.disk = new DiskManager(totalBlocks, blockSize);
        this.raid = new RaidController(RaidType.RAID5, 3, blockSize);
        this.security = new SecurityManager();

        this.root = new FSNode("", true, null);
        this.root.setParent(this.root);
        this.root.setOwner("root");
        this.root.setGroup("admin");
        this.root.setPermissions(0755);

        AllocationResult res = disk.allocateIndexed("/", 0, true);
        this.root.setInodeIdx(res.getInodeIdx());
        this.cwd = this.root;
    }

    public SecurityManager getSecurity() {
        return security;
    }

    public RaidController getRaid() {
        return raid;
    }

    private FSNode[] resolvePath(String path) {
        FSNode curr = path.startsWith("/") ? root : cwd;
        String[] parts = path.split("/");
        List<String> cleanParts = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) cleanParts.add(p);
        }

        if (cleanParts.isEmpty()) {
            return new FSNode[]{curr, curr};
        }

        for (int i = 0; i < cleanParts.size(); i++) {
            String part = cleanParts.get(i);
            if (part.equals(".")) continue;
            if (part.equals("..")) {
                curr = curr.getParent();
            } else {
                if (!curr.isDir()) return new FSNode[]{null, null};
                if (curr.getChildren().containsKey(part)) {
                    curr = curr.getChildren().get(part);
                } else {
                    if (i == cleanParts.size() - 1) return new FSNode[]{null, curr};
                    return new FSNode[]{null, null};
                }
            }
        }
        return new FSNode[]{curr, curr.getParent()};
    }

    // --- OPERAÇÕES COM VERIFICAÇÃO DE PRIVILÉGIOS E ACL ---

    public String mkdir(String path) {
        FSNode[] resolved = resolvePath(path);
        FSNode target = resolved[0];
        FSNode parent = resolved[1];
        String name = path.substring(path.lastIndexOf('/') + 1);

        if (target != null) return "Erro: Diretório ou arquivo já existe.";
        if (parent == null || !parent.isDir()) return "Erro: Caminho pai não encontrado.";

        User user = security.getCurrentUser();
        if (!AccessControl.hasPermission(user, parent, AccessControl.WRITE)) {
            return formatAclDenied(parent, user, AccessControl.WRITE);
        }

        String fullPath = (parent.getFullPath().endsWith("/") ? "" : parent.getFullPath()) + "/" + name;
        AllocationResult res = disk.allocateIndexed(fullPath, 0, true);
        if (!res.isSuccess()) return "Erro: Sem espaço em disco.";

        FSNode newDir = new FSNode(name, true, parent);
        newDir.setInodeIdx(res.getInodeIdx());
        newDir.setOwner(user.getUsername());
        newDir.setGroup(user.getGroup());
        newDir.setPermissions(0750);
        parent.getChildren().put(name, newDir);
        return "Diretório '" + fullPath + "' criado com sucesso.";
    }

    public String create(String path, int sizeBytes) {
        return createWithContent(path, new byte[sizeBytes], sizeBytes);
    }

    public String createWithContent(String path, byte[] initialData, int logicalSizeBytes) {
        FSNode[] resolved = resolvePath(path);
        FSNode target = resolved[0];
        FSNode parent = resolved[1];
        String name = path.substring(path.lastIndexOf('/') + 1);

        if (target != null) return "Erro: Arquivo já existe.";
        if (parent == null || !parent.isDir()) return "Erro: Caminho pai inválido.";

        User user = security.getCurrentUser();
        if (!AccessControl.hasPermission(user, parent, AccessControl.WRITE)) {
            return formatAclDenied(parent, user, AccessControl.WRITE);
        }

        String fullPath = (parent.getFullPath().endsWith("/") ? "" : parent.getFullPath()) + "/" + name;
        AllocationResult res = disk.allocateIndexed(fullPath, logicalSizeBytes, false);
        if (!res.isSuccess()) return "Erro: Sem espaço em disco suficiente.";

        FSNode newFile = new FSNode(name, false, parent);
        newFile.setSizeBytes(logicalSizeBytes);
        newFile.setInodeIdx(res.getInodeIdx());
        newFile.setDataBlocks(res.getDataBlocks());
        newFile.setOwner(user.getUsername());
        newFile.setGroup(user.getGroup());
        newFile.setPermissions(0750);

        writePayloadToPhysicalBlocks(newFile, initialData);
        parent.getChildren().put(name, newFile);

        return "Arquivo '" + fullPath + "' criado (" + logicalSizeBytes + " bytes).";
    }

    public String readFile(String path) {
        FSNode[] resolved = resolvePath(path);
        FSNode target = resolved[0];

        if (target == null || target.isDir()) {
            return "Erro: Arquivo '" + path + "' não encontrado.";
        }

        User user = security.getCurrentUser();
        if (!AccessControl.hasPermission(user, target, AccessControl.READ)) {
            return formatAclDenied(target, user, AccessControl.READ);
        }

        byte[] raw = readPayloadFromPhysicalBlocks(target);
        String content = target.isEncrypted()
                ? "0x" + SecurityManager.bytesToHex(raw).substring(0, Math.min(16, raw.length * 2)) + "... (Conteúdo Criptografado)"
                : new String(raw, StandardCharsets.UTF_8).trim();

        return String.format("[LEITURA AUTORIZADA - %s]\nConteúdo: %s", target.getFullPath(), content);
    }

    public String writeFile(String path, String content, boolean showHardwareIO) {
        FSNode[] resolved = resolvePath(path);
        FSNode target = resolved[0];

        if (target == null || target.isDir()) {
            return "Erro: Arquivo '" + path + "' não encontrado.";
        }

        User user = security.getCurrentUser();
        if (!AccessControl.hasPermission(user, target, AccessControl.WRITE)) {
            return formatAclDenied(target, user, AccessControl.WRITE);
        }

        byte[] dataBytes = content.getBytes(StandardCharsets.UTF_8);
        writePayloadToPhysicalBlocks(target, dataBytes);

        StringBuilder sb = new StringBuilder();
        sb.append("Dados gravados em '").append(target.getFullPath()).append("' com sucesso.\n");

        if (showHardwareIO) {
            List<Integer> tracks = new ArrayList<>();
            for (int blk : target.getDataBlocks()) {
                tracks.add((blk * 17 + 14) % 200);
            }
            if (!tracks.isEmpty()) {
                DiskScheduler.ScheduleResult sched = DiskScheduler.executeSSTF(currentHeadTrack, tracks);
                sb.append("\n").append(DiskScheduler.formatReport("SSTF (Shortest Seek Time First)", currentHeadTrack, sched));
                currentHeadTrack = sched.getSequence().get(sched.getSequence().size() - 1);
            }
            sb.append("\n").append(raid.writeData(target.getName(), dataBytes, 0));
        }
        return sb.toString();
    }

    public String delete(String path) {
        FSNode[] resolved = resolvePath(path);
        FSNode target = resolved[0];
        FSNode parent = resolved[1];

        if (target == null) return "Erro: Caminho '" + path + "' não encontrado.";
        if (target == root) return "Erro: Não é possível remover o diretório raiz.";
        if (target.isDir() && !target.getChildren().isEmpty()) return "Erro: Diretório não está vazio.";

        User user = security.getCurrentUser();
        if (!AccessControl.hasPermission(user, parent, AccessControl.WRITE)) {
            return formatAclDenied(parent, user, AccessControl.WRITE);
        }

        // Operação crítica: remover itens em /sistema exige MODO_KERNEL
        if (target.getFullPath().startsWith("/sistema") && !security.isKernelMode()) {
            security.logSecurityEvent("Tentativa de remoção crítica em '" + target.getFullPath() + "' por '" + user.getUsername() + "'.");
            return "[NEGADO]: Operação crítica em '/sistema' exige privilégios de MODO_KERNEL (root).";
        }

        disk.freeBlocks(target.getInodeIdx(), target.getDataBlocks());
        parent.getChildren().remove(target.getName());
        return (target.isDir() ? "Diretório" : "Arquivo") + " '" + path + "' removido com sucesso.";
    }

    public String chmod(String path, int octalMode) {
        FSNode[] resolved = resolvePath(path);
        FSNode target = resolved[0];
        if (target == null) return "Erro: Caminho '" + path + "' não encontrado.";

        User user = security.getCurrentUser();
        if (!security.isKernelMode() && !user.getUsername().equals(target.getOwner())) {
            security.logSecurityEvent("Tentativa não autorizada de chmod em '" + path + "' por '" + user.getUsername() + "'.");
            return "[NEGADO]: Apenas o proprietário ou MODO_KERNEL (root) pode alterar permissões.";
        }

        target.setPermissions(octalMode);
        return String.format("Permissões de '%s' alteradas para %s (%s).",
                target.getFullPath(), AccessControl.formatPermissions(octalMode), AccessControl.formatOctal(octalMode));
    }

    public String chown(String path, String newOwner, String newGroup) {
        if (!security.isKernelMode()) {
            security.logSecurityEvent("Tentativa de chown sem privilégio de kernel por '" + security.getCurrentUser().getUsername() + "'.");
            return "[NEGADO]: Alteração de proprietário (chown) exige MODO_KERNEL (root).";
        }

        FSNode[] resolved = resolvePath(path);
        FSNode target = resolved[0];
        if (target == null) return "Erro: Caminho '" + path + "' não encontrado.";

        target.setOwner(newOwner);
        target.setGroup(newGroup);
        return String.format("Proprietário de '%s' alterado para '%s' (Grupo: '%s').", target.getFullPath(), newOwner, newGroup);
    }

    // --- CRIPTOGRAFIA EM REPOUSO E ANTIVÍRUS ---

    public String encryptFile(String path, String key) {
        FSNode[] resolved = resolvePath(path);
        FSNode target = resolved[0];
        if (target == null || target.isDir()) {
            return "Erro: Arquivo '" + path + "' não encontrado.";
        }

        User user = security.getCurrentUser();
        if (!AccessControl.hasPermission(user, target, AccessControl.WRITE)) {
            return formatAclDenied(target, user, AccessControl.WRITE);
        }

        byte[] plainBytes = readPayloadFromPhysicalBlocks(target);
        if (plainBytes.length == 0) {
            plainBytes = ("Conteudo confidencial de " + target.getName()).getBytes(StandardCharsets.UTF_8);
        }

        byte[] cipherBytes = security.encrypt(plainBytes, key);
        target.setEncrypted(true);
        writePayloadToPhysicalBlocks(target, cipherBytes);

        String hexPreview = "0x" + SecurityManager.bytesToHex(cipherBytes);
        if (hexPreview.length() > 12) {
            hexPreview = hexPreview.substring(0, 11) + "...";
        }

        return String.format("Arquivo '%s' criptografado com sucesso.\nConteúdo gravado nos blocos físicos: \"%s (Encrypted Ciphertext)\"",
                target.getFullPath(), hexPreview);
    }

    public String antivirusScan(String basePath) {
        FSNode[] resolved = resolvePath(basePath);
        FSNode startNode = resolved[0];
        if (startNode == null) {
            return "Erro: Caminho '" + basePath + "' não encontrado.";
        }

        List<FSNode> files = new ArrayList<>();
        collectFilesRecursive(startNode, files);

        StringBuilder sb = new StringBuilder();
        sb.append("[MÓDULO DE SEGURANÇA - VARREDURA DE INTEGRIDADE]\n");
        sb.append(String.format("Analisando %d arquivo(s) em busca de assinaturas maliciosas e hashes corrompidos...\n", files.size()));

        for (FSNode file : files) {
            byte[] currentPhysicalBytes = readPayloadFromPhysicalBlocks(file);
            String currentHash = security.sha256Hex(currentPhysicalBytes);
            boolean hashValid = currentHash.equals(file.getOfficialHash());
            boolean hasMalware = !file.isEncrypted() && security.containsMalwareSignature(currentPhysicalBytes);

            if (!hashValid) {
                sb.append(String.format("- %-26s: [ALERTA] Hash do bloco difere do registro oficial!\n", file.getFullPath()));
                sb.append("  Resultado: Possível modificação direta não autorizada no bloco do disco.\n");
            } else if (hasMalware) {
                sb.append(String.format("- %-26s: [PERIGO] Assinatura maliciosa detectada nos blocos!\n", file.getFullPath()));
                sb.append("  Resultado: Arquivo marcado para quarentena pelo módulo de segurança.\n");
            } else {
                String statusTag = file.isEncrypted() ? "[OK - Criptografado e Íntegro]" : "[OK - Íntegro]";
                sb.append(String.format("- %-26s: %s\n", file.getFullPath(), statusTag));
            }
        }
        return sb.toString();
    }

    /**
     * Simula um ataque de baixo nível alterando diretamente os bytes de um bloco físico no disco
     * sem atualizar os metadados oficiais (officialHash) do sistema de arquivos.
     */
    public String simulateDirectDiskTampering(String path, String injectedPayload) {
        FSNode[] resolved = resolvePath(path);
        FSNode target = resolved[0];
        if (target == null || target.isDir() || target.getDataBlocks().isEmpty()) {
            return "Erro: Arquivo inválido para simulação de corrupção de bloco.";
        }
        int firstBlockIdx = target.getDataBlocks().get(0);
        DiskBlock block = disk.getBlocks()[firstBlockIdx];
        block.setRawData(injectedPayload.getBytes(StandardCharsets.UTF_8));
        return String.format("[SIMULAÇÃO DE ATAQUE] Bloco físico %02d ('%s') modificado diretamente no hardware.",
                firstBlockIdx, target.getFullPath());
    }

    // --- AUXILIARES DE BLOCOS FÍSICOS E FORMATAÇÃO ---

    private void writePayloadToPhysicalBlocks(FSNode file, byte[] data) {
        List<Integer> blocks = file.getDataBlocks();
        int blockSize = disk.getBlockSize();
        int offset = 0;

        for (int idx : blocks) {
            DiskBlock b = disk.getBlocks()[idx];
            if (offset < data.length) {
                int len = Math.min(blockSize, data.length - offset);
                byte[] chunk = Arrays.copyOfRange(data, offset, offset + len);
                b.setRawData(chunk);
                b.setBytesUsed(len);
                offset += len;
            } else {
                b.setRawData(new byte[0]);
            }
        }
        // Recalcula hash oficial baseado na gravação legítima via VFS
        byte[] stored = readPayloadFromPhysicalBlocks(file);
        file.setOfficialHash(security.sha256Hex(stored));
    }

    private byte[] readPayloadFromPhysicalBlocks(FSNode file) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int idx : file.getDataBlocks()) {
            byte[] chunk = disk.getBlocks()[idx].getRawData();
            if (chunk != null && chunk.length > 0) {
                out.write(chunk, 0, chunk.length);
            }
        }
        return out.toByteArray();
    }

    private void collectFilesRecursive(FSNode node, List<FSNode> acc) {
        if (!node.isDir()) {
            acc.add(node);
        }
        for (FSNode child : node.getChildren().values()) {
            collectFilesRecursive(child, acc);
        }
    }

    private String formatAclDenied(FSNode node, User user, int action) {
        String logMsg = String.format("Acesso negado (%s) em '%s' para usuário '%s' (Grupo: %s).",
                AccessControl.getActionName(action), node.getFullPath(), user.getUsername(), user.getGroup());
        security.logSecurityEvent(logMsg);

        StringBuilder sb = new StringBuilder();
        sb.append("[SISTEMA DE SEGURANÇA - VERIFICAÇÃO DE ACL]\n");
        sb.append(String.format("Objeto      : %s\n", node.getFullPath()));
        sb.append(String.format("Proprietário: %s (Grupo: %s)\n", node.getOwner(), node.getGroup()));
        sb.append(String.format("Permissões  : %s (%s)\n",
                AccessControl.formatPermissions(node.getPermissions()),
                AccessControl.formatOctal(node.getPermissions())));
        sb.append(String.format("Ação Pedida : %s por '%s' (Grupo: %s)\n\n",
                AccessControl.getActionName(action), user.getUsername(), user.getGroup()));
        sb.append(String.format("[NEGADO]: O usuário '%s' não possui permissão de %s no objeto '%s'.\n",
                user.getUsername(), AccessControl.getActionName(action).toLowerCase(), node.getFullPath()));
        sb.append("Tentativa não autorizada registrada nos logs do sistema.");
        return sb.toString();
    }

    public String renderTree() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[ÁRVORE DO SISTEMA DE ARQUIVOS]\n/");
        renderTreeRecursive(root, "", sb);
        return sb.toString();
    }

    private void renderTreeRecursive(FSNode node, String prefix, StringBuilder sb) {
        List<FSNode> children = new ArrayList<>(node.getChildren().values());
        for (int i = 0; i < children.size(); i++) {
            FSNode child = children.get(i);
            boolean isLast = (i == children.size() - 1);
            String connector = isLast ? "└── " : "├── ";
            String permStr = AccessControl.formatPermissions(child.getPermissions());

            if (child.isDir()) {
                sb.append(String.format("\n%s%s%s/ [DIR | %s | %s:%s]",
                        prefix, connector, child.getName(), permStr, child.getOwner(), child.getGroup()));
                renderTreeRecursive(child, prefix + (isLast ? "    " : "│   "), sb);
            } else {
                double kb = child.getSizeBytes() / 1024.0;
                String sizeStr = (kb == (long) kb) ? String.format("%d KB", (long) kb) : String.format("%.1f KB", kb);
                String encTag = child.isEncrypted() ? " | ENC" : "";
                sb.append(String.format("\n%s%s%s (%s) [ARQ | %s | %s:%s%s]",
                        prefix, connector, child.getName(), sizeStr, permStr, child.getOwner(), child.getGroup(), encTag));
            }
        }
    }

    public String fsStatus() {
        int blockKb = disk.getBlockSize() / 1024;
        int totalKb = (disk.getTotalBlocks() * disk.getBlockSize()) / 1024;
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Tamanho do Bloco de Disco: %d KB | Total de Blocos: %d (%d KB)\n", blockKb, disk.getTotalBlocks(), totalKb));
        sb.append("\n[MAPA DE ALOCAÇÃO DO DISCO (Indexado)]\n");

        int i = 0;
        while (i < disk.getTotalBlocks()) {
            DiskBlock b = disk.getBlocks()[i];
            if (b.getType() == BlockType.LIVRE) {
                int start = i;
                while (i < disk.getTotalBlocks() && disk.getBlocks()[i].getType() == BlockType.LIVRE) {
                    i++;
                }
                int end = i - 1;
                if (start == end) {
                    sb.append(String.format("Bloco %02d               : [LIVRE]\n", start));
                } else {
                    sb.append(String.format("Bloco %02d..%02d            : [LIVRE]\n", start, end));
                }
            } else {
                if (b.getType() == BlockType.INODE) {
                    String typeStr = b.getOwnerPath().equals("/") ? "Diretório" : (b.getOwnerPath().contains(".") ? "Arquivo" : "Diretório");
                    sb.append(String.format("Bloco %02d [%-13s] : %s '%s'\n", b.getIndex(), b.getLabel(), typeStr, b.getOwnerPath()));
                } else if (b.getType() == BlockType.DADO) {
                    FSNode[] res = resolvePath(b.getOwnerPath());
                    FSNode node = res[0];
                    int blockNum = node != null ? node.getDataBlocks().indexOf(b.getIndex()) + 1 : 1;
                    sb.append(String.format("Bloco %02d [DADO         ] : '%s' (Bloco %d - %d bytes ocupados)\n", b.getIndex(), b.getOwnerPath(), blockNum, b.getBytesUsed()));
                }
                i++;
            }
        }
        return sb.toString();
    }
}