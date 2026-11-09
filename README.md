# Simulador de Sistema de Arquivos Virtual (VFS), E/S e RAID

Simulador de Sistema de Arquivos desenvolvido em Java para fins educacionais e de análise de Sistemas Operacionais. O projeto integra a abstração de uma camada lógica de diretórios em árvore, alocação física de blocos no disco via i-node, simulador de Entrada e Saída (E/S) com escalonamento da cabeça de leitura/escrita e tolerância a falhas via arranjos RAID.

---

## Recursos Implementados

### Etapa 1: Sistema de Arquivos e Diagnóstico de Disco
* **Navegação e Estrutura Hierárquica:** Suporte a criação de diretórios e arquivos utilizando caminhos absolutos (`/diretorio/subdiretorio`) e relativos (`.`, `..`).
* **Operações CRUD:**
  * `mkdir`: Criação de diretórios.
  * `create`: Criação de arquivos especificando tamanho em bytes ou kilobytes.
  * `delete`: Remoção de arquivos e diretórios vazios com liberação automática de blocos físicos.
* **Alocação Física Indexada (i-node):** Gerenciamento de blocos de tamanho fixo com armazenamento de ponteiros de metadados e dados.
* **Diagnóstico de Fragmentação:** Cálculo automatizado de fragmentação interna (desperdício no último bloco alocado) e verificação de fragmentação externa e necessidade de compactação.

### Etapa 2: Subsistema de E/S e Redundância (RAID)
* **Tradução de Operações de E/S:** Mapeamento de gravações lógicas em solicitações físicas de trilhas e blocos.
* **Escalonamento do Braço de Disco (HDD):** Algoritmos de ordenação e otimização do movimento do braço de leitura/escrita:
  * **SSTF (Shortest Seek Time First):** Atendimento por proximidade da cabeça.
  * **FCFS, SCAN e C-SCAN:** Algoritmos alternativos de busca de trilhas.
* **Redundância e Tolerância a Falhas (RAID):**
  * **RAID 0:** Distribuição de dados (*striping*) sem paridade.
  * **RAID 1:** Espelhamento integral de blocos.
  * **RAID 5:** Distribuição de blocos de dados com paridade distribuída calculada via operação *bitwise* XOR, com simulação de falha de disco e reconstrução dinâmica de dados.

---

## Estrutura do Projeto

O código-fonte é organizado nos seguintes pacotes Java:

```text
src/
└── simulator/
    └── fs/
        ├── Main.java               # Ponto de entrada e rotinas de teste
        ├── core/
        │   └── VirtualFileSystem.java  # Gerenciador da árvore lógica e resolução de caminhos
        ├── disk/
        │   ├── AllocationResult.java   # Encapsulamento de status da alocação física
        │   ├── BlockType.java          # Tipos de bloco (LIVRE, INODE, DADO)
        │   ├── DiskBlock.java          # Abstração de bloco físico
        │   └── DiskManager.java        # Gerenciador de memória/blocos do disco
        ├── io/
        │   ├── DiskRequest.java        # Requisição de E/S com identificação de trilha
        │   ├── DiskScheduler.java      # Algoritmos de escalonamento (SSTF, FCFS, SCAN, C-SCAN)
        │   └── SchedulingAlgorithm.java# Enumeração dos algoritmos de busca
        ├── raid/
        │   ├── PhysicalDisk.java       # Abstração de disco físico individual
        │   ├── RaidController.java     # Controlador de arranjos e cálculo de paridade XOR
        │   └── RaidType.java           # Enumeração dos níveis RAID (RAID0, RAID1, RAID5)
        └── tree/
            └── FSNode.java             # Nó da árvore hierárquica (diretório/arquivo)