package cn.qztc.timetable.feature.timetable

/** Compact pinyin for campus names. Matching is always on, not a separate search mode. */
internal object CampusPinyin {
    private val syllables: Map<Char, String> = TABLE.split('|').associate { token ->
        token.first() to token.substring(1)
    }

    fun full(text: String): String = buildString {
        for (character in text) {
            val syllable = syllables[character]
            if (syllable != null) append(syllable)
            else if (character.isLetterOrDigit()) append(character.lowercaseChar())
        }
    }

    fun initials(text: String): String = buildString {
        for (character in text) {
            val syllable = syllables[character]
            if (!syllable.isNullOrEmpty()) append(syllable.first())
            else if (character.isLetter()) append(character.lowercaseChar())
        }
    }

    fun matches(query: String, text: String): Boolean {
        val needle = compact(query)
        if (needle.isEmpty()) return false
        return full(text).contains(needle) ||
            initials(text).contains(needle) ||
            compact(text).contains(needle)
    }

    fun compact(value: String): String =
        value.lowercase().filter { character -> character in 'a'..'z' || character.isDigit() }
}

private const val TABLE = "与yu|业ye|东dong|中zhong|主zhu|乐le|书shu|二er|仁ren|伟wei|体ti|侧ce|俊jun|信xin|停ting|光guang|公gong|创chuang|利li|刻ke|办ban|北bei|区qu|南nan|厅ting|口kou|史shi|号hao|合he|商shang|园yuan|国guo|图tu|场chang|堂tang|外wai|大da|子zi|学xue|守shou|宝bao|实shi|室shi|宿su|寓yu|山shan|州zhou|工gong|巷xiang|师shi|广guang|应ying|德de|心xin|息xi|慈ci|技ji|操cao|政zheng|教jiao|数shu|文wen|新xin|昌chang|明ming|是shi|服fu|朝chao|术shu|机ji|栋dong|校xiao|案an|档dang|桥qiao|楼lou|正zheng|水shui|求qiu|泉quan|浅qian|海hai|湾wan|爱ai|物wu|王wang|理li|生sheng|用yong|知zhi|研yan|礼li|祖zu|秀xiu|科ke|程cheng|究jiu|第di|算suan|约yue|纺fang|织zhi|综zong|美mei|育yu|舍she|舞wu|航hang|艺yi|茂mao|范fan|荣rong|行xing|街jie|装zhuang|西xi|计ji|设she|语yu|路lu|蹈dao|车che|门men|阳yang|陈chen|院yuan|雕diao|雨yu|音yin|风feng|食shi|餐can|馆guan|验yan|黄huang|龙long"
