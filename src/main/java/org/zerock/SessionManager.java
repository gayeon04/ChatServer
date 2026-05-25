package org.zerock;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 현재 로그인된 사용자 세션을 관리하는 클래스
 * - username → ClientHandler 매핑
 * - IP별 접속 횟수 추적 (DoS 방어)
 */
public class SessionManager {

    private final ConcurrentHashMap<String, ClientHandler> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> ipConnectionCount = new ConcurrentHashMap<>();
    private static final int MAX_CONNECTIONS_PER_IP = 3;

    public boolean allowConnection(String ip) {
        int count = ipConnectionCount.getOrDefault(ip, 0);
        if (count >= MAX_CONNECTIONS_PER_IP) {
            System.out.println("[DoS 차단] IP " + ip + " 접속 초과");
            return false;
        }
        ipConnectionCount.put(ip, count + 1);
        return true;
    }

    public void releaseConnection(String ip) {
        ipConnectionCount.computeIfPresent(ip, (k, v) -> v <= 1 ? null : v - 1);
    }

    public boolean login(String username, ClientHandler handler) {
        if (sessions.containsKey(username)) return false;
        sessions.put(username, handler);
        System.out.println("[로그인] " + username);
        return true;
    }

    public void logout(String username) {
        sessions.remove(username);
        System.out.println("[로그아웃] " + username);
    }

    public ClientHandler getHandler(String username) {
        return sessions.get(username);
    }

    public int getSessionCount() {
        return sessions.size();
    }
}