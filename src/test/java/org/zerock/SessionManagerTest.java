package org.zerock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    private SessionManager manager;
    private static final String TEST_IP = "192.168.0.1";

    @BeforeEach
    void setUp() {
        manager = new SessionManager();
    }

    // ── allowConnection ────────────────────────────────────────

    @Test
    @DisplayName("처음 IP 접속은 허용된다")
    void allowConnectionFirstTime() {
        assertTrue(manager.allowConnection(TEST_IP));
    }

    @Test
    @DisplayName("같은 IP로 3번까지 접속할 수 있다 (경계값)")
    void allowConnectionUpToLimit() {
        assertTrue(manager.allowConnection(TEST_IP));
        assertTrue(manager.allowConnection(TEST_IP));
        assertTrue(manager.allowConnection(TEST_IP));
    }

    @Test
    @DisplayName("같은 IP로 4번째 접속은 차단된다 (DoS 방어)")
    void blockConnectionOverLimit() {
        manager.allowConnection(TEST_IP);
        manager.allowConnection(TEST_IP);
        manager.allowConnection(TEST_IP);
        assertFalse(manager.allowConnection(TEST_IP));
    }

    @Test
    @DisplayName("다른 IP는 독립적으로 카운트된다")
    void differentIpIsIndependent() {
        manager.allowConnection(TEST_IP);
        manager.allowConnection(TEST_IP);
        manager.allowConnection(TEST_IP);

        assertTrue(manager.allowConnection("10.0.0.1"));
    }

    // ── releaseConnection ──────────────────────────────────────

    @Test
    @DisplayName("releaseConnection 후 다시 접속 가능해진다")
    void releaseAllowsReconnect() {
        manager.allowConnection(TEST_IP);
        manager.allowConnection(TEST_IP);
        manager.allowConnection(TEST_IP); // 한도 도달

        manager.releaseConnection(TEST_IP); // 1 해제 → count=2

        assertTrue(manager.allowConnection(TEST_IP)); // count=3, 허용
    }

    @Test
    @DisplayName("접속 기록이 없는 IP를 release해도 예외가 발생하지 않는다")
    void releaseUnknownIpIsNoOp() {
        assertDoesNotThrow(() -> manager.releaseConnection("9.9.9.9"));
    }

    @Test
    @DisplayName("마지막 연결을 해제하면 해당 IP 항목이 맵에서 제거된다 (count=0 시 null)")
    void releaseLastConnectionRemovesEntry() {
        manager.allowConnection(TEST_IP); // count=1
        manager.releaseConnection(TEST_IP); // count=0 → null

        // null 제거 후 다시 3번 접속 가능
        assertTrue(manager.allowConnection(TEST_IP));
        assertTrue(manager.allowConnection(TEST_IP));
        assertTrue(manager.allowConnection(TEST_IP));
        assertFalse(manager.allowConnection(TEST_IP));
    }

    // ── login ──────────────────────────────────────────────────

    @Test
    @DisplayName("처음 로그인은 성공한다")
    void loginSuccess() {
        ClientHandler h = Mockito.mock(ClientHandler.class);
        assertTrue(manager.login("alice", h));
    }

    @Test
    @DisplayName("동일 닉네임으로 두 번 로그인하면 false를 반환한다 (중복 방지)")
    void loginDuplicateFails() {
        ClientHandler h1 = Mockito.mock(ClientHandler.class);
        ClientHandler h2 = Mockito.mock(ClientHandler.class);
        manager.login("alice", h1);
        assertFalse(manager.login("alice", h2));
    }

    @Test
    @DisplayName("다른 닉네임은 동시에 로그인 가능하다")
    void loginDifferentUsernamesAllowed() {
        assertTrue(manager.login("alice", Mockito.mock(ClientHandler.class)));
        assertTrue(manager.login("bob",   Mockito.mock(ClientHandler.class)));
    }

    // ── logout ────────────────────────────────────────────────

    @Test
    @DisplayName("logout 후 동일 닉네임으로 재로그인이 가능하다")
    void reloginAfterLogout() {
        ClientHandler h = Mockito.mock(ClientHandler.class);
        manager.login("alice", h);
        manager.logout("alice");
        assertTrue(manager.login("alice", Mockito.mock(ClientHandler.class)));
    }

    @Test
    @DisplayName("로그인하지 않은 닉네임을 logout해도 예외가 발생하지 않는다")
    void logoutUnknownUserIsNoOp() {
        assertDoesNotThrow(() -> manager.logout("ghost"));
    }

    // ── getHandler ────────────────────────────────────────────

    @Test
    @DisplayName("로그인한 유저의 핸들러를 getHandler로 찾을 수 있다")
    void getHandlerFound() {
        ClientHandler h = Mockito.mock(ClientHandler.class);
        manager.login("alice", h);
        assertSame(h, manager.getHandler("alice"));
    }

    @Test
    @DisplayName("존재하지 않는 닉네임 getHandler는 null을 반환한다")
    void getHandlerNotFound() {
        assertNull(manager.getHandler("nobody"));
    }

    @Test
    @DisplayName("logout 후 getHandler는 null을 반환한다")
    void getHandlerAfterLogoutReturnsNull() {
        ClientHandler h = Mockito.mock(ClientHandler.class);
        manager.login("alice", h);
        manager.logout("alice");
        assertNull(manager.getHandler("alice"));
    }

    // ── getSessionCount ───────────────────────────────────────

    @Test
    @DisplayName("로그인 전 세션 수는 0이다")
    void sessionCountInitiallyZero() {
        assertEquals(0, manager.getSessionCount());
    }

    @Test
    @DisplayName("로그인할 때마다 세션 수가 증가한다")
    void sessionCountIncreasesOnLogin() {
        manager.login("alice", Mockito.mock(ClientHandler.class));
        manager.login("bob",   Mockito.mock(ClientHandler.class));
        assertEquals(2, manager.getSessionCount());
    }

    @Test
    @DisplayName("logout하면 세션 수가 감소한다")
    void sessionCountDecreasesOnLogout() {
        manager.login("alice", Mockito.mock(ClientHandler.class));
        manager.login("bob",   Mockito.mock(ClientHandler.class));
        manager.logout("alice");
        assertEquals(1, manager.getSessionCount());
    }

    @Test
    @DisplayName("중복 로그인 실패 시 세션 수는 증가하지 않는다")
    void sessionCountNotIncreasedOnDuplicateLogin() {
        manager.login("alice", Mockito.mock(ClientHandler.class));
        manager.login("alice", Mockito.mock(ClientHandler.class)); // 실패
        assertEquals(1, manager.getSessionCount());
    }
}
