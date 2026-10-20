package kz.sro.sessions;

import org.junit.jupiter.api.*;
import redis.clients.jedis.JedisPooled;
import static org.junit.jupiter.api.Assertions.*;

class SessionServiceTest {
    static JedisPooled r;
    static SessionService svc;

    @BeforeAll static void init() {
        r = Db.connect(1);
        svc = new SessionService(r, 60);
    }
    @BeforeEach void clean() { svc.clear(); }
    @AfterAll static void done() { svc.clear(); r.close(); }

    @Test void loginCreatesValidSession() {
        String id = svc.login("u1", "1.1.1.1", "UA");
        assertTrue(svc.validate(id).isPresent());
        assertTrue(svc.ttlRemaining(id) > 0);
    }

    @Test void logoutInvalidatesSession() {
        String id = svc.login("u1", "1.1.1.1", "UA");
        assertTrue(svc.logout(id));
        assertFalse(svc.validate(id).isPresent());
        assertEquals(-2, svc.ttlRemaining(id));
    }

    @Test void revokedSessionIsInvalid() {
        String id = svc.login("u1", "1.1.1.1", "UA");
        assertTrue(svc.revoke(id));
        assertFalse(svc.validate(id).isPresent());
        assertFalse(svc.touch(id));
    }

    @Test void expiredSessionIsInvalid() throws Exception {
        String id = svc.login("u2", "1.1.1.1", "UA", 1);   // TTL = 1 секунда
        Thread.sleep(1500);
        assertFalse(svc.validate(id).isPresent());
    }

    @Test void revokeAllForUserWorks() {
        svc.login("u3", "1.1.1.1", "UA");
        svc.login("u3", "1.1.1.2", "UA");
        assertEquals(2, svc.revokeAllForUser("u3"));
        assertEquals(0, svc.activeSessions("u3").size());
    }
}