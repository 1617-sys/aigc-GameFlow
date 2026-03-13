import aigc.gameflow.model.entity.SysUser;
import aigc.gameflow.model.graph.GameCharacter;
import aigc.gameflow.repository.CharacterRepository;
import aigc.gameflow.service.UserService;
import aigc.gameflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(classes = aigc.gameflow.Main.class)
public class UserTest {

    @Autowired
    private UserService userService;

    @Autowired
    private CharacterRepository characterRepository;

    @Test
    void testRegisterAndLogin() {
        // 1. 测试注册
        String name = "test_user_" + System.currentTimeMillis();
        userService.register(name, "123456");
        System.out.println("✅ 注册成功");

        // 2. 测试登录
        SysUser user = userService.login(name, "123456");
        System.out.println("✅ 登录成功，用户ID: " + user.getId());

        // 3. 测试 JWT
        String token = JwtUtils.createToken(user.getId(), user.getUsername());
        System.out.println("✅ 生成 Token: " + token);

        Long parsedId = JwtUtils.getUserId(token);
        System.out.println("✅ 解析 Token 得到 ID: " + parsedId);
    }

    @Test
    public void testGraph() {
        // 1. 创建两个角色
        GameCharacter hero = new GameCharacter();
        hero.setName("亚瑟");
        hero.setDesc("正义的骑士");

        GameCharacter villain = new GameCharacter();
        villain.setName("魔王");
        villain.setDesc("邪恶的统治者");

        // 2. 建立关系 (亚瑟 认识 魔王)
        hero.addFriend(villain);

        // 3. 存入图数据库 (级联保存)
        characterRepository.save(hero);

        System.out.println("✅ 图谱构建成功！");
    }
}