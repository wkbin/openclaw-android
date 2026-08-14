package com.openclaw.android.model

data class ProviderModel(
    val id: String,
    val name: String,
    val description: String,
)

data class ModelProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val api: String,
    val models: List<ProviderModel>,
)

/**
 * 内置官方厂商及其主流模型目录，供引导页与设置页共用。
 * 模型 ID、baseUrl、api 均来自 OpenClaw 官方扩展的 modelCatalog（对应各家官方 API 文档）。
 */
object ModelCatalog {
    val providers: List<ModelProvider> = listOf(
        ModelProvider(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            api = "openai-responses",
            models = listOf(
                ProviderModel("gpt-5.6-sol", "GPT-5.6 Sol", "旗舰模型"),
                ProviderModel("gpt-5.6-terra", "GPT-5.6 Terra", "均衡 · 推荐"),
                ProviderModel("gpt-5.6-luna", "GPT-5.6 Luna", "轻量快速"),
                ProviderModel("gpt-5.5", "GPT-5.5", "上一代均衡"),
                ProviderModel("gpt-5.5-pro", "GPT-5.5 Pro", "上一代旗舰"),
            ),
        ),
        ModelProvider(
            id = "anthropic",
            name = "Anthropic",
            baseUrl = "https://api.anthropic.com",
            api = "anthropic-messages",
            models = listOf(
                ProviderModel("claude-opus-5", "Claude Opus 5", "旗舰模型"),
                ProviderModel("claude-sonnet-5", "Claude Sonnet 5", "均衡 · 推荐"),
                ProviderModel("claude-haiku-4-5", "Claude Haiku 4.5", "轻量快速"),
            ),
        ),
        ModelProvider(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            api = "openai-completions",
            models = listOf(
                ProviderModel("deepseek-v4-flash", "DeepSeek V4 Flash", "快速 · 推荐"),
                ProviderModel("deepseek-v4-pro", "DeepSeek V4 Pro", "旗舰模型"),
            ),
        ),
        ModelProvider(
            id = "qwen",
            name = "通义千问",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            api = "openai-completions",
            models = listOf(
                ProviderModel("qwen3.7-plus", "qwen3.7-plus", "旗舰模型"),
                ProviderModel("qwen3.6-plus", "qwen3.6-plus", "均衡模型"),
                ProviderModel("qwen3-coder-next", "qwen3-coder-next", "编程模型"),
            ),
        ),
        ModelProvider(
            id = "kimi",
            name = "Kimi",
            baseUrl = "https://api.moonshot.ai/v1",
            api = "openai-completions",
            models = listOf(
                ProviderModel("kimi-k3", "Kimi K3", "旗舰 · 推荐"),
                ProviderModel("kimi-k2.7-code", "Kimi K2.7 Code", "编程模型"),
                ProviderModel("kimi-k2.7-code-highspeed", "Kimi K2.7 Code HighSpeed", "编程 · 快速"),
            ),
        ),
        ModelProvider(
            id = "stepfun",
            name = "阶跃星辰",
            baseUrl = "https://api.stepfun.ai/v1",
            api = "openai-completions",
            models = listOf(
                ProviderModel("step-3.7-flash", "Step 3.7 Flash", "推荐"),
                ProviderModel("step-3.5-flash", "Step 3.5 Flash", "均衡"),
            ),
        ),
        ModelProvider(
            id = "mimo",
            name = "小米 MiMo",
            baseUrl = "https://api.xiaomimimo.com/v1",
            api = "openai-completions",
            models = listOf(
                ProviderModel("mimo-v2.5", "Xiaomi MiMo V2.5", "均衡"),
                ProviderModel("mimo-v2.5-pro", "Xiaomi MiMo V2.5 Pro", "旗舰"),
            ),
        ),
    )

    fun providerOf(modelId: String): ModelProvider? {
        val provider = modelId.substringBefore('/')
        return providers.firstOrNull { it.id == provider }
    }

    fun providerIdOf(modelId: String): String? = providerOf(modelId)?.id

    fun displayName(modelId: String): String {
        val provider = providerOf(modelId) ?: return modelId
        val model = modelId.substringAfter('/', "")
        return provider.models.firstOrNull { it.id == model }?.name ?: modelId
    }
}
