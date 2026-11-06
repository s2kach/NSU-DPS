package ru.nsu.dizmestev;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {
    private static final int MAX_LENGTH = 80;

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Usage: Main <threads> <delayMs> <linked|array>");
            return;
        }
        int threads = Integer.parseInt(args[0]);
        long delay = Long.parseLong(args[1]);
        StringList list = switch (args[2]) {
            case "linked" -> new LinkedStringList(delay);
            case "array" -> new ArrayStringList(delay);
            default -> throw new IllegalArgumentException("Unknown list: " + args[2]);
        };

        for (int i = 0; i < threads; i++) {
            Thread sorter = new Thread(() -> sort(list));
            sorter.setDaemon(true);
            sorter.start();
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                for (String value : list) {
                    System.out.println(value);
                }
                System.out.println("Steps: " + list.steps());
            } else {
                for (int i = 0; i < line.length(); i += MAX_LENGTH) {
                    list.addFirst(line.substring(i, Math.min(i + MAX_LENGTH, line.length())));
                }
            }
        }
    }

    private static void sort(StringList list) {
        try {
            while (true) {
                list.sortPass();
            }
        } catch (InterruptedException ignored) {
        }
    }
}
