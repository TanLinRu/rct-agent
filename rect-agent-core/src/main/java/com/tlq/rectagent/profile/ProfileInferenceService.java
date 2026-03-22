package com.tlq.rectagent.profile;

import com.tlq.rectagent.data.entity.ProfileChange;
import com.tlq.rectagent.data.service.ProfileChangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileInferenceService {

    private final ProfileChangeService profileChangeService;

    private static final Map<String, String> INTEREST_KEYWORDS = Map.ofEntries(
            Map.entry("account_security", "账户|安全|登录|密码|MFA|暴力破解|认证|会话劫持|撞库"),
            Map.entry("device_fingerprint", "设备|指纹|FP|模拟器|多开|越狱|Jailbreak|Root|设备伪造"),
            Map.entry("risk_analysis", "风险|评估|RPI|概率|影响|风险识别|风险量化"),
            Map.entry("data_export", "导出|报表|下载|CSV|PDF|数据导出"),
            Map.entry("visualization", "图表|可视化|热力图|看板|dashboard"),
            Map.entry("network_security", "IP|代理|VPN|网络|流量|防火墙|端口扫描"),
            Map.entry("behavior_analysis", "行为|异常|模式|基线|LSTM|机器学习|用户行为")
    );

    private static final Map<String, String> EXPERTISE_KEYWORDS = Map.ofEntries(
            Map.entry("expert", "RPI|OWASP|NIST|CVE|漏洞利用|SLA|合规|PIPL|GDPR"),
            Map.entry("intermediate", "量化|评估|影响|阈值|热力图|看板|策略"),
            Map.entry("beginner", "什么是|怎么|如何|帮我|给我|解释一下|告诉我")
    );

    public void inferAndRecord(String userId, String userInput, String aiResponse) {
        String combined = ((userInput != null ? userInput : "") + " " + (aiResponse != null ? aiResponse : "")).toLowerCase();
        Map<String, ProfileChange> currentProfile = buildCurrentProfile(userId);

        ProfileChange interestChange = inferInterestArea(userId, combined, currentProfile);
        if (interestChange != null) recordChange(interestChange);

        ProfileChange expertiseChange = inferExpertiseLevel(userId, combined, currentProfile);
        if (expertiseChange != null) recordChange(expertiseChange);

        ProfileChange scopeChange = inferDataScopePreference(userId, combined, currentProfile);
        if (scopeChange != null) recordChange(scopeChange);
    }

    private ProfileChange inferInterestArea(String userId, String combined, Map<String, ProfileChange> current) {
        String currentValue = current.containsKey("interest_area") ? current.get("interest_area").getNewValue() : null;
        for (Map.Entry<String, String> entry : INTEREST_KEYWORDS.entrySet()) {
            if (Pattern.compile(entry.getValue(), Pattern.CASE_INSENSITIVE).matcher(combined).find()) {
                if (!entry.getKey().equals(currentValue)) {
                    ProfileChange c = new ProfileChange();
                    c.setUserId(userId);
                    c.setFieldName("interest_area");
                    c.setOldValue(currentValue);
                    c.setNewValue(entry.getKey());
                    c.setReasoning("基于对话内容自动推断: 检测到关键词[" + entry.getKey() + "]");
                    return c;
                }
                break;
            }
        }
        return null;
    }

    private ProfileChange inferExpertiseLevel(String userId, String combined, Map<String, ProfileChange> current) {
        String currentValue = current.containsKey("expertise_level") ? current.get("expertise_level").getNewValue() : null;
        for (Map.Entry<String, String> entry : EXPERTISE_KEYWORDS.entrySet()) {
            if (Pattern.compile(entry.getValue(), Pattern.CASE_INSENSITIVE).matcher(combined).find()) {
                if (!entry.getKey().equals(currentValue)) {
                    ProfileChange c = new ProfileChange();
                    c.setUserId(userId);
                    c.setFieldName("expertise_level");
                    c.setOldValue(currentValue);
                    c.setNewValue(entry.getKey());
                    c.setReasoning("基于对话内容自动推断: 检测到专业术语[" + entry.getKey() + "]");
                    return c;
                }
                break;
            }
        }
        return null;
    }

    private ProfileChange inferDataScopePreference(String userId, String combined, Map<String, ProfileChange> current) {
        String currentValue = current.containsKey("data_scope") ? current.get("data_scope").getNewValue() : null;
        String newValue = null;
        if (Pattern.compile("(最近|过去|昨日|本周|本月)", Pattern.CASE_INSENSITIVE).matcher(combined).find()) {
            newValue = "recent";
        } else if (Pattern.compile("(历史|全年|历年|长期)", Pattern.CASE_INSENSITIVE).matcher(combined).find()) {
            newValue = "historical";
        } else if (Pattern.compile("(实时|当前|今日|现在)", Pattern.CASE_INSENSITIVE).matcher(combined).find()) {
            newValue = "realtime";
        }
        if (newValue != null && !newValue.equals(currentValue)) {
            ProfileChange c = new ProfileChange();
            c.setUserId(userId);
            c.setFieldName("data_scope");
            c.setOldValue(currentValue);
            c.setNewValue(newValue);
            c.setReasoning("基于对话内容自动推断: 数据范围偏好[" + newValue + "]");
            return c;
        }
        return null;
    }

    private Map<String, ProfileChange> buildCurrentProfile(String userId) {
        List<ProfileChange> changes = profileChangeService.getChangesByUserId(userId);
        Map<String, ProfileChange> latest = new LinkedHashMap<>();
        for (ProfileChange c : changes) {
            latest.putIfAbsent(c.getFieldName(), c);
        }
        return latest;
    }

    private void recordChange(ProfileChange change) {
        if (change == null || change.getNewValue() == null) return;
        profileChangeService.recordChange(
                change.getUserId(),
                change.getFieldName(),
                change.getOldValue(),
                change.getNewValue(),
                change.getReasoning()
        );
        log.info("自动画像推断: userId={}, field={}, {} -> {}",
                change.getUserId(), change.getFieldName(),
                change.getOldValue(), change.getNewValue());
    }
}
