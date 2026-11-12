package ru.promoit.shortener;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import ru.promoit.shortener.config.AppConfig;
import ru.promoit.shortener.core.port.BrowserPort;
import ru.promoit.shortener.core.port.LinkRepository;
import ru.promoit.shortener.core.port.NotificationPort;
import ru.promoit.shortener.core.service.UrlShortenerService;
import ru.promoit.shortener.infra.DesktopBrowserAdapter;
import ru.promoit.shortener.infra.InMemoryLinkRepository;
import ru.promoit.shortener.infra.InMemoryNotificationAdapter;
import ru.promoit.shortener.infra.ShortCodeGenerator;
import ru.promoit.shortener.ui.ConsoleController;

public class Main {
    public static void main(String[] args) {
        AppConfig config = new AppConfig("application.properties");
        LinkRepository repository = new InMemoryLinkRepository();
        NotificationPort notificationPort = new InMemoryNotificationAdapter();
        BrowserPort browserPort = new DesktopBrowserAdapter();
        ShortCodeGenerator codeGenerator = new ShortCodeGenerator();

        UrlShortenerService service = new UrlShortenerService(
                repository, notificationPort, browserPort, codeGenerator, config, Clock.systemUTC());

        // Фабрика легковесных виртуальных потоков (Java 21-25)
        ThreadFactory virtualThreadFactory = Thread.ofVirtual().name("ttl-cleanup-vt-", 0).factory();

        try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(virtualThreadFactory)) {
            scheduler.scheduleAtFixedRate(
                    service::cleanupExpiredLinks,
                    config.cleanupIntervalSeconds(),
                    config.cleanupIntervalSeconds(),
                    TimeUnit.SECONDS);

            ConsoleController controller = new ConsoleController(service, notificationPort);
            controller.start();
        }
    }
}
