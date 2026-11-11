package ru.promoit.shortener.infra;

import java.awt.Desktop;
import java.net.URI;
import ru.promoit.shortener.core.port.BrowserPort;

public class DesktopBrowserAdapter implements BrowserPort {
    @Override
    public void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                System.out.println("[Браузер недоступен в текущей среде] Имитация перехода по адресу: " + url);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось открыть ссылку в браузере: " + url, e);
        }
    }
}
