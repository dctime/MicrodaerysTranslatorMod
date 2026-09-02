package net.github.dctime.datagen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.data.PackOutput;

/** Korean (ko_kr) strings -- see {@link LanguageProvider}'s javadoc for the per-locale-file rationale. */
public class LanguageProviderKoKr extends net.neoforged.neoforge.common.data.LanguageProvider {

    public LanguageProviderKoKr(PackOutput output) {
        super(output, MicrodaerysTranslatorClient.MODID, "ko_kr");
    }

    @Override
    protected void addTranslations() {
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_translation_cache", "번역 캐시 삭제");
        add("key." + MicrodaerysTranslatorClient.MODID + ".show_translation_in_gui", "스크린샷 번역 내용 GUI에 표시 (누르고 있기)");
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_showing_translation", "표시 중인 번역 다시 번역 (누르고 있기)");
        add("key.categories." + MicrodaerysTranslatorClient.MODID + ".key_mapping_category", "Google AI Studio Translator");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.API_KEY_PATH, "API 키");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.MODEL_NAME_PATH, "모델 이름");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_PATH, "프롬프트");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_SCREENSHOT_PATH, "스크린샷 번역용 프롬프트");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_JADE_CONFIG_PATH, "Jade 연동 활성화");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_FTB_QUEST_TRANSLATION_PATH, "FTB Quests 연동 활성화");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TOOLTIP_TRANSLATION_PATH, "아이템 툴팁 번역 활성화");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ADVANCEMENTS_CONFIG_PATH, "진행 상황 번역 활성화");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_SCREENSHOT_CONFIG_PATH, "스크린샷 번역 활성화");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ICON_CONFIG_PATH, "Jade와 툴팁 첫 줄에 아이템 이미지 함께 전송");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH, "번역 중 애니메이션 표시 활성화");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.TIMEOUT_DURATION_CONFIG_PATH, "제한 시간");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.FEATURE_TOGGLE_PATH, "기능");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENDPOINT_CONFIG_PATH, "공급자");

        add(MicrodaerysTranslatorClient.MODID + ".translator.vision_unsupported",
                "번역 실패! 선택한 모델은 이미지 입력을 지원하지 않습니다 -- 설정 화면에서 다른 모델을 선택해 보세요.");

        String p = MicrodaerysTranslatorClient.MODID + ".config.";
        add(p + "title", "Microdaery's Translator");
        add(p + "options_button", "Microdaery's Translator...");

        add(p + "section.service", "번역 서비스");
        add(p + "service", "서비스");
        add(p + "provider.google", "Google AI Studio");
        add(p + "provider.nvidia", "NVIDIA NIM");
        add(p + "provider.groq", "Groq");
        add(p + "provider.openrouter", "OpenRouter");
        add(p + "provider.mistral", "Mistral AI");
        add(p + "provider.deepseek", "DeepSeek");
        add(p + "provider.cerebras", "Cerebras");
        add(p + "provider.anthropic", "Anthropic Claude");
        add(p + "provider.openai", "OpenAI");
        add(p + "provider.ollama", "Ollama (로컬)");
        add(p + "provider.custom", "사용자 지정 공급자");

        add(p + "provider_mode", "제공자 모드");
        add(p + "provider_mode.single", "단일");
        add(p + "provider_mode.single.tooltip", "하나의 제공자만 사용합니다. 실패 시 자동 대체가 없습니다 -- 이 모드의 원래 동작과 동일합니다.");
        add(p + "provider_mode.priority", "우선순위");
        add(p + "provider_mode.priority.tooltip", "활성화된 제공자를 우선순위 순서로 시도하며, 일시적인 실패(속도 제한, 시간 초과, 연결 오류, 서버 오류) 시 다음 제공자로 대체합니다.");
        add(p + "provider_mode.round_robin", "라운드 로빈");
        add(p + "provider_mode.round_robin.tooltip", "활성화되고 사용 가능한 제공자를 균등하게 순환하며 사용합니다.");
        add(p + "provider_mode.automatic", "자동");
        add(p + "provider_mode.automatic.tooltip", "현재 부하, 최근 실패 기록, 지연 시간을 기반으로 가장 적합한 제공자를 자동으로 선택합니다. 우선순위는 보조 기준으로만 사용됩니다. 일시적인 실패 시에도 대체가 이루어집니다. 권장 설정입니다.");
        add(p + "manage_providers", "제공자 관리");
        add(p + "manage_providers.note", "여기서 각 제공자를 켜고 끌 수 있습니다. Configure 버튼으로 API 키, 모델, 우선순위, 분당 요청 수를 설정할 수 있습니다.");
        add(p + "provider.configure", "설정");
        add(p + "provider.enabled", "활성화");
        add(p + "provider.priority", "우선순위");
        add(p + "provider.priority.tooltip", "숫자가 작을수록 우선순위가 높습니다: Priority 모드에서는 먼저 시도되고, Automatic 모드에서는 보조 기준으로 사용됩니다. 1이 가장 높은 우선순위이며, 대부분의 경우 변경할 필요가 없습니다.");
        add(p + "provider.rpm", "분당 요청 수 (이 제공자)");
        add(p + "provider.rpm.tooltip", "이 값은 모드가 제안하는 시작값이며 공식적으로 보장된 할당량이 아닙니다 -- 실제 계정의 속도 제한에 맞게 조정하세요.");
        add(p + "provider.status", "상태");
        add(p + "provider.status.ready", "준비됨");
        add(p + "provider.status.rate_limited", "속도 제한됨 (%s초)");
        add(p + "provider.status.cooldown", "쿨다운 중 (%s초)");
        add(p + "provider.status.invalid_key", "잘못된 API 키");
        add(p + "provider.status.cannot_connect", "연결할 수 없음");
        add(p + "provider.status.disabled", "비활성화됨");
        add(p + "provider.status.detail.untried", "(시도한 적 없음)");
        add(p + "provider.status.detail", "(평균 %s초, %s초 전 시도)");

        add(p + "model", "모델");
        add(p + "model.custom", "사용자 지정...");
        add(p + "model.custom_id", "사용자 지정 모델 ID");
        add(p + "api_key", "API 키");
        add(p + "api_key.show", "표시");
        add(p + "api_key.hide", "숨기기");
        add(p + "api_key.paste", "붙여넣기");
        add(p + "api_key.ollama_note", "로컬 서비스 - API 키가 필요하지 않습니다");
        add(p + "test_connection", "연결 테스트");
        add(p + "test_connection.testing", "테스트 중...");
        add(p + "test_connection.connected", "연결됨");
        add(p + "test_connection.invalid_key", "API 키가 유효하지 않습니다");
        add(p + "test_connection.rate_limited", "속도 제한 초과");
        add(p + "test_connection.cannot_connect", "연결할 수 없습니다");
        add(p + "test_connection.http_error", "HTTP 오류 %s");
        add(p + "test_connection.model_not_found", "목록에서 모델 '%s'을(를) 찾을 수 없습니다 (목록이 불완전할 수 있습니다)");
        add(p + "test_connection.invalid_base_url", "잘못된 기본 URL");
        add(p + "test_connection.note", "연결 및 인증만 확인합니다 -- 번역 요청이 반드시 성공한다는 보장은 아닙니다");

        add(p + "custom_provider.name", "공급자 이름");
        add(p + "custom_provider.base_url", "기본 URL");
        add(p + "custom_provider.authentication", "인증");
        add(p + "custom_provider.authentication.bearer", "Bearer 토큰");
        add(p + "custom_provider.authentication.none", "없음");
        add(p + "custom_provider.supports_images", "이미지 입력 지원");
        add(p + "custom_provider.privacy_note", "요청과 번역 내용은 위에 설정한 서버로 직접 전송됩니다.");

        add(p + "section.language", "언어");
        add(p + "follow_game_language", "Minecraft 언어 따르기");
        add(p + "follow_game_language.tooltip", "현재 Minecraft 옵션에서 선택한 언어를 사용합니다.");
        add(p + "target_language", "번역 대상 언어");
        add(p + "target_language.custom", "사용자 지정/알 수 없음: %s");

        add(p + "section.features", "번역 기능");
        add(p + "feature.tooltip", "아이템 툴팁");
        add(p + "feature.jade", "Jade 툴팁");
        add(p + "feature.ftbquests", "FTB 퀘스트");
        add(p + "feature.advancements", "진행 상황");
        add(p + "feature.screenshot", "스크린샷 번역");

        add(p + "advanced_settings", "고급 설정");
        add(p + "model_cache_note", "공급자나 모델을 변경해도 기존 번역 캐시는 자동으로 무효화되거나 삭제되지 않습니다.");
        add(p + "clear_cache_confirm.title", "번역 캐시를 삭제하시겠습니까?");
        add(p + "clear_cache_confirm.message", "번역 관련 설정이 변경되었습니다. 캐시된 번역 %s개를 삭제하시겠습니까?");

        add(p + "section.translation", "번역");
        add(p + "include_icon", "번역에 아이템 아이콘 포함");
        add(p + "include_icon.tooltip", "더 나은 번역 맥락을 위해 지원되는 비전 모델에 아이템 아이콘을 전송합니다.");
        add(p + "pretranslate_containers", "컨테이너 아이템 사전 번역");
        add(p + "pretranslate_containers.tooltip", "마우스를 올리기 전에 화면에 보이는 컨테이너 아이템을 자동으로 번역합니다. API 요청이 더 많이 사용될 수 있습니다.");
        add(p + "custom_prompt", "사용자 지정 번역 프롬프트");
        add(p + "custom_prompt.builtin", "내장 프롬프트 사용");
        add(p + "custom_prompt.custom", "사용자 지정 프롬프트");
        add(p + "edit_custom_prompt", "사용자 지정 프롬프트 편집");
        add(p + "edit_screenshot_prompt", "스크린샷 프롬프트 편집");
        add(p + "reset_to_builtin", "내장 프롬프트로 재설정");

        add(p + "section.interface", "인터페이스");
        add(p + "translating_animation", "번역 중 애니메이션 표시");
        add(p + "translating_animation.tooltip", "번역 결과를 기다리는 동안 화면에 작은 애니메이션을 표시합니다.");

        add(p + "section.network", "네트워크");
        add(p + "timeout", "요청 제한 시간");
        add(p + "timeout.seconds", "%s 초");
        add(p + "timeout.custom", "사용자 지정: %s 초");
        add(p + "rpm", "분당 요청 수");
        add(p + "rpm.tooltip", "60초 동안 보낼 수 있는 AI 번역 요청 수를 제한합니다. 공급자가 HTTP 429를 반환하면 이 값을 낮추세요.");
        add(p + "rpm.custom", "사용자 지정: %s");

        add(p + "section.cache", "캐시");
        add(p + "clear_cache", "번역 캐시 삭제 (%s개)");

        add(p + "prompt_edit.title", "사용자 지정 프롬프트 편집");
        add(p + "prompt_edit.screenshot_title", "스크린샷 프롬프트 편집");
        add(p + "prompt_edit.hint", "비워두면 내장 프롬프트를 사용합니다");
    }
}
