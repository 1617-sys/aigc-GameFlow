package aigc.gameflow;

import org.mybatis.spring.annotation.MapperScan; // 1. 务必导入这个包
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// 2. 加上这行，强制指定 Mapper 的位置
@MapperScan("aigc.gameflow.mapper")
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}