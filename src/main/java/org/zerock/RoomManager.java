package org.zerock;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버 내 모든 채팅방을 관리하는 클래스
 * - 방 생성 / 조회 / 삭제
 * - DoS 방어: 최대 방 개수 제한
 */
public class RoomManager {

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    // DoS 방어: 방을 무한 생성하는 공격 방지
    private static final int MAX_ROOMS = 20;

    /** 방 생성 */
    public synchronized Room createRoom(String roomName) {
        // size() → putIfAbsent() 사이의 race condition을 막기 위해 synchronized
        if (rooms.size() >= MAX_ROOMS) {
            System.out.println("[DoS defense] Max rooms exceeded: " + roomName);
            return null;
        }
        rooms.putIfAbsent(roomName, new Room(roomName));
        return rooms.get(roomName);
    }

    /** 방 조회 */
    public Room getRoom(String roomName) {
        return rooms.get(roomName);
    }

    /** 방이 비어있으면 자동 삭제 */
    public void removeIfEmpty(String roomName) {
        rooms.computeIfPresent(roomName, (k, room) -> room.isEmpty() ? null : room);
    }

    /** 방 목록 반환 */
    public String getRoomList() {
        if (rooms.isEmpty()) return "No rooms available.";
        StringBuilder sb = new StringBuilder("=== Room List ===\n");
        rooms.forEach((name, room) ->
                sb.append("  - ").append(name)
                        .append(" (").append(room.getMemberCount()).append(" members)\n"));
        return sb.toString();
    }
}