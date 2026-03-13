package aigc.gameflow.model.graph;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Node("Character") // 这是一个“角色”节点
@Data
public class GameCharacter {

    @Id @GeneratedValue // 自动生成图数据库ID
    private Long id;

    private String name;
    private String desc; // 人物设定
    private String race;// 种族/身份/职业

    // 定义关系：我 -> [认识] -> 别人
    @Relationship(type = "KNOWS", direction = Relationship.Direction.OUTGOING)
    private List<GameCharacter> friends = new ArrayList<>();

    public void addFriend(GameCharacter friend) {
        this.friends.add(friend);
    }
}
