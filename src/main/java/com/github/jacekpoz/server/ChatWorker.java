package com.github.jacekpoz.server;

import com.github.jacekpoz.common.jackson.JsonObjectMapper;
import com.github.jacekpoz.common.sendables.Chat;
import com.github.jacekpoz.common.sendables.Sendable;
import com.github.jacekpoz.common.sendables.User;
import com.github.jacekpoz.common.sendables.database.queries.basequeries.Query;
import com.github.jacekpoz.common.sendables.database.results.LoginResult;
import com.github.jacekpoz.common.sendables.database.results.Result;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class ChatWorker extends Thread {

    @Getter
    private final Socket clientSocket;
    @Getter
    private final Server server;
    @ToString.Include
    @EqualsAndHashCode.Include
    @Getter
    @Setter
    private User currentUser;
    @ToString.Include
    @EqualsAndHashCode.Include
    @Getter
    @Setter
    private Chat currentChat;
    private final PrintWriter out;
    private final BufferedReader in;

    @Getter
    private final JsonObjectMapper mapper;


    public ChatWorker(Socket so, Server se) throws IOException {
        super("ChatThread");
        clientSocket = so;
        server = se;
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        mapper = new JsonObjectMapper();

    }

    @Override
    public void run() {
        ExecutorService executor = Executors.newCachedThreadPool();

        executor.submit(() -> {
            String inputJSON;
            Result<?> output;
            InputHandler ih = new InputHandler(this);
            QueryHandler qh = new QueryHandler();

            try {
                while ((inputJSON = in.readLine()) != null) {
//                    System.out.println("inputJSON: " + inputJSON + "\n");
                    Sendable input = mapper.readValue(inputJSON, Sendable.class);
//                    System.out.println("input: " + input + "\n");

                    if (input instanceof Query) {
                        output = qh.handleQuery((Query<?>) input);
                        if (output instanceof LoginResult lr && output.success())
                            setCurrentUser(lr.get().get(0));
//                        System.out.println("result: " + output + "\n");
                        String json = mapper.writeValueAsString(output);
//                        System.out.println("result json: " + json + "\n");
                        send(json);
                    } else ih.handleInput(input);
                }
            } catch (SocketException e) {
                System.out.println("Thread disconnected: " + this);
                try {
                    out.close();
                    in.close();
                    clientSocket.close();
                    server.getThreads().remove(this);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        });
    }

    public void send(String json) {
        out.println(json);
    }

}
