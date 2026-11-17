package workshop.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.Desktop;
import java.net.URI;

/**
 * Web 端入口。
 *
 * 启动方式：
 *   java -jar target/lc-workshop-0.1.0-SNAPSHOT-web.jar     ← 推荐，双击 web.bat 也行
 *   mvn compile exec:exec                                   ← 开发时用
 * 访问地址：http://localhost:8080
 */
@SpringBootApplication
public class LcWorkshopApplication {

    @Value("${server.port:8080}")
    private int port;

    @Value("${workshop.open-browser:true}")
    private boolean openBrowser;

    public static void main(String[] args) {
        SpringApplication.run(LcWorkshopApplication.class, args);
    }

    /** 启动完成后自动打开浏览器（可用 --workshop.open-browser=false 关掉） */
    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        if (!openBrowser) {
            return;
        }
        String url = "http://localhost:" + port;
        System.out.println("Web 端已启动：" + url);
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            System.out.println("（自动打开浏览器失败，请手动访问上面的地址）");
        }
    }
}
