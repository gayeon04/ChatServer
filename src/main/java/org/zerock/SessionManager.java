package org.zerock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
        // compute()로 read-check-write를 atomic하게 처리해 race condition 방지
        AtomicBoolean allowed = new AtomicBoolean(false);
        ipConnectionCount.compute(ip, (k, v) -> {
            int count = (v == null) ? 0 : v;
            if (count >= MAX_CONNECTIONS_PER_IP) return count;
            allowed.set(true);
            return count + 1;
        });
        if (!allowed.get()) {
            System.out.println("[DoS blocked] IP " + ip + " connection limit exceeded");
        }
        return allowed.get();
    }

    public void releaseConnection(String ip) {
        ipConnectionCount.computeIfPresent(ip, (k, v) -> v <= 1 ? null : v - 1);
    }

    public boolean login(String username, ClientHandler handler) {
        if (sessions.containsKey(username)) return false;
        sessions.put(username, handler);
        System.out.println("[login] " + username);
        return true;
    }

    public void logout(String username) {
        sessions.remove(username);
        System.out.println("[logout] " + username);
    }

    public ClientHandler getHandler(String username) {
        return sessions.get(username);
    }

    public int getSessionCount() {
        return sessions.size();
    }
}