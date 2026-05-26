package org.zerock;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 채팅방 하나를 나타내는 클래스
 * - 참여자 목록 관리
 * - 방 내 브로드캐스트 / 귓속말
 */
public class Room {

    private final String name;

    // 참여 중인 클라이언트 목록 (thread-safe)
    private final Set<ClientHandler> members =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public Room(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** 방 입장 */
    public void join(ClientHandler handler) {
        members.add(handler);
        broadcast("[" + name + "] " + handler.getUsername() + " has joined.", null);
    }

    /** 방 퇴장 */
    public void leave(ClientHandler handler) {
        members.remove(handler);
        broadcast("[" + name + "] " + handler.getUsername() + " has left.", null);
    }

    /**
     * 방 전체 메시지 전송
     * sender가 null이면 시스템 메시지 → 전원 전송
     */
    public void broadcast(String message, ClientHandler sender) {
        for (ClientHandler member : members) {
            if (sender == null || !member.equals(sender)) {
                member.sendMessage(message);
            }
        }
    }

    public int getMemberCount() {
        return members.size();
    }

    /** 방이 비어있는지 확인 (비면 자동 삭제용) */
    public boolean isEmpty() {
        return members.isEmpty();
    }
}