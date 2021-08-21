package com.github.jacekpoz.server;

import lombok.Getter;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class XnorServer {

    private final ServerSocket serverSocket;
    private ExecutorService executor;
    @Getter
    private final List<ChatWorker> threads;

    public XnorServer(ServerSocket ss) throws IOException {
        serverSocket = ss;
        executor = Executors.newCachedThreadPool();
        threads = new ArrayList<>();
    }

    public void start() throws IOException {
        if (executor.isShutdown()) executor = Executors.newCachedThreadPool();
        executor.submit(() -> {
            try {
                while (true) {
                    ChatWorker thread = new ChatWorker(serverSocket.accept(), this);
                    threads.add(thread);
                    executor.submit(thread);
                }
            } catch (IOException e) {
                System.err.println("Couldn't listen on port " + serverSocket.getLocalPort());
                e.printStackTrace();
            }
        });
    }

    public void stop() throws IOException {
        executor.shutdown();
        serverSocket.close();
    }
}
