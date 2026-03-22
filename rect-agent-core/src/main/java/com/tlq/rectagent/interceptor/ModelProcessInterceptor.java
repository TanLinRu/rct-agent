package com.tlq.rectagent.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class ModelProcessInterceptor extends ModelInterceptor {
    public static final String MOCK_DATA = "[\n" +
            "    {\n" +
            "        \"login_token\":\"a1b2c3d4e5f67890a1b2c3d4e5f67890\",\n" +
            "        \"fp_earliest\":\"multi_device_fp_001\",\n" +
            "        \"account_id_earliest\":\"RISK_USER_001\",\n" +
            "        \"user_id_earliest\":\"RISK_USER_001\",\n" +
            "        \"server_id_earliest\":\"10000\",\n" +
            "        \"role_level_earliest\":\"1\",\n" +
            "        \"game_version_earliest\":\"1.0\",\n" +
            "        \"sdk_version_earliest\":\"1.2.52\",\n" +
            "        \"device_name_earliest\":\"iPhone9,1\",\n" +
            "        \"system_version_earliest\":\"14.7.1\",\n" +
            "        \"remote_ip_earliest\":\"10.157.16.31, 10.32.65.92\",\n" +
            "        \"receive_time_earliest\":\"2025-11-18 13:48:02.176\",\n" +
            "        \"account_name_earliest\":\"multi_player_1\",\n" +
            "        \"role_name_earliest\":\"newbie\",\n" +
            "        \"country_earliest\":\"CN\",\n" +
            "        \"city_earliest\":\"Beijing\",\n" +
            "        \"app_name_earliest\":\"GameApp\",\n" +
            "        \"apk_sign_earliest\":\"cbd0745a1cc2a9281ecf40699abc1d1d\",\n" +
            "        \"package_name_earliest\":\"com.game.app\",\n" +
            "        \"safe_tags_map\":{\n" +
            "            \"ios_act_mutiapp\": [\n" +
            "                {\n" +
            "                    \"tagName\":\"安装多开插件\",\n" +
            "                    \"description\":\"检测到多开框架\",\n" +
            "                    \"count\":null\n" +
            "                }\n" +
            "            ]\n" +
            "        }\n" +
            "    },\n" +
            "    {\n" +
            "        \"login_token\":\"b2c3d4e5f67890a1b2c3d4e5f67890a1\",\n" +
            "        \"fp_earliest\":\"multi_device_fp_001\",\n" +
            "        \"account_id_earliest\":\"RISK_USER_002\",\n" +
            "        \"user_id_earliest\":\"RISK_USER_002\",\n" +
            "        \"server_id_earliest\":\"10000\",\n" +
            "        \"role_level_earliest\":\"1\",\n" +
            "        \"game_version_earliest\":\"1.0\",\n" +
            "        \"sdk_version_earliest\":\"1.2.52\",\n" +
            "        \"device_name_earliest\":\"iPhone9,1\",\n" +
            "        \"system_version_earliest\":\"14.7.1\",\n" +
            "        \"remote_ip_earliest\":\"10.157.16.31, 10.32.65.92\",\n" +
            "        \"receive_time_earliest\":\"2025-11-18 13:50:15.331\",\n" +
            "        \"account_name_earliest\":\"multi_player_2\",\n" +
            "        \"role_name_earliest\":\"newbie\",\n" +
            "        \"country_earliest\":\"CN\",\n" +
            "        \"city_earliest\":\"Beijing\",\n" +
            "        \"app_name_earliest\":\"GameApp\",\n" +
            "        \"apk_sign_earliest\":\"cbd0745a1cc2a9281ecf40699abc1d1d\",\n" +
            "        \"package_name_earliest\":\"com.game.app\",\n" +
            "        \"safe_tags_map\":{\n" +
            "        }\n" +
            "    },\n" +
            "    {\n" +
            "        \"login_token\":\"c3d4e5f67890a1b2c3d4e5f67890a1b2\",\n" +
            "        \"fp_earliest\":\"spoof_device_fp_001\",\n" +
            "        \"account_id_earliest\":\"SPOOF_USER_001\",\n" +
            "        \"user_id_earliest\":\"SPOOF_USER_001\",\n" +
            "        \"server_id_earliest\":\"10000\",\n" +
            "        \"role_level_earliest\":\"1\",\n" +
            "        \"game_version_earliest\":\"1.0\",\n" +
            "        \"sdk_version_earliest\":\"1.2.52\",\n" +
            "        \"device_name_earliest\":\"iPhone12,1\",\n" +
            "        \"system_version_earliest\":\"16.5\",\n" +
            "        \"remote_ip_earliest\":\"10.157.22.45, 10.32.65.92\",\n" +
            "        \"receive_time_earliest\":\"2025-11-18 14:05:10.000\",\n" +
            "        \"account_name_earliest\":\"spoof_player_1\",\n" +
            "        \"role_name_earliest\":\"newbie\",\n" +
            "        \"country_earliest\":\"CN\",\n" +
            "        \"city_earliest\":\"Shanghai\",\n" +
            "        \"app_name_earliest\":\"GameApp\",\n" +
            "        \"apk_sign_earliest\":\"cbd0745a1cc2a9281ecf40699abc1d1d\",\n" +
            "        \"package_name_earliest\":\"com.game.app\",\n" +
            "        \"safe_tags_map\":{\n" +
            "        }\n" +
            "    },\n" +
            "    {\n" +
            "        \"login_token\":\"d4e5f67890a1b2c3d4e5f67890a1b2c3\",\n" +
            "        \"fp_earliest\":\"spoof_device_fp_001\",\n" +
            "        \"account_id_earliest\":\"SPOOF_USER_002\",\n" +
            "        \"user_id_earliest\":\"SPOOF_USER_002\",\n" +
            "        \"server_id_earliest\":\"10000\",\n" +
            "        \"role_level_earliest\":\"1\",\n" +
            "        \"game_version_earliest\":\"1.0\",\n" +
            "        \"sdk_version_earliest\":\"1.2.52\",\n" +
            "        \"device_name_earliest\":\"iPhone12,1\",\n" +
            "        \"system_version_earliest\":\"16.5\",\n" +
            "        \"remote_ip_earliest\":\"10.157.22.45, 10.32.65.92\",\n" +
            "        \"receive_time_earliest\":\"2025-11-18 14:07:22.418\",\n" +
            "        \"account_name_earliest\":\"spoof_player_2\",\n" +
            "        \"role_name_earliest\":\"newbie\",\n" +
            "        \"country_earliest\":\"CN\",\n" +
            "        \"city_earliest\":\"Shanghai\",\n" +
            "        \"app_name_earliest\":\"GameApp\",\n" +
            "        \"apk_sign_earliest\":\"cbd0745a1cc2a9281ecf40699abc1d1d\",\n" +
            "        \"package_name_earliest\":\"com.game.app\",\n" +
            "        \"safe_tags_map\":{\n" +
            "        }\n" +
            "    },\n" +
            "    {\n" +
            "        \"login_token\":\"e5f67890a1b2c3d4e5f67890a1b2c3d4\",\n" +
            "        \"fp_earliest\":\"root_device_fp_001\",\n" +
            "        \"account_id_earliest\":\"ROOT_USER_001\",\n" +
            "        \"user_id_earliest\":\"ROOT_USER_001\",\n" +
            "        \"server_id_earliest\":\"10000\",\n" +
            "        \"role_level_earliest\":\"85\",\n" +
            "        \"game_version_earliest\":\"1.0\",\n" +
            "        \"sdk_version_earliest\":\"1.2.52\",\n" +
            "        \"device_name_earliest\":\"iPhone11,8\",\n" +
            "        \"system_version_earliest\":\"15.0\",\n" +
            "        \"remote_ip_earliest\":\"10.157.99.99, 10.32.65.92\",\n" +
            "        \"receive_time_earliest\":\"2025-11-18 15:22:47.891\",\n" +
            "        \"account_name_earliest\":\"root_player\",\n" +
            "        \"role_name_earliest\":\"hacker\",\n" +
            "        \"country_earliest\":\"CN\",\n" +
            "        \"city_earliest\":\"Beijing\",\n" +
            "        \"app_name_earliest\":\"GameApp\",\n" +
            "        \"apk_sign_earliest\":\"fake_sign_root_abc\",\n" +
            "        \"package_name_earliest\":\"com.game.app\",\n" +
            "        \"safe_tags_map\":{\n" +
            "            \"root_detected\": [\n" +
            "                {\n" +
            "                    \"tagName\":\"检测到越狱\",\n" +
            "                    \"description\":\"设备已越狱\",\n" +
            "                    \"count\":null\n" +
            "                }\n" +
            "            ]\n" +
            "        }\n" +
            "    },\n" +
            "    {\n" +
            "        \"login_token\":\"f67890a1b2c3d4e5f67890a1b2c3d4e5\",\n" +
            "        \"fp_earliest\":\"proxy_device_fp_001\",\n" +
            "        \"account_id_earliest\":\"PROXY_USER_001\",\n" +
            "        \"user_id_earliest\":\"PROXY_USER_001\",\n" +
            "        \"server_id_earliest\":\"10000\",\n" +
            "        \"role_level_earliest\":\"1\",\n" +
            "        \"game_version_earliest\":\"1.0\",\n" +
            "        \"sdk_version_earliest\":\"1.2.52\",\n" +
            "        \"device_name_earliest\":\"iPhone13,2\",\n" +
            "        \"system_version_earliest\":\"17.2\",\n" +
            "        \"remote_ip_earliest\":\"43.240.10.100, 10.32.65.92\",\n" +
            "        \"receive_time_earliest\":\"2025-11-18 16:01:05.223\",\n" +
            "        \"account_name_earliest\":\"proxy_player_1\",\n" +
            "        \"role_name_earliest\":\"newbie\",\n" +
            "        \"country_earliest\":\"CN\",\n" +
            "        \"city_earliest\":\"Unknown\",\n" +
            "        \"app_name_earliest\":\"GameApp\",\n" +
            "        \"apk_sign_earliest\":\"cbd0745a1cc2a9281ecf40699abc1d1d\",\n" +
            "        \"package_name_earliest\":\"com.game.app\",\n" +
            "        \"safe_tags_map\":{\n" +
            "        }\n" +
            "    },\n" +
            "    {\n" +
            "        \"login_token\":\"7890a1b2c3d4e5f67890a1b2c3d4e5f6\",\n" +
            "        \"fp_earliest\":\"proxy_device_fp_002\",\n" +
            "        \"account_id_earliest\":\"PROXY_USER_002\",\n" +
            "        \"user_id_earliest\":\"PROXY_USER_002\",\n" +
            "        \"server_id_earliest\":\"10000\",\n" +
            "        \"role_level_earliest\":\"1\",\n" +
            "        \"game_version_earliest\":\"1.0\",\n" +
            "        \"sdk_version_earliest\":\"1.2.52\",\n" +
            "        \"device_name_earliest\":\"SM-G998B\",\n" +
            "        \"system_version_earliest\":\"Android 13\",\n" +
            "        \"remote_ip_earliest\":\"43.240.10.100, 10.32.65.92\",\n" +
            "        \"receive_time_earliest\":\"2025-11-18 16:02:18.774\",\n" +
            "        \"account_name_earliest\":\"proxy_player_2\",\n" +
            "        \"role_name_earliest\":\"newbie\",\n" +
            "        \"country_earliest\":\"CN\",\n" +
            "        \"city_earliest\":\"Unknown\",\n" +
            "        \"app_name_earliest\":\"GameApp\",\n" +
            "        \"apk_sign_earliest\":\"cbd0745a1cc2a9281ecf40699abc1d1d\",\n" +
            "        \"package_name_earliest\":\"com.game.app\",\n" +
            "        \"safe_tags_map\":{\n" +
            "        }\n" +
            "    },\n" +
            "    {\n" +
            "        \"login_token\":\"890a1b2c3d4e5f67890a1b2c3d4e5f67\",\n" +
            "        \"fp_earliest\":\"future_os_fp_001\",\n" +
            "        \"account_id_earliest\":\"FUTURE_OS_USER\",\n" +
            "        \"user_id_earliest\":\"FUTURE_OS_USER\",\n" +
            "        \"server_id_earliest\":\"10000\",\n" +
            "        \"role_level_earliest\":\"120\",\n" +
            "        \"game_version_earliest\":\"1.0\",\n" +
            "        \"sdk_version_earliest\":\"1.2.52\",\n" +
            "        \"device_name_earliest\":\"iPhone13,2\",\n" +
            "        \"system_version_earliest\":\"18.7.2\",\n" +
            "        \"remote_ip_earliest\":\"10.157.4.121, 10.32.65.92\",\n" +
            "        \"receive_time_earliest\":\"2025-11-18 17:33:41.552\",\n" +
            "        \"account_name_earliest\":\"future_player\",\n" +
            "        \"role_name_earliest\":\"cheater\",\n" +
            "        \"country_earliest\":\"CN\",\n" +
            "        \"city_earliest\":\"Beijing\",\n" +
            "        \"app_name_earliest\":\"GameApp\",\n" +
            "        \"apk_sign_earliest\":\"cbd0745a1cc2a9281ecf40699abc1d1d\",\n" +
            "        \"package_name_earliest\":\"com.game.app\",\n" +
            "        \"safe_tags_map\":{\n" +
            "        }\n" +
            "    },\n" +
            "    {\n" +
            "        \"login_token\":\"90a1b2c3d4e5f67890a1b2c3d4e5f678\",\n" +
            "        \"fp_earliest\":\"night_login_fp_001\",\n" +
            "        \"account_id_earliest\":\"NIGHT_USER_001\",\n" +
            "        \"user_id_earliest\":\"NIGHT_USER_001\",\n" +
            "        \"server_id_earliest\":\"10000\",\n" +
            "        \"role_level_earliest\":\"50\",\n" +
            "        \"game_version_earliest\":\"1.0\",\n" +
            "        \"sdk_version_earliest\":\"1.2.52\",\n" +
            "        \"device_name_earliest\":\"iPhone14,3\",\n" +
            "        \"system_version_earliest\":\"17.5\",\n" +
            "        \"remote_ip_earliest\":\"118.193.30.45, 10.32.65.92\",\n" +
            "        \"receive_time_earliest\":\"2025-11-18 03:15:27.119\",\n" +
            "        \"account_name_earliest\":\"night_player\",\n" +
            "        \"role_name_earliest\":\"sleeper\",\n" +
            "        \"country_earliest\":\"CN\",\n" +
            "        \"city_earliest\":\"Guangzhou\",\n" +
            "        \"app_name_earliest\":\"GameApp\",\n" +
            "        \"apk_sign_earliest\":\"cbd0745a1cc2a9281ecf40699abc1d1d\",\n" +
            "        \"package_name_earliest\":\"com.game.app\",\n" +
            "        \"safe_tags_map\":{\n" +
            "        }\n" +
            "    },\n" +
            "    {\n" +
            "        \"login_token\":\"0a1b2c3d4e5f67890a1b2c3d4e5f6789\",\n" +
            "        \"fp_earliest\":\"abnormal_level_fp_001\",\n" +
            "        \"account_id_earliest\":\"ABNORMAL_LEVEL_USER\",\n" +
            "        \"user_id_earliest\":\"ABNORMAL_LEVEL_USER\",\n" +
            "        \"server_id_earliest\":\"10000\",\n" +
            "        \"role_level_earliest\":\"999999\",\n" +
            "        \"game_version_earliest\":\"1.0\",\n" +
            "        \"sdk_version_earliest\":\"1.2.52\",\n" +
            "        \"device_name_earliest\":\"FakeDeviceXYZ\",\n" +
            "        \"system_version_earliest\":\"99.99.99\",\n" +
            "        \"remote_ip_earliest\":\"45.76.123.88, 10.32.65.92\",\n" +
            "        \"receive_time_earliest\":\"2025-11-18 18:44:59.003\",\n" +
            "        \"account_name_earliest\":\"god_player\",\n" +
            "        \"role_name_earliest\":\"god\",\n" +
            "        \"country_earliest\":\"XX\",\n" +
            "        \"city_earliest\":\"XXX\",\n" +
            "        \"app_name_earliest\":\"FakeApp\",\n" +
            "        \"apk_sign_earliest\":\"ffffffffffffffffffffffffffffffff\",\n" +
            "        \"package_name_earliest\":\"com.fake.app\",\n" +
            "        \"safe_tags_map\":{\n" +
            "        }\n" +
            "    }\n" +
            "]";

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        String traceId = MDC.get("traceId");
        List<?> messages = request.getMessages();
        log.info("[{}] 发送请求到模型: {} 条消息, 消息类型: {}", 
                traceId, messages.size(), messages.getClass().getSimpleName());
        log.debug("[{}] 消息内容: {}", traceId, messages);

        long startTime = System.currentTimeMillis();

        ModelResponse response = handler.call(request);

        long duration = System.currentTimeMillis() - startTime;
        log.info("[{}] 模型响应完成: 耗时={}ms, 响应类型={}", 
                traceId, duration, response != null ? response.getClass().getSimpleName() : "null");

        return response;
    }

    @Override
    public String getName() {
        return "ModelProcessInterceptor";
    }
//
//    // 创建增强的请求
////        String prompt = "你是一位资深的数据安全分析专家，专注于从复杂的数据结构中识别安全风险、异常模式和关键洞察。你的核心能力包括数据解析、风险识别、跨维度关联分析、风险量化评估、数据质量评估和业务影响映射。\n" +
////                "\n" +
////                "请仔细分析以下安全监控数据，该数据包含用户设备、账户行为和风险标签信息：\n" +
////                "<安全监控数据>\n" +
////                "{{SECURITY_DATA}}\n" +
////                "</安全监控数据>\n" +
//                "请仔细分析安全监控数据，该数据包含用户设备、账户行为和风险标签信息：\n" +
//                        "你的分析需满足以下要求：\n" +
//                        "1. **数据概览分析**：统计总记录数、唯一账户数、设备类型分布等关键维度；识别数据缺失、异常值等质量问题；概述整体风险态势\n" +
//                        "2. **核心风险识别**：检测账户异常、设备风险、网络风险、行为风险、签名风险五大类风险\n" +
//                        "3. **深度关联分析**：跨设备、账户、时间、地理等维度关联分析隐藏模式\n" +
//                        "4. **风险量化评估**：为风险分配优先级（高/中/低）和置信度评分（1-100%）\n" +
//                        "5. **数据洞察与建议**：提供非风险类关键洞察、业务影响说明和可操作建议\n" +
//                        "\n" +
//                        "你的输出必须严格遵循以下格式：\n" +
//                        "\n" +
//                        "【风险概览】\n" +
//                        "• 总记录数：X | 高风险项：X | 中风险项：X | 低风险项：X\n" +
//                        "• 关键风险摘要：[1-2句话总结最严重的风险]\n" +
//                        "\n" +
//                        "【详细风险分析】\n" +
//                        "[按风险等级从高到低排序，每个风险条目包含]\n" +
//                        "\uD83D\uDD34/\uD83D\uDFE1/\uD83D\uDFE2 [风险名称] (置信度：XX%)\n" +
//                        "• 证据：[具体数据支撑，需引用原始数据中的关键信息]\n" +
//                        "• 影响：[业务影响说明，如作弊风险、账户盗用等]\n" +
//                        "• 建议：[具体可落地的行动建议]\n" +
//                        "\n" +
//                        "【数据洞察】\n" +
//                        "• 洞察1：[非风险类的有价值发现，如用户行为模式]\n" +
//                        "• 洞察2：[技术趋势或设备偏好]\n" +
//                        "• 洞察3：[数据质量问题或监控改进建议]\n" +
//                        "\n" +
//                        "【行动优先级】\n" +
//                        "立即行动：[最高优先级的1-2个事项]\n" +
//                        "本周关注：[中等优先级的2-3个事项]\n" +
//                        "长期优化：[数据或系统改进的1-2个建议]\n" +
//                        "\n" +
//                        "注意事项：\n" +
//                        "- 所有风险分析必须有明确的数据支撑，避免主观臆断\n" +
//                        "- 风险等级用\uD83D\uDD34（高）/\uD83D\uDFE1（中）/\uD83D\uDFE2（低）标识，置信度需具体到百分比\n" +
//                        "- 输出需结构化、条理清晰，确保每个部分信息完整\n" +
//                        "- 行动建议需具备可操作性，能直接指导安全团队采取措施\n" +
//                        "- 数据洞察需区分风险类和非风险类发现\n" +
//                        "\n" +
//                        "现在开始你的分析。\n";
}
