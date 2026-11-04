package simulator.fs.core;

import simulator.fs.disk.AllocationResult;
import simulator.fs.disk.BlockType;
import simulator.fs.disk.DiskBlock;
import simulator.fs.disk.DiskManager;
import simulator.fs.tree.FSNode;

import java.util.ArrayList;
import java.util.List;

public class VirtualFileSystem {
    private final DiskManager disk;
    private final FSNode root;
    private FSNode cwd;

    public VirtualFileSystem(int totalBlocks, int blockSize) {
        this.disk = new DiskManager(totalBlocks, blockSize);
        this.root = new FSNode("", true, null);
        this.root.setParent(this.root);

        AllocationResult res = disk.allocateIndexed("/", 0, true);
        this.root.setInodeIdx(res.getInodeIdx());
        this.cwd = this.root;
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

        FSNode parent = curr;
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

    public String mkdir(String path) {
        FSNode[] resolved = resolvePath(path);
        FSNode target = resolved[0];
        FSNode parent = resolved[1];
        String name = path.substring(path.lastIndexOf('/') + 1);

        if (target != null) return "Erro: Diretório ou arquivo já existe.";
        if (parent == null || !parent.isDir()) return "Erro: Caminho pai não encontrado.";

        String fullPath = (parent.getFullPath().endsWith("/") ? "" : parent.getFullPath()) + "/" + name;
        AllocationResult res = disk.allocateIndexed(fullPath, 0, true);
        if (!res.isSuccess()) return "Erro: Sem espaço em disco.";

        FSNode newDir = new FSNode(name, true, parent);
        newDir.setInodeIdx(res.getInodeIdx());
        parent.getChildren().put(name, newDir);
        return "Diretório '" + fullPath + "' criado com sucesso.";
    }

    public String create(String path, int sizeBytes) {
        FSNode[] resolved = resolvePath(path);
        FSNode target = resolved[0];
        FSNode parent = resolved[1];
        String name = path.substring(path.lastIndexOf('/') + 1);

        if (target != null) return "Erro: Arquivo já existe.";
        if (parent == null || !parent.isDir()) return "Erro: Caminho pai inválido.";

        String fullPath = (parent.getFullPath().endsWith("/") ? "" : parent.getFullPath()) + "/" + name;
        AllocationResult res = disk.allocateIndexed(fullPath, sizeBytes, false);
        if (!res.isSuccess()) return "Erro: Sem espaço em disco suficiente.";

        FSNode newFile = new FSNode(name, false, parent);
        newFile.setSizeBytes(sizeBytes);
        newFile.setInodeIdx(res.getInodeIdx());
        newFile.setDataBlocks(res.getDataBlocks());
        parent.getChildren().put(name, newFile);

        return "Arquivo '" + fullPath + "' criado (" + sizeBytes + " bytes).";
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

            if (child.isDir()) {
                sb.append("\n").append(prefix).append(connector).append(child.getName()).append("/ [DIR]");
                renderTreeRecursive(child, prefix + (isLast ? "    " : "│   "), sb);
            } else {
                double kb = child.getSizeBytes() / 1024.0;
                String sizeStr = (kb == (long) kb) ? String.format("%d KB", (long) kb) : String.format("%.1f KB", kb);
                sb.append("\n").append(prefix).append(connector).append(child.getName()).append(" (").append(sizeStr).append(") [ARQ]");
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

        sb.append("\n--- RELATÓRIO DE FRAGMENTAÇÃO ---\n");
        analyzeFragmentation(root, sb);
        sb.append("Fragmentação Externa             : 0 bytes\n");
        sb.append("Necessidade de Compactação       : NÃO (Blocos alocados via i-node indexado)\n");

        return sb.toString();
    }

    private void analyzeFragmentation(FSNode node, StringBuilder sb) {
        if (!node.isDir()) {
            int blockKb = disk.getBlockSize() / 1024;
            int allocatedSpace = node.getDataBlocks().size() * disk.getBlockSize();
            int internalFrag = allocatedSpace - node.getSizeBytes();
            int lastBlockUsed = node.getSizeBytes() % disk.getBlockSize();
            int wastePct = lastBlockUsed > 0 ? (int) (((double) (disk.getBlockSize() - lastBlockUsed) / disk.getBlockSize()) * 100) : 0;
            int lastBlockIdx = node.getDataBlocks().isEmpty() ? -1 : node.getDataBlocks().get(node.getDataBlocks().size() - 1);

            sb.append(String.format("Tamanho Real do Arquivo '%s': %d bytes (%d KB)\n", node.getName(), node.getSizeBytes(), node.getSizeBytes() / 1024));
            sb.append(String.format("Espaço Alocado no Disco          : %d bytes (%d blocos de %d KB)\n", allocatedSpace, node.getDataBlocks().size(), blockKb));
            sb.append(String.format("Fragmentação Interna             : %d bytes (%d%% de desperdício no Bloco %02d)\n", internalFrag, wastePct, lastBlockIdx));
        }
        for (FSNode child : node.getChildren().values()) {
            analyzeFragmentation(child, sb);
        }
    }
}