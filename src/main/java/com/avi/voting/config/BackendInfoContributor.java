package com.avi.voting.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Shows under /actuator/info where Redis and PostgreSQL are, so it is always clear
 * which servers the running app talks to. Only hosts, ports and database names are
 * shown, never usernames or passwords.
 */
@Component
@RequiredArgsConstructor
public class BackendInfoContributor implements InfoContributor {

    private final Environment env;

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> backends = new LinkedHashMap<>();

        backends.put("redis", Map.of("endpoint",
                env.getProperty("spring.data.redis.host", "localhost")
                        + ":" + env.getProperty("spring.data.redis.port", "6379")
                        + "/db" + env.getProperty("spring.data.redis.database", "0")));

        // jdbc:postgresql://host:port/db?params -> host:port/db
        String dbUrl = env.getProperty("spring.datasource.url", "");
        backends.put("postgres", Map.of("endpoint",
                dbUrl.replaceFirst("^jdbc:postgresql://", "").replaceFirst("\\?.*$", "")));

        builder.withDetail("backends", backends);
    }
}
