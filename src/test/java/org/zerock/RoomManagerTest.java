package org.zerock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomManagerTest {

    private RoomManager manager;

    @BeforeEach
    void setUp() {
        manager = new RoomManager();
    }

    // ── createRoom ─────────────────────────────────────────────

    @Test
    @DisplayName("새 방을 생성하면 non-null Room이 반환된다")
    void createRoomSuccess() {
        Room room = manager.createRoom("lobby");
        assertNotNull(room);
        assertEquals("lobby", room.getName());
    }

    @Test
    @DisplayName("같은 이름으로 두 번 생성하면 동일한 Room 객체가 반환된다 (putIfAbsent)")
    void createRoomDuplicateReturnsSameRoom() {
        Room first  = manager.createRoom("lobby");
        Room second = manager.createRoom("lobby");
        assertSame(first, second);
    }

    @Test
    @DisplayName("방 20개를 생성한 후 21번째 생성 시도는 null을 반환한다 (DoS 방어)")
    void createRoomExceedsMaxReturnsNull() {
        for (int i = 0; i < 20; i++) {
            assertNotNull(manager.createRoom("room" + i));
        }
        Room overflow = manager.createRoom("overflow");
        assertNull(overflow);
    }

    @Test
    @DisplayName("방 19개까지는 정상 생성된다 (경계값)")
    void createRoomUpToLimit() {
        for (int i = 0; i < 20; i++) {
            assertNotNull(manager.createRoom("room" + i), "room" + i + " should be created");
        }
    }

    // ── getRoom ────────────────────────────────────────────────

    @Test
    @DisplayName("생성한 방은 getRoom으로 찾을 수 있다")
    void getRoomFound() {
        manager.createRoom("lobby");
        assertNotNull(manager.getRoom("lobby"));
    }

    @Test
    @DisplayName("존재하지 않는 방 이름으로 조회하면 null이 반환된다")
    void getRoomNotFound() {
        assertNull(manager.getRoom("nonexistent"));
    }

    // ── removeIfEmpty ──────────────────────────────────────────

    @Test
    @DisplayName("빈 방은 removeIfEmpty 호출 후 getRoom이 null을 반환한다")
    void removeIfEmptyDeletesEmptyRoom() {
        manager.createRoom("lobby");
        manager.removeIfEmpty("lobby");
        assertNull(manager.getRoom("lobby"));
    }

    @Test
    @DisplayName("멤버가 있는 방은 removeIfEmpty로 삭제되지 않는다")
    void removeIfEmptyKeepsNonEmptyRoom() {
        Room room = manager.createRoom("lobby");
        ClientHandler member = Mockito.mock(ClientHandler.class);
        when(member.getUsername()).thenReturn("alice");
        room.join(member);

        manager.removeIfEmpty("lobby");

        assertNotNull(manager.getRoom("lobby"));
    }

    @Test
    @DisplayName("존재하지 않는 방에 removeIfEmpty를 호출해도 예외가 발생하지 않는다")
    void removeIfEmptyOnNonExistentRoomIsNoOp() {
        assertDoesNotThrow(() -> manager.removeIfEmpty("ghost"));
    }

    // ── getRoomList ────────────────────────────────────────────

    @Test
    @DisplayName("방이 없을 때 getRoomList는 'No rooms available.' 문자열을 반환한다")
    void getRoomListEmpty() {
        assertEquals("No rooms available.", manager.getRoomList());
    }

    @Test
    @DisplayName("방을 생성하면 getRoomList에 방 이름이 포함된다")
    void getRoomListContainsRoomName() {
        manager.createRoom("lobby");
        manager.createRoom("gaming");
        String list = manager.getRoomList();
        assertTrue(list.contains("lobby"));
        assertTrue(list.contains("gaming"));
    }

    @Test
    @DisplayName("getRoomList에는 멤버 수가 표시된다")
    void getRoomListShowsMemberCount() {
        Room room = manager.createRoom("lobby");
        ClientHandler member = Mockito.mock(ClientHandler.class);
        when(member.getUsername()).thenReturn("alice");
        room.join(member);

        assertTrue(manager.getRoomList().contains("1 members"));
    }

    @Test
    @DisplayName("방이 삭제된 후 getRoomList에서 사라진다")
    void getRoomListAfterRemoval() {
        manager.createRoom("lobby");
        manager.removeIfEmpty("lobby");
        assertEquals("No rooms available.", manager.getRoomList());
    }
}
