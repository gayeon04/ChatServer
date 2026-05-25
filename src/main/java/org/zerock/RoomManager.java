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
    public Room createRoom(String roomName) {
        if (rooms.size() >= MAX_ROOMS) {
            System.out.println("[DoS 방어] 최대 방 개수 초과: " + roomName);
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
        if (rooms.isEmpty()) return "현재 생성된 방이 없습니다.";
        StringBuilder sb = new StringBuilder("=== 방 목록 ===\n");
        rooms.forEach((name, room) ->
                sb.append("  - ").append(name)
                        .append(" (").append(room.getMemberCount()).append("명)\n"));
        return sb.toString();
    }
}