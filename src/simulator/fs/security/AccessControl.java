package simulator.fs.security;

import simulator.fs.tree.FSNode;

public class AccessControl {
    public static final int READ = 4;    // r
    public static final int WRITE = 2;   // w
    public static final int EXECUTE = 1; // x

    /**
     * Verifica se o usuário possui a permissão solicitada no nó (arquivo ou diretório).
     * Usuários em MODO_KERNEL (root) possuem acesso irrestrito.
     */
    public static boolean hasPermission(User user, FSNode node, int requestedPerm) {
        if (user.getMode() == ExecutionMode.MODO_KERNEL) {
            return true;
        }

        int mode = node.getPermissions();
        int effectiveBits;

        if (user.getUsername().equals(node.getOwner())) {
            effectiveBits = (mode >> 6) & 7; // Bits do Proprietário
        } else if (user.getGroup().equals(node.getGroup())) {
            effectiveBits = (mode >> 3) & 7; // Bits do Grupo
        } else {
            effectiveBits = mode & 7;        // Bits de Outros
        }

        return (effectiveBits & requestedPerm) == requestedPerm;
    }

    public static String formatPermissions(int mode) {
        return triadToString((mode >> 6) & 7) +
                triadToString((mode >> 3) & 7) +
                triadToString(mode & 7);
    }

    private static String triadToString(int triad) {
        return ((triad & READ) != 0 ? "r" : "-") +
                ((triad & WRITE) != 0 ? "w" : "-") +
                ((triad & EXECUTE) != 0 ? "x" : "-");
    }

    public static String formatOctal(int mode) {
        return String.format("%03o", mode);
    }

    public static String getActionName(int perm) {
        switch (perm) {
            case READ: return "LEITURA";
            case WRITE: return "ESCRITA";
            case EXECUTE: return "EXECUÇÃO";
            default: return "ACESSO";
        }
    }
}