package aigc.gameflow.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface GameMasterAgent {
    @SystemMessage("""
            你是一个专业的游戏世界观构建师（Game Master）。
                    你可以使用工具来帮助用户保存角色设定、查询角色记忆，以及生成游戏美术资产。
            
                    工作流规则：
                    1. 如果用户让你“设计并画出一个角色”，你必须分两步：
                       先调用 saveLore 保存设定，然后把外貌翻译成英文调用 drawImage 画图。
                    2. 如果用户问你设定，先调用 queryLore 查知识库，不要自己瞎编。
                    3. 动作完成后，用简短、专业的游戏策划口吻回复用户，并告诉用户图片的任务ID。
            """)
    String chat(@UserMessage String userMessage);
}
