package ru.nsu.dizmestev;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Client {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 3) {
            System.out.println("Usage: Client <name> <host> <port> [--delay seconds] [--abort]");
            return;
        }
        String name = args[0];
        String host = args[1];
        int port = Integer.parseInt(args[2]);
        int delay = 0;
        boolean abort = false;
        for (int i = 3; i < args.length; i++) {
            switch (args[i]) {
                case "--delay" -> delay = Integer.parseInt(args[++i]);
                case "--abort" -> abort = true;
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }

        try (Socket socket = new Socket(host, port)) {
            OutputStream out = socket.getOutputStream();
            out.write(name.getBytes(StandardCharsets.US_ASCII));
            out.write(0);
            out.flush();

            Thread.sleep(delay * 1000L);
            if (abort) {
                System.out.println("Aborted");
                return;
            }

            DataInputStream in = new DataInputStream(socket.getInputStream());
            Files.write(Path.of(name + ".key"), readBlock(in));
            Files.write(Path.of(name + ".crt"), readBlock(in));
            System.out.println("Saved " + name + ".key and " + name + ".crt");
        }
    }

    private static byte[] readBlock(DataInputStream in) throws IOException {
        byte[] data = new byte[in.readInt()];
        in.readFully(data);
        return data;
    }
}
