package simulator.fs;

import simulator.fs.io.DiskScheduler;
import simulator.fs.raid.RaidController;
import simulator.fs.raid.RaidType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("   SIMULADOR DE SISTEMA DE ARQUIVOS (ETAPA 2: E/S E RAID) ");
        System.out.println("==========================================================\n");

        // -------------------------------------------------------------
        // EXEMPLO 2.1: ESCALONAMENTO DO BRAÇO DO DISCO (SSTF)
        // -------------------------------------------------------------
        System.out.println("> write_file /sistema/logs/app.log \"Novo Registro\"");
        List<Integer> pendingTracks = Arrays.asList(98, 183, 37, 122, 14);
        int currentHeadPosition = 53;

        System.out.println(String.format("[E/S] Requisições de leitura/escrita pendentes nas trilhas: %s", pendingTracks));
        System.out.println(String.format("Posição atual da cabeça de leitura: Trilha %d\n", currentHeadPosition));

        DiskScheduler.ScheduleResult sstfResult = DiskScheduler.executeSSTF(currentHeadPosition, pendingTracks);
        System.out.println(DiskScheduler.formatReport("SSTF (Shortest Seek Time First)", currentHeadPosition, sstfResult));

        System.out.println("----------------------------------------------------------\n");

        // -------------------------------------------------------------
        // EXEMPLO 2.2: GRAVAÇÃO FÍSICA SOB ARRANJO RAID 5
        // -------------------------------------------------------------
        System.out.println("> write_file /dados/planilha.csv --size 8KB");

        // Configura RAID 5 com 3 discos físicos e blocos de 4 KB
        RaidController raid5 = new RaidController(RaidType.RAID5, 3, 4096);

        // Simula o payload de 8 KB
        byte[] fileData = "Dados da Planilha".getBytes(StandardCharsets.UTF_8);
        String raidWriteReport = raid5.writeData("/dados/planilha.csv", fileData, 0);
        System.out.println(raidWriteReport);

        // Teste de Falha Simulado no Disco 2
        raid5.simulateDiskFailure(2);
        String recoveryReport = raid5.recoverDataFromFailure(2, 0);
        System.out.println(recoveryReport);
    }
}