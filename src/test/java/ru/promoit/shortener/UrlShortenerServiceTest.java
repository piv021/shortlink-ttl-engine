package ru.promoit.shortener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.promoit.shortener.config.AppConfig;
import ru.promoit.shortener.core.exception.AccessDeniedException;
import ru.promoit.shortener.core.exception.LinkExpiredException;
import ru.promoit.shortener.core.exception.LinkNotFoundException;
import ru.promoit.shortener.core.model.ShortLink;
import ru.promoit.shortener.core.port.LinkRepository;
import ru.promoit.shortener.core.service.UrlShortenerService;
import ru.promoit.shortener.infra.InMemoryLinkRepository;
import ru.promoit.shortener.infra.InMemoryNotificationAdapter;
import ru.promoit.shortener.infra.ShortCodeGenerator;

class UrlShortenerServiceTest {

    private LinkRepository repository;
    private InMemoryNotificationAdapter notificationAdapter;
    private List<String> openedUrls;
    private MutableClock clock;
    private AppConfig config;
    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLinkRepository();
        notificationAdapter = new InMemoryNotificationAdapter();
        openedUrls = new ArrayList<>();
        clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        config = new AppConfig("clck.ru/", 6, Duration.ofHours(24), 3, 60);
        service = new UrlShortenerService(
                repository,
                notificationAdapter,
                openedUrls::add,
                new ShortCodeGenerator(),
                config,
                clock);
    }

    @Test
    @DisplayName("1. Создание короткой ссылки с префиксом домена и нужной длиной кода")
    void shouldCreateShortLinkWithValidFormat() {
        UUID user = UUID.randomUUID();
        ShortLink link = service.createShortLink("https://www.baeldung.com/java-9-http-client", user, null);
        assertTrue(link.getShortUrl().startsWith("clck.ru/"));
        assertEquals("clck.ru/".length() + 6, link.getShortUrl().length());
    }

    @Test
    @DisplayName("2. Уникальные короткие ссылки для разных пользователей на один и тот же URL")
    void shouldGenerateUniqueLinksForDifferentUsers() {
        String url = "https://www.baeldung.com/java-9-http-client";
        ShortLink link1 = service.createShortLink(url, UUID.randomUUID(), null);
        ShortLink link2 = service.createShortLink(url, UUID.randomUUID(), null);
        assertNotEquals(link1.getShortUrl(), link2.getShortUrl());
    }

    @Test
    @DisplayName("3. Успешный редирект открывает исходный URL в браузере")
    void shouldNavigateAndOpenBrowser() {
        UUID user = UUID.randomUUID();
        ShortLink link = service.createShortLink("https://ru.stackoverflow.com", user, 5);
        String target = service.navigate(link.getShortUrl());
        assertEquals("https://ru.stackoverflow.com", target);
        assertEquals(1, openedUrls.size());
    }

    @Test
    @DisplayName("4. Блокировка перехода после исчерпания лимита кликов")
    void shouldBlockNavigationWhenClickLimitExceeded() {
        UUID user = UUID.randomUUID();
        ShortLink link = service.createShortLink("https://example.com", user, 2);
        service.navigate(link.getShortUrl());
        service.navigate(link.getShortUrl());

        assertThrows(LinkExpiredException.class, () -> service.navigate(link.getShortUrl()));
        assertEquals(2, openedUrls.size());
    }

    @Test
    @DisplayName("5. Отправка уведомления пользователю при исчерпании лимита переходов")
    void shouldNotifyUserWhenClickLimitReached() {
        UUID user = UUID.randomUUID();
        ShortLink link = service.createShortLink("https://example.com", user, 1);
        service.navigate(link.getShortUrl());

        List<String> notifications = notificationAdapter.consumeUserNotifications(user);
        assertFalse(notifications.isEmpty());
        assertTrue(notifications.getFirst().contains(link.getShortUrl()));
    }

    @Test
    @DisplayName("6. Блокировка и удаление ссылки при попытке перехода после истечения TTL")
    void shouldExpireAndRemoveLinkOnNavigateAfterTtl() {
        UUID user = UUID.randomUUID();
        ShortLink link = service.createShortLink("https://example.com", user, 5);
        clock.advance(Duration.ofHours(25));

        assertThrows(LinkExpiredException.class, () -> service.navigate(link.getShortUrl()));
        assertFalse(repository.existsByShortUrl(link.getShortUrl()));
    }

    @Test
    @DisplayName("7. Автоматическая фоновая очистка протухших ссылок по TTL с уведомлением")
    void shouldAutoDeleteExpiredLinksDuringBackgroundCleanup() {
        UUID user = UUID.randomUUID();
        ShortLink link = service.createShortLink("https://example.com", user, 5);
        clock.advance(Duration.ofHours(24).plusSeconds(1));

        int removed = service.cleanupExpiredLinks();
        assertEquals(1, removed);
        assertFalse(repository.existsByShortUrl(link.getShortUrl()));
        assertFalse(notificationAdapter.consumeUserNotifications(user).isEmpty());
    }

    @Test
    @DisplayName("8. Владелец может изменить лимит переходов своей ссылки")
    void shouldAllowOwnerToUpdateMaxClicks() {
        UUID owner = UUID.randomUUID();
        ShortLink link = service.createShortLink("https://example.com", owner, 1);
        service.navigate(link.getShortUrl());

        service.updateMaxClicks(link.getShortUrl(), owner, 3);
        assertDoesNotThrow(() -> service.navigate(link.getShortUrl()));
    }

    @Test
    @DisplayName("9. Запрет изменения лимита чужой ссылки (проверка прав по UUID)")
    void shouldDenyLimitUpdateForNonOwner() {
        UUID owner = UUID.randomUUID();
        UUID hacker = UUID.randomUUID();
        ShortLink link = service.createShortLink("https://example.com", owner, 5);

        assertThrows(
                AccessDeniedException.class,
                () -> service.updateMaxClicks(link.getShortUrl(), hacker, 100));
    }

    @Test
    @DisplayName("10. Владелец может удалить свою ссылку")
    void shouldAllowOwnerToDeleteLink() {
        UUID owner = UUID.randomUUID();
        ShortLink link = service.createShortLink("https://example.com", owner, 5);
        service.deleteLink(link.getShortUrl(), owner);
        assertThrows(LinkNotFoundException.class, () -> service.navigate(link.getShortUrl()));
    }

    @Test
    @DisplayName("11. Запрет удаления чужой ссылки другим пользователем")
    void shouldDenyDeletionForNonOwner() {
        UUID owner = UUID.randomUUID();
        UUID anotherUser = UUID.randomUUID();
        ShortLink link = service.createShortLink("https://example.com", owner, 5);

        assertThrows(
                AccessDeniedException.class,
                () -> service.deleteLink(link.getShortUrl(), anotherUser));
    }

    @Test
    @DisplayName("12. Поддержка нескольких ссылок у одного пользователя и изоляция списков")
    void shouldSupportMultipleLinksPerUserAndIsolateUsers() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        service.createShortLink("https://site1.com", user1, 5);
        service.createShortLink("https://site2.com", user1, 5);
        service.createShortLink("https://site3.com", user2, 5);

        assertEquals(2, service.getUserLinks(user1).size());
        assertEquals(1, service.getUserLinks(user2).size());
    }

    @Test
    @DisplayName("13. Ошибка при передаче невалидного или пустого URL")
    void shouldRejectInvalidUrls() {
        UUID user = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.createShortLink("", user, 5));
        assertThrows(IllegalArgumentException.class, () -> service.createShortLink("ftp://files.com", user, 5));
        assertThrows(IllegalArgumentException.class, () -> service.createShortLink("not-a-url", user, 5));
    }

    @Test
    @DisplayName("14. Ошибка при установке нулевого или отрицательного лимита переходов")
    void shouldRejectNonPositiveClickLimits() {
        UUID user = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.createShortLink("https://ok.ru", user, 0));
        assertThrows(IllegalArgumentException.class, () -> service.createShortLink("https://ok.ru", user, -5));
    }

    @Test
    @DisplayName("15. Ошибка при переходе по несуществующей короткой ссылке")
    void shouldThrowLinkNotFoundForUnknownShortUrl() {
        assertThrows(LinkNotFoundException.class, () -> service.navigate("clck.ru/000000"));
    }

    @Test
    @DisplayName("16. Загрузка параметров из конфигурационного файла application.properties")
    void shouldLoadConfigurationFromPropertiesFile() {
        AppConfig loaded = new AppConfig("application.properties");
        assertEquals("clck.ru/", loaded.baseDomain());
        assertEquals(6, loaded.codeLength());
        assertEquals(Duration.ofSeconds(86400), loaded.ttl());
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
