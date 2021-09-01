package com.github.jacekpoz.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.jacekpoz.common.exceptions.UnknownSendableException;
import com.github.jacekpoz.common.sendables.Chat;
import com.github.jacekpoz.common.sendables.Message;
import com.github.jacekpoz.common.sendables.Sendable;
import com.github.jacekpoz.common.sendables.User;

public class InputHandler {

    private final ChatWorker worker;

    public InputHandler(ChatWorker w) {
        worker = w;
    }

    public void handleInput(Sendable input) {
        if (input instanceof User u) handleUser(u);
        else if (input instanceof Chat c) handleChat(c);
        else if (input instanceof Message m) handleMessage(m);
        else throw new UnknownSendableException(input);
    }

    private void handleUser(User u) {
        worker.setCurrentUser(u);
    }

    private void handleChat(Chat c) {
        worker.setCurrentChat(c);
    }

    private void handleMessage(Message m) {
        try {
            for (ChatWorker ct : worker.getServer().getThreads())
                if (worker.getCurrentChat().getMemberIDs().contains(ct.getCurrentUser().getUserID()) &&
                        !worker.getCurrentUser().equals(ct.getCurrentUser()))
                    ct.send(worker.getMapper().writeValueAsString(m));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
