package com.github.jacekpoz.server;

import java.io.IOException;
import java.net.ServerSocket;

public class XnorServerMain {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Correct usage: java -jar xnor-server-x.x.x.jar <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);

        try {
            XnorServer s = new XnorServer(new ServerSocket(port));
            s.start();
        } catch (IOException e) {
            System.err.println("Couldn't listen on port " + port);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
