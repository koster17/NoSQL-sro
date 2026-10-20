package kz.sro.sessions;

import io.github.cdimascio.dotenv.Dotenv;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

public class Db {
    private static final Dotenv env = Dotenv.configure().ignoreIfMissing().load();

    public static String get(String key, String def) {
        String v = env.get(key);
        return (v != null && !v.isBlank()) ? v : def;
    }

    public static JedisPooled connect(int db) {
        String pass = get("REDIS_PASSWORD", null);
        DefaultJedisClientConfig cfg = DefaultJedisClientConfig.builder()
                .password(pass).database(db).build();
        return new JedisPooled(new HostAndPort(
                get("REDIS_HOST", "localhost"),
                Integer.parseInt(get("REDIS_PORT", "6379"))), cfg);
    }

    public static JedisPooled connect() {
        return connect(Integer.parseInt(get("REDIS_DB", "0")));
    }
}