package ru.nsu.dizmestev;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class ArrayStringList implements StringList {
    private final List<String> list = Collections.synchronizedList(new ArrayList<>());
    private final long delay;
    private final AtomicLong steps = new AtomicLong();

    public ArrayStringList(long delay) {
        this.delay = delay;
    }

    @Override
    public void addFirst(String value) {
        list.add(0, value);
    }

    @Override
    public void sortPass() throws InterruptedException {
        for (int i = 0; ; i++) {
            Thread.sleep(delay);
            synchronized (list) {
                if (i + 1 >= list.size()) {
                    return;
                }
                Thread.sleep(delay);
                steps.incrementAndGet();
                String a = list.get(i);
                String b = list.get(i + 1);
                if (a.compareTo(b) > 0) {
                    list.set(i, b);
                    list.set(i + 1, a);
                }
            }
        }
    }

    @Override
    public long steps() {
        return steps.get();
    }

    @Override
    public Iterator<String> iterator() {
        synchronized (list) {
            return new ArrayList<>(list).iterator();
        }
    }
}
