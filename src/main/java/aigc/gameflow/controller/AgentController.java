package aigc.gameflow.controller;

import aigc.gameflow.service.GameMasterAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
