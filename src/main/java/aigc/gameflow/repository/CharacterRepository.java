package aigc.gameflow.repository;

import aigc.gameflow.model.graph.GameCharacter;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CharacterRepository extends Neo4jRepository<GameCharacter, Long> {
    GameCharacter findByName(String name);
}
