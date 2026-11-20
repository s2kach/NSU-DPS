package ru.nsu.dizmestev;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Set<String> visited = ConcurrentHashMap.newKeySet();
    private static final Queue<String> messages = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger inFlight = new AtomicInteger();
    private static final Object LOCK = new Object();
    private static final Semaphore LIMIT = new Semaphore(100);

    private static final Pattern STRING_VALUE = Pattern.compile(":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static String host;
    private static int port;
    private static String base;
    private static ExecutorService executor;

    public static void main(String[] args) throws Exception {
        host = args.length > 0 ? args[0] : DEFAULT_HOST;
        port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        base = "http://" + host + ":" + port;
        executor = Executors.newVirtualThreadPerTaskExecutor();

        visit("/");

        synchronized (LOCK) {
            while (inFlight.get() > 0) {
                LOCK.wait();
            }
        }
        executor.close();

        List<String> sorted = new ArrayList<>(messages);
        Collections.sort(sorted);
        for (String m : sorted) {
            System.out.println(m);
        }
    }

    private static void visit(String path) {
        String p = path.startsWith("/") ? path : "/" + path;
        if (!visited.add(p)) {
            return;
        }
        inFlight.incrementAndGet();
        executor.submit(() -> {
            try {
                LIMIT.acquire();
                try {
                    HttpRequest req = HttpRequest.newBuilder(toUri(p))
                            .timeout(Duration.ofSeconds(60))
                            .GET()
                            .build();
                    HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        String body = resp.body();
                        String msg = extractString(body, "message");
                        if (msg != null) {
                            messages.add(msg);
                        }
                        for (String next : extractArray(body, "successors")) {
                            visit(next);
                        }
                    } else {
                        System.err.println("status " + resp.statusCode() + " for " + p);
                    }
                } finally {
                    LIMIT.release();
                }
            } catch (Exception e) {
                System.err.println("error " + p + ": " + e);
            } finally {
                if (inFlight.decrementAndGet() == 0) {
                    synchronized (LOCK) {
                        LOCK.notifyAll();
                    }
                }
            }
        });
    }

    private static URI toUri(String path) throws Exception {
        try {
            return URI.create(base + path);
        } catch (IllegalArgumentException e) {
            return new URI("http", null, host, port, path, null, null);
        }
    }

    private static String extractString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*" + STRING_VALUE.pattern())
                .matcher(json);
        return m.find() ? unescape(m.group(1)) : null;
    }

    private static List<String> extractArray(String json, String key) {
        List<String> res = new ArrayList<>();
        int keyPos = json.indexOf('"' + key + '"');
        if (keyPos < 0) {
            return res;
        }
        int open = json.indexOf('[', keyPos);
        int close = -1;
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = open; i >= 0 && i < json.length(); i++) {
            char c = json.charAt(i);
            if (inStr) {
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
            } else if (c == '"') {
                inStr = true;
            } else if (c == '[') {
                depth++;
            } else if (c == ']' && --depth == 0) {
                close = i;
                break;
            }
        }
        if (open < 0 || close < 0) {
            return res;
        }
        Matcher m = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"")
                .matcher(json.substring(open + 1, close));
        while (m.find()) {
            String s = m.group();
            res.add(unescape(s.substring(1, s.length() - 1)));
        }
        return res;
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> sb.append(n);
            }
        }
        return sb.toString();
    }
}