package ru.nsu.dizmestev;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class LinkedStringList implements StringList {
    private static class Node {
        final String value;
        final ReentrantLock lock = new ReentrantLock();
        Node next;

        Node(String value, Node next) {
            this.value = value;
            this.next = next;
        }
    }

    private final Node head = new Node(null, null);
    private final long delay;
    private final AtomicLong steps = new AtomicLong();

    public LinkedStringList(long delay) {
        this.delay = delay;
    }

    @Override
    public void addFirst(String value) {
        head.lock.lock();
        head.next = new Node(value, head.next);
        head.lock.unlock();
    }

    @Override
    public void sortPass() throws InterruptedException {
        Node prev = head;
        while (true) {
            Thread.sleep(delay);
            prev.lock.lock();
            Node a = prev.next;
            if (a == null) {
                prev.lock.unlock();
                return;
            }
            a.lock.lock();
            Node b = a.next;
            if (b == null) {
                a.lock.unlock();
                prev.lock.unlock();
                return;
            }
            b.lock.lock();

            Thread.sleep(delay);
            steps.incrementAndGet();
            Node nextPrev = a;
            if (a.value.compareTo(b.value) > 0) {
                prev.next = b;
                a.next = b.next;
                b.next = a;
                nextPrev = b;
            }

            b.lock.unlock();
            a.lock.unlock();
            prev.lock.unlock();
            prev = nextPrev;
        }
    }

    @Override
    public long steps() {
        return steps.get();
    }

    @Override
    public Iterator<String> iterator() {
        head.lock.lock();
        return new Iterator<>() {
            private Node current = head;

            @Override
            public boolean hasNext() {
                if (current.next != null) {
                    return true;
                }
                current.lock.unlock();
                return false;
            }

            @Override
            public String next() {
                Node node = current.next;
                node.lock.lock();
                current.lock.unlock();
                current = node;
                return node.value;
            }
        };
    }
}
