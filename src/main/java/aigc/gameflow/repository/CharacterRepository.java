package aigc.gameflow.repository;

import aigc.gameflow.model.graph.GameCharacter;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CharacterRepository extends Neo4jRepository<GameCharacter, Long> {
    // 自动获得 save, findAll, findById 等方法
    // 也可以写自定义查询：
    GameCharacter findByName(String name);
}
