package com.github.jacekpoz.server;

import com.github.jacekpoz.common.Constants;
import com.github.jacekpoz.common.exceptions.UnknownQueryException;
import com.github.jacekpoz.common.sendables.Chat;
import com.github.jacekpoz.common.sendables.Message;
import com.github.jacekpoz.common.sendables.database.queries.basequeries.ChatQuery;
import com.github.jacekpoz.common.sendables.database.queries.basequeries.MessageQuery;
import com.github.jacekpoz.common.sendables.database.queries.basequeries.Query;
import com.github.jacekpoz.common.sendables.database.queries.basequeries.UserQuery;
import com.github.jacekpoz.common.sendables.database.queries.chat.GetChatQuery;
import com.github.jacekpoz.common.sendables.database.queries.chat.GetUsersChatsQuery;
import com.github.jacekpoz.common.sendables.database.queries.chat.InsertChatQuery;
import com.github.jacekpoz.common.sendables.database.queries.message.GetMessagesInChatQuery;
import com.github.jacekpoz.common.sendables.database.queries.message.InsertMessageQuery;
import com.github.jacekpoz.common.sendables.database.queries.user.*;
import com.github.jacekpoz.common.sendables.database.results.ChatResult;
import com.github.jacekpoz.common.sendables.database.results.MessageResult;
import com.github.jacekpoz.common.sendables.database.results.Result;
import com.github.jacekpoz.common.sendables.database.results.UserResult;

import java.sql.SQLException;
import java.util.List;

public class QueryHandler {

    private DatabaseConnector connector;

    public QueryHandler() {
        try {
            connector = new DatabaseConnector("jdbc:mysql://localhost:3306/" + Constants.DB_NAME,
                    "xnor-chat-client", System.getenv("DB_PASSWORD"));
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Result<?> handleQuery(Query<?> q) {
        if (q instanceof MessageQuery mq) return handleMessageQuery(mq);
        else if (q instanceof ChatQuery cq) return handleChatQuery(cq);
        else if (q instanceof UserQuery uq) return handleUserQuery(uq);
        else throw new UnknownQueryException(q);
    }

    private MessageResult handleMessageQuery(MessageQuery mq) {
        MessageResult mr = new MessageResult(mq);
        if (mq instanceof GetMessagesInChatQuery gmq) {
            List<Message> messages = connector.getMessagesFromChat(gmq.getChatID(), gmq.getOffset(), gmq.getLimit());
            mr.setSuccess(!messages.isEmpty());
            mr.add(messages);
        } else if (mq instanceof InsertMessageQuery imq) {
            Message m = connector.addMessage(
                    imq.getMessageID(),
                    imq.getChatID(),
                    imq.getAuthorID(),
                    imq.getContent()
            );
            mr.setSuccess(m != null);
            if (mr.getSuccess()) mr.add(m);
        } else {
            throw new UnknownQueryException(mq);
        }
        return mr;
    }

    private ChatResult handleChatQuery(ChatQuery cq) {
        ChatResult cr = new ChatResult(cq);
        if (cq instanceof GetChatQuery gcq) {
            if (gcq instanceof GetUsersChatsQuery gucq) {
                List<Chat> usersChats = connector.getUsersChats(gucq.getUserID());
                cr.setSuccess(!usersChats.isEmpty());
                cr.add(usersChats);
            } else {
                Chat c = connector.getChat(gcq.getChatID());
                cr.setSuccess(c != null);
                if (cr.getSuccess()) cr.add(c);
            }
        } else if (cq instanceof InsertChatQuery icq) {
            Chat c = connector.createChat(icq.getChatName(), icq.getMemberIDs());
            cr.setSuccess(c != null);
            if (cr.getSuccess()) cr.add(c);
            System.out.println("icq: " + icq);
            System.out.println("cr: " + cr);
        } else {
            throw new UnknownQueryException(cq);
        }
        return cr;
    }

    private UserResult handleUserQuery(UserQuery uq) {
        UserResult ur = new UserResult(uq);
        if (uq instanceof GetUserQuery guq) {
            if (guq instanceof LoginQuery lq) {
                return connector.login(lq);
            } else if (guq instanceof GetMessageAuthorQuery gmaq) {
                ur.add(connector.getUser(gmaq.getUserID()));
                ur.setSuccess(true);
            } else {
                String username = guq.getUsername();
                ur.add(username == null ?
                        connector.getUser(guq.getUserID()) :
                        connector.getUser(username)
                );
                ur.setSuccess(true);
            }
        } else if (uq instanceof RegisterQuery rq) {
            return connector.register(rq);
        } else if (uq instanceof SendFriendRequestQuery sfrq) {
            switch (connector.sendFriendRequest(sfrq.getUserID(), sfrq.getFriendID())) {
                case SENT_SUCCESSFULLY -> ur.setSuccess(true);
                case SAME_USER, ALREADY_SENT, ALREADY_FRIENDS, SQL_EXCEPTION -> ur.setSuccess(false);
            }
        } else if (uq instanceof AcceptFriendRequestQuery afrq) {
            ur.setSuccess(connector.acceptFriendRequest(afrq.getUserID(), afrq.getFriendID()));
        } else if (uq instanceof DenyFriendRequestQuery dfrq) {
            connector.denyFriendRequest(dfrq.getUserID(), dfrq.getFriendID());
            ur.setSuccess(true);
        } else if (uq instanceof RemoveFriendQuery rfq) {
            switch (connector.removeFriend(rfq.getUserID(), rfq.getFriendID())) {
                case REMOVED_FRIEND -> ur.setSuccess(true);
                case SAME_USER, SQL_EXCEPTION -> ur.setSuccess(false);
            }
        } else if (uq instanceof GetFriendsQuery gfq) {
            ur.add(connector.getFriends(gfq.getUserID()));
            ur.setSuccess(!ur.get().isEmpty());
        } else if (uq instanceof GetFriendRequestsQuery gfrq) {
            ur.add(connector.getFriendRequests(gfrq.getUserID()));
            ur.setSuccess(!ur.get().isEmpty());
        } else if (uq instanceof GetAllUsersQuery gauq) {
            ur.add(connector.getAllUsers());
            ur.setSuccess(!ur.get().isEmpty());
        } else if (uq instanceof GetUsersInChatQuery guicq) {
            ur.add(connector.getUsersInChat(guicq.getChatID()));
            ur.setSuccess(!ur.get().isEmpty());
        } else if (uq instanceof DeleteUserQuery duq) {
            ur.add(connector.getUser(duq.getUserID()));
            ur.setSuccess(connector.deleteUser(duq.getUserID()));
        } else {
            throw new UnknownQueryException(uq);
        }
        return ur;
    }
}
