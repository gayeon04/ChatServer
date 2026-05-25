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
                    sendMessage("[경고] 메시지가 너무 깁니다. " + MAX_MESSAGE_LENGTH + "자 이하로 보내세요.");
                    continue;
                }

                // DoS 방어 2: Rate Limiting
                if (!checkRateLimit()) {
                    sendMessage("[경고] 메시지를 너무 빠르게 보내고 있습니다. 잠시 후 다시 시도하세요.");
                    continue;
                }

                processCommand(line.trim());
            }

        } catch (IOException e) {
            System.out.println("[연결 끊김] " + (username != null ? username : clientIp));
        } finally {
            disconnect();
        }
    }

    /** 로그인 처리 */
    private boolean handleLogin() throws IOException {
        sendMessage("닉네임을 입력하세요:");
        String name = in.readLine();

        if (name == null || name.isBlank()) {
            sendMessage("[오류] 닉네임이 비어있습니다. 연결을 종료합니다.");
            return false;
        }

        name = name.trim();

        if (!sessionManager.login(name, this)) {
            sendMessage("[오류] 이미 사용 중인 닉네임입니다. 연결을 종료합니다.");
            return false;
        }

        this.username = name;
        logAccess("로그인");
        sendMessage("=== 환영합니다, " + username + "님! ===");
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
                    if (parts.length < 2) { sendMessage("사용법: /create <방이름>"); return; }
                    handleCreate(parts[1]);
                }
                case "/join" -> {
                    if (parts.length < 2) { sendMessage("사용법: /join <방이름>"); return; }
                    handleJoin(parts[1]);
                }
                case "/leave"  -> handleLeave();
                case "/rooms"  -> sendMessage(roomManager.getRoomList());
                case "/whisper", "/w" -> {
                    if (parts.length < 3) { sendMessage("사용법: /w <닉네임> <메시지>"); return; }
                    handleWhisper(parts[1], parts[2]);
                }
                case "/quit"   -> disconnect();
                case "/help"   -> sendHelp();
                default        -> sendMessage("[오류] 알 수 없는 명령어. /help 참조");
            }
        } else {
            handleChat(input);
        }
    }

    private void handleCreate(String roomName) {
        Room room = roomManager.createRoom(roomName);
        if (room == null) {
            sendMessage("[오류] 방 생성 실패 (이미 존재하거나 최대 방 수 초과)");
            return;
        }
        handleLeave();
        currentRoom = room;
        room.join(this);
        sendMessage("[방 생성 완료] '" + roomName + "'에 입장했습니다.");
    }

    private void handleJoin(String roomName) {
        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendMessage("[오류] '" + roomName + "' 방이 없습니다. /rooms로 확인하세요.");
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
            sendMessage("[안내] 먼저 방에 입장하세요. /rooms → /join <방이름>");
            return;
        }
        String formatted = "[" + currentRoom.getName() + "] " + username + ": " + message;
        currentRoom.broadcast(formatted, this);
        sendMessage(formatted);
    }

    private void handleWhisper(String targetName, String message) {
        ClientHandler target = sessionManager.getHandler(targetName);
        if (target == null) {
            sendMessage("[오류] '" + targetName + "' 사용자를 찾을 수 없습니다.");
            return;
        }
        target.sendMessage("[귓속말] " + username + " → " + targetName + ": " + message);
        sendMessage("[귓속말] 나 → " + targetName + ": " + message);
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
                + event + " | IP: " + clientIp + " | 닉네임: " + username;
        System.out.println(log);

        try (FileWriter fw = new FileWriter("access.log", true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(log);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("[로그 저장 실패] " + e.getMessage());
        }
    }

    private void sendHelp() {
        sendMessage("""
                === 명령어 목록 ===
                /create <방이름>        - 방 생성 후 입장
                /join <방이름>          - 방 입장
                /leave                  - 방 퇴장
                /rooms                  - 방 목록 보기
                /w <닉네임> <메시지>    - 귓속말
                /quit                   - 연결 종료
                """);
    }

    /** 연결 종료 및 자원 해제 */
    private void disconnect() {
        handleLeave();
        if (username != null) {
            sessionManager.logout(username);
            logAccess("로그아웃");
        }
        sessionManager.releaseConnection(clientIp);
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[소켓 닫기 오류] " + e.getMessage());
        }
    }
}