package ru.promoit.shortener.config;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

public record AppConfig(
        String baseDomain,
        int codeLength,
        Duration ttl,
        int defaultMaxClicks,
        long cleanupIntervalSeconds) {

    public AppConfig {
        Objects.requireNonNull(baseDomain, "baseDomain не может быть null");
        Objects.requireNonNull(ttl, "ttl не может быть null");
        if (codeLength <= 0) {
            throw new IllegalArgumentException("Длина кода должна быть больше 0");
        }
        if (defaultMaxClicks <= 0) {
            throw new IllegalArgumentException("Лимит переходов по умолчанию должен быть больше 0");
        }
        if (cleanupIntervalSeconds <= 0) {
            throw new IllegalArgumentException("Интервал очистки должен быть больше 0");
        }
    }

    // Java 25 (JEP 513): Выполнение инструкций в конструкторе ДО вызова this(...)
    public AppConfig(String propertiesFileName) {
        Properties props = new Properties();
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(propertiesFileName)) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Ошибка чтения файла конфигурации: " + propertiesFileName, e);
        }

        String domain = props.getProperty("app.base-domain", "clck.ru/");
        int length = Integer.parseInt(props.getProperty("app.code-length", "6"));
        Duration ttlDuration = Duration.ofSeconds(Long.parseLong(props.getProperty("app.ttl-seconds", "86400")));
        int maxClicks = Integer.parseInt(props.getProperty("app.default-max-clicks", "5"));
        long cleanupSec = Long.parseLong(props.getProperty("app.cleanup-interval-seconds", "30"));

        this(domain, length, ttlDuration, maxClicks, cleanupSec);
    }
}