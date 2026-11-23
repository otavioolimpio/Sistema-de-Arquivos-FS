package simulator.fs;

import simulator.fs.core.VirtualFileSystem;
import simulator.fs.security.ExecutionMode;
import simulator.fs.security.User;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        // Disco de 32 blocos de 4 KB (128 KB)
        VirtualFileSystem fs = new VirtualFileSystem(32, 4096);
        bootstrapSystemEnvironment(fs);

        Scanner scanner = new Scanner(System.in);
        System.out.println("=================================================================");
        System.out.println("   SIMULADOR DE SISTEMA DE ARQUIVOS (VFS + E/S + RAID + SEC)     ");
        System.out.println("   Digite 'demo' para rodar os Exemplos 3.1 e 3.2 ou 'help'.     ");
        System.out.println("=================================================================\n");

        while (true) {
            User current = fs.getSecurity().getCurrentUser();
            String promptSymbol = current.getMode() == ExecutionMode.MODO_KERNEL ? "#" : "$";
            System.out.print(String.format("[%s@vfs %s]%s ", current.getUsername(), current.getMode(), promptSymbol));

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            String[] tokens = input.split("\\s+");
            String command = tokens[0].toLowerCase();

            switch (command) {
                case "exit":
                case "quit":
                    scanner.close();
                    return;

                case "help":
                    printHelp();
                    break;

                case "demo":
                    runStage3Demo(fs);
                    break;

                case "login":
                    handleLogin(fs, scanner, tokens);
                    break;

                case "mkdir":
                    if (tokens.length < 2) System.out.println("Uso: mkdir <caminho>");
                    else System.out.println(fs.mkdir(tokens[1]));
                    break;

                case "create":
                    if (tokens.length < 4 || !tokens[2].equalsIgnoreCase("--size")) {
                        System.out.println("Uso: create <caminho> --size <tamanhoKB>");
                    } else {
                        int bytes = parseSize(tokens[3]);
                        System.out.println(fs.create(tokens[1], bytes));
                    }
                    break;

                case "read_file":
                    if (tokens.length < 2) System.out.println("Uso: read_file <caminho>");
                    else System.out.println(fs.readFile(tokens[1]));
                    break;

                case "write_file":
                    if (tokens.length < 3) {
                        System.out.println("Uso: write_file <caminho> <conteudo>");
                    } else {
                        String content = input.substring(input.indexOf(tokens[1]) + tokens[1].length()).trim().replace("\"", "");
                        System.out.println(fs.writeFile(tokens[1], content, true));
                    }
                    break;

                case "delete":
                    if (tokens.length < 2) System.out.println("Uso: delete <caminho>");
                    else System.out.println(fs.delete(tokens[1]));
                    break;

                case "chmod":
                    if (tokens.length < 3) System.out.println("Uso: chmod <octal> <caminho> (ex: chmod 750 /sistema/config.sys)");
                    else System.out.println(fs.chmod(tokens[2], Integer.parseInt(tokens[1], 8)));
                    break;

                case "chown":
                    if (tokens.length < 4) System.out.println("Uso: chown <usuario> <grupo> <caminho>");
                    else System.out.println(fs.chown(tokens[3], tokens[1], tokens[2]));
                    break;

                case "useradd":
                    if (!fs.getSecurity().isKernelMode()) {
                        System.out.println("[NEGADO]: Cadastro de usuários exige MODO_KERNEL (root).");
                    } else if (tokens.length < 4) {
                        System.out.println("Uso: useradd <usuario> <grupo> <senha>");
                    } else {
                        fs.getSecurity().registerUser(tokens[1], tokens[2], tokens[3], ExecutionMode.MODO_USUARIO);
                        System.out.println("Usuário '" + tokens[1] + "' cadastrado com hash SHA-256 + salt.");
                    }
                    break;

                case "encrypt_file":
                    if (tokens.length < 4 || !tokens[2].equalsIgnoreCase("--key")) {
                        System.out.println("Uso: encrypt_file <caminho> --key \"<chave>\"");
                    } else {
                        String key = tokens[3].replace("\"", "");
                        System.out.println(fs.encryptFile(tokens[1], key));
                    }
                    break;

                case "antivirus_scan":
                    String scanPath = tokens.length >= 2 ? tokens[1] : "/";
                    System.out.println(fs.antivirusScan(scanPath));
                    break;

                case "tamper_disk":
                    if (tokens.length < 2) System.out.println("Uso: tamper_disk <caminho>");
                    else System.out.println(fs.simulateDirectDiskTampering(tokens[1], "MODIFICACAO_NAO_AUTORIZADA_NO_BLOCO"));
                    break;

                case "logs":
                    System.out.println("\n[LOGS DE SEGURANÇA DO SISTEMA]");
                    for (String log : fs.getSecurity().getSecurityLogs()) {
                        System.out.println(" - " + log);
                    }
                    break;

                case "tree":
                    System.out.println(fs.renderTree());
                    break;

                case "fs_status":
                    System.out.println(fs.fsStatus());
                    break;

                default:
                    System.out.println("Comando não reconhecido. Digite 'help'.");
                    break;
            }
        }
    }

    private static void bootstrapSystemEnvironment(VirtualFileSystem fs) {
        // Inicia como root (MODO_KERNEL) para configurar o sistema
        fs.mkdir("/sistema");
        fs.mkdir("/sistema/logs");
        fs.createWithContent("/sistema/config.sys", "KERNEL_PARAM=1".getBytes(StandardCharsets.UTF_8), 2048);
        fs.chmod("/sistema/config.sys", 0750); // rwxr-x--- (root:admin)

        fs.createWithContent("/sistema/logs/app.log", "Log inicial do sistema".getBytes(StandardCharsets.UTF_8), 10 * 1024);

        fs.mkdir("/home");
        fs.chmod("/home", 0755);
        fs.mkdir("/home/carlos");
        fs.chown("/home/carlos", "carlos", "devs");
        fs.chmod("/home/carlos", 0750);

        fs.createWithContent("/home/carlos/segredo.txt", "Relatorio Confidencial Q4".getBytes(StandardCharsets.UTF_8), 4096);
        fs.chown("/home/carlos/segredo.txt", "carlos", "devs");
        fs.chmod("/home/carlos/segredo.txt", 0700);
    }

    private static void runStage3Demo(VirtualFileSystem fs) {
        System.out.println("\n=== EXECUTANDO DEMONSTRAÇÃO DA ETAPA 3 ===\n");

        // Exemplo 3.1: Autenticação e Verificação de ACL
        System.out.println("> login --user carlos");
        System.out.println("Senha: **********");
        fs.getSecurity().authenticate("carlos", "senha12345");
        User u = fs.getSecurity().getCurrentUser();
        System.out.println(String.format("Autenticado com sucesso. Usuário: '%s' | Grupo: '%s' | Modo: %s\n",
                u.getUsername(), u.getGroup(), u.getMode()));

        System.out.println("> read_file /sistema/config.sys");
        System.out.println(fs.readFile("/sistema/config.sys") + "\n");

        // Exemplo 3.2: Criptografia de Conteúdo e Anti-Vírus Simulado
        System.out.println("> encrypt_file /home/carlos/segredo.txt --key \"MinhaChave123\"");
        System.out.println(fs.encryptFile("/home/carlos/segredo.txt", "MinhaChave123") + "\n");

        // Simula alteração direta indevida no bloco físico de app.log
        fs.simulateDirectDiskTampering("/sistema/logs/app.log", "ALTERACAO_INDEVIDA_RAW_BLOCK");

        System.out.println("> antivirus_scan /");
        System.out.println(fs.antivirusScan("/"));
    }

    private static void handleLogin(VirtualFileSystem fs, Scanner scanner, String[] tokens) {
        if (tokens.length < 3 || !tokens[1].equalsIgnoreCase("--user")) {
            System.out.println("Uso: login --user <usuario>");
            return;
        }
        String username = tokens[2];
        System.out.print("Senha: ");
        String password = scanner.nextLine();

        if (fs.getSecurity().authenticate(username, password)) {
            User u = fs.getSecurity().getCurrentUser();
            System.out.println(String.format("Autenticado com sucesso. Usuário: '%s' | Grupo: '%s' | Modo: %s",
                    u.getUsername(), u.getGroup(), u.getMode()));
        } else {
            System.out.println("[FALHA DE AUTENTICAÇÃO]: Usuário ou senha inválidos.");
        }
    }

    private static int parseSize(String sizeStr) {
        sizeStr = sizeStr.toUpperCase().trim();
        if (sizeStr.endsWith("KB")) {
            return (int) (Double.parseDouble(sizeStr.replace("KB", "").trim()) * 1024);
        }
        return Integer.parseInt(sizeStr.replace("B", "").trim());
    }

    private static void printHelp() {
        System.out.println("\nComandos Disponíveis (Etapas 1, 2 e 3):");
        System.out.println("  demo                                        Executa os Exemplos 3.1 e 3.2");
        System.out.println("  login --user <usuario>                      Autentica usuário (senhas padrão: root123 / senha12345)");
        System.out.println("  useradd <user> <grupo> <senha>              Cadastra usuário [MODO_KERNEL]");
        System.out.println("  mkdir <caminho>                             Cria diretório");
        System.out.println("  create <caminho> --size <KB>                Cria arquivo e aloca blocos");
        System.out.println("  read_file <caminho>                         Lê arquivo validando ACL");
        System.out.println("  write_file <caminho> <texto>                Grava dados com E/S SSTF e RAID 5");
        System.out.println("  delete <caminho>                            Remove arquivo/diretório");
        System.out.println("  chmod <octal> <caminho>                     Altera permissões rwx (ex: 750)");
        System.out.println("  chown <user> <grupo> <caminho>              Altera proprietário [MODO_KERNEL]");
        System.out.println("  encrypt_file <caminho> --key \"<chave>\"      Criptografa arquivo em repouso (AES)");
        System.out.println("  antivirus_scan <caminho>                    Audita hashes dos blocos e assinaturas");
        System.out.println("  tamper_disk <caminho>                       Simula corrupção direta no bloco físico");
        System.out.println("  logs                                        Exibe tentativas de acesso negadas");
        System.out.println("  tree | fs_status | exit\n");
    }
}