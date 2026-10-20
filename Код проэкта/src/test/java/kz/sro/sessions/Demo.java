package kz.sro.sessions;

import redis.clients.jedis.JedisPooled;

public class Demo {
    public static void main(String[] args) throws Exception {
        JedisPooled r = Db.connect();
        int ttl = Integer.parseInt(Db.get("SESSION_TTL_SECONDS", "1800"));
        SessionService s = new SessionService(r, ttl);

        String id = s.login("user_01", "10.0.0.99", "Chrome/Windows");
        System.out.println("Q1 login: sessionId = " + id);
        System.out.println("Q2 validate: " + s.validate(id).isPresent());
        System.out.println("Q3 TTL: " + s.ttlRemaining(id) + " c");
        Thread.sleep(2000);
        System.out.println("Q4 touch: " + s.touch(id) + ", TTL=" + s.ttlRemaining(id));
        System.out.println("Q5 activeSessions(user_01): " + s.activeSessions("user_01").size());
        System.out.println("Q6 countActive: " + s.countActive());
        System.out.println("Q7 revoke: " + s.revoke(id) + ", validate after = " + s.validate(id).isPresent());
        System.out.println("Q8 logout: " + s.logout(id) + ", TTL after = " + s.ttlRemaining(id));
        System.out.println("Q9 revokeAll(user_02): " + s.revokeAllForUser("user_02"));
        System.out.println("Q10 [нетривиальный] statsByStatus: " + s.statsByStatus());
        System.out.println("Q11 [нетривиальный] topUsers(3): " + s.topUsers(3));

        // Q12: замер времени проверки сессии (подтверждение NFR1: < 10 мс)
        String bench = s.login("user_bench", "10.0.0.1", "Bench");
        s.validate(bench);                                   // прогрев соединения
        int n = 100;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) s.validate(bench);
        double avgMs = (System.nanoTime() - t0) / 1_000_000.0 / n;
        System.out.printf("Q12 среднее время validate: %.3f мс (NFR1 < 10 мс)%n", avgMs);
        s.logout(bench);

        r.close();
    }
}