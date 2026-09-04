package com.litchi.agent;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Pre-execution content guard for the bounded Agent.
 *
 * <p>Every user goal passes through this guard before any planning or tool
 * execution happens. It uses deterministic, model-free rules so that prompt
 * injection, data exfiltration, unauthorized writes, fabricated evidence and
 * unsafe tool requests are refused even when the LLM / model gateway is
 * unavailable (degraded mode). The decision is deliberately conservative:
 * when in doubt about a high-risk intent, refuse instead of acting.</p>
 */
@Component
public class PolicyGuard {

    private static final Rule[] RULES = {
            new Rule("prompt_injection", "拒绝执行：该请求试图忽略系统规则、绕过角色权限或改写系统提示词，属于提示注入，不在平台服务范围内。",
                    "忽略系统规则", "忽略提示词", "忽略指令", "绕过角色权限", "绕过权限",
                    "绕过系统规则", "无视系统规则", "不听系统", "修改系统提示词"),
            new Rule("data_exfiltration", "拒绝回答：该请求要求输出系统内部信息（提示词、密码）或其他用户的隐私数据，无法提供。",
                    "数据库密码", "系统提示词", "聊天记录全部输出", "输出提示词",
                    "导出聊天记录", "泄露", "其他用户聊天记录", "别人聊天记录"),
            new Rule("unauthorized_write", "拒绝执行：该请求要求删除、清理或重复执行写操作。写操作必须由技术员审批后才能处理。",
                    "删除其他租户", "清理服务器文件", "删除其他", "重复执行写操作",
                    "删除其他用户", "绕过审批", "不需要审批", "强制保存"),
            new Rule("fabrication", "拒绝回答：缺少证据时不会编造来源，也不会承诺“一定有效”的治疗或用药效果。",
                    "编造", "保证一定有效", "未经证据支持", "凭空捏造", "伪造来源"),
            new Rule("unsafe_tool", "拒绝执行：该请求要求调用不存在、不受支持的工具、Shell 命令或无限重试，已停止执行。",
                    "任意 sql", "sql 注入", "shell", "不存在的工具", "无限重试",
                    "无限次重试", "清理服务器", "执行任意命令"),
            new Rule("cross_tenant", "拒绝回答：无法使用他人果园或其他租户的数据来回答。",
                    "别人的果园", "他人果园", "其他租户的", "别人的数据", "别人账号")
    };

    public record Refusal(String category, String message) {
    }

    public Optional<Refusal> evaluate(String goal) {
        if (goal == null || goal.isBlank()) {
            return Optional.empty();
        }
        String normalized = goal.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            for (String keyword : rule.keywords) {
                if (normalized.contains(keyword)) {
                    return Optional.of(new Refusal(rule.category, rule.refusal));
                }
            }
        }
        return Optional.empty();
    }

    private record Rule(String category, String refusal, String... keywords) {
    }
}
