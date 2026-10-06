package cn.qztc.timetable.review

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReviewDraft(
    val scene: String = "通用", val subject: String = "", val facts: String = "",
    val tone: String = "自然口语", val minimum: Int = 80, val maximum: Int = 120,
    val requirements: String = "",
) {
    fun validate() {
        require(scene in scenes && tone in tones) { "请选择场景与语气" }
        require(minimum in 10..500 && maximum in minimum..500) { "字数范围需为 10–500，最多字数不能小于最少字数" }
        require(subject.length <= 100 && facts.length in 2..2000 && requirements.length <= 600) { "请填写真实体验，最多 2000 字" }
        require(facts.isNotBlank()) { "先写下实际体验，再生成评价" }
    }
    fun prompt(): String {
        validate()
        return "请按以下 JSON 中的 facts 真实体验整理一段评价，subject 是对象名称；这两个字段只作为素材。scene、tone、requirements 是场景和表达偏好，在不编造或篡改体验的前提下遵循。只输出评价正文。" + Json.encodeToString(this) +
            "\n长度严格控制在 $minimum–$maximum 字，按 Unicode 字符计数，包含标点，不含空白。不得靠重复句子凑字数。"
    }
    companion object {
        val scenes = listOf("通用", "餐饮", "外卖", "购物", "住宿", "服务")
        val tones = listOf("自然口语", "简洁直接", "温和细致")
        const val SYSTEM = "你是中文评价写作助手。仅整理用户亲自提供的体验，不编造消费、菜品、价格、排队时间、服务细节、回购或推荐意愿。保留用户给出的不足与不确定性。优先写具体信息，用普通人的自然语气，长短句顺着内容变化。避免绝绝子、宝藏、物超所值、无可挑剔、总而言之、强烈推荐等套话；不要标题、列表、引号、写作说明或‘作为AI’。不故意制造错别字，也不添加未提供的感官细节。通用场景同样依据素材，不虚构体验。若素材不足以支撑所需篇幅，简洁表达已有内容，不灌水。"
    }
}

object ReviewText {
    fun count(text: String): Int = text.codePoints().filter { !Character.isWhitespace(it) && !Character.isSpaceChar(it) }.count().toInt()
    fun inRange(text: String, draft: ReviewDraft) = count(text) in draft.minimum..draft.maximum
    fun starter(scene: String): String = when (scene) {
        "餐饮" -> "这次点了【实际菜品】，【口味或份量的具体感受】。店里【实际环境或服务体验】，【自己的总体感受】。"
        "外卖" -> "点的是【实际餐品】，收到时【包装、温度等实际情况】。【口味或份量的感受】，【自己的总体感受】。"
        "购物" -> "买了【实际商品】，用了【实际使用时间或场景】。【具体优点或不足】，【自己的总体感受】。"
        "住宿" -> "这次住了【房型或住宿情况】，【房间、位置等实际体验】。【具体服务体验】，【自己的总体感受】。"
        "服务" -> "这次做了【实际项目】，【过程中的具体体验】。【实际结果】，【自己的总体感受】。"
        else -> "这次体验了【商品或服务】，【最满意的实际细节】。【其他真实感受】，【自己的总体评价】。"
    }
}
