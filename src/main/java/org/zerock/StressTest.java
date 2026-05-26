package org.zerock;

import java.io.*;
import java.net.Socket;

/**
 * 멀티스레드 동시 접속 스트레스 테스트
 * - 10명이 동시에 접속해서 메시지 전송
 */
public class StressTest {

    private static final int CLIENT_COUNT = 10; // 동시 접속 클라이언트 수
    private static final String HOST = "localhost";
    private static final int PORT = 9999;

    public static void main(String[] args) {
        System.out.println("[stress test start] client count: " + CLIENT_COUNT);

        for (int i = 1; i <= CLIENT_COUNT; i++) {
            final int clientId = i;

            // 각 클라이언트를 별도 스레드로 실행
            Thread t = new Thread(() -> {
                try {
                    Socket socket = new Socket(HOST, PORT);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(
                            new OutputStreamWriter(socket.getOutputStream()), true);

                    // 닉네임 입력 프롬프트 읽기
                    in.readLine();

                    // 로그인
                    out.println("User" + clientId);
                    Thread.sleep(300);

                    // 방 생성 or 입장
                    if (clientId == 1) {
                        out.println("/create testroom");
                    } else {
                        out.println("/join testroom");
                    }
                    Thread.sleep(300);

                    // 메시지 3번 전송
                    for (int j = 1; j <= 3; j++) {
                        out.println("User" + clientId + " message " + j);
                        Thread.sleep(200);
                    }

                    // 서버 응답 읽기
                    Thread.sleep(500);
                    System.out.println("[User" + clientId + "] test completed");

                    out.println("/quit");
                    socket.close();

                } catch (Exception e) {
                    System.out.println("[User" + clientId + "] error: " + e.getMessage());
                }
            });

            t.setName("TestClient-" + i);
            t.start();
        }
    }
}