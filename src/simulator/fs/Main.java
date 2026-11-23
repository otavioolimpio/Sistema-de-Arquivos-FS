package simulator.fs;

import simulator.fs.core.VirtualFileSystem;
import simulator.fs.memory.ProcessType;
import simulator.fs.security.ExecutionMode;
import simulator.fs.security.User;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args) {
        VirtualFileSystem fs = new VirtualFileSystem(32, 4096);
        bootstrapSystemEnvironment(fs);

        Scanner scanner = new Scanner(System.in);
        System.out.println("=======================================================================");
        System.out.println("   SIMULADOR DE SO INTEGRADO (VFS + E/S + RAID + SEGURANÇA + MEMÓRIA)  ");
        System.out.println("   Digite 'demo_mem' (Etapa 4), 'demo' (Etapa 3) ou 'help'.            ");
        System.out.println("=======================================================================\n");

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

                case "demo_mem":
                    runStage4Demo(fs);
                    break;

                case "demo":
                    runStage3Demo(fs);
                    break;

                case "create_process":
                    handleCreateProcess(fs, input);
                    break;

                case "terminate_process":
                    handleTerminateProcess(fs, input, tokens);
                    break;

                case "memory_status":
                    if (tokens.length > 1 && tokens[1].equalsIgnoreCase("--virtual")) {
                        System.out.println(fs.getMemory().getVirtualMemoryStatus());
                    } else if (tokens.length > 1 && tokens[1].equalsIgnoreCase("--reserved")) {
                        System.out.println(fs.getMemory().getReservedMemoryStatus());
                    } else {
                        System.out.println(fs.getMemory().getReservedMemoryStatus());
                        System.out.println("\n" + fs.getMemory().getVirtualMemoryStatus());
                    }
                    break;

                case "memory_compact":
                    System.out.println(fs.getMemory().compactReservedMemory());
                    break;

                case "translate_address":
                    handleTranslateAddress(fs, input);
                    break;

                case "execute_step":
                    handleExecuteStep(fs, input);
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
                        System.out.println(fs.create(tokens[1], parseSize(tokens[3])));
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
                    if (tokens.length < 3) System.out.println("Uso: chmod <octal> <caminho>");
                    else System.out.println(fs.chmod(tokens[2], Integer.parseInt(tokens[1], 8)));
                    break;

                case "chown":
                    if (tokens.length < 4) System.out.println("Uso: chown <usuario> <grupo> <caminho>");
                    else System.out.println(fs.chown(tokens[3], tokens[1], tokens[2]));
                    break;

                case "encrypt_file":
                    if (tokens.length < 4 || !tokens[2].equalsIgnoreCase("--key")) {
                        System.out.println("Uso: encrypt_file <caminho> --key \"<chave>\"");
                    } else {
                        System.out.println(fs.encryptFile(tokens[1], tokens[3].replace("\"", "")));
                    }
                    break;

                case "antivirus_scan":
                    System.out.println(fs.antivirusScan(tokens.length >= 2 ? tokens[1] : "/"));
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
        fs.mkdir("/sistema");
        fs.mkdir("/sistema/logs");
        fs.mkdir("/sistema/swap");
        fs.createWithContent("/sistema/swap/pagefile.sys", new byte[4096], 4096);
        fs.createWithContent("/sistema/config.sys", "KERNEL_PARAM=1".getBytes(StandardCharsets.UTF_8), 2048);
        fs.chmod("/sistema/config.sys", 0750);
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

    private static void runStage4Demo(VirtualFileSystem fs) {
        System.out.println("\n=== EXECUTANDO DEMONSTRAÇÃO DA ETAPA 4 (MEMÓRIA HÍBRIDA, COMPACTAÇÃO E LRU) ===\n");

        // Exemplo 4.1
        System.out.println("> create_process --name \"Kernel_DiskDriver\" --size 512KB --type critical");
        System.out.println(fs.createProcess("Kernel_DiskDriver", 512 * 1024, ProcessType.CRITICAL) + "\n");

        System.out.println("> create_process --name \"Kernel_NetDriver\" --size 1024KB --type critical");
        System.out.println(fs.createProcess("Kernel_NetDriver", 1024 * 1024, ProcessType.CRITICAL) + "\n");

        System.out.println("> create_process --name \"Kernel_CryptoEngine\" --size 512KB --type critical");
        System.out.println(fs.createProcess("Kernel_CryptoEngine", 512 * 1024, ProcessType.CRITICAL) + "\n");

        System.out.println("> terminate_process --name \"Kernel_NetDriver\"");
        System.out.println(fs.terminateProcess("Kernel_NetDriver") + "\n");

        System.out.println("> memory_status --reserved");
        System.out.println(fs.getMemory().getReservedMemoryStatus() + "\n");

        System.out.println("> memory_compact --reserved");
        System.out.println(fs.getMemory().compactReservedMemory() + "\n");

        // Exemplo 4.2
        System.out.println("> create_process --name \"App_Navegador\" --size 10KB --type normal");
        System.out.println(fs.createProcess("App_Navegador", 10 * 1024, ProcessType.NORMAL) + "\n");

        System.out.println("> translate_address --process App_Navegador --page 1");
        System.out.println(fs.getMemory().translateAddress("App_Navegador", 1) + "\n");

        System.out.println("> execute_step --process App_Navegador --instruction \"LOAD_DATA Página 2\"");
        System.out.println(fs.executeMemoryStep("App_Navegador", 2) + "\n");
    }

    private static void runStage3Demo(VirtualFileSystem fs) {
        System.out.println("\n=== EXECUTANDO DEMONSTRAÇÃO DA ETAPA 3 ===\n");
        fs.getSecurity().authenticate("carlos", "senha12345");
        System.out.println(fs.readFile("/sistema/config.sys") + "\n");
        System.out.println(fs.encryptFile("/home/carlos/segredo.txt", "MinhaChave123") + "\n");
        fs.simulateDirectDiskTampering("/sistema/logs/app.log", "ALTERACAO_INDEVIDA_RAW_BLOCK");
        System.out.println(fs.antivirusScan("/"));
    }

    private static void handleCreateProcess(VirtualFileSystem fs, String input) {
        String name = extractFlagValue(input, "--name");
        String sizeStr = extractFlagValue(input, "--size");
        String typeStr = extractFlagValue(input, "--type");

        if (name == null || sizeStr == null || typeStr == null) {
            System.out.println("Uso: create_process --name \"<nome>\" --size <tamanhoKB> --type <critical|normal>");
            return;
        }
        int sizeBytes = parseSize(sizeStr);
        ProcessType type = typeStr.equalsIgnoreCase("critical") ? ProcessType.CRITICAL : ProcessType.NORMAL;
        System.out.println(fs.createProcess(name, sizeBytes, type));
    }

    private static void handleTerminateProcess(VirtualFileSystem fs, String input, String[] tokens) {
        String name = extractFlagValue(input, "--name");
        if (name == null && tokens.length >= 2) {
            name = tokens[1].replace("\"", "");
        }
        if (name == null) {
            System.out.println("Uso: terminate_process --name \"<nome>\"");
            return;
        }
        System.out.println(fs.terminateProcess(name));
    }

    private static void handleTranslateAddress(VirtualFileSystem fs, String input) {
        String process = extractFlagValue(input, "--process");
        String pageStr = extractFlagValue(input, "--page");
        if (process == null || pageStr == null) {
            System.out.println("Uso: translate_address --process <nome> --page <numero>");
            return;
        }
        System.out.println(fs.getMemory().translateAddress(process, Integer.parseInt(pageStr)));
    }

    private static void handleExecuteStep(VirtualFileSystem fs, String input) {
        String process = extractFlagValue(input, "--process");
        String instruction = extractFlagValue(input, "--instruction");
        if (process == null || instruction == null) {
            System.out.println("Uso: execute_step --process <nome> --instruction \"LOAD_DATA Página <num>\"");
            return;
        }
        Matcher m = Pattern.compile("(\\d+)").matcher(instruction);
        if (!m.find()) {
            System.out.println("Erro: Número da página não identificado na instrução.");
            return;
        }
        int pageNum = Integer.parseInt(m.group(1));
        System.out.println(fs.executeMemoryStep(process, pageNum));
    }

    private static String extractFlagValue(String input, String flag) {
        Pattern p = Pattern.compile(Pattern.quote(flag) + "\\s+(?:\"([^\"]+)\"|(\\S+))", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(input);
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
        return null;
    }

    private static void handleLogin(VirtualFileSystem fs, Scanner scanner, String[] tokens) {
        if (tokens.length < 3 || !tokens[1].equalsIgnoreCase("--user")) {
            System.out.println("Uso: login --user <usuario>");
            return;
        }
        System.out.print("Senha: ");
        String password = scanner.nextLine();
        if (fs.getSecurity().authenticate(tokens[2], password)) {
            User u = fs.getSecurity().getCurrentUser();
            System.out.println(String.format("Autenticado com sucesso. Usuário: '%s' | Grupo: '%s' | Modo: %s",
                    u.getUsername(), u.getGroup(), u.getMode()));
        } else {
            System.out.println("[FALHA DE AUTENTICAÇÃO]: Usuário ou senha inválidos.");
        }
    }

    private static int parseSize(String sizeStr) {
        sizeStr = sizeStr.toUpperCase().trim();
        if (sizeStr.endsWith("MB")) {
            return (int) (Double.parseDouble(sizeStr.replace("MB", "").trim()) * 1024 * 1024);
        }
        if (sizeStr.endsWith("KB")) {
            return (int) (Double.parseDouble(sizeStr.replace("KB", "").trim()) * 1024);
        }
        return Integer.parseInt(sizeStr.replace("B", "").trim());
    }

    private static void printHelp() {
        System.out.println("\nComandos de Memória (Etapa 4):");
        System.out.println("  demo_mem                                                     Executa Exemplos 4.1 e 4.2");
        System.out.println("  create_process --name \"<n>\" --size <KB> --type <critical|normal>");
        System.out.println("  terminate_process --name \"<n>\"                               Finaliza processo e libera RAM");
        System.out.println("  memory_status [--reserved | --virtual]                       Exibe mapa e fragmentação");
        System.out.println("  memory_compact --reserved                                    Compacta Área Reservada da RAM");
        System.out.println("  translate_address --process <n> --page <num>                 Traduz Página Lógica -> Moldura");
        System.out.println("  execute_step --process <n> --instruction \"LOAD_DATA Página <num>\"");
        System.out.println("\nComandos de Arquivos, E/S, RAID e Segurança (Etapas 1, 2 e 3):");
        System.out.println("  demo | login --user <u > | mkdir | create | read_file | write_file | delete");
        System.out.println("  chmod | chown | encrypt_file | antivirus_scan | tamper_disk | logs | tree | fs_status | exit\n");
    }
}