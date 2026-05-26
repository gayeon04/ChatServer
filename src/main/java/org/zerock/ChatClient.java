package org.zerock;

import java.io.*;
import java.net.Socket;

/**
 * 테스트용 클라이언트
 * - 서버에 접속해서 키보드 입력을 전송
 * - 서버 메시지를 화면에 출력
 */
public class ChatClient {

    private static final String HOST = "localhost";
    private static final int PORT = 9999;

    public static void main(String[] args) throws IOException {

        Socket socket = new Socket(HOST, PORT);
        System.out.println("[서버 접속 완료]");

        // 서버 메시지 수신 스레드 (백그라운드)
        Thread receiver = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
                System.out.println("[서버 연결 종료]");
            }
        });
        receiver.setDaemon(true);
        receiver.start();

        // 키보드 입력 → 서버 전송
        try (BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream()), true)) {
            String input;
            while ((input = keyboard.readLine()) != null) {
                out.println(input);
                if (input.equalsIgnoreCase("/quit")) break;
            }
        }

        socket.close();
    }
}