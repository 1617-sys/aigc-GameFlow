package aigc.gameflow.controller;

import aigc.gameflow.service.GameMasterAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/agent")
public class AgentController {
    @Autowired
    GameMasterAgent gameMasterAgent;

    @PostMapping("/chat")
    public String chat(@RequestBody Map<String,String> params){
        System.out.println("🚀 成功进入 AgentController 房间！");
        String msg = params.get("message");
        return gameMasterAgent.chat(msg);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, String> params) {
        String msg = params.get("message");
        // 设置超时时间为 0 (无限等待)，因为 AI 生成可能比较慢
        SseEmitter emitter = new SseEmitter(0L);

        // 调用流式接口，逐字推送
        gameMasterAgent.chatStream(msg)
                .onNext(token -> {
                    try {
                        // 将每个字推给前端
                        emitter.send(token);
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                })
                .onComplete(response -> emitter.complete())
                .onError(emitter::completeWithError)
                .start();

        return emitter;
    }
}
