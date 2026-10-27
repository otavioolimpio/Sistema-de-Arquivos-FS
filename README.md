# Simulator de Sistema de Arquivos em Memória (VFS)

Simulador de Sistema de Arquivos Virtual desenvolvido em Java para fins educacionais e de análise de Sistemas Operacionais. O projeto implementa uma camada lógica de diretórios em árvore e uma camada de gerenciamento físico de blocos de disco com suporte à alocação indexada (i-node), relatório de ocupação e diagnóstico de fragmentação.

---

## Recursos Implementados

* **Navegação e Estrutura Hierárquica:** Suporte a criação de diretórios e arquivos utilizando caminhos absolutos (`/diretorio/subdiretorio`) e relativos (`.`, `..`).
* **Operações de Sistema de Arquivos (CRUD):**
  * `mkdir`: Criação de diretórios na hierarquia.
  * `create`: Criação de arquivos com especificação de tamanho em bytes ou kilobytes.
  * `delete`: Remoção de arquivos e diretórios vazios com liberação automática de blocos físicos.
* **Gerenciamento de Disco e Alocação Física:**
  * Simulação de disco dividido em blocos de tamanho fixo configurável (padrão: 4 KB).
  * Estratégia de alocação indexada via i-node (bloco dedicado contendo ponteiros de metadados e blocos de dados associados).
* **Diagnóstico e Telemetria:**
  * Exibição visual da árvore de diretórios.
  * Mapeamento do estado do disco (blocos livres, blocos i-node e blocos de dados).
  * Análise estatística de fragmentação interna (desperdício por arquivo no último bloco ocupado).
  * Análise de fragmentação externa e indicação automatizada sobre necessidade de compactação.

---

## Estrutura do Projeto

O código-fonte é organizado nos seguintes pacotes Java:

```text
src/
└── simulator/
    └── fs/
        ├── Main.java               # Ponto de entrada e script de demonstração
        ├── core/
        │   └── VirtualFileSystem.java  # Motor principal e resolução de caminhos
        ├── disk/
        │   ├── AllocationResult.java   # Encapsulamento do resultado de alocação
        │   ├── BlockType.java          # Enumeração dos tipos de bloco (LIVRE, INODE, DADO)
        │   ├── DiskBlock.java          # Abstração de bloco físico
        │   └── DiskManager.java        # Gerenciador da camada física do disco
        └── tree/
            └── FSNode.java             # Nó da estrutura em árvore (diretório/arquivo)
