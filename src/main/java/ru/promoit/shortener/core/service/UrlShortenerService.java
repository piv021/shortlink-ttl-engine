package ru.promoit.shortener.core.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import ru.promoit.shortener.config.AppConfig;
import ru.promoit.shortener.core.exception.AccessDeniedException;
import ru.promoit.shortener.core.exception.LinkExpiredException;
import ru.promoit.shortener.core.exception.LinkNotFoundException;
import ru.promoit.shortener.core.model.ShortLink;
import ru.promoit.shortener.core.port.BrowserPort;
import ru.promoit.shortener.core.port.LinkRepository;
import ru.promoit.shortener.core.port.NotificationPort;
import ru.promoit.shortener.infra.ShortCodeGenerator;

public class UrlShortenerService {
    private final LinkRepository repository;
    private final NotificationPort notificationPort;
    private final BrowserPort browserPort;
    private final ShortCodeGenerator codeGenerator;
    private final AppConfig config;
    private final Clock clock;

    public UrlShortenerService(
            LinkRepository repository,
            NotificationPort notificationPort,
            BrowserPort browserPort,
            ShortCodeGenerator codeGenerator,
            AppConfig config,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.notificationPort = Objects.requireNonNull(notificationPort);
        this.browserPort = Objects.requireNonNull(browserPort);
        this.codeGenerator = Objects.requireNonNull(codeGenerator);
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
    }

    public ShortLink createShortLink(String originalUrl, UUID userUuid, Integer customMaxClicks) {
        validateUrl(originalUrl);
        Objects.requireNonNull(userUuid, "UUID пользователя обязателен");

        int maxClicks = (customMaxClicks != null) ? customMaxClicks : config.defaultMaxClicks();
        if (maxClicks <= 0) {
            throw new IllegalArgumentException("Лимит переходов должен быть положительным числом.");
        }

        String shortUrl;
        do {
            String code = codeGenerator.generate(originalUrl, userUuid, config.codeLength());
            shortUrl = config.baseDomain() + code;
        } while (repository.existsByShortUrl(shortUrl));

        Instant now = clock.instant();
        Instant expiresAt = now.plus(config.ttl());

        ShortLink link = new ShortLink(shortUrl, originalUrl, userUuid, now, expiresAt, maxClicks);
        repository.save(link);
        return link;
    }

    public String navigate(String shortUrl) {
        ShortLink link = repository
                .findByShortUrl(shortUrl)
                .orElseThrow(() -> new LinkNotFoundException("Ссылка не найдена или была удалена: " + shortUrl));

        Instant now = clock.instant();
        if (link.isExpired(now)) {
            repository.deleteByShortUrl(shortUrl);
            notificationPort.notifyUser(
                    link.getOwnerUuid(),
                    "Срок действия вашей ссылки %s истек. Ссылка удалена. Создайте новую ссылку.".formatted(shortUrl));
            throw new LinkExpiredException("Время жизни ссылки " + shortUrl + " истекло.");
        }

        if (!link.incrementClick()) {
            notificationPort.notifyUser(
                    link.getOwnerUuid(),
                    "Лимит переходов (%d) по ссылке %s исчерпан. Предлагаем создать новую ссылку."
                            .formatted(link.getMaxClicks(), shortUrl));
            throw new LinkExpiredException("Лимит переходов по ссылке " + shortUrl + " исчерпан.");
        }

        if (link.isClickLimitReached()) {
            notificationPort.notifyUser(
                    link.getOwnerUuid(),
                    "Достигнут максимальный лимит переходов (%d) по ссылке %s. При следующих запросах ссылка будет заблокирована. Создайте новую ссылку."
                            .formatted(link.getMaxClicks(), shortUrl));
        }

        browserPort.openUrl(link.getOriginalUrl());
        return link.getOriginalUrl();
    }

    public void updateMaxClicks(String shortUrl, UUID requesterUuid, int newMaxClicks) {
        ShortLink link = getOwnedLink(shortUrl, requesterUuid);
        link.updateMaxClicks(newMaxClicks);
    }

    public void deleteLink(String shortUrl, UUID requesterUuid) {
        getOwnedLink(shortUrl, requesterUuid);
        repository.deleteByShortUrl(shortUrl);
    }

    public List<ShortLink> getUserLinks(UUID ownerUuid) {
        cleanupExpiredLinks();
        return repository.findByOwnerUuid(ownerUuid);
    }

    public int cleanupExpiredLinks() {
        Instant now = clock.instant();
        List<ShortLink> expired = repository.findAll().stream()
                .filter(link -> link.isExpired(now))
                .toList();

        int removedCount = 0;
        for (ShortLink link : expired) {
            if (repository.deleteByShortUrl(link.getShortUrl())) {
                removedCount++;
                notificationPort.notifyUser(
                        link.getOwnerUuid(),
                        "Срок действия ссылки %s (%s) истек. Ссылка автоматически удалена. Вы можете создать новую."
                                .formatted(link.getShortUrl(), link.getOriginalUrl()));
            }
        }
        return removedCount;
    }

    private ShortLink getOwnedLink(String shortUrl, UUID requesterUuid) {
        ShortLink link = repository
                .findByShortUrl(shortUrl)
                .orElseThrow(() -> new LinkNotFoundException("Ссылка не найдена: " + shortUrl));
        if (!link.getOwnerUuid().equals(requesterUuid)) {
            throw new AccessDeniedException("Отказ в доступе: вы не являетесь владельцем ссылки " + shortUrl);
        }
        return link;
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL не может быть пустым.");
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("URL должен начинаться с http:// или https://");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Некорректный хост в URL: " + url);
            }
        } catch (URISyntaxException _) {
            // JEP 456: безымянная переменная исключения '_'
            throw new IllegalArgumentException("Некорректный формат URL: " + url);
        }
    }
}
