package com.github.jacekpoz.server.util;

import com.github.jacekpoz.common.sendables.Attachment;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class FileUtil {

    private FileUtil() {
        throw new AssertionError("nah");
    }

    public static String getAttachmentPath(long chatID, long messageID, Attachment a) {
        return String.format(
                "/var/xnor/data/%d/%d/%d/%s.%s",
                chatID,
                messageID,
                a.getAttachmentID(),
                a.getFileName(),
                a.getFileExtension()
        );
    }

    public static void writeFile(String path, List<Byte> fileContents) throws IOException {
        FileWriter writer = new FileWriter(path);
        for (Byte b : fileContents)
            writer.write(b);
    }

    public static List<Byte> getFileContents(String path) throws IOException {
        List<Byte> contents = new ArrayList<>();
        FileReader reader = new FileReader(path);
        int fileByte;
        while ((fileByte = reader.read()) != -1) {
            contents.add((byte) fileByte);
        }
        return contents;
    }
}
