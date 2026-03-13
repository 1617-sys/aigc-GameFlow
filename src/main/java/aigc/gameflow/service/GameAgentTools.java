package aigc.gameflow.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GameAgentTools {

    @Autowired
    private TaskService taskService;

    @Autowired
    private KnowledgeService knowledgeService;

    @Tool("当用户要求生成图片、立绘、CG、或者画画时，调用此工具。返回任务的UUID。")
    public String drawImage(@P("必须是英文，符合Danbooru标签风格的图像描述词，例如: 1girl, elf, forest") String englishPrompt) {
        System.out.println("🤖 Agent 触发生图工具: " + englishPrompt);
        return taskService.submitTask(englishPrompt);
    }

    @Tool("当用户要求创建新角色、保存角色设定、记录世界观时，调用此工具。")
    public String saveLore(
            @P("角色姓名") String name,
            @P("种族，如人类、精灵") String race,
            @P("角色的详细背景设定") String desc) {
        System.out.println("🤖 Agent 触发知识图谱写入: " + name);
        knowledgeService.saveCharacter(name, race, desc);
        return "角色 [" + name + "] 设定已成功存入知识库。";
    }

    @Tool("当用户询问某个角色的信息、设定、或者他认识谁时，调用此工具查询知识库。")
    public String queryLore(@P("要查询的角色姓名") String name) {
        System.out.println("🤖 Agent 触发知识图谱查询: " + name);
        return knowledgeService.getCharacterLore(name);
    }
}
