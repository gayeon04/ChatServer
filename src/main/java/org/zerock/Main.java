package org.zerock;

import java.io.IOException;

/**
 * 서버 진입점
 */
public class Main {
    public static void main(String[] args) throws IOException {
        ChatServer server = new ChatServer();
        server.start();
    }
}