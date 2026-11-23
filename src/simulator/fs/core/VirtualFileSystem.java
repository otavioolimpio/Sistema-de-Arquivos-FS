package simulator.fs.core;

import simulator.fs.disk.AllocationResult;
import simulator.fs.disk.BlockType;
import simulator.fs.disk.DiskBlock;
import simulator.fs.disk.DiskManager;
import simulator.fs.io.DiskScheduler;
import simulator.fs.memory.MemoryManager;
import simulator.fs.memory.ProcessType;
import simulator.fs.raid.RaidController;
import simulator.fs.raid.RaidType;
import simulator.fs.security.AccessControl;
import simulator.fs.security.SecurityManager;
import simulator.fs.security.User;
import simulator.fs.tree.FSNode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VirtualFileSystem {
    private final DiskManager disk;
    private final RaidController raid;
    private final SecurityManager security;
    private final MemoryManager memory;
    private final FSNode root;
    private FSNode cwd;
    private int currentHeadTrack = 53;

    public VirtualFileSystem(int totalBlocks, int blockSize) {
        this.disk = new DiskManager(totalBlocks, blockSize);
        this.raid = new RaidController(RaidType.RAID5, 3, blockSize);
        this.security = new SecurityManager();
        this.memory = new MemoryManager();

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

    public MemoryManager getMemory() {
        return memory;
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

    // --- OPERAÇÕES DE MEMÓRIA E PROCESSOS (ETAPA 4) ---

    public String createProcess(String name, int sizeBytes, ProcessType type) {
        if (type == ProcessType.CRITICAL && !security.isKernelMode()) {
            security.logSecurityEvent("Tentativa de alocar processo crítico '" + name + "' sem MODO_KERNEL.");
            return "[NEGADO]: Alocação na Área Reservada exige privilégios de MODO_KERNEL (root).";
        }
        return memory.createProcess(name, sizeBytes, type);
    }

    public String terminateProcess(String name) {
        return memory.terminateProcess(name);
    }

    public String executeMemoryStep(String processName, int pageNumber) {
        MemoryManager.PageFaultResult result = memory.executeMemoryStep(processName, pageNumber);
        if (result.isPageFaultOccurred()) {
            // Integração com Etapas 1 e 2: persiste a página removida no arquivo de Swap em disco
            FSNode[] swapNode = resolvePath("/sistema/swap/pagefile.sys");
            if (swapNode[0] != null) {
                byte[] swapPayload = ("SWAP_FRAME_" + result.getFrameNumber()).getBytes(StandardCharsets.UTF_8);
                writePayloadToPhysicalBlocks(swapNode[0], swapPayload);
            }
        }
        return result.getReport();
    }

    // --- OPERAÇÕES DO SISTEMA DE ARQUIVOS COM ACL, E/S E RAID (ETAPAS 1, 2 E 3) ---

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
        sb.append("Dados gravados em