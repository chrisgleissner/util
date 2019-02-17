package com.github.chrisgleissner.jutil.sqllog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@ToString
@Getter
@Slf4j
@RequiredArgsConstructor
public class SqlRecording implements Closeable {
    @ToString.Include
    private final String id;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<String> messages = new LinkedList<>();
    private boolean firstWrite = true;
    private OutputStreamWriter osw;

    SqlRecording(String id, OutputStream os, Charset charset) {
        this.id = id;
        if (os != null)
            this.osw = new OutputStreamWriter(os, charset);
    }

    void add(String msg) {
        if (isRecordToStreamEnabled())
            write(msg);
        else {
            lock.writeLock().lock();
            try {
                messages.add(msg);
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    public Collection<String> getAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(messages);
        } finally {
            lock.readLock().unlock();
        }
    }

    boolean isRecordToStreamEnabled() {
        return osw != null;
    }

    void write(String msg) {
        if (firstWrite) {
            writeToStream("[");
            firstWrite = false;
        } else {
            writeToStream(",");
            writeToStream("\n");
        }
        writeToStream(msg);
    }

    private void writeToStream(String s) {
        try {
            osw.write(s);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write to stream: " + s, e);
        }
    }

    @Override
    public void close() {
        if (osw != null) {
            if (!firstWrite) {
                writeToStream("]");
                writeToStream("\n");
            }
            try {
                osw.flush();
                osw = null;
                log.debug("Closed OutputStream for ID {}", id);
            } catch (Exception e) {
                throw new RuntimeException("Could not close OutputStream for ID " + id, e);
            }
        }
        log.info("Stopped recording of SQL for ID {}", id);
    }
}
