package ru.nsu.dizmestev;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private record Result(String name, byte[] data) {
    }

    private final Selector selector;
    private final ExecutorService generators;
    private final Issuer issuer;
    private final Map<String, byte[]> ready = new HashMap<>();
    private final Map<String, List<SelectionKey>> waiting = new HashMap<>();
    private final Queue<Result> finished = new ConcurrentLinkedQueue<>();

    public Server(int threads, Issuer issuer) throws IOException {
        this.selector = Selector.open();
        this.generators = Executors.newFixedThreadPool(threads);
        this.issuer = issuer;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.out.println("Usage: Server <port> <threads> <ca.key> <issuer>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        int threads = Integer.parseInt(args[1]);
        Issuer issuer = new Issuer(Path.of(args[2]), args[3]);
        new Server(threads, issuer).run(port);
    }

    public void run(int port) throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(port));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Listening on port " + port);

        while (true) {
            selector.select();
            sendFinished();
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                try {
                    if (key.isAcceptable()) {
                        accept(server);
                    } else if (key.isReadable()) {
                        read(key);
                    } else if (key.isWritable()) {
                        write(key);
                    }
                } catch (IOException e) {
                    close(key);
                }
            }
        }
    }

    private void accept(ServerSocketChannel server) throws IOException {
        SocketChannel client = server.accept();
        if (client == null) {
            return;
        }
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, new StringBuilder());
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(256);
        if (client.read(buffer) == -1) {
            close(key);
            return;
        }
        buffer.flip();
        StringBuilder name = (StringBuilder) key.attachment();
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == 0) {
                key.interestOps(0);
                request(key, name.toString());
                return;
            }
            name.append((char) b);
        }
    }

    private void request(SelectionKey key, String name) {
        System.out.println("Request: " + name);
        if (ready.containsKey(name)) {
            send(key, ready.get(name));
            return;
        }
        if (waiting.containsKey(name)) {
            waiting.get(name).add(key);
            return;
        }
        List<SelectionKey> clients = new ArrayList<>();
        clients.add(key);
        waiting.put(name, clients);
        generators.submit(() -> generate(name));
    }

    private void generate(String name) {
        byte[] data = null;
        try {
            data = issuer.issue(name);
            System.out.println("Generated: " + name);
        } catch (Exception e) {
            System.out.println("Failed: " + name + " " + e);
        }
        finished.add(new Result(name, data));
        selector.wakeup();
    }

    private void sendFinished() {
        Result result;
        while ((result = finished.poll()) != null) {
            List<SelectionKey> clients = waiting.remove(result.name());
            if (result.data() != null) {
                ready.put(result.name(), result.data());
            }
            for (SelectionKey key : clients) {
                if (result.data() != null) {
                    send(key, result.data());
                } else {
                    close(key);
                }
            }
        }
    }

    private void send(SelectionKey key, byte[] data) {
        if (!key.isValid()) {
            return;
        }
        key.attach(ByteBuffer.wrap(data));
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void write(SelectionKey key) throws IOException {
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        ((SocketChannel) key.channel()).write(buffer);
        if (!buffer.hasRemaining()) {
            close(key);
        }
    }

    private void close(SelectionKey key) {
        key.cancel();
        try {
            key.channel().close();
        } catch (IOException ignored) {
        }
    }
}
