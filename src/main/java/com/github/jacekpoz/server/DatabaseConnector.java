package com.github.jacekpoz.server;

import com.github.jacekpoz.common.Constants;
import com.github.jacekpoz.common.EnumResults;
import com.github.jacekpoz.common.sendables.*;
import com.github.jacekpoz.common.sendables.database.queries.UserQuery;
import com.github.jacekpoz.common.sendables.database.queries.UserQueryEnum;
import com.github.jacekpoz.common.sendables.database.results.LoginResult;
import com.github.jacekpoz.common.sendables.database.results.RegisterResult;
import com.github.jacekpoz.server.util.FileUtil;
import com.kosprov.jargon2.api.Jargon2;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DatabaseConnector {

    private final Connection con;

    public DatabaseConnector(String url, String dbUsername, String dbPassword) throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        con = DriverManager.getConnection(url, dbUsername, dbPassword);
    }

    public RegisterResult register(UserQuery rq) {
        if (rq.getQueryType() != UserQueryEnum.REGISTER) return null;
        RegisterResult returned = new RegisterResult(rq);
        returned.setSuccess(false);
        try (PreparedStatement checkUsername = con.prepareStatement(

                "SELECT username " +
                    "FROM " + Constants.USERS_TABLE +
                    " WHERE username = ?;"
        )) {
            checkUsername.setString(1, rq.getValue("username", String.class));
            ResultSet rs = checkUsername.executeQuery();
            if (rs.next()) {
                rs.close();
                returned.setResult(EnumResults.Register.USERNAME_TAKEN);
                return returned;
            }
            rs.close();

            User registered = createUser(
                    rq.getValue("username", String.class),
                    rq.getValue("hash", String.class)
            );

            returned.add(registered);
            returned.setSuccess(true);
            returned.setResult(EnumResults.Register.ACCOUNT_CREATED);
            return returned;
        } catch (SQLException e) {
            e.printStackTrace();
            returned.setResult(EnumResults.Register.SQL_EXCEPTION);
            returned.setEx(e);
            return returned;
        }
    }

    public LoginResult login(UserQuery lq) {
        if (lq.getQueryType() != UserQueryEnum.LOGIN) return null;
        LoginResult returned = new LoginResult(lq);
        returned.setSuccess(false);
        try (PreparedStatement checkUsername = con.prepareStatement(
                "SELECT * " +
                    "FROM " + Constants.USERS_TABLE +
                    " WHERE username = ?;"
        )) {
            checkUsername.setString(1, lq.getValue("username", String.class));
            ResultSet rs = checkUsername.executeQuery();
            if (!rs.next()) {
                rs.close();
                returned.setResult(EnumResults.Login.ACCOUNT_DOESNT_EXIST);
                return returned;
            }

            String dbHash = rs.getString("password_hash");
            rs.close();

            Jargon2.Verifier v = Jargon2.jargon2Verifier();
            String stringPassword = lq.getValue("password", String.class);
            System.out.println("stringPassword: " + stringPassword);
            byte[] stringToBytePassword = lq.getValue("password", String.class).getBytes(StandardCharsets.UTF_8);
            System.out.println("stringToBytePassword: " + Arrays.toString(stringToBytePassword));
            byte[] bytePassword = lq.getValue("password", byte[].class);
            System.out.println("bytePassword: " + Arrays.toString(bytePassword));

            if (v.hash(dbHash).password(bytePassword).verifyEncoded()) {
                returned.setSuccess(true);
                returned.setResult(EnumResults.Login.LOGGED_IN);
                returned.add(getUser(lq.getValue("username", String.class)));
                return returned;
            }

            returned.setResult(EnumResults.Login.WRONG_PASSWORD);
            return returned;
        } catch (SQLException e) {
            e.printStackTrace();
            returned.setResult(EnumResults.Login.SQL_EXCEPTION);
            returned.setEx(e);
            return returned;
        }
    }

    public EnumResults.AddFriend addFriend(long userID, long friendID) {
        if (userID == friendID) return EnumResults.AddFriend.SAME_USER;

        PreparedStatement insertFriend = null;
        try (PreparedStatement checkFriend = con.prepareStatement(
                "SELECT * " +
                    "FROM " + Constants.FRIENDS_TABLE +
                    " WHERE user_id = ? AND friend_id = ?;"
        )) {
            checkFriend.setLong(1, userID);
            checkFriend.setLong(2, friendID);
            ResultSet rs = checkFriend.executeQuery();
            if (rs.next()) {
                rs.close();
                return EnumResults.AddFriend.ALREADY_FRIEND;
            }
            rs.close();

            insertFriend = con.prepareStatement(
                    "INSERT INTO " + Constants.FRIENDS_TABLE + " (user_id, friend_id) " +
                        "VALUES (?, ?);"
            );
            insertFriend.setLong(1, userID);
            insertFriend.setLong(2, friendID);
            insertFriend.executeUpdate();

            return EnumResults.AddFriend.ADDED_FRIEND;
        } catch (SQLException e) {
            e.printStackTrace();
            return EnumResults.AddFriend.SQL_EXCEPTION;
        } finally {
            try {
                if (insertFriend != null) insertFriend.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public EnumResults.RemoveFriend removeFriend(long userID, long friendID) {
        if (userID == friendID) return EnumResults.RemoveFriend.SAME_USER;

        try (PreparedStatement removeFriend = con.prepareStatement(
                "DELETE FROM " + Constants.FRIENDS_TABLE +
                " WHERE user_id = ? AND friend_id = ?;"
        )) {
            removeFriend.setLong(1, userID);
            removeFriend.setLong(2, friendID);
            removeFriend.executeUpdate();

            return EnumResults.RemoveFriend.REMOVED_FRIEND;
        } catch (SQLException e) {
            e.printStackTrace();
            return EnumResults.RemoveFriend.SQL_EXCEPTION;
        }
    }

    public Message addMessage(long messageID, long chatID, long authorID, String content) {
        try (PreparedStatement addMessage = con.prepareStatement(
                "INSERT INTO " + Constants.MESSAGES_TABLE + "(message_id, chat_id, author_id, content)" +
                    "VALUES (?, ?, ?, ?);"
        )) {
            addMessage.setLong(1, messageID);
            addMessage.setLong(2, chatID);
            addMessage.setLong(3, authorID);
            addMessage.setString(4, content);
            addMessage.executeUpdate();
            incrementChatMessageCounter(chatID);

            return getMessage(messageID, chatID);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public EnumResults.SendFriendRequest sendFriendRequest(long senderID, long friendID) {
        if (senderID == friendID) return EnumResults.SendFriendRequest.SAME_USER;
        if (isFriend(senderID, friendID)) return EnumResults.SendFriendRequest.ALREADY_FRIENDS;

        PreparedStatement insertRequest = null;
        try (PreparedStatement checkRequest = con.prepareStatement(
                "SELECT * " +
                    "FROM " + Constants.FRIEND_REQUESTS_TABLE +
                    " WHERE sender_id = ? AND friend_id = ?;"
        )) {
            checkRequest.setLong(1, senderID);
            checkRequest.setLong(2, friendID);
            ResultSet rs = checkRequest.executeQuery();
            if (rs.next()) {
                rs.close();
                return EnumResults.SendFriendRequest.ALREADY_SENT;
            }
            rs.close();

            insertRequest = con.prepareStatement(
                    "INSERT INTO " + Constants.FRIEND_REQUESTS_TABLE +
                        " VALUES (?, ?);"
            );
            insertRequest.setLong(1, senderID);
            insertRequest.setLong(2, friendID);
            insertRequest.executeUpdate();

            return EnumResults.SendFriendRequest.SENT_SUCCESSFULLY;
        } catch (SQLException e) {
            e.printStackTrace();
            return EnumResults.SendFriendRequest.SQL_EXCEPTION;
        } finally {
            try {
                if (insertRequest != null) insertRequest.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean acceptFriendRequest(long senderID, long friendID) {
        if (senderID == friendID) return false;

        PreparedStatement deleteRequest = null;
        try (PreparedStatement checkRequest = con.prepareStatement(
                "SELECT * " +
                    "FROM " + Constants.FRIEND_REQUESTS_TABLE +
                    " WHERE sender_id = ? AND friend_id = ?;"
        )) {
            checkRequest.setLong(1, senderID);
            checkRequest.setLong(2, friendID);

            ResultSet rs = checkRequest.executeQuery();
            if (!rs.next()) {
                rs.close();
                return false;
            }
            rs.close();

            deleteRequest = con.prepareStatement(
                    "DELETE FROM " + Constants.FRIEND_REQUESTS_TABLE +
                        " WHERE sender_id = ? AND friend_id = ?;"
            );
            deleteRequest.setLong(1, senderID);
            deleteRequest.setLong(2, friendID);


            switch (addFriend(senderID, friendID)) {
                case ADDED_FRIEND -> {
                    deleteRequest.executeUpdate();
                    return true;
                }
                case ALREADY_FRIEND, SAME_USER -> {
                    deleteRequest.executeUpdate();
                    return false;
                }
                default -> {
                    return false;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (deleteRequest != null) deleteRequest.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void denyFriendRequest(long senderID, long friendID) {
        if (senderID == friendID) return;

        PreparedStatement deleteRequest = null;
        try (PreparedStatement checkRequest = con.prepareStatement(
                "SELECT * " +
                    "FROM " + Constants.FRIEND_REQUESTS_TABLE +
                    " WHERE sender_id = ? AND friend_id = ?;"
        )) {
            checkRequest.setLong(1, senderID);
            checkRequest.setLong(2, friendID);
            ResultSet rs = checkRequest.executeQuery();
            if (!rs.next()) {
                rs.close();
                return;
            }
            rs.close();

            deleteRequest = con.prepareStatement(
                    "DELETE FROM " + Constants.FRIEND_REQUESTS_TABLE +
                        " WHERE sender_id = ? AND friend_id = ?;"
            );
            deleteRequest.setLong(1, senderID);
            deleteRequest.setLong(2, friendID);
            deleteRequest.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (deleteRequest != null) deleteRequest.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public Chat createChat(String name, long[] memberIDs) {
        PreparedStatement selectMissingInfo = null;
        PreparedStatement insertChatMessageCounter = null;

        try (PreparedStatement insertChat = con.prepareStatement(
                "INSERT INTO " + Constants.CHATS_TABLE + " (name) " +
                    "VALUES (?);"
        )) {
            insertChat.setString(1, name);
            insertChat.executeUpdate();

            selectMissingInfo = con.prepareStatement(
                    "SELECT * " +
                        "FROM " + Constants.CHATS_TABLE +
                        " WHERE chat_id = LAST_INSERT_ID();"
            );
            ResultSet rs = selectMissingInfo.executeQuery();
            rs.next();
            long chatID = rs.getLong("chat_id");
            Timestamp dateCreated = rs.getTimestamp("date_created");
            rs.close();
            insertChatMessageCounter = con.prepareStatement(
                    "INSERT INTO " + Constants.CHATS_MESSAGE_COUNTERS_TABLE + " (chat_id) " +
                            "VALUES (?);"
            );
            insertChatMessageCounter.setLong(1, chatID);
            insertChatMessageCounter.executeUpdate();
            Chat c = new Chat(chatID, name, dateCreated.toLocalDateTime(), 0);

            for (long id : memberIDs) {
                PreparedStatement insertMember = con.prepareStatement(
                        "INSERT INTO " + Constants.USERS_IN_CHATS_TABLE +
                            " VALUES (?, ?);"
                );
                insertMember.setLong(1, chatID);
                insertMember.setLong(2, id);
                insertMember.executeUpdate();
                insertMember.close();
                c.getMemberIDs().add(id);
            }

            return c;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (selectMissingInfo != null) selectMissingInfo.close();
                if (insertChatMessageCounter != null) insertChatMessageCounter.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void addUserToChat(long chatID, long userID) {
        try (PreparedStatement addUserToChat = con.prepareStatement(
                "INSERT INTO " + Constants.USERS_IN_CHATS_TABLE +
                    " VALUES (?, ?);"
        )) {
            addUserToChat.setLong(1, chatID);
            addUserToChat.setLong(2, userID);

            addUserToChat.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public User createUser(String username, String hash) {
        try (PreparedStatement insertUser = con.prepareStatement(
                "INSERT INTO " + Constants.USERS_TABLE + "(username, password_hash)" +
                    " VALUES (?, ?);"
        )) {
            insertUser.setString(1, username);
            insertUser.setString(2, hash);
            insertUser.executeUpdate();

            User returned = getUser(username);
            addUserToChat(Constants.GLOBAL_CHAT_ID, returned.getUserID());

            return returned;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean deleteUser(long userID) {
        try (PreparedStatement deleteUser = con.prepareStatement(
                "DELETE FROM " + Constants.USERS_TABLE +
                    " WHERE user_id =?;"
        )) {
            deleteUser.setLong(1, userID);
            return deleteUser.executeUpdate() == 1;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void setAutoIncrement(String tableName, long value) {
        try (PreparedStatement resetAutoIncrement = con.prepareStatement(
                "ALTER TABLE ?" +
                    "AUTO_INCREMENT = ?;"
        )) {
            resetAutoIncrement.setString(1, tableName);
            resetAutoIncrement.setLong(2, value);
            resetAutoIncrement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void incrementChatMessageCounter(long chatID) {
        try (PreparedStatement incrementChatMessageCounter = con.prepareStatement(
                "UPDATE " + Constants.CHATS_MESSAGE_COUNTERS_TABLE +
                    " SET message_counter = message_counter + 1 " +
                    "WHERE chat_id = ?;"
        )) {
            incrementChatMessageCounter.setLong(1, chatID);
            incrementChatMessageCounter.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void insertAttachment(long attachmentID, long chatID, long messageID, String path, Long attachmentPosition) {
        try (PreparedStatement insertAttachment = con.prepareStatement(
                "INSERT INTO " + Constants.ATTACHMENTS_TABLE +
                    " VALUES (?, ?, ?, ?, ?);"
        )) {
            insertAttachment.setLong(1, attachmentID);
            insertAttachment.setLong(2, chatID);
            insertAttachment.setLong(3, messageID);
            insertAttachment.setString(4, path);
            if (attachmentPosition == null) insertAttachment.setNull(5, Types.BIGINT);
            else insertAttachment.setLong(5, attachmentPosition);

            insertAttachment.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public User getUser(long id) {
        return getUser0(String.valueOf(id), "user_id");
    }

    public User getUser(String name) {
        return getUser0(name, "username");
    }

    private User getUser0(String arg, String columnName) {
        try (PreparedStatement st = con.prepareStatement(
                "SELECT * " +
                    "FROM " + Constants.USERS_TABLE +
                    " WHERE " + columnName + " = ?;"
        )) {
            st.setString(1, arg);
            ResultSet rs = st.executeQuery();
            if (!rs.next()) return null;

            long id = rs.getLong("user_id");
            String nickname = rs.getString("username");
            String hashedPassword = rs.getString("password_hash");
            Timestamp joined = rs.getTimestamp("date_joined");
            rs.close();

            User returned = new User(id, nickname, hashedPassword, joined.toLocalDateTime());

            List<Long> friends = getFriendIDs(id);
            friends.forEach(returned::addFriend);

            return returned;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<User> getAllUsers() {
        try (PreparedStatement getUsers = con.prepareStatement(
                "SELECT * " +
                    "FROM " + Constants.USERS_TABLE
        )) {
            ResultSet rs = getUsers.executeQuery();
            List<User> allUsers = new ArrayList<>();
            while (rs.next()) {
                long id = rs.getLong("user_id");
                String nickname = rs.getString("username");
                String hashedPassword = rs.getString("password_hash");
                Timestamp joined = rs.getTimestamp("date_joined");

                allUsers.add(new User(id, nickname, hashedPassword, joined.toLocalDateTime()));
            }
            rs.close();

            return allUsers;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Chat> getAllChats() {
        try (PreparedStatement st = con.prepareStatement(
                "SELECT chat_id " +
                    "FROM " + Constants.CHATS_TABLE
        )) {
            ResultSet rs = st.executeQuery();
            List<Chat> allChats = new ArrayList<>();
            while (rs.next()) {
                long id = rs.getLong("chat_id");
                allChats.add(getChat(id));
            }
            rs.close();

            return allChats;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public Chat getChat(long chatID) {
        try (PreparedStatement getChat = con.prepareStatement(
                "SELECT * " +
                    "FROM " + Constants.CHATS_TABLE +
                    " WHERE chat_id = ?;"
        )) {
            getChat.setLong(1, chatID);
            ResultSet rs = getChat.executeQuery();
            if (!rs.next()) return null;
            String name = rs.getString("name");
            Timestamp created = rs.getTimestamp("date_created");
            rs.close();

            Chat c = new Chat(chatID, name, created.toLocalDateTime(), getChatMessageCounter(chatID));

            getMessagesFromChat(chatID, 0, Constants.DEFAULT_MESSAGES_LIMIT)
                    .forEach(message -> c.getMessages().add(message));

            getUsersInChat(chatID)
                    .forEach(user -> c.getMemberIDs().add(user.getUserID()));

            return c;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Message getMessage(long messageID, long chatID) {
        try (PreparedStatement getMessage = con.prepareStatement(
                "SELECT * " +
                    "FROM " + Constants.MESSAGES_TABLE +
                    " WHERE message_id = ? AND chat_id = ?;"
        )) {
            getMessage.setLong(1, messageID);
            getMessage.setLong(2, chatID);
            ResultSet rs = getMessage.executeQuery();
            if (!rs.next()) return null;

            long authorID = rs.getLong("author_id");
            String content = rs.getString("content");
            Timestamp sendDate = rs.getTimestamp("date_sent");
            rs.close();

            Message m = new Message(messageID, chatID, authorID, content, sendDate.toLocalDateTime());
            m.getAttachments().addAll(getAttachments(chatID, messageID));

            return m;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public long getChatMessageCounter(long chatID) {
        try (PreparedStatement getMessageCounter = con.prepareStatement(
                "SELECT message_counter " +
                    "FROM " + Constants.CHATS_MESSAGE_COUNTERS_TABLE +
                    " WHERE chat_id = ?;"
        )) {
            getMessageCounter.setLong(1, chatID);
            ResultSet rs = getMessageCounter.executeQuery();
            long counter;
            if (!rs.next()) counter = -1;
            else counter = rs.getLong("message_counter");
            rs.close();

            return counter;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public List<Message> getMessagesFromChat(long chatID, long offset, long limit) {
        try (PreparedStatement getMessageIDs = con.prepareStatement(
                "SELECT message_id " +
                    "FROM " + Constants.MESSAGES_TABLE +
                    " WHERE chat_id = ? " +
                    "LIMIT ?, ?;"
        )) {
            getMessageIDs.setLong(1, chatID);
            getMessageIDs.setLong(2, offset);
            getMessageIDs.setLong(3, limit);

            ResultSet rs = getMessageIDs.executeQuery();
            List<Message> messages = new ArrayList<>();

            while (rs.next()) {
                long messageID = rs.getLong("message_id");
                messages.add(getMessage(messageID, chatID));
            }
            rs.close();

            return messages;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Message> getMessagesFromChat(long chatID, long offset, long limit, long authorID) {
        return getMessagesFromChat(chatID, offset, limit)
                .stream()
                .filter(m -> m.getAuthorID() == authorID)
                .collect(Collectors.toList());
    }

    public List<User> getUsersInChat(long chatID) {
        try (PreparedStatement getUserIDs = con.prepareStatement(
                "SELECT user_id " +
                    "FROM " + Constants.USERS_IN_CHATS_TABLE +
                    " WHERE chat_id = ?;"
        )) {
            getUserIDs.setLong(1, chatID);
            ResultSet rs = getUserIDs.executeQuery();
            List<User> users = new ArrayList<>();

            while (rs.next()) {
                long userID = rs.getLong("user_id");
                users.add(getUser(userID));
            }
            rs.close();

            return users;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Chat> getUsersChats(long userID) {
        try (PreparedStatement getChatIDs = con.prepareStatement(
                "SELECT chat_id " +
                    "FROM " + Constants.USERS_IN_CHATS_TABLE +
                    " WHERE user_id = ?;"
        )) {
            getChatIDs.setLong(1, userID);
            ResultSet rs = getChatIDs.executeQuery();
            List<Chat> chats = new ArrayList<>();

            while (rs.next()) {
                long chatID = rs.getLong("chat_id");
                chats.add(getChat(chatID));
            }
            rs.close();

            return chats;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public boolean isFriend(long userID, long friendID) {
        try (PreparedStatement getFriendIDs = con.prepareStatement(
                "SELECT friend_id " +
                    "FROM " + Constants.FRIENDS_TABLE +
                    " WHERE user_id = ?;"
        )) {
            getFriendIDs.setLong(1, userID);
            ResultSet rs = getFriendIDs.executeQuery();
            while (rs.next()) {
                long dbFriendID = rs.getLong("friend_id");
                if (friendID == dbFriendID) return true;
            }
            rs.close();

            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Long> getFriendIDs(long userID) {
        try (PreparedStatement getFriendIDs = con.prepareStatement(
                "SELECT friend_id " +
                        "FROM " + Constants.FRIENDS_TABLE +
                        " WHERE user_id = ?;"
        )) {
            getFriendIDs.setLong(1, userID);
            List<Long> friends = new ArrayList<>();
            ResultSet rs = getFriendIDs.executeQuery();

            while (rs.next()) {
                long id = rs.getLong("friend_id");
                friends.add(id);
            }
            rs.close();

            return friends;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<User> getFriends(long userID) {
        return getFriendIDs(userID).stream()
                .map(this::getUser)
                .collect(Collectors.toList());
    }

    public List<FriendRequest> getFriendRequests(long recipientID) {
        try (PreparedStatement st = con.prepareStatement(
                "SELECT sender_id " +
                    "FROM " + Constants.FRIEND_REQUESTS_TABLE +
                    " WHERE friend_id = ?;"
        )) {
            st.setLong(1, recipientID);
            List<FriendRequest> friendRequests = new ArrayList<>();

            ResultSet rs = st.executeQuery();

            while (rs.next()) {
                long senderID = rs.getLong("sender_id");
                friendRequests.add(new FriendRequest(getUser(senderID), getUser(recipientID)));
            }
            rs.close();

            return friendRequests;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Attachment> getAttachments(long chatID, long messageID) {
        try (PreparedStatement getAttachment = con.prepareStatement(
                "SELECT * " +
                    "FROM " + Constants.ATTACHMENTS_TABLE +
                    " WHERE chat_id = ? AND " +
                    "message_id = ?;"
        )) {
            getAttachment.setLong(1, chatID);
            getAttachment.setLong(2, messageID);

            List<Attachment> attachments = new ArrayList<>();

            ResultSet rs = getAttachment.executeQuery();

            while (rs.next()) {
                long attachmentID = rs.getLong("attachment_id");
                String path = rs.getString("path");
                long attachmentPosition = rs.getLong("attachment_position");

                Attachment a = new Attachment(
                        attachmentID,
                        attachmentPosition,
                        FilenameUtils.getName(path),
                        FilenameUtils.getExtension(path)
                );

                a.getFileContents().addAll(FileUtil.getFileContents(path));

                attachments.add(a);
            }

            return attachments;
        } catch (SQLException | IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
