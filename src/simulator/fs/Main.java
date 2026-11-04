package simulator.fs;

import simulator.fs.core.VirtualFileSystem;

public class Main {
    public static void main(String[] args) {
        VirtualFileSystem fs = new VirtualFileSystem(16, 4096);

        System.out.println("> mkdir /sistema");
        fs.mkdir("/sistema");

        System.out.println("> mkdir /sistema/logs");
        fs.mkdir("/sistema/logs");

        System.out.println("> create /sistema/logs/app.log --size 10KB");
        fs.create("/sistema/logs/app.log", 10 * 1024);

        System.out.println(fs.renderTree());

        System.out.println("\n> fs_status --details");
        System.out.println(fs.fsStatus());
    }
}