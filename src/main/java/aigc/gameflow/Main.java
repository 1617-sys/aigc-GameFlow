package aigc.gameflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 应用启动入口，同时开启定时任务并扫描 MyBatis Mapper。 */
@EnableScheduling
@SpringBootApplication
@MapperScan("aigc.gameflow.mapper")
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
