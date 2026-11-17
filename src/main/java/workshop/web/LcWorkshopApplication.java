package workshop.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web 端入口。
 *
 * 启动方式：mvn compile exec:exec
 * 访问地址：http://localhost:8080
 */
@SpringBootApplication
public class LcWorkshopApplication {

    public static void main(String[] args) {
        SpringApplication.run(LcWorkshopApplication.class, args);
    }
}
