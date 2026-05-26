package org.zerock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 멀티스레드 환경에서 RoomManager / SessionManager의 동시성 안전성을 검증한다.
 */
class ConcurrencyTest {

    private RoomManager    roomManager;
    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        roomManager    = new RoomManager();
        sessionManager = new SessionManager();
    }

    // ── RoomManager 동시성 ─────────────────────────────────────

    @Test
    @DisplayName("20개 스레드가 동시에 서로 다른 방을 생성해도 최대 20개를 넘지 않는다")
    void concurrentRoomCreationRespectsLimit() throws Exception {
        int threads = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger created = new AtomicInteger(0);
        CountDownLatch start  = new CountDownLatch(1);
        CountDownLatch done   = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    Room r = roomManager.createRoom("room" + idx);
                    if (r != null) created.incrementAndGet();
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertTrue(created.get() <= 20, "방은 최대 20개여야 한다. actual=" + created.get());
    }

    @Test
    @DisplayName("같은 방 이름으로 여러 스레드가 동시에 createRoom해도 중복 생성되지 않는다")
    void concurrentSameRoomCreationNoDuplicate() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);
        List<Room> results   = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    Room r = roomManager.createRoom("shared");
                    if (r != null) results.add(r);
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        // 모든 결과는 동일한 Room 인스턴스여야 한다
        Room first = results.get(0);
        for (Room r : results) {
            assertSame(first, r);
        }
    }

    // ── SessionManager 동시성 ──────────────────────────────────

    @Test
    @DisplayName("서로 다른 닉네임으로 동시에 로그인해도 세션 수가 정확하다")
    void concurrentLoginsDifferentNames() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    sessionManager.login("user" + idx, mock(ClientHandler.class));
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(threads, sessionManager.getSessionCount());
    }

    @Test
    @DisplayName("같은 닉네임으로 여러 스레드가 동시에 로그인해도 정확히 한 명만 성공한다")
    void concurrentLoginSameNameOnlyOneSucceeds() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (sessionManager.login("alice", mock(ClientHandler.class))) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, success.get(), "동일 닉네임은 한 번만 로그인 가능해야 한다");
        assertEquals(1, sessionManager.getSessionCount());
    }

    @Test
    @DisplayName("같은 IP에서 동시에 접속 시도해도 최대 3개를 넘지 않는다")
    void concurrentConnectionsSameIpRespectsLimit() throws Exception {
        int threads = 10;
        String ip = "192.168.1.1";
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start   = new CountDownLatch(1);
        CountDownLatch done    = new CountDownLatch(threads);
        AtomicInteger allowed  = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (sessionManager.allowConnection(ip)) {
                        allowed.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertTrue(allowed.get() <= 3, "IP당 최대 3개 접속이어야 한다. actual=" + allowed.get());
    }

    // ── Room 동시성 ────────────────────────────────────────────

    @Test
    @DisplayName("여러 스레드가 동시에 Room에 join/leave해도 getMemberCount가 음수가 되지 않는다")
    void concurrentJoinLeaveNonNegativeCount() throws Exception {
        Room room = new Room("stress");
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        List<ClientHandler> handlers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            ClientHandler h = mock(ClientHandler.class);
            when(h.getUsername()).thenReturn("user" + i);
            handlers.add(h);
        }

        for (int i = 0; i < threads; i++) {
            final ClientHandler h = handlers.get(i);
            pool.submit(() -> {
                try {
                    start.await();
                    room.join(h);
                    Thread.sleep(5);
                    room.leave(h);
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertTrue(room.getMemberCount() >= 0);
    }
}
