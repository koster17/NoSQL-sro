package kz.sro.sessions;

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;

public class SessionService {
    private static final String S = "session:";
    private static final String U = "user_sessions:";
    private static final int AUDIT_TTL = 300;   // отозванная сессия хранится 5 минут для аудита

    private final JedisPooled r;
    private final int ttl;

    public SessionService(JedisPooled r, int ttlSeconds) {
        this.r = r;
        this.ttl = ttlSeconds;
    }

    // AP1 / CREATE
    public String login(String userId, String ip, String userAgent) {
        return login(userId, ip, userAgent, ttl);
    }

    public String login(String userId, String ip, String userAgent, int ttlSec) {
        String id = UUID.randomUUID().toString();
        String now = String.valueOf(System.currentTimeMillis());
        Map<String, String> h = new HashMap<>();
        h.put("userId", userId);
        h.put("ip", ip);
        h.put("userAgent", userAgent);
        h.put("status", "ACTIVE");
        h.put("createdAt", now);
        h.put("lastAccessAt", now);
        r.hset(S + id, h);
        r.expire(S + id, ttlSec);
        r.sadd(U + userId, id);
        return id;
    }

    // AP2 / READ: пусто, если сессии нет, она истекла или отозвана
    public Optional<Map<String, String>> validate(String id) {
        Map<String, String> m = r.hgetAll(S + id);
        if (m.isEmpty() || !"ACTIVE".equals(m.get("status"))) return Optional.empty();
        return Optional.of(m);
    }

    // AP3 / UPDATE (sliding expiration)
    public boolean touch(String id) {
        if (validate(id).isEmpty()) return false;
        r.hset(S + id, "lastAccessAt", String.valueOf(System.currentTimeMillis()));
        r.expire(S + id, ttl);
        return true;
    }

    // AP4 / DELETE
    public boolean logout(String id) {
        String userId = r.hget(S + id, "userId");
        long deleted = r.del(S + id);
        if (userId != null) r.srem(U + userId, id);
        return deleted == 1;
    }

    // AP5
    public boolean revoke(String id) {
        if (validate(id).isEmpty()) return false;
        r.hset(S + id, "status", "REVOKED");
        r.expire(S + id, AUDIT_TTL);
        return true;
    }

    // AP6
    public int revokeAllForUser(String userId) {
        int n = 0;
        for (String id : r.smembers(U + userId)) if (revoke(id)) n++;
        return n;
    }

    // AP7 (ленивая очистка Set от «мёртвых» ID)
    public List<Map<String, String>> activeSessions(String userId) {
        List<Map<String, String>> res = new ArrayList<>();
        for (String id : r.smembers(U + userId)) {
            Map<String, String> m = r.hgetAll(S + id);
            if (m.isEmpty()) { r.srem(U + userId, id); continue; }
            if ("ACTIVE".equals(m.get("status"))) { m.put("sessionId", id); res.add(m); }
        }
        res.sort((a, b) -> Long.compare(
                Long.parseLong(b.get("lastAccessAt")), Long.parseLong(a.get("lastAccessAt"))));
        return res;
    }

    // AP9 (аналог команды TTL): -2, если ключа нет
    public long ttlRemaining(String id) { return r.ttl(S + id); }

    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        String cursor = ScanParams.SCAN_POINTER_START;
        ScanParams p = new ScanParams().match(pattern).count(100);
        do {
            ScanResult<String> sr = r.scan(cursor, p);
            keys.addAll(sr.getResult());
            cursor = sr.getCursor();
        } while (!"0".equals(cursor));
        return keys;
    }

    // AP8
    public long countActive() {
        return scanKeys(S + "*").stream()
                .filter(k -> "ACTIVE".equals(r.hget(k, "status"))).count();
    }

    // AP10a: статистика по статусам
    public Map<String, Long> statsByStatus() {
        Map<String, Long> res = new TreeMap<>();
        for (String k : scanKeys(S + "*")) {
            String st = r.hget(k, "status");
            if (st != null) res.merge(st, 1L, Long::sum);
        }
        return res;
    }

    // AP10b: топ пользователей по числу активных сессий
    public List<Map.Entry<String, Long>> topUsers(int limit) {
        Map<String, Long> cnt = new HashMap<>();
        for (String k : scanKeys(S + "*")) {
            Map<String, String> m = r.hgetAll(k);
            if ("ACTIVE".equals(m.get("status"))) cnt.merge(m.get("userId"), 1L, Long::sum);
        }
        List<Map.Entry<String, Long>> list = new ArrayList<>(cnt.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return list.subList(0, Math.min(limit, list.size()));
    }

    public long countAll() { return scanKeys(S + "*").size(); }

    public void clear() {
        for (String k : scanKeys(S + "*")) r.del(k);
        for (String k : scanKeys(U + "*")) r.del(k);
    }
}