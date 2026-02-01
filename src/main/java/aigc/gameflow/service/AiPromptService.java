package aigc.gameflow.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * AI 提示词服务
 * 这是一个声明式接口，LangChain4j 会自动通过动态代理实现它。
 * 你不需要写 "AiPromptServiceImpl" 类！
 */
@AiService // 1. 告诉 Spring 这是一个 AI 服务
public interface AiPromptService {

    /**
     * 定义系统提示词 (System Prompt)
     * 这就像是给 AI 设定人设。
     */
    @SystemMessage("""
        你是一个资深的二次元插画 Prompt 专家。
        你的任务是将用户的自然语言描述（中文）转化为 Stable Diffusion 只能读懂的 Danbooru 风格标签（英文）。
        
        请遵循以下规则：
        1. 必须包含高质量起手式："(masterpiece, best quality, highres), "
        2. 翻译准确：将核心元素翻译为通用的英文标签（如：女孩->1girl, 白发->white hair）。
        3. 格式规范：单词之间用逗号分隔，不要包含任何解释性语句，不要输出 Markdown 格式。
        4. 补充细节：适当补充画面细节（如：cinematic lighting, detailed face）。
        
        示例：
        输入：一个在雨中哭泣的女孩
        输出：(masterpiece, best quality), 1girl, crying, tears, rain, umbrella, wet clothes, detailed eyes, cinematic lighting
        """)
    String optimize(@UserMessage String userDescription);
}