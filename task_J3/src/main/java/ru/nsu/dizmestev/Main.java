package ru.nsu.dizmestev;

import com.google.gson.Gson;

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

public class Main {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Set<String> visited = ConcurrentHashMap.newKeySet();
    private static final Queue<String> messages = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger inFlight = new AtomicInteger();
    private static final Object LOCK = new Object();
    private static final Semaphore LIMIT = new Semaphore(100);

    private static final Gson GSON = new Gson();

    private record ServerResponse(String message, List<String> successors) {}

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
                        ServerResponse r = GSON.fromJson(resp.body(), ServerResponse.class);
                        if (r.message() != null) {
                            messages.add(r.message());
                        }
                        if (r.successors() != null) {
                            for (String next : r.successors()) {
                                visit(next);
                            }
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

}