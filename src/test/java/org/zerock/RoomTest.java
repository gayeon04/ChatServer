package org.zerock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomTest {

    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room("testRoom");
    }

    // ── 기본 속성 ──────────────────────────────────────────────

    @Test
    @DisplayName("방 이름이 정확히 반환된다")
    void getName() {
        assertEquals("testRoom", room.getName());
    }

    @Test
    @DisplayName("생성 직후 방은 비어 있다")
    void emptyOnCreate() {
        assertTrue(room.isEmpty());
        assertEquals(0, room.getMemberCount());
    }

    // ── join ──────────────────────────────────────────────────

    @Test
    @DisplayName("join 하면 멤버 수가 증가한다")
    void joinIncreasesMemberCount() {
        ClientHandler h = mockHandler("alice");
        room.join(h);
        assertEquals(1, room.getMemberCount());
        assertFalse(room.isEmpty());
    }

    @Test
    @DisplayName("여러 명이 join하면 멤버 수가 모두 반영된다")
    void joinMultipleMembers() {
        room.join(mockHandler("alice"));
        room.join(mockHandler("bob"));
        room.join(mockHandler("carol"));
        assertEquals(3, room.getMemberCount());
    }

    @Test
    @DisplayName("같은 핸들러를 두 번 join해도 멤버는 하나다 (Set 보장)")
    void joinSameHandlerTwice() {
        ClientHandler h = mockHandler("alice");
        room.join(h);
        room.join(h);
        assertEquals(1, room.getMemberCount());
    }

    @Test
    @DisplayName("join 시 나머지 멤버에게 입장 알림이 전송된다")
    void joinBroadcastsJoinMessage() {
        ClientHandler existing = mockHandler("bob");
        room.join(existing);

        ClientHandler newbie = mockHandler("alice");
        room.join(newbie);

        // bob은 alice 입장 메시지를 받아야 한다
        verify(existing, atLeastOnce()).sendMessage(contains("alice has joined."));
    }

    // ── leave ─────────────────────────────────────────────────

    @Test
    @DisplayName("leave 하면 멤버 수가 감소한다")
    void leaveDecreasesMemberCount() {
        ClientHandler h = mockHandler("alice");
        room.join(h);
        room.leave(h);
        assertEquals(0, room.getMemberCount());
        assertTrue(room.isEmpty());
    }

    @Test
    @DisplayName("leave 시 남은 멤버에게 퇴장 알림이 전송된다")
    void leaveBroadcastsLeaveMessage() {
        ClientHandler alice = mockHandler("alice");
        ClientHandler bob   = mockHandler("bob");
        room.join(alice);
        room.join(bob);

        clearInvocations(alice, bob); // join 알림 초기화
        room.leave(alice);

        verify(bob, atLeastOnce()).sendMessage(contains("alice has left."));
    }

    @Test
    @DisplayName("없는 멤버를 leave해도 예외가 발생하지 않는다")
    void leaveNonMemberIsNoOp() {
        ClientHandler stranger = mockHandler("stranger");
        assertDoesNotThrow(() -> room.leave(stranger));
    }

    // ── broadcast ─────────────────────────────────────────────

    @Test
    @DisplayName("sender=null이면 전원에게 메시지가 전달된다 (시스템 메시지)")
    void broadcastSystemMessageToAll() {
        ClientHandler alice = mockHandler("alice");
        ClientHandler bob   = mockHandler("bob");
        room.join(alice);
        room.join(bob);

        clearInvocations(alice, bob);
        room.broadcast("system notice", null);

        verify(alice).sendMessage("system notice");
        verify(bob).sendMessage("system notice");
    }

    @Test
    @DisplayName("sender 지정 시 sender 본인에게는 전달되지 않는다")
    void broadcastExcludesSender() {
        ClientHandler alice = mockHandler("alice");
        ClientHandler bob   = mockHandler("bob");
        room.join(alice);
        room.join(bob);

        clearInvocations(alice, bob);
        room.broadcast("hello", alice);

        verify(alice, never()).sendMessage("hello");
        verify(bob).sendMessage("hello");
    }

    @Test
    @DisplayName("멤버가 아무도 없을 때 broadcast해도 예외가 발생하지 않는다")
    void broadcastToEmptyRoomIsNoOp() {
        assertDoesNotThrow(() -> room.broadcast("msg", null));
    }

    // ── isEmpty / getMemberCount ───────────────────────────────

    @Test
    @DisplayName("join 후 leave하면 다시 isEmpty=true가 된다")
    void isEmptyAfterLeave() {
        ClientHandler h = mockHandler("alice");
        room.join(h);
        room.leave(h);
        assertTrue(room.isEmpty());
    }

    // ── helper ────────────────────────────────────────────────

    private ClientHandler mockHandler(String username) {
        ClientHandler h = Mockito.mock(ClientHandler.class);
        when(h.getUsername()).thenReturn(username);
        return h;
    }
}
