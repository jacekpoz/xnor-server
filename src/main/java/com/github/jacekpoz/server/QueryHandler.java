package com.github.jacekpoz.server;

import com.github.jacekpoz.common.XnorConstants;
import com.github.jacekpoz.common.exceptions.UnknownQueryException;
import com.github.jacekpoz.common.sendables.Chat;
import com.github.jacekpoz.common.sendables.Message;
import com.github.jacekpoz.common.sendables.User;
import com.github.jacekpoz.common.sendables.database.queries.*;
import com.github.jacekpoz.common.sendables.database.results.*;
import com.github.jacekpoz.server.util.FileUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class QueryHandler {

    private DatabaseConnector connector;

    public QueryHandler() {
        try {
            connector = new DatabaseConnector("jdbc:mysql://localhost:3306/" + XnorConstants.DB_NAME,
                    "xnor-chat-client", "DB_Password_0123456789");
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Result<?> handleQuery(Query<?> q) {
        if (q instanceof MessageQuery mq) return handleMessageQuery(mq);
        else if (q instanceof ChatQuery cq) return handleChatQuery(cq);
        else if (q instanceof UserQuery uq) return handleUserQuery(uq);
        else if (q instanceof FriendRequestQuery frq) return handleFriendRequestQuery(frq);
        else throw new UnknownQueryException(q);
    }

    private MessageResult handleMessageQuery(MessageQuery mq) {
        MessageResult mr = new MessageResult(mq);

        switch (mq.getQueryType()) {
            case GET_MESSAGE -> {
                Message m = connector.getMessage(
                        mq.getValue("messageID", long.class),
                        mq.getValue("chatID", long.class)
                );
                mr.setSuccess(m != null);
                if (m != null) mr.add(m);
            }
            case GET_MESSAGES_IN_CHAT -> {
                List<Message> messages = connector.getMessagesFromChat(
                        mq.getValue("chatID", long.class),
                        mq.getValue("offset", long.class),
                        mq.getValue("limit", long.class)
                );
                mr.setSuccess(!messages.isEmpty());
                mr.add(messages);
            }
            case INSERT_MESSAGE -> {
                Message m = connector.addMessage(
                        mq.getValue("messageID", long.class),
                        mq.getValue("chatID", long.class),
                        mq.getValue("authorID", long.class),
                        mq.getValue("content", String.class)
                );
                mr.setSuccess(m != null);
                if (m != null) {
                    mr.add(m);
                    m.getAttachments().forEach(a -> {
                        String attachmentPath = FileUtil.getAttachmentPath(m.getChatID(), m.getMessageID(), a);

                        try {
                            FileUtil.writeFile(attachmentPath, a.getFileContents());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        connector.insertAttachment(
                                a.getAttachmentID(),
                                m.getMessageID(),
                                m.getChatID(),
                                attachmentPath,
                                a.getAttachmentPosition()
                        );
                    });
                }
            }
            default -> throw new UnknownQueryException(mq);
        }

        return mr;
    }

    private ChatResult handleChatQuery(ChatQuery cq) {
        ChatResult cr = new ChatResult(cq);

        switch (cq.getQueryType()) {
            case GET_CHAT -> {
                Chat c = connector.getChat(
                        cq.getValue("chatID", long.class)
                );
                cr.setSuccess(c != null);
                if (c != null) cr.add(c);
            }
            case GET_USERS_CHATS -> {
                List<Chat> usersChats = connector.getUsersChats(
                        cq.getValue("userID", long.class)
                );
                cr.setSuccess(!usersChats.isEmpty());
                cr.add(usersChats);
            }
            case INSERT_CHAT -> {
                Chat c = connector.createChat(
                        cq.getValue("chatName", String.class),
                        cq.getValue("memberIDs", long[].class)
                );
                cr.setSuccess(c != null);
                if (c != null) cr.add(c);
            }
            default -> throw new UnknownQueryException(cq);
        }
        return cr;
    }

    private UserResult handleUserQuery(UserQuery uq) {
        UserResult ur = new UserResult(uq);

        switch (uq.getQueryType()) {
            case LOGIN -> {
                String salt = connector.getSalt(uq.getValue("username", String.class));
                LoginResult lr = new LoginResult(uq, salt);
                User u = connector.getUser(uq.getValue("username", String.class));
                if (u != null) lr.add(u);
                return lr;
            }
            case REGISTER -> {
                return connector.register(uq);
            }
            case GET_USER -> {
                String username = uq.getValue("username", String.class);
                ur.add(username == null ?
                        connector.getUser(uq.getValue("userID", long.class)) :
                        connector.getUser(username)
                );
                ur.setSuccess(true);
            }
            case GET_MESSAGE_AUTHOR -> {
                Message m = connector.getMessage(
                        uq.getValue("messageID", long.class),
                        uq.getValue("chatID", long.class)
                );
                ur.add(connector.getUser(m.getAuthorID()));
                ur.setSuccess(true);
            }
            case GET_USERS_IN_CHAT -> {
                ur.add(connector.getUsersInChat(uq.getValue("chatID", long.class)));
                ur.setSuccess(!ur.get().isEmpty());
            }
            case DELETE_USER -> {
                ur.add(connector.getUser(uq.getValue("userID", long.class)));
                ur.setSuccess(connector.deleteUser(uq.getValue("userID", long.class)));
            }
            case GET_FRIENDS -> {
                ur.add(connector.getFriends(uq.getValue("userID", long.class)));
                ur.setSuccess(!ur.get().isEmpty());
            }
            case REMOVE_FRIEND -> {
                switch (connector.removeFriend(
                        uq.getValue("userID", long.class),
                        uq.getValue("friendID", long.class)
                )) {
                    case REMOVED_FRIEND -> ur.setSuccess(true);
                    case SAME_USER, SQL_EXCEPTION -> ur.setSuccess(false);
                }
            }
            default -> throw new UnknownQueryException(uq);
        }
        return ur;
    }

    private FriendRequestResult handleFriendRequestQuery(FriendRequestQuery frq) {
        FriendRequestResult frr = new FriendRequestResult(frq);

        switch (frq.getQueryType()) {
            case GET_FRIEND_REQUESTS -> {
                frr.add(connector.getFriendRequests(frq.getValue("userID", long.class)));
                frr.setSuccess(!frr.get().isEmpty());
            }
            case SEND_FRIEND_REQUEST -> {
                switch (connector.sendFriendRequest(
                        frq.getValue("senderID", long.class),
                        frq.getValue("receiverID", long.class)
                )) {
                    case SENT_SUCCESSFULLY -> frr.setSuccess(true);
                    case SAME_USER, ALREADY_SENT, ALREADY_FRIENDS, SQL_EXCEPTION -> frr.setSuccess(false);
                }
            }
            case ACCEPT_FRIEND_REQUEST -> frr.setSuccess(connector.acceptFriendRequest(
                    frq.getValue("senderID", long.class),
                    frq.getValue("receiverID", long.class)
            ));
            case DENY_FRIEND_REQUEST -> {
                connector.denyFriendRequest(
                        frq.getValue("senderID", long.class),
                        frq.getValue("receiverID", long.class)
                );
                frr.setSuccess(true);
            }
            default -> throw new UnknownQueryException(frq);
        }
        return frr;
    }
}
