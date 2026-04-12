package aigc.gameflow.service;


import aigc.gameflow.model.graph.GameCharacter;
import aigc.gameflow.repository.CharacterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KnowledgeService {
    /**
     * 保存或更新角色基础信息
     * */

    @Autowired
    private CharacterRepository characterRepository;

    public void saveCharacter(String name, String race, String desc){
        GameCharacter character = characterRepository.findByName(name);
        if (character == null) {
            character = new GameCharacter();
            character.setName(name);
        } else {
            log.info("角色已存在，执行更新: {}", name);
        }
        character.setRace(race);
        character.setDesc(desc);
        characterRepository.save(character);
    }

    public void addRelationship(String fromName,String toName){
        GameCharacter from = characterRepository.findByName(fromName);
        GameCharacter to = characterRepository.findByName(toName);
        if (from != null && to != null){
            from.addFriend(to);
            characterRepository.save(from);
            log.info("已添加羁绊{} -> {}",fromName,toName);
        }else {
            log.info("添加羁绊失败");
        }
    }

    public String getCharacterLore(String name){
        GameCharacter character = characterRepository.findByName(name);
        if (character == null){
            return "未找到关于["+name+"]的关系";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("角色名: ").append(character.getName()).append("\n");
        sb.append("种族: ").append(character.getRace()).append("\n");
        sb.append("设定: ").append(character.getDesc()).append("\n");
        if (character.getFriends() != null && !character.getFriends().isEmpty()) {
            sb.append("他认识的人有：");
            for (GameCharacter friend : character.getFriends()) {
                sb.append(friend.getName()).append(" ");
            }
        } else {
            sb.append("他认识的人有：暂无已知关系");
        }
        return sb.toString();
    }
}
