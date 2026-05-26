package org.zerock;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 클라이언트 1명을 담당하는 스레드
 * - 로그인, 방 입퇴장, 채팅, 귓속말, 종료 처리
 * - DoS 방어: 메시지 길이 제한, Rate Limiting
 */
public class ClientHandler implements Runnable {

    // DoS 방어 상수
    private static final int MAX_MESSAGE_LENGTH = 500;   // 메시지 최대 길이
    private static final int RATE_LIMIT_COUNT = 5;        // 1초당 최대 메시지 수
    private static final long RATE_LIMIT_WINDOW_MS = 1000;

    private final Socket socket;
    private final RoomManager roomManager;
    private final SessionManager sessionManager;

    private BufferedReader in;
    private PrintWriter out;

    private String username;
    private Room currentRoom;
    private String clientIp;

    // Rate Limiting 상태
    private int messageCountInWindow = 0;
    private long windowStartTime = System.currentTimeMillis();

    private static final DateTimeFormatter LOG_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ClientHandler(Socket socket, RoomManager roomManager, SessionManager sessionManager) {
        this.socket = socket;
        this.roomManager = roomManager;
        this.sessionManager = sessionManager;
        this.clientIp = socket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            // 1) 로그인
            if (!handleLogin()) return;

            // 2) 명령어 루프
            String line;
            while ((line = in.readLine()) != null) {

                // DoS 방어 1: 메시지 길이 제한
                if (line.length() > MAX_MESSAGE_LENGTH) {
                    sendMessage("[warning] Message is too long. Please keep it under " + MAX_MESSAGE_LENGTH + " characters.");
                    continue;
                }

                // DoS 방어 2: Rate Limiting
                if (!checkRateLimit()) {
                    sendMessage("[warning] You are sending messages too fast. Please try again later.");
                    continue;
                }

                processCommand(line.trim());
            }

        } catch (IOException e) {
            System.out.println("[disconnected] " + (username != null ? username : clientIp));
        } finally {
            disconnect();
        }
    }

    /** 로그인 처리 */
    private boolean handleLogin() throws IOException {
        sendMessage("Enter your nickname:");
        String name = in.readLine();

        if (name == null || name.isBlank()) {
            sendMessage("[error] Nickname is empty. Closing connection.");
            return false;
        }

        name = name.trim();

        if (!sessionManager.login(name, this)) {
            sendMessage("[error] Nickname already in use. Closing connection.");
            return false;
        }

        this.username = name;
        logAccess("login");
        sendMessage("=== Welcome, " + username + "! ===");
        sendMessage("도움말: /help");
        return true;
    }

    /** 명령어 파싱 및 실행 */
    private void processCommand(String input) {
        if (input.startsWith("/")) {
            String[] parts = input.split(" ", 3);
            String cmd = parts[0].toLowerCase();

            switch (cmd) {
                case "/create" -> {
                    if (parts.length < 2) { sendMessage("Usage: /create <roomname>"); return; }
                    handleCreate(parts[1]);
                }
                case "/join" -> {
                    if (parts.length < 2) { sendMessage("Usage: /join <roomname>"); return; }
                    handleJoin(parts[1]);
                }
                case "/leave"  -> handleLeave();
                case "/rooms"  -> sendMessage(roomManager.getRoomList());
                case "/whisper", "/w" -> {
                    if (parts.length < 3) { sendMessage("Usage: /w <nickname> <message>"); return; }
                    handleWhisper(parts[1], parts[2]);
                }
                case "/quit"   -> disconnect();
                case "/help"   -> sendHelp();
                default        -> sendMessage("[error] Unknown command. See /help");
            }
        } else {
            handleChat(input);
        }
    }

    private void handleCreate(String roomName) {
        Room room = roomManager.createRoom(roomName);
        if (room == null) {
            sendMessage("[error] Failed to create room (already exists or max rooms exceeded)");
            return;
        }
        handleLeave();
        currentRoom = room;
        room.join(this);
        sendMessage("[room created] Joined '" + roomName + "'.");
    }

    private void handleJoin(String roomName) {
        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendMessage("[error] Room '" + roomName + "' does not exist. Check /rooms.");
            return;
        }
        handleLeave();
        currentRoom = room;
        room.join(this);
    }

    private void handleLeave() {
        if (currentRoom == null) return;
        currentRoom.leave(this);
        roomManager.removeIfEmpty(currentRoom.getName());
        currentRoom = null;
    }

    private void handleChat(String message) {
        if (currentRoom == null) {
            sendMessage("[info] Please join a room first. /rooms -> /join <roomname>");
            return;
        }
        String formatted = "[" + currentRoom.getName() + "] " + username + ": " + message;
        currentRoom.broadcast(formatted, this);
        sendMessage(formatted);
    }

    private void handleWhisper(String targetName, String message) {
        ClientHandler target = sessionManager.getHandler(targetName);
        if (target == null) {
            sendMessage("[error] User '" + targetName + "' not found.");
            return;
        }
        target.sendMessage("[whisper] " + username + " -> " + targetName + ": " + message);
        sendMessage("[whisper] me -> " + targetName + ": " + message);
    }

    /** Rate Limiting 체크 */
    private boolean checkRateLimit() {
        long now = System.currentTimeMillis();
        if (now - windowStartTime > RATE_LIMIT_WINDOW_MS) {
            windowStartTime = now;
            messageCountInWindow = 0;
        }
        messageCountInWindow++;
        return messageCountInWindow <= RATE_LIMIT_COUNT;
    }

    /** 클라이언트에게 메시지 전송 */
    public void sendMessage(String message) {
        if (out != null) out.println(message);
    }

    public String getUsername() { return username; }

    /** 접속 로그 콘솔 + 파일 저장 */
    private void logAccess(String event) {
        String log = "[" + LocalDateTime.now().format(LOG_FORMAT) + "] "
                + event + " | IP: " + clientIp + " | nickname: " + username;
        System.out.println(log);

        try (FileWriter fw = new FileWriter("access.log", true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(log);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("[log write failed] " + e.getMessage());
        }
    }

    private void sendHelp() {
        sendMessage("""
                === Command List ===
                /create <roomname>       - Create and join a room
                /join <roomname>         - Join a room
                /leave                   - Leave the current room
                /rooms                   - List all rooms
                /w <nickname> <message>  - Send a whisper
                /quit                    - Disconnect
                """);
    }

    /** 연결 종료 및 자원 해제 */
    private void disconnect() {
        handleLeave();
        if (username != null) {
            sessionManager.logout(username);
            logAccess("logout");
        }
        sessionManager.releaseConnection(clientIp);
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[socket close error] " + e.getMessage());
        }
    }
}