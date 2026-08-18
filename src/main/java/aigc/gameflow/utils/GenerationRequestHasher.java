package aigc.gameflow.utils;

import aigc.gameflow.dto.GenerationSubmitRequest;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;

/** 将完整请求序列化并计算摘要，用于判断同一幂等键是否对应相同参数。 */
public final class GenerationRequestHasher {

    private GenerationRequestHasher() {
    }

    public static String hash(GenerationSubmitRequest request) {
        return DigestUtil.sha256Hex(JSON.toJSONString(request));
    }
}
