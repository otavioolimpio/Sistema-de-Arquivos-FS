# Simulador de Sistema de Arquivos Virtual (VFS), E/S, RAID e Segurança

Simulador de Sistema de Arquivos desenvolvido em Java para fins educacionais e de análise de Sistemas Operacionais. O projeto integra a abstração de uma camada lógica de diretórios em árvore, alocação física de blocos via i-node, subsistema de Entrada e Saída (E/S) com escalonamento do braço de disco, tolerância a falhas via arranjos RAID e camada completa de segurança, controle de acesso (ACL), criptografia em repouso e auditoria de integridade.

---

## Arquitetura e Recursos Implementados

### Etapa 1: Sistema de Arquivos e Diagnóstico de Disco
* **Navegação e Estrutura Hierárquica:** Criação e gerenciamento de diretórios e arquivos com suporte a caminhos absolutos (`/sistema/logs`) e relativos (`.`, `..`).
* **Operações CRUD:**
  * `mkdir`: Criação de diretórios.
  * `create`: Criação de arquivos com definição de tamanho em bytes ou kilobytes.
  * `read_file` / `write_file`: Leitura e gravação de conteúdo nos blocos alocados.
  * `delete`: Remoção de arquivos e diretórios vazios com liberação imediata dos blocos físicos.
* **Alocação Física Indexada (i-node):** Simulação de disco dividido em blocos de tamanho fixo (4 KB), utilizando blocos dedicados de metadados (i-node) com ponteiros para os blocos de dados.
* **Diagnóstico de Fragmentação:** Cálculo automático de fragmentação interna (desperdício no último bloco ocupado de cada arquivo), análise de fragmentação externa e verificação da necessidade de compactação.

### Etapa 2: Subsistema de E/S e Redundância (RAID)
* **Tradução de Operações de E/S:** Mapeamento de gravações lógicas em requisições físicas de trilhas e blocos no controlador de disco.
* **Escalonamento do Braço de Disco (HDD):** Otimização do deslocamento da cabeça de leitura/escrita por meio do algoritmo **SSTF (Shortest Seek Time First)**, com cálculo detalhado do total de trilhas percorridas.
* **Redundância e Tolerância a Falhas (RAID 5):**
  * Distribuição de blocos de dados (*striping*) entre múltiplos discos físicos.
  * Cálculo e rotação de blocos de paridade via operação *bitwise* XOR.
  * Simulação de falha de disco físico e reconstrução dinâmica dos blocos perdidos a partir dos discos remanescentes.

### Etapa 3: Segurança, Controle de Acesso e Integridade
* **Modos de Execução e Privilégios:** Separação entre `MODO_USUARIO` (operações restritas) e `MODO_KERNEL` (`root`, com privilégios administrativos para comandos críticos e gerenciamento do sistema).
* **Identidade e Autenticação:** Cadastro de usuários e grupos (`useradd`, `login`) com armazenamento seguro de credenciais utilizando hash criptográfico **SHA-256 combinado com salt** aleatório.
* **Controle de Acesso (ACL / Matriz de Permissões):** Associação de permissões octais (`rwx`) para Proprietário, Grupo e Outros em cada nó da árvore (`chmod`, `chown`), com registro de tentativas não autorizadas nos logs de auditoria (`logs`).
* **Criptografia em Repouso:** Cifragem do conteúdo dos arquivos via **AES-128** (`encrypt_file`) antes da persistência nos blocos físicos do disco.
* **Auditoria de Integridade (Antivírus Simulado):** Varredura recursiva (`antivirus_scan`) que verifica assinaturas maliciosas e compara o hash SHA-256 atual dos blocos físicos contra o registro oficial do sistema de arquivos, detectando alterações diretas indevidas (`tamper_disk`).

---

## Estrutura do Projeto

```text
src/
└── simulator/
    └── fs/
        ├── Main.java                       # Interface CLI interativa e cenários de demonstração
        ├── core/
        │   └── VirtualFileSystem.java      # Motor integrado (VFS, E/S, RAID e Segurança)
        ├── disk/
        │   ├── AllocationResult.java       # Resultado das operações de alocação física
        │   ├── BlockType.java              # Tipos de bloco (LIVRE, INODE, DADO)
        │   ├── DiskBlock.java              # Representação do bloco físico e payload bruto
        │   └── DiskManager.java            # Gerenciador de blocos do disco virtual
        ├── io/
        │   ├── DiskRequest.java            # Requisição de E/S com endereçamento de trilha
        │   ├── DiskScheduler.java          # Escalonador de braço de disco (SSTF)
        │   └── SchedulingAlgorithm.java    # Enumeração dos algoritmos de escalonamento
        ├── raid/
        │   ├── PhysicalDisk.java           # Abstração de unidade física de disco
        │   ├── RaidController.java         # Controlador RAID 5 e reconstrução via XOR
        │   └── RaidType.java               # Enumeração dos níveis RAID (RAID0, RAID1, RAID5)
        ├── security/
        │   ├── AccessControl.java          # Validação de ACL (rwx) e formatação octal
        │   ├── ExecutionMode.java          # Modos de execução (MODO_USUARIO, MODO_KERNEL)
        │   ├── SecurityManager.java        # Autenticação SHA-256+salt, AES-128 e Antivírus
        │   └── User.java                   # Modelo de usuário, grupo e credenciais
        └── tree/
            └── FSNode.java                 # Nó da árvore hierárquica com metadados de segurança