package simulator.fs.memory;

import java.util.*;

public class MemoryManager {
    public static final int RESERVED_TOTAL_KB = 4096; // 4 MB
    public static final int PAGE_SIZE_BYTES = 4096;   // 4 KB
    public static final int DYNAMIC_FRAMES_COUNT = 96; // 96 molduras (384 KB)

    private final List<MemoryBlock> reservedBlocks = new ArrayList<>();
    private final FrameEntry[] dynamicFrames = new FrameEntry[DYNAMIC_FRAMES_COUNT];
    private final Map<String, ProcessControlBlock> processes = new LinkedHashMap<>();

    private int nextPid = 101;
    private long logicalClock = 1;
    private int swapOperationsCount = 0;

    public MemoryManager() {
        // Inicializa Área Reservada: [0.0 MB - 1.0 MB] para o Núcleo Base do SO
        reservedBlocks.add(new MemoryBlock(0, 1024, false, "Núcleo Base do SO"));
        reservedBlocks.add(new MemoryBlock(1024, RESERVED_TOTAL_KB, true, "LIVRE"));

        // Inicializa Molduras da RAM Dinâmica
        for (int i = 0; i < DYNAMIC_FRAMES_COUNT; i++) {
            dynamicFrames[i] = new FrameEntry(i);
        }
        bootstrapBackgroundDynamicMemory();
    }

    /**
     * Popula processos residentes de sistema na RAM dinâmica para simular carga real,
     * deixando a Moldura 12 livre para alocação sob demanda e a Moldura 88 (PID 102, Página 0)
     * como a página menos recentemente usada (LRU - 45ms).
     */
    private void bootstrapBackgroundDynamicMemory() {
        ProcessControlBlock sysIndexer = new ProcessControlBlock(nextPid++, "Sys_Indexer", ProcessType.NORMAL, 380 * 1024);
        ProcessControlBlock daemonService = new ProcessControlBlock(nextPid++, "Sys_Daemon", ProcessType.NORMAL, 16 * 1024);
        processes.put(sysIndexer.getName(), sysIndexer);
        processes.put(daemonService.getName(), daemonService);

        for (int f = 0; f < DYNAMIC_FRAMES_COUNT; f++) {
            if (f == 12) continue; // Moldura 12 disponível para primeira alocação por demanda

            FrameEntry frame = dynamicFrames[f];
            frame.setOccupied(true);
            if (f == 88) {
                // Página mais antiga no histórico LRU (45ms sem acesso)
                frame.setPid(daemonService.getPid());
                frame.setProcessName(daemonService.getName());
                frame.setPageNumber(0);
                frame.setLastAccessTimestamp(1);
                frame.setIdleMilliseconds(45);
            } else {
                frame.setPid(sysIndexer.getPid());
                frame.setProcessName(sysIndexer.getName());
                frame.setPageNumber(f);
                frame.setLastAccessTimestamp(10 + f);
                frame.setIdleMilliseconds(Math.max(1, 35 - (f % 30)));
            }
        }
    }

    // --- CRIAÇÃO DE PROCESSOS (MODELO HÍBRIDO) ---

    public String createProcess(String name, int sizeBytes, ProcessType type) {
        if (processes.containsKey(name)) {
            return "Erro: Já existe um processo ativo com o nome '" + name + "'.";
        }

        if (type == ProcessType.CRITICAL) {
            return allocateCriticalProcess(name, sizeBytes);
        } else {
            return allocateNormalProcess(name, sizeBytes);
        }
    }

    private String allocateCriticalProcess(String name, int sizeBytes) {
        int sizeKb = (int) Math.ceil(sizeBytes / 1024.0);

        for (int i = 0; i < reservedBlocks.size(); i++) {
            MemoryBlock block = reservedBlocks.get(i);
            if (block.isFree() && block.getSizeKb() >= sizeKb) {
                int allocStart = block.getStartKb();
                int allocEnd = allocStart + sizeKb;
                int remainingEnd = block.getEndKb();

                block.setEndKb(allocEnd);
                block.setFree(false);
                block.setOwnerName(name);

                if (allocEnd < remainingEnd) {
                    reservedBlocks.add(i + 1, new MemoryBlock(allocEnd, remainingEnd, true, "LIVRE"));
                }

                ProcessControlBlock pcb = new ProcessControlBlock(nextPid++, name, ProcessType.CRITICAL, sizeBytes);
                processes.put(name, pcb);

                return String.format(Locale.US, "- Alocado de forma contígua na Área Reservada: [%.1f MB até %.1f MB] (%d KB)",
                        allocStart / 1024.0, allocEnd / 1024.0, sizeKb);
            }
        }

        return String.format("Erro: Falha na alocação contígua de %d KB na Área Reservada (Fragmentação Externa ou falta de espaço).", sizeKb);
    }

    private String allocateNormalProcess(String name, int sizeBytes) {
        ProcessControlBlock pcb = new ProcessControlBlock(nextPid++, name, ProcessType.NORMAL, sizeBytes);
        int numPages = (int) Math.ceil((double) sizeBytes / PAGE_SIZE_BYTES);
        if (numPages == 0) numPages = 1;

        int remainingBytes = sizeBytes;
        int totalInternalFrag = 0;
        int lastFragPage = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("[CRIAÇÃO DE PROCESSO NÃO CRÍTICO - MEMÓRIA VIRTUAL]\n");
        sb.append(String.format("Tamanho do Processo : %d KB (%d Bytes)\n", sizeBytes / 1024, sizeBytes));
        sb.append(String.format("Tamanho da Página   : %d KB (%d Bytes)\n", PAGE_SIZE_BYTES / 1024, PAGE_SIZE_BYTES));

        double exactPages = (double) sizeBytes / PAGE_SIZE_BYTES;
        String exactStr = (exactPages == (long) exactPages)
                ? String.format(Locale.US, "%.0f", exactPages)
                : String.format(Locale.US, "%.1f", exactPages);

        sb.append(String.format("Cálculo de Páginas  : %d KB / %d KB = %s -> Requer %d Páginas Lógicas (",
                sizeBytes / 1024, PAGE_SIZE_BYTES / 1024, exactStr, numPages));
        for (int p = 0; p < numPages; p++) {
            sb.append(p == numPages - 1 ? (numPages > 1 ? " e " + p : "" + p) : (p == 0 ? "" + p : ", " + p));
        }
        sb.append(").\n\n");

        sb.append("--- ANÁLISE DE FRAGMENTAÇÃO INTERNA ---\n");
        for (int p = 0; p < numPages; p++) {
            int used = Math.min(PAGE_SIZE_BYTES, Math.max(0, remainingBytes));
            remainingBytes -= used;
            int pct = (int) Math.round((used * 100.0) / PAGE_SIZE_BYTES);
            int waste = PAGE_SIZE_BYTES - used;

            if (waste > 0) {
                totalInternalFrag += waste;
                lastFragPage = p;
            }

            pcb.getPageTable().add(new PageTableEntry(p, used));
            sb.append(String.format("Página Lógica %d : %d Bytes ocupados (%d%%%s)\n",
                    p, used, pct, pct < 100 ? " de uso" : ""));
        }

        if (totalInternalFrag > 0) {
            sb.append(String.format("Fragmentação Interna Total: %d Bytes de desperdício na Página Lógica %d.\n",
                    totalInternalFrag, lastFragPage));
        } else {
            sb.append("Fragmentação Interna Total: 0 Bytes (Alinhamento exato de página).\n");
        }
        sb.append("Status: Tabela de Páginas gerada sem alocar molduras na RAM (Alocação por demanda).");

        processes.put(name, pcb);
        return sb.toString();
    }

    // --- ENCERRAMENTO DE PROCESSOS ---

    public String terminateProcess(String name) {
        ProcessControlBlock pcb = processes.remove(name);
        if (pcb == null) {
            return "Erro: Processo '" + name + "' não encontrado.";
        }

        if (pcb.getType() == ProcessType.CRITICAL) {
            for (MemoryBlock block : reservedBlocks) {
                if (!block.isFree() && block.getOwnerName().equals(name)) {
                    int freedKb = block.getSizeKb();
                    double startMb = block.getStartKb() / 1024.0;
                    double endMb = block.getEndKb() / 1024.0;
                    block.setFree(true);
                    block.setOwnerName("LIVRE");
                    mergeAdjacentFreeBlocks();

                    return String.format(Locale.US,
                            "[ENCERRAMENTO DE PROCESSO CRÍTICO] Processo '%s' finalizado.\n" +
                                    "Área de %d KB no intervalo [%.1f MB a %.1f MB] liberada na Área Reservada da RAM.",
                            name, freedKb, startMb, endMb);
                }
            }
        } else {
            int freedFrames = 0;
            for (FrameEntry frame : dynamicFrames) {
                if (frame.isOccupied() && frame.getProcessName().equals(name)) {
                    frame.setOccupied(false);
                    frame.setPid(-1);
                    frame.setProcessName("");
                    frame.setPageNumber(-1);
                    freedFrames++;
                }
            }
            return String.format("[ENCERRAMENTO DE PROCESSO] Processo '%s' finalizado. %d molduras liberadas na RAM Dinâmica.",
                    name, freedFrames);
        }

        return "Processo '" + name + "' encerrado.";
    }

    private void mergeAdjacentFreeBlocks() {
        for (int i = 0; i < reservedBlocks.size() - 1; i++) {
            MemoryBlock current = reservedBlocks.get(i);
            MemoryBlock next = reservedBlocks.get(i + 1);
            if (current.isFree() && next.isFree()) {
                current.setEndKb(next.getEndKb());
                reservedBlocks.remove(i + 1);
                i--;
            }
        }
    }

    // --- DIAGNÓSTICO E COMPACTAÇÃO DA ÁREA RESERVADA ---

    public String getReservedMemoryStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("--- MAPA DA ÁREA CRÍTICA/RESERVADA DA RAM (Total: %d MB - Alocação Fixa) ---\n",
                RESERVED_TOTAL_KB / 1024));

        int totalFreeKb = 0;
        int largestFreeKb = 0;
        int freeFragmentsCount = 0;

        for (MemoryBlock b : reservedBlocks) {
            if (b.isFree()) {
                freeFragmentsCount++;
                totalFreeKb += b.getSizeKb();
                if (b.getSizeKb() > largestFreeKb) {
                    largestFreeKb = b.getSizeKb();
                }
            }
        }

        int freeIdx = 1;
        for (MemoryBlock b : reservedBlocks) {
            String label;
            String statusTag;
            if (b.isFree()) {
                label = freeFragmentsCount > 1 ? "FRAGMENTO LIVRE " + freeIdx++ : "Bloco Livre Contíguo";
                statusTag = freeFragmentsCount > 1 ? "[LIVRE - LACUNA]" : "[LIVRE]";
            } else {
                label = b.getOwnerName();
                statusTag = "[OCUPADO]";
            }
            sb.append(String.format(Locale.US, "[%.1f MB - %.1f MB] : %-19s (%d KB) %s\n",
                    b.getStartKb() / 1024.0, b.getEndKb() / 1024.0, label, b.getSizeKb(), statusTag));
        }

        sb.append("\n--- DIAGNÓSTICO DE FRAGMENTAÇÃO EXTERNA ---\n");
        sb.append(String.format(Locale.US, "Total de Memória Livre na Área Reservada : %d KB (%.1f MB)\n",
                totalFreeKb, totalFreeKb / 1024.0));
        sb.append(String.format(Locale.US, "Maior Bloco Contíguo Disponível          : %d KB (%.1f MB)\n",
                largestFreeKb, largestFreeKb / 1024.0));

        if (freeFragmentsCount > 1) {
            int testReqKb = largestFreeKb + 512;
            sb.append(String.format("Fragmentação Externa Detectada           : SIM (%d fragmentos não contíguos)\n", freeFragmentsCount));
            sb.append(String.format("Status: Uma requisição contígua de %d KB falhará por ausência de bloco único.", testReqKb));
        } else {
            sb.append("Fragmentação Externa Detectada           : NÃO (Espaço livre totalmente contíguo)\n");
            sb.append("Status: Área reservada otimizada sem lacunas internas.");
        }

        return sb.toString();
    }

    public String compactReservedMemory() {
        StringBuilder sb = new StringBuilder();
        sb.append("[EXECUTANDO COMPACTAÇÃO DA ÁREA RESERVADA DA RAM]\n");
        sb.append("Realocando blocos contíguos para eliminar a fragmentação externa...\n");

        List<MemoryBlock> compacted = new ArrayList<>();
        int cursorKb = 0;
        boolean movedAny = false;

        for (MemoryBlock b : reservedBlocks) {
            if (!b.isFree()) {
                int size = b.getSizeKb();
                int oldStart = b.getStartKb();
                int oldEnd = b.getEndKb();
                int newStart = cursorKb;
                int newEnd = cursorKb + size;

                if (oldStart != newStart) {
                    movedAny = true;
                    sb.append(String.format(Locale.US, "- Mapeamento de '%s': [%.1f MB a %.1f MB] -> Ajustado para: [%.1f MB a %.1f MB]\n",
                            b.getOwnerName(), oldStart / 1024.0, oldEnd / 1024.0, newStart / 1024.0, newEnd / 1024.0));
                }

                compacted.add(new MemoryBlock(newStart, newEnd, false, b.getOwnerName()));
                cursorKb = newEnd;
            }
        }

        if (!movedAny) {
            sb.append("- Nenhuma realocação necessária (blocos já estão contíguos).\n");
        }

        int remainingFreeKb = RESERVED_TOTAL_KB - cursorKb;
        if (remainingFreeKb > 0) {
            compacted.add(new MemoryBlock(cursorKb, RESERVED_TOTAL_KB, true, "LIVRE"));
        }

        reservedBlocks.clear();
        reservedBlocks.addAll(compacted);

        sb.append("\n\n--- MAPA APÓS COMPACTAÇÃO ---\n");
        for (MemoryBlock b : reservedBlocks) {
            String label = b.isFree() ? "Bloco Livre Contíguo" : b.getOwnerName();
            String status = b.isFree() ? "[LIVRE]" : "[OCUPADO]";
            sb.append(String.format(Locale.US, "[%.1f MB - %.1f MB] : %-20s (%d KB) %s\n",
                    b.getStartKb() / 1024.0, b.getEndKb() / 1024.0, label, b.getSizeKb(), status));
        }
        sb.append(String.format("Resultado: Fragmentação externa eliminada. Maior bloco contíguo: %d KB.", remainingFreeKb));

        return sb.toString();
    }

    // --- TRADUÇÃO DE ENDEREÇOS E PAGE FAULTS (ALGORITMO LRU) ---

    public String translateAddress(String processName, int pageNumber) {
        ProcessControlBlock pcb = processes.get(processName);
        if (pcb == null || pcb.getType() != ProcessType.NORMAL) {
            return "Erro: Processo paginado '" + processName + "' não encontrado.";
        }
        if (pageNumber < 0 || pageNumber >= pcb.getPageTable().size()) {
            return String.format("Erro: Segmentation Fault — Página Lógica %d fora dos limites de '%s'.",
                    pageNumber, processName);
        }

        PageTableEntry pte = pcb.getPageTable().get(pageNumber);

        // Se ainda não foi carregada e existe moldura livre (ex.: Moldura 12), mapeia sob demanda
        if (!pte.isPresent()) {
            int freeFrameIdx = findFreeDynamicFrame();
            if (freeFrameIdx != -1) {
                mapPageToFrame(pcb, pte, freeFrameIdx);
            } else {
                return String.format("[TRADUÇÃO DE ENDEREÇO: PÁGINA LÓGICA -> MOLDURA FÍSICA]\n" +
                        "Solicitação: Página Lógica %d -> [MISS / PAGE FAULT] Página ausente na RAM.", pageNumber);
            }
        }

        touchFrame(pte.getFrameNumber());
        return String.format("[TRADUÇÃO DE ENDEREÇO: PÁGINA LÓGICA -> MOLDURA FÍSICA]\n" +
                        "Solicitação: Página Lógica %d -> Mapeada na Moldura Física %d (RAM Dinâmica) [HIT]",
                pageNumber, pte.getFrameNumber());
    }

    public PageFaultResult executeMemoryStep(String processName, int pageNumber) {
        ProcessControlBlock pcb = processes.get(processName);
        if (pcb == null || pcb.getType() != ProcessType.NORMAL) {
            return new PageFaultResult(false, "Erro: Processo paginado '" + processName + "' não encontrado.", -1);
        }
        if (pageNumber < 0 || pageNumber >= pcb.getPageTable().size()) {
            return new PageFaultResult(false, "Erro: Segmentation Fault — Página Lógica inválida.", -1);
        }

        PageTableEntry pte = pcb.getPageTable().get(pageNumber);
        StringBuilder sb = new StringBuilder();
        sb.append("[CPU - REFERÊNCIA DE MEMÓRIA EM EXECUÇÃO]\n");
        sb.append(String.format("%s requisita acesso à Página Lógica %d...\n", processName, pageNumber));

        if (pte.isPresent()) {
            touchFrame(pte.getFrameNumber());
            sb.append(String.format("- Consulta Tabela de Páginas -> Página Lógica %d presente na Moldura Física %d [HIT].",
                    pageNumber, pte.getFrameNumber()));
            return new PageFaultResult(false, sb.toString(), pte.getFrameNumber());
        }

        // Page Fault detectado
        sb.append(String.format("- Consulta Tabela de Páginas -> Página Lógica %d NÃO ESTÁ NA RAM!\n\n\n", pageNumber));
        sb.append(String.format("[PAGE FAULT DETECTADO]: Página Lógica %d ausente na RAM!\n", pageNumber));

        int freeFrameIdx = findFreeDynamicFrame();
        if (freeFrameIdx != -1) {
            mapPageToFrame(pcb, pte, freeFrameIdx);
            sb.append(String.format("- Carregando Página Lógica %d de %s na Moldura Física %d (RAM Dinâmica).\n",
                    pageNumber, processName, freeFrameIdx));
            sb.append(String.format("- Tabela de Páginas atualizada: Página Lógica %d ---> Moldura Física %d.",
                    pageNumber, freeFrameIdx));
            return new PageFaultResult(true, sb.toString(), freeFrameIdx);
        }

        // Memória RAM cheia -> Executar substituição LRU
        FrameEntry victim = selectLruVictimFrame();
        int victimPid = victim.getPid();
        int victimPage = victim.getPageNumber();
        long idleMs = victim.getIdleMilliseconds();
        int targetFrameIdx = victim.getFrameNumber();

        swapOperationsCount++;

        sb.append("Memória RAM cheia. Executando Substituição de Página (Algoritmo LRU):\n");
        sb.append(String.format("- Analisando histórico de acesso: Página Lógica %d do PID %d não é usada há %dms (Mais antiga).\n\n",
                victimPage, victimPid, idleMs));
        sb.append(String.format("- Removendo Página Lógica %d do PID %d da RAM -> Salvando no Swap (Disco).\n\n",
                victimPage, victimPid));

        // Atualiza tabela do processo vítima se existir
        ProcessControlBlock victimPcb = processes.get(victim.getProcessName());
        if (victimPcb != null && victimPage >= 0 && victimPage < victimPcb.getPageTable().size()) {
            PageTableEntry victimPte = victimPcb.getPageTable().get(victimPage);
            victimPte.setPresent(false);
            victimPte.setFrameNumber(-1);
            victimPte.setInSwap(true);
        }

        // Mapeia nova página na moldura liberada
        mapPageToFrame(pcb, pte, targetFrameIdx);

        sb.append(String.format("- Carregando Página Lógica %d do %s na Moldura Física %d (RAM Dinâmica).\n\n",
                pageNumber, processName, targetFrameIdx));
        sb.append(String.format("- Tabela de Páginas atualizada: Página Lógica %d ---> Moldura Física %d.",
                pageNumber, targetFrameIdx));

        return new PageFaultResult(true, sb.toString(), targetFrameIdx);
    }

    public String getVirtualMemoryStatus() {
        int occupiedFrames = 0;
        for (FrameEntry f : dynamicFrames) {
            if (f.isOccupied()) occupiedFrames++;
        }
        int totalDynamicKb = DYNAMIC_FRAMES_COUNT * (PAGE_SIZE_BYTES / 1024);
        int occupiedKb = occupiedFrames * (PAGE_SIZE_BYTES / 1024);
        int pct = (int) Math.round((occupiedFrames * 100.0) / DYNAMIC_FRAMES_COUNT);

        StringBuilder sb = new StringBuilder();
        sb.append("--- OCUPAÇÃO DA MEMÓRIA VIRTUAL (RAM DINÂMICA PAGINADA) ---\n");
        sb.append(String.format("Molduras Ocupadas : %d / %d (%d KB de %d KB - %d%%)\n",
                occupiedFrames, DYNAMIC_FRAMES_COUNT, occupiedKb, totalDynamicKb, pct));
        sb.append(String.format("Operações de Swap : %d transferências realizadas com o disco\n", swapOperationsCount));
        return sb.toString();
    }

    private int findFreeDynamicFrame() {
        for (FrameEntry f : dynamicFrames) {
            if (!f.isOccupied()) {
                return f.getFrameNumber();
            }
        }
        return -1;
    }

    private FrameEntry selectLruVictimFrame() {
        FrameEntry oldest = dynamicFrames[0];
        for (FrameEntry f : dynamicFrames) {
            if (f.getLastAccessTimestamp() < oldest.getLastAccessTimestamp()) {
                oldest = f;
            }
        }
        return oldest;
    }

    private void mapPageToFrame(ProcessControlBlock pcb, PageTableEntry pte, int frameIdx) {
        long tick = ++logicalClock * 100;
        FrameEntry frame = dynamicFrames[frameIdx];
        frame.setOccupied(true);
        frame.setPid(pcb.getPid());
        frame.setProcessName(pcb.getName());
        frame.setPageNumber(pte.getPageNumber());
        frame.setLastAccessTimestamp(tick);
        frame.setIdleMilliseconds(0);

        pte.setPresent(true);
        pte.setFrameNumber(frameIdx);
        pte.setInSwap(false);
        pte.setLastAccessOrder(tick);
    }

    private void touchFrame(int frameIdx) {
        if (frameIdx >= 0 && frameIdx < DYNAMIC_FRAMES_COUNT) {
            long tick = ++logicalClock * 100;
            dynamicFrames[frameIdx].setLastAccessTimestamp(tick);
            dynamicFrames[frameIdx].setIdleMilliseconds(0);
        }
    }

    public static class PageFaultResult {
        private final boolean pageFaultOccurred;
        private final String report;
        private final int frameNumber;

        public PageFaultResult(boolean pageFaultOccurred, String report, int frameNumber) {
            this.pageFaultOccurred = pageFaultOccurred;
            this.report = report;
            this.frameNumber = frameNumber;
        }

        public boolean isPageFaultOccurred() { return pageFaultOccurred; }
        public String getReport() { return report; }
        public int getFrameNumber() { return frameNumber; }
    }
}