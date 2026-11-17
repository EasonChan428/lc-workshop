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

    /** 启动完成后尝试自动打开浏览器（可用 --workshop.open-browser=false 关掉） */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String url = "http://localhost:" + port;
        System.out.println();
        System.out.println("=================================================");
        System.out.println(" Web 端已启动：" + url);
        System.out.println("=================================================");
        System.out.println();
        if (!openBrowser) {
            return;
        }
        String how = openBrowser(url);
        if (how == null) {
            System.out.println("（自动打开浏览器失败，请手动访问上面的地址）");
        } else {
            System.out.println("（已通过 " + how + " 打开浏览器；没弹出的话手动访问上面的地址）");
        }
    }

    /**
     * 三级兜底打开浏览器，返回成功的方式名，全失败返回 null。
     * AWT Desktop 在部分环境下不可用，所以再退到 Windows 原生命令。
     */
    static String openBrowser(String url) {
        // ① AWT Desktop（跨平台）
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return "Desktop.browse";
            }
        } catch (Exception ignored) {
            // 继续尝试下一种
        }
        // ② Windows：rundll32 调默认浏览器
        try {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            return "rundll32";
        } catch (Exception ignored) {
            // 继续尝试下一种
        }
        // ③ 最后退路：cmd start
        try {
            new ProcessBuilder("cmd", "/c", "start", "", url).start();
            return "cmd start";
        } catch (Exception ignored) {
            return null;
        }
    }
}
