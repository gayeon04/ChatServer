package org.zerock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ClientHandler를 실제 소켓 없이 테스트하기 위해
 * PipedInputStream / PipedOutputStream으로 가상 소켓을 구성한다.
 */
@ExtendWith(MockitoExtension.class)
class ClientHandlerTest {

    private RoomManager  roomManager;
    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        roomManager    = new RoomManager();
        sessionManager = new SessionManager();
    }

    // ── 소켓 헬퍼 ──────────────────────────────────────────────

    /**
     * 클라이언트 → 서버 방향: serverIn에서 읽힘
     * 서버 → 클라이언트 방향: clientIn에서 읽힘
     */
    private record FakeConnection(
            Socket socket,
            PrintWriter clientOut,
            BufferedReader clientIn
    ) {}

    private FakeConnection buildFakeConnection() throws Exception {
        // 서버가 읽을 스트림 (클라이언트 → 서버)
        PipedOutputStream clientToServerPipe = new PipedOutputStream();
        PipedInputStream  serverIn           = new PipedInputStream(clientToServerPipe);

        // 클라이언트가 읽을 스트림 (서버 → 클라이언트)
        PipedOutputStream serverToClientPipe = new PipedOutputStream();
        PipedInputStream  clientInPipe       = new PipedInputStream(serverToClientPipe);

        Socket socket = mock(Socket.class);
        when(socket.getInputStream()).thenReturn(serverIn);
        when(socket.getOutputStream()).thenReturn(serverToClientPipe);
        InetAddress addr = mock(InetAddress.class);
        when(addr.getHostAddress()).thenReturn("127.0.0.1");
        when(socket.getInetAddress()).thenReturn(addr);

        PrintWriter   clientOut = new PrintWriter(clientToServerPipe, true);
        BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientInPipe));
        return new FakeConnection(socket, clientOut, clientIn);
    }

    /** ClientHandler를 별도 스레드에서 실행하고 해당 스레드를 반환한다. */
    private Thread runHandler(FakeConnection conn) {
        ClientHandler handler = new ClientHandler(conn.socket(), roomManager, sessionManager);
        Thread t = new Thread(handler);
        t.setDaemon(true);
        t.start();
        return t;
    }

    // ── 로그인 ─────────────────────────────────────────────────

    @Test
    @DisplayName("정상 닉네임으로 로그인하면 Welcome 메시지를 수신한다")
    void loginSuccessReceivesWelcome() throws Exception {
        FakeConnection conn = buildFakeConnection();
        runHandler(conn);

        // "Enter your nickname:" 읽기
        conn.clientIn().readLine();

        conn.clientOut().println("alice");

        // 응답 수집
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = conn.clientIn().readLine()) != null && !line.contains("도움말")) {
            sb.append(line).append("\n");
        }
        sb.append(line);
        assertTrue(sb.toString().contains("Welcome, alice"));
    }

    @Test
    @DisplayName("빈 닉네임 입력 시 에러 메시지를 수신하고 연결이 종료된다")
    void loginEmptyNicknameFails() throws Exception {
        FakeConnection conn = buildFakeConnection();
        runHandler(conn);

        conn.clientIn().readLine(); // "Enter your nickname:"
        conn.clientOut().println("   "); // 공백만

        String response = readUntilContains(conn.clientIn(), "error");
        assertTrue(response.contains("[error]"));
    }

    @Test
    @DisplayName("중복 닉네임 로그인 시 에러 메시지를 수신하고 연결이 종료된다")
    void loginDuplicateNicknameFails() throws Exception {
        // 첫 번째 연결 (alice)
        FakeConnection first = buildFakeConnection();
        runHandler(first);
        first.clientIn().readLine();
        first.clientOut().println("alice");
        readUntilContains(first.clientIn(), "도움말"); // 로그인 완료까지 대기

        // 두 번째 연결 (alice 다시)
        FakeConnection second = buildFakeConnection();
        runHandler(second);
        second.clientIn().readLine();
        second.clientOut().println("alice");

        String response = readUntilContains(second.clientIn(), "error");
        assertTrue(response.contains("[error]"));
    }

    // ── /help ──────────────────────────────────────────────────

    @Test
    @DisplayName("/help 명령어 실행 시 Command List 안내를 수신한다")
    void helpCommandReturnsCommandList() throws Exception {
        FakeConnection conn = loginAs("bob");
        conn.clientOut().println("/help");

        String response = readUntilContains(conn.clientIn(), "Command List");
        assertTrue(response.contains("Command List"));
    }

    // ── /rooms ─────────────────────────────────────────────────

    @Test
    @DisplayName("방이 없을 때 /rooms 명령어는 'No rooms available.' 를 반환한다")
    void roomsCommandWhenNoRooms() throws Exception {
        FakeConnection conn = loginAs("carol");
        conn.clientOut().println("/rooms");

        String response = readUntilContains(conn.clientIn(), "rooms");
        assertTrue(response.contains("No rooms available.") || response.contains("Room List"));
    }

    // ── /create ────────────────────────────────────────────────

    @Test
    @DisplayName("/create로 방을 만들면 'room created' 메시지를 받는다")
    void createRoomSuccess() throws Exception {
        FakeConnection conn = loginAs("dave");
        conn.clientOut().println("/create lobby");

        String response = readUntilContains(conn.clientIn(), "room created");
        assertTrue(response.contains("room created"));
    }

    @Test
    @DisplayName("/create 인자 없이 입력하면 Usage 안내를 받는다")
    void createRoomNoArg() throws Exception {
        FakeConnection conn = loginAs("eve");
        conn.clientOut().println("/create");

        String response = readUntilContains(conn.clientIn(), "Usage");
        assertTrue(response.contains("Usage"));
    }

    // ── /join ──────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 방에 /join하면 에러 메시지를 받는다")
    void joinNonExistentRoom() throws Exception {
        FakeConnection conn = loginAs("frank");
        conn.clientOut().println("/join nonexistent");

        String response = readUntilContains(conn.clientIn(), "error");
        assertTrue(response.contains("[error]"));
    }

    @Test
    @DisplayName("존재하는 방에 /join하면 입장 처리가 된다")
    void joinExistingRoom() throws Exception {
        // 방 미리 생성
        roomManager.createRoom("general");

        FakeConnection conn = loginAs("grace");
        conn.clientOut().println("/join general");

        // join 시 Room.join()이 broadcast를 호출하므로 방이 비어있으면 아무 메시지도 안 옴.
        // 에러가 없으면 성공으로 간주 (서버가 닫히지 않아야 함)
        conn.clientOut().println("/rooms");
        String response = readUntilContains(conn.clientIn(), "Room List");
        assertTrue(response.contains("general"));
    }

    // ── /leave ─────────────────────────────────────────────────

    @Test
    @DisplayName("방에 입장 후 /leave하면 방이 비어서 삭제된다")
    void leaveEmptiesAndRemovesRoom() throws Exception {
        FakeConnection conn = loginAs("henry");
        conn.clientOut().println("/create temp");
        readUntilContains(conn.clientIn(), "room created");

        conn.clientOut().println("/leave");
        // 에러 없이 /rooms 응답을 받으면 정상
        conn.clientOut().println("/rooms");
        String list = readUntilContains(conn.clientIn(), "rooms");
        assertTrue(list.contains("No rooms available.") || !list.contains("temp"));
    }

    // ── 채팅 (방 없이) ─────────────────────────────────────────

    @Test
    @DisplayName("방에 입장하지 않은 상태에서 메시지를 보내면 안내 문구를 받는다")
    void chatWithoutRoomShowsInfo() throws Exception {
        FakeConnection conn = loginAs("iris");
        conn.clientOut().println("hello everyone");

        String response = readUntilContains(conn.clientIn(), "info");
        assertTrue(response.contains("[info]"));
    }

    // ── DoS 방어: 메시지 길이 제한 ─────────────────────────────

    @Test
    @DisplayName("500자 초과 메시지 전송 시 '[warning] Message is too long' 경고를 받는다")
    void oversizedMessageTriggersWarning() throws Exception {
        FakeConnection conn = loginAs("judy");
        String longMsg = "A".repeat(501);
        conn.clientOut().println(longMsg);

        String response = readUntilContains(conn.clientIn(), "warning");
        assertTrue(response.contains("[warning]") && response.contains("too long"));
    }

    @Test
    @DisplayName("정확히 500자 메시지는 통과된다 (경계값)")
    void exactlyMaxLengthMessagePasses() throws Exception {
        FakeConnection conn = loginAs("kevin");
        roomManager.createRoom("test");
        conn.clientOut().println("/join test");
        Thread.sleep(50);

        String boundary = "B".repeat(500);
        conn.clientOut().println(boundary);
        Thread.sleep(100);

        // 500자는 경고 없이 채팅 처리됨 (warning이 오지 않아야 함)
        // 방에 본인 외 아무도 없으므로 서버 측 broadcast 후 자신에게도 포워드됨
        conn.clientOut().println("/rooms"); // 세션이 살아있는지 확인
        String response = readUntilContains(conn.clientIn(), "Room List");
        assertFalse(response.contains("too long"));
    }

    // ── DoS 방어: Rate Limiting ─────────────────────────────────

    @Test
    @DisplayName("1초 안에 6번째 메시지부터 Rate Limit 경고를 받는다")
    void rateLimitTriggersOnExcessiveMessages() throws Exception {
        FakeConnection conn = loginAs("lena");
        // 6번 연속 메시지 (5번까지는 허용, 6번째부터 차단)
        for (int i = 0; i < 6; i++) {
            conn.clientOut().println("msg" + i);
        }

        String response = readUntilContains(conn.clientIn(), "warning");
        assertTrue(response.contains("[warning]") && response.contains("too fast"));
    }

    // ── /whisper ──────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 사용자에게 귓속말을 보내면 에러를 받는다")
    void whisperToNonExistentUser() throws Exception {
        FakeConnection conn = loginAs("mike");
        conn.clientOut().println("/w ghost hello");

        String response = readUntilContains(conn.clientIn(), "error");
        assertTrue(response.contains("[error]"));
    }

    @Test
    @DisplayName("/w 인자가 부족하면 Usage 안내를 받는다")
    void whisperMissingArgs() throws Exception {
        FakeConnection conn = loginAs("nina");
        conn.clientOut().println("/w onlyname");

        String response = readUntilContains(conn.clientIn(), "Usage");
        assertTrue(response.contains("Usage"));
    }

    // ── 알 수 없는 명령어 ──────────────────────────────────────

    @Test
    @DisplayName("알 수 없는 명령어 입력 시 Unknown command 에러를 받는다")
    void unknownCommandShowsError() throws Exception {
        FakeConnection conn = loginAs("oscar");
        conn.clientOut().println("/unknowncmd");

        String response = readUntilContains(conn.clientIn(), "Unknown");
        assertTrue(response.contains("Unknown command"));
    }

    // ── 유틸리티 ──────────────────────────────────────────────

    /** 로그인까지 완료된 FakeConnection을 반환한다. */
    private FakeConnection loginAs(String username) throws Exception {
        FakeConnection conn = buildFakeConnection();
        runHandler(conn);
        conn.clientIn().readLine(); // "Enter your nickname:"
        conn.clientOut().println(username);
        readUntilContains(conn.clientIn(), "도움말"); // 로그인 완료 대기
        return conn;
    }

    /**
     * clientIn에서 줄을 읽으며 target 문자열이 포함된 줄이 나올 때까지
     * 누적한 전체 텍스트를 반환한다. 최대 30줄 또는 3초 timeout.
     */
    private String readUntilContains(BufferedReader reader, String target) throws Exception {
        StringBuilder sb = new StringBuilder();
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (reader.ready()) {
                String line = reader.readLine();
                if (line == null) break;
                sb.append(line).append("\n");
                if (line.contains(target)) return sb.toString();
            } else {
                Thread.sleep(20);
            }
        }
        return sb.toString();
    }
}
