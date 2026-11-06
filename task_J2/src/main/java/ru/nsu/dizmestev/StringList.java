package ru.nsu.dizmestev;

public interface StringList extends Iterable<String> {
    void addFirst(String value);

    void sortPass() throws InterruptedException;

    long steps();
}
