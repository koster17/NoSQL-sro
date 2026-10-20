package kz.sro.sessions;

import redis.clients.jedis.JedisPooled;

import java.util.Random;

public class Seed {
    public static void main(String[] args) {
        JedisPooled r = Db.connect();
        int ttl = Integer.parseInt(Db.get("SESSION_TTL_SECONDS", "1800"));
        SessionService svc = new SessionService(r, ttl);
        svc.clear();

        String[] agents = {"Chrome/Windows", "Safari/iOS", "Firefox/Linux", "Edge/Windows"};
        Random rnd = new Random(42);

        for (int i = 1; i <= 40; i++) {
            String user = String.format("user_%02d", (i % 10) + 1);   // 10 тестовых пользователей
            String ip = "10.0.0." + (1 + rnd.nextInt(200));            // приватные адреса, не реальные
            int sessionTtl = (i % 5 == 0) ? 60 : ttl;                   // каждая 5-я живёт 60 секунд
            String id = svc.login(user, ip, agents[rnd.nextInt(agents.length)], sessionTtl);
            if (i % 7 == 0) svc.revoke(id);                             // каждая 7-я отозвана
        }
        System.out.println("Inserted sessions: " + svc.countAll());
        r.close();
    }
}