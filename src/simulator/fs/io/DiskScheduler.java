package simulator.fs.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiskScheduler {

    public static class ScheduleResult {
        private final List<Integer> sequence;
        private final List<Integer> stepMovements;
        private final int totalMovement;

        public ScheduleResult(List<Integer> sequence, List<Integer> stepMovements, int totalMovement) {
            this.sequence = sequence;
            this.stepMovements = stepMovements;
            this.totalMovement = totalMovement;
        }

        public List<Integer> getSequence() { return sequence; }
        public List<Integer> getStepMovements() { return stepMovements; }
        public int getTotalMovement() { return totalMovement; }
    }

    public static ScheduleResult executeSSTF(int initialHeadPosition, List<Integer> requests) {
        List<Integer> pending = new ArrayList<>(requests);
        List<Integer> sequence = new ArrayList<>();
        List<Integer> stepMovements = new ArrayList<>();

        sequence.add(initialHeadPosition);
        int currentHead = initialHeadPosition;
        int totalMovement = 0;

        while (!pending.isEmpty()) {
            int closestTrack = -1;
            int minDistance = Integer.MAX_VALUE;
            int closestIndex = -1;

            for (int i = 0; i < pending.size(); i++) {
                int distance = Math.abs(currentHead - pending.get(i));
                if (distance < minDistance) {
                    minDistance = distance;
                    closestTrack = pending.get(i);
                    closestIndex = i;
                }
            }

            totalMovement += minDistance;
            stepMovements.add(minDistance);
            sequence.add(closestTrack);
            currentHead = closestTrack;
            pending.remove(closestIndex);
        }

        return new ScheduleResult(sequence, stepMovements, totalMovement);
    }

    public static String formatReport(String algorithmName, int initialHead, ScheduleResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("--- ESCALONAMENTO DE DISCO: %s ---\n", algorithmName));

        // Sequência
        sb.append("Ordem de Atendimento : ");
        for (int i = 0; i < result.getSequence().size(); i++) {
            sb.append(result.getSequence().get(i));
            if (i < result.getSequence().size() - 1) sb.append(" -> ");
        }
        sb.append("\n");

        // Detalhamento do cálculo
        sb.append("Deslocamento da Cabeça: ");
        List<Integer> seq = result.getSequence();
        for (int i = 0; i < seq.size() - 1; i++) {
            sb.append(String.format("|%d-%d|", seq.get(i), seq.get(i + 1)));
            if (i < seq.size() - 2) sb.append(" + ");
        }
        sb.append("\n");

        // Total
        sb.append("Total de Movimento    : ");
        for (int i = 0; i < result.getStepMovements().size(); i++) {
            sb.append(result.getStepMovements().get(i));
            if (i < result.getStepMovements().size() - 1) sb.append(" + ");
        }
        sb.append(String.format(" = %d trilhas\n", result.getTotalMovement()));

        return sb.toString();
    }
}