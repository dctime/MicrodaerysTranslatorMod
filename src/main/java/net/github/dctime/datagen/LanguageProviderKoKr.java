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

        String p = MicrodaerysTranslatorClient.MODID + ".config.";
        add(p + "title", "Microdaery's Translator");

        add(p + "section.service", "번역 서비스");
        add(p + "service", "서비스");
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
        add(p + "test_connection.note", "연결 및 인증만 확인합니다 -- 번역 요청이 반드시 성공한다는 보장은 아닙니다");

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
