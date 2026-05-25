package org.zerock;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP 채팅 서버 메인 클래스
 * - 클라이언트 접속 수락 및 스레드 풀에 위임
 * - DoS 방어: 최대 동시 접속자 수 제한, IP별 접속 수 제한
 */
public class ChatServer {

    private static final int PORT = 9999;
    private static final int MAX_CLIENTS = 20; // DoS 방어: 최대 동시 접속자

    private final ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private final RoomManager roomManager;
    private final SessionManager sessionManager;

    public ChatServer() throws IOException {
        this.serverSocket   = new ServerSocket(PORT);
        this.threadPool     = Executors.newFixedThreadPool(MAX_CLIENTS);
        this.roomManager    = new RoomManager();
        this.sessionManager = new SessionManager();
        System.out.println("[server start] port: " + PORT + " | max connection: " + MAX_CLIENTS);
    }

    public void start() {
        // JVM 종료 시 자동으로 stop() 호출
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                String ip = clientSocket.getInetAddress().getHostAddress();

                // DoS 방어 1: IP별 동시 접속 수 초과 시 즉시 차단
                if (!sessionManager.allowConnection(ip)) {
                    System.out.println("[차단] IP " + ip + " 접속 거부");
                    clientSocket.close();
                    continue;
                }

                // DoS 방어 2: 전체 접속자 수 초과 시 거부
                if (sessionManager.getSessionCount() >= MAX_CLIENTS) {
                    System.out.println("[차단] 최대 접속자 초과 - " + ip + " 거부");
                    clientSocket.close();
                    continue;
                }

                System.out.println("[connect] " + ip + " | 스레드: "+Thread.currentThread().getName());
                threadPool.execute(new ClientHandler(clientSocket, roomManager, sessionManager));

            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("[오류] " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        try {
            threadPool.shutdown();
            if (!serverSocket.isClosed()) serverSocket.close();
            System.out.println("[서버 정상 종료]");
        } catch (IOException e) {
            System.err.println("[종료 오류] " + e.getMessage());
        }
    }
}