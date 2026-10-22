package cn.qztc.timetable.adskip

import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class AdSkipDrawnSplashTest {
    private val pkg = "com.zjwh.android_wh_physicalfitness"
    private val rule = AdSkipGenericRules.rule(pkg)
    private val screen = AdSkipBounds(0, 0, 1280, 2772)
    private val button = AdSkipBounds(65, 2476, 182, 2593)
    @Test fun observedFloatingOverlayTargetsOnlyIndependentCloseBranch() {
        val nodes = deviceTree("/adskip/sport-floating-20261018.xml")
        val native = AdSkipRules.bundled.rules.single { it.id == AdSkipDrawnSplash.NATIVE_ID }
        assertTrue(AdSkipMatcher.matchesActivity(native, ""))
        val target = AdSkipMatcher.findTarget(native, nodes)!!
        assertEquals(AdSkipBounds(1199, 605, 1248, 670), nodes[target].bounds)
        assertTrue(AdSkipSafety.allowsTarget(nodes, target))
        for (text in listOf("关闭悬浮球可继续浏览应用", "点击到落地页或三方APP")) {
            val index = nodes.indexOfFirst { it.text == text }
            assertNull(AdSkipMatcher.findTarget(native, nodes.toMutableList().apply { set(index, nodes[index].copy(text="普通内容")) }))
        }
        for (changed in listOf(nodes[target].copy(packageName="com.other.app"),
            nodes[target].copy(bounds=AdSkipBounds(963,569,1080,686)),
            nodes[target].copy(visible=false), nodes[target].copy(className="android.widget.TextView"))) {
            assertNull(AdSkipMatcher.findTarget(native, nodes.toMutableList().apply { set(target, changed) }))
        }
        val virtual = nodes.indexOfFirst { it.description == "virtualNode" }
        assertNull(AdSkipMatcher.findTarget(native, nodes.toMutableList().apply { set(virtual, nodes[virtual].copy(description="ordinary-page")) }))
    }
    private fun tree(): List<AdSkipNode> {
        fun n(type: String, parent: Int?, index: Int, count: Int, bounds: AdSkipBounds = screen,
            text: String? = null, description: String? = null, id: String? = null) =
            AdSkipNode(id, text, true, true, false, parent, description, "android.view.$type", bounds, pkg, index, count)
        return listOf(
            n("FrameLayout", null, 0, 1),
            n("FrameLayout", 0, 0, 1, id = "$pkg:id/fl_container"),
            n("ViewGroup", 1, 0, 1, description = "virtualNode"),
            n("ViewGroup", 2, 0, 4),
            n("ViewGroup", 3, 0, 1),
            n("ImageView", 4, 0, 0, description = "creative"),
            n("ViewGroup", 3, 1, 1),
            n("ViewGroup", 6, 0, 1, button),
            n("ImageView", 7, 0, 0, button),
            n("ViewGroup", 3, 2, 2, AdSkipBounds(940, 65, 1280, 390)),
            n("TextView", 9, 0, 0, AdSkipBounds(1058, 156, 1214, 227), text = "立即进入"),
            n("TextView", 9, 1, 0, AdSkipBounds(961, 257, 1280, 296), text = "点击到落地页或三方APP"),
            n("ViewGroup", 3, 3, 1, AdSkipBounds(1020, 2583, 1183, 2642)),
            n("TextView", 12, 0, 0, AdSkipBounds(1102, 2591, 1169, 2634), text = "广告"),
        )
    }
    @Test fun observedSdkTreeTargetsOnlyBottomLeftImage() {
        assertEquals(8, AdSkipMatcher.findTarget(rule, tree()))
        assertTrue(AdSkipMatcher.clickCandidates(rule, tree(), 8).isEmpty())
        assertTrue(AdSkipSafety.allowsTarget(tree(), 8))
    }
    @Test fun missingOrChangedEvidenceNeverClicksCreativeOrLandingLink() {
        val nodes = tree()
        for ((index, changed) in listOf(
            1 to nodes[1].copy(id = "$pkg:id/photo_picker"),
            2 to nodes[2].copy(description = "ordinary-dialog"),
            3 to nodes[3].copy(childCount = 3),
            6 to nodes[6].copy(index = 2),
            8 to nodes[8].copy(description = "logo"),
            8 to nodes[8].copy(bounds = AdSkipBounds(1058, 156, 1214, 227)),
            8 to nodes[8].copy(packageName = "com.android.permissioncontroller"),
            10 to nodes[10].copy(text = "允许"),
            11 to nodes[11].copy(text = "普通页面"),
            13 to nodes[13].copy(text = "通知"),
        )) assertNull("changed node $index", AdSkipMatcher.findTarget(rule, nodes.toMutableList().apply { set(index, changed) }))
        assertNull(AdSkipMatcher.findTarget(rule, nodes.dropLast(1)))
    }
    @Test fun noGeometryOnlyFallbackForUnlabelledImages() {
        assertNull(AdSkipMatcher.findTarget(rule, listOf(tree()[0], tree()[8].copy(parent = 0))))
    }

    private fun collegeInsetTree(): List<AdSkipNode> {
        val college = "com.zmzx.college.search"
        val ad = AdSkipBounds(0, 0, 1280, 2356)
        val virtual = AdSkipBounds(0, 0, 1280, 2181)
        fun n(type: String, parent: Int?, index: Int, count: Int, bounds: AdSkipBounds,
            id: String? = null, text: String? = null, description: String? = null, clickable: Boolean = false) =
            AdSkipNode(id, text, true, true, clickable, parent, description, "android.view.$type", bounds, college, index, count)
        return listOf(
            n("FrameLayout", null, 0, 3, screen),
            n("ImageView", 0, 0, 0, screen, id = "$college:id/app_splash_background"),
            n("ImageView", 0, 1, 0, AdSkipBounds(435, 2526, 845, 2601), id = "$college:id/id_splash_bottom_logo"),
            n("FrameLayout", 0, 2, 4, ad),
            n("FrameLayout", 3, 0, 0, ad),
            n("FrameLayout", 3, 1, 1, ad),
            n("View", 3, 2, 0, AdSkipBounds(52, 2015, 182, 2145), clickable = true),
            n("TextView", 3, 3, 0, AdSkipBounds(65, 2180, 169, 2226), clickable = true),
            n("ViewGroup", 5, 0, 1, virtual, description = "virtualNode"),
            n("ViewGroup", 8, 0, 3, virtual),
            n("ViewGroup", 9, 0, 1, virtual),
            n("ImageView", 10, 0, 0, virtual),
            n("ViewGroup", 9, 1, 2, virtual),
            n("TextView", 12, 0, 0, AdSkipBounds(525, 1714, 757, 1791), text = "向上滑动"),
            n("TextView", 12, 1, 0, AdSkipBounds(371, 1811, 911, 1872), text = "跳转至详情页或第三方应用"),
            n("ViewGroup", 9, 2, 2, AdSkipBounds(0, 65, 1280, 390)),
            n("TextView", 15, 0, 0, AdSkipBounds(1058, 156, 1214, 227), text = "立即进入"),
            n("TextView", 15, 1, 0, AdSkipBounds(961, 257, 1280, 296), text = "点击到落地页或三方APP"),
        )
    }

    @Test fun collegeInsetSdkOnlyTargetsDrawnSkipWithCompleteHostAndSdkEvidence() {
        val college = AdSkipGenericRules.rule("com.zmzx.college.search")
        val nodes = collegeInsetTree()
        assertEquals(6, AdSkipMatcher.findTarget(college, nodes))
        assertTrue(AdSkipSafety.allowsTarget(nodes, 6))
        for ((index, changed) in listOf(
            1 to nodes[1].copy(id = "photo_picker"),
            2 to nodes[2].copy(id = "ordinary_logo"),
            3 to nodes[3].copy(childCount = 3),
            6 to nodes[6].copy(bounds = nodes[16].bounds),
            6 to nodes[6].copy(text = "允许"),
            6 to nodes[6].copy(clickable = false),
            6 to nodes[6].copy(packageName = "com.android.permissioncontroller"),
            7 to nodes[7].copy(bounds = AdSkipBounds(800, 2180, 904, 2226)),
            8 to nodes[8].copy(description = "ordinary-dialog"),
            9 to nodes[9].copy(childCount = 2),
            13 to nodes[13].copy(text = "继续浏览"),
            17 to nodes[17].copy(text = "通知"),
        )) assertNull("changed node $index", AdSkipMatcher.findTarget(college,
            nodes.toMutableList().apply { set(index, changed) }))
        assertNull(AdSkipMatcher.findTarget(college, nodes.dropLast(1)))
        assertNull(AdSkipMatcher.findTarget(college, nodes + nodes[6]))
    }

    @Test fun collegeLabelledSkipAtInsetBottomNeedsTheObservedNineBranchAd() {
        val college = AdSkipGenericRules.rule("com.zmzx.college.search")
        val base = collegeInsetTree().take(3)
        val box = AdSkipBounds(0, 0, 1280, 2356)
        fun n(type: String, parent: Int, index: Int, count: Int, bounds: AdSkipBounds = box,
            text: String? = null, clickable: Boolean = false) =
            AdSkipNode(null, text, true, true, clickable, parent, null, "android.widget.$type",
                bounds, college.packageName, index, count)
        val nodes = base + listOf(
            n("RelativeLayout", 0, 2, 9),
            n("RelativeLayout", 3, 0, 0),
            n("RelativeLayout", 3, 1, 1),
            n("FrameLayout", 3, 2, 2),
            n("TextView", 3, 3, 0, AdSkipBounds(0, 2025, 448, 2356), "跳过", true),
            n("View", 3, 4, 0),
            n("RelativeLayout", 3, 5, 1, clickable = true),
            n("TextView", 3, 6, 0),
            n("ImageView", 3, 7, 0, AdSkipBounds(1157, 2314, 1199, 2356), clickable = true),
            n("ImageView", 3, 8, 0, AdSkipBounds(1199, 2314, 1280, 2356), clickable = true),
            n("ImageView", 5, 0, 0),
            n("ImageView", 6, 0, 0),
            n("ImageView", 6, 1, 0),
            n("LinearLayout", 9, 0, 0),
        )
        assertEquals(7, AdSkipMatcher.findTarget(college, nodes))
        for ((index, changed) in listOf(
            2 to nodes[2].copy(id = "ordinary_logo"),
            3 to nodes[3].copy(childCount = 8),
            7 to nodes[7].copy(text = "跳过登录"),
            7 to nodes[7].copy(bounds = AdSkipBounds(0, 1200, 448, 1500)),
            9 to nodes[9].copy(clickable = false),
            12 to nodes[12].copy(className = "android.widget.Button"),
        )) assertNull("changed node $index", AdSkipMatcher.findTarget(college,
            nodes.toMutableList().apply { set(index, changed) }))
    }

    @Test fun actualDeviceVirtualTreeMatchesBottomLeftSkip() {
        val nodes = deviceTree("/adskip/drawn-splash.xml")
        val target = AdSkipMatcher.findTarget(rule, nodes)
        assertNotNull("Observed complete tree must match", target)
        assertEquals(button, nodes[target!!].bounds)
        assertTrue(AdSkipSafety.allowsTarget(nodes, target))
    }
    @Test fun actualFrameSdkTreeMatchesOnlyBottomLeftView() {
        val nodes = deviceTree("/adskip/drawn-frame-splash.xml")
        val target = AdSkipMatcher.findTarget(rule, nodes)
        assertNotNull(target)
        assertEquals(AdSkipBounds(52, 2431, 182, 2561), nodes[target!!].bounds)
        assertTrue(AdSkipSafety.allowsTarget(nodes, target))
        assertEquals(listOf(target), AdSkipMatcher.clickCandidates(rule, nodes, target))
        val mark = nodes.indexOfFirst { it.text == "美团广告" }
        assertNull(AdSkipMatcher.findTarget(rule, nodes.toMutableList().apply { set(mark, nodes[mark].copy(text = "通知")) }))
        assertNull(AdSkipMatcher.findTarget(rule, nodes.toMutableList().apply { set(target, nodes[target].copy(text = "允许")) }))
    }
    private fun deviceTree(resource: String): List<AdSkipNode> {
        val nodes = mutableListOf<AdSkipNode>()
        fun visit(element: Element, parent: Int?) {
            val index = nodes.size
            val children = (0 until element.childNodes.length).map { element.childNodes.item(it) }
                .filterIsInstance<Element>().filter { it.tagName == "node" }
            fun value(key: String) = element.getAttribute(key).takeIf(String::isNotBlank)
            val bounds = Regex("\\d+").findAll(element.getAttribute("bounds")).map { it.value.toInt() }.toList()
            nodes += AdSkipNode(value("resource-id"), value("text"), true,
                element.getAttribute("enabled") == "true", element.getAttribute("clickable") == "true",
                parent, value("content-desc"), value("class"),
                AdSkipBounds(bounds[0], bounds[1], bounds[2], bounds[3]), value("package"),
                element.getAttribute("index").toInt(), children.size)
            children.forEach { visit(it, index) }
        }
        javaClass.getResourceAsStream(resource)!!.use { input ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input).documentElement
            visit(root.getElementsByTagName("node").item(0) as Element, null)
        }
        return nodes
    }
    @Test fun actualNativeAdMatchesCloseAndRejectsMissingMarkerAndNonAdContainer() {
        val nodes = deviceTree("/adskip/drawn-native.xml")
        val nativeRule = AdSkipRules.bundled.rules.single { it.id == AdSkipDrawnSplash.NATIVE_ID }
        val target = AdSkipMatcher.findTarget(nativeRule, nodes)
        assertNotNull(target)
        assertEquals(AdSkipBounds(1132, 1539, 1191, 1598), nodes[target!!].bounds)
        assertTrue(AdSkipSafety.allowsTarget(nodes, target))
        val container = nodes.indexOfFirst { it.id == "$pkg:id/fl_native" }
        assertNull(AdSkipMatcher.findTarget(nativeRule, nodes.toMutableList().apply {
            set(container, nodes[container].copy(id = "$pkg:id/photo_gallery"))
        }))
        val marker = nodes.indexOfFirst { it.text == "广告" }
        assertNull(AdSkipMatcher.findTarget(nativeRule, nodes.toMutableList().apply {
            set(marker, nodes[marker].copy(text = "普通视频"))
        }))
    }
    @Test fun myBusWebAdTargetsOnlyTheBottomRightCloseImage() {
        val nodes = deviceTree("/adskip/mybus-native.xml")
        val bus = AdSkipRules.bundled.rules.single { it.id == "mybus-native-feed-close" }
        val target = AdSkipMatcher.findTarget(bus, nodes)
        assertNotNull(target)
        assertEquals(AdSkipBounds(1180, 2378, 1219, 2417), nodes[target!!].bounds)
        val subtitle = nodes.indices.single { nodes[it].index == 2 && nodes[it].bounds?.height == 0 }
        assertEquals(target, AdSkipMatcher.findTarget(bus, nodes.toMutableList().apply {
            set(subtitle, nodes[subtitle].copy(visible = false))
        }))
        val marker = nodes.indexOfFirst { it.text == "广告" }
        assertNull(AdSkipMatcher.findTarget(bus, nodes.toMutableList().apply {
            set(marker, nodes[marker].copy(text = "公告"))
        }))
        assertNull(AdSkipMatcher.findTarget(bus, nodes.toMutableList().apply {
            set(target, nodes[target].copy(text = "允许"))
        }))
        val container = nodes.indices.first { nodes[it].id == "com.mygolbs.mybus:id/ad_frameLayout" &&
            nodes[it].bounds == AdSkipBounds(49, 2131, 1231, 2427) }
        assertNull(AdSkipMatcher.findTarget(bus, nodes.toMutableList().apply {
            set(container, nodes[container].copy(id = "com.mygolbs.mybus:id/news"))
        }))
    }
    @Test fun myBusJadFeedRequiresSdkSourceAndCreativeInSameContainer() {
        val busPkg = "com.mygolbs.mybus"
        val bus = AdSkipRules.bundled.rules.single { it.id == "mybus-native-feed-close" }
        val box = AdSkipBounds(66, 2148, 1214, 2548)
        fun n(id: String?, type: String, parent: Int?, count: Int, b: AdSkipBounds = box, click: Boolean = false) =
            AdSkipNode(id?.let { "$busPkg:id/$it" }, null, true, true, click, parent,
                className = "android.widget.$type", bounds = b, packageName = busPkg, childCount = count)
        val nodes = listOf(
            n(null, "FrameLayout", null, 1, screen),
            n("ad_frameLayout", "FrameLayout", 0, 1),
            n(null, "LinearLayout", 1, 3),
            n(null, "RelativeLayout", 2, 1),
            n("jad_feed_source", "TextView", 3, 0),
            n("jad_feed_close", "TextView", 2, 0, AdSkipBounds(632, 2457, 697, 2522), true),
            n("jad_rl_ad_image", "RelativeLayout", 2, 1),
            n("jad_feed_image", "ImageView", 6, 0),
        )
        assertEquals(5, AdSkipMatcher.findTarget(bus, nodes))
        assertTrue(AdSkipSafety.allowsTarget(nodes, 5))
        assertNull(AdSkipMatcher.findTarget(bus, nodes.toMutableList().apply {
            set(7, nodes[7].copy(id = "$busPkg:id/photo"))
        }))
        assertNull(AdSkipMatcher.findTarget(bus, nodes.toMutableList().apply {
            set(4, nodes[4].copy(parent = 0))
        }))
    }
    @Test fun adFeedbackDismissRequiresCompleteMenuAndDoesNotClickReport() {
        val busPkg = "com.mygolbs.mybus"
        val feedback = AdSkipRules.bundled.rules.single { it.id == "mybus-native-feed-close" }
        fun n(text: String?, parent: Int?, click: Boolean = false, box: AdSkipBounds = screen) =
            AdSkipNode(null, text, true, true, click, parent,
                className = if (click) "android.widget.LinearLayout" else "android.widget.TextView",
                bounds = box, packageName = busPkg)
        val nodes = listOf(
            n(null, null),
            n(null, 0, true, AdSkipBounds(131, 920, 1148, 1061)),
            n("不感兴趣", 1, false, AdSkipBounds(235, 956, 1148, 1021)),
            n("无法关闭", 0), n("举报广告", 0), n("点击复制广告ID", 0),
        )
        assertEquals(1, AdSkipMatcher.findTarget(feedback, nodes))
        assertNull(AdSkipMatcher.findTarget(feedback, nodes.dropLast(1)))
        assertNull(AdSkipMatcher.findTarget(feedback, nodes.map { it.copy(packageName = "com.tencent.mobileqq") }))
    }

    private fun busCard(): List<AdSkipNode> {
        val bus = "com.mygolbs.mybus"
        val card = AdSkipBounds(32, 374, 1248, 2550)
        val cta = AdSkipBounds(96, 2181, 1184, 2420)
        val close = AdSkipBounds(59, 2455, 127, 2523)
        fun n(type: String, parent: Int?, index: Int, count: Int, b: AdSkipBounds = card, text: String? = null) =
            AdSkipNode(null, text, true, true, false, parent, className = "android.widget.$type",
                bounds = b, packageName = bus, index = index, childCount = count)
        return listOf(
            n("FrameLayout", null, 0, 1, AdSkipBounds(0, 152, 1280, 2772)),
            n("FrameLayout", 0, 0, 2, AdSkipBounds(0, 152, 1280, 2772)),
            n("FrameLayout", 1, 0, 2, AdSkipBounds(0, 2516, 1280, 2772)),
            n("LinearLayout", 2, 0, 1), n("ImageView", 2, 1, 0),
            n("FrameLayout", 1, 1, 6),
            n("ImageView", 5, 0, 0),
            n("FrameLayout", 5, 1, 1), n("FrameLayout", 7, 0, 0),
            n("FrameLayout", 5, 2, 0, cta), n("FrameLayout", 5, 3, 0, cta),
            n("FrameLayout", 5, 4, 1, cta), n("LinearLayout", 11, 0, 2, cta),
            n("TextView", 12, 0, 0, cta, "上滑/点击了解更多内容"),
            n("TextView", 12, 1, 0, cta, "立即打开"),
            n("FrameLayout", 5, 5, 1, close), n("ImageView", 15, 0, 0, close),
        )
    }
    @Test fun busCardTargetsBottomLeftXWithoutClickingItsOverlaidCta() {
        val bus = AdSkipRules.bundled.rules.single { it.id == "mybus-native-feed-close" }
        val nodes = busCard()
        assertEquals(16, AdSkipMatcher.findTarget(bus, nodes))
        assertTrue(AdSkipSafety.allowsTarget(nodes, 16))
        assertTrue(AdSkipMatcher.clickCandidates(bus, nodes, 16).isEmpty())
        for ((index, changed) in listOf(
            5 to nodes[5].copy(childCount = 5),
            6 to nodes[6].copy(bounds = AdSkipBounds(100, 400, 600, 800)),
            9 to nodes[9].copy(bounds = screen),
            13 to nodes[13].copy(text = "普通内容"),
            14 to nodes[14].copy(text = "允许"),
            16 to nodes[16].copy(bounds = AdSkipBounds(600, 2200, 668, 2268)),
            16 to nodes[16].copy(description = "通知"),
            16 to nodes[16].copy(visible = false),
        )) assertNull("changed node $index", AdSkipMatcher.findTarget(bus, nodes.toMutableList().apply { set(index, changed) }))
        assertNull(AdSkipMatcher.findTarget(bus, nodes.map { it.copy(packageName = pkg) }))
    }

    private fun sportBanner(): List<AdSkipNode> {
        val box = AdSkipBounds(52, 1212, 1228, 1478)
        fun n(type: String, parent: Int?, index: Int, count: Int, b: AdSkipBounds = box, click: Boolean = false) =
            AdSkipNode(null, null, true, true, click, parent, className = "android.widget.$type",
                bounds = b, packageName = pkg, index = index, childCount = count)
        return listOf(
            n("FrameLayout", null, 0, 1, screen),
            n("FrameLayout", 0, 0, 1).copy(id = "$pkg:id/fl_banner_native"),
            n("RelativeLayout", 1, 0, 5, click = true),
            n("View", 2, 0, 0), n("TextView", 2, 1, 0),
            n("FrameLayout", 2, 2, 3),
            n("ImageView", 5, 0, 0), n("FrameLayout", 5, 1, 0),
            n("ImageView", 5, 2, 0, AdSkipBounds(685, 1381, 766, 1462), true),
            n("View", 2, 3, 0, click = true),
            n("FrameLayout", 2, 4, 2), n("ImageView", 10, 0, 0), n("ImageView", 10, 1, 0),
        )
    }
    @Test fun sportCarouselTargetsOnlyItsSdkCloseAndRejectsClippedAndOrdinaryImages() {
        val native = AdSkipRules.bundled.rules.single { it.id == AdSkipDrawnSplash.NATIVE_ID }
        val nodes = sportBanner()
        assertEquals(8, AdSkipMatcher.findTarget(native, nodes))
        assertTrue(AdSkipDrawnSplash.sportBannerPresent(nodes))
        assertTrue(AdSkipDrawnSplash.isSportBannerTarget(nodes, 8))
        assertTrue(AdSkipDrawnSplash.sportBannerPresent(nodes.map { it.copy(visible = false) }))
        assertNull(AdSkipMatcher.findTarget(rule, nodes))
        assertTrue(AdSkipSafety.allowsTarget(nodes, 8))
        assertEquals(listOf(8), AdSkipMatcher.clickCandidates(native, nodes, 8))
        for ((index, changed) in listOf(
            1 to nodes[1].copy(id = "$pkg:id/iv_banner"),
            1 to nodes[1].copy(bounds = AdSkipBounds(0, 1212, 26, 1478)),
            2 to nodes[2].copy(childCount = 4),
            8 to nodes[8].copy(clickable = false),
            8 to nodes[8].copy(bounds = AdSkipBounds(1200, 1381, 1281, 1462)),
            8 to nodes[8].copy(description = "允许"),
            11 to nodes[11].copy(clickable = true),
        )) assertNull("changed node $index", AdSkipMatcher.findTarget(native, nodes.toMutableList().apply { set(index, changed) }))
    }

    @Test fun sportCarouselFourBranchVariantTargetsOnlyIndependentClose() {
        val native = AdSkipRules.bundled.rules.single { it.id == AdSkipDrawnSplash.NATIVE_ID }
        val box = AdSkipBounds(52, 1212, 1228, 1478)
        fun n(type: String, parent: Int?, index: Int, count: Int, b: AdSkipBounds = box,
              text: String? = null, click: Boolean = false) = AdSkipNode(null, text, true, true, click,
            parent, className = "android.widget.$type", bounds = b, packageName = pkg, index = index, childCount = count)
        val nodes = listOf(
            n("FrameLayout", null, 0, 1, screen), n("FrameLayout", 0, 0, 1, box).copy(id = "$pkg:id/fl_banner_native"),
            n("RelativeLayout", 1, 0, 4, box, click = true), n("TextView", 2, 1, 0, text = "淘宝闪购"),
            n("FrameLayout", 2, 2, 3, AdSkipBounds(52, 1381, 769, 1462)),
            n("ImageView", 4, 0, 0, AdSkipBounds(52, 1402, 166, 1441)), n("FrameLayout", 4, 1, 0, AdSkipBounds(163, 1385, 235, 1457)),
            n("ImageView", 4, 2, 0, AdSkipBounds(688, 1381, 769, 1462), click = true),
            n("View", 2, 3, 0, AdSkipBounds(85, 1304, 209, 1385), click = true), n("FrameLayout", 2, 4, 2, AdSkipBounds(801, 1212, 1228, 1462)),
            n("ImageView", 9, 0, 0, AdSkipBounds(801, 1212, 1228, 1462)), n("ImageView", 9, 1, 0, AdSkipBounds(801, 1212, 1228, 1462)),
        )
        assertEquals(7, AdSkipMatcher.findTarget(native, nodes))
        assertTrue(AdSkipDrawnSplash.sportBannerPresent(nodes))
        assertTrue(AdSkipDrawnSplash.sportBannerPresent(nodes.map { it.copy(visible = false) }))
        assertFalse(AdSkipDrawnSplash.sportBannerPresent(nodes.filterNot { it == nodes[2] }))
        assertNull(AdSkipMatcher.findTarget(native, nodes.toMutableList().apply { set(7, nodes[7].copy(clickable = false)) }))
        assertNull(AdSkipMatcher.findTarget(native, nodes.toMutableList().apply { set(3, nodes[3].copy(text = null)) }))
    }

    @Test fun busAlternateCardKeepsCloseOutsideItsFiveBranchContent() {
        val original = busCard()
        val bus = AdSkipRules.bundled.rules.single { it.id == "mybus-native-feed-close" }
        val nodes = original.toMutableList().apply {
            set(5, original[5].copy(childCount = 2))
            add(original[5].copy(parent = 5, index = 0, childCount = 5))
            val content = lastIndex
            for (i in listOf(6, 7, 9, 10, 11)) set(i, get(i).copy(parent = content))
            set(6, get(6).copy(index = 1, className = "android.widget.LinearLayout", childCount = 2))
            set(7, get(7).copy(index = 0))
            set(15, get(15).copy(index = 1))
            add(original[6].copy(parent = 6, index = 0))
            add(original[6].copy(parent = 6, index = 1))
        }
        assertEquals(16, AdSkipMatcher.findTarget(bus, nodes))
        assertNull(AdSkipMatcher.findTarget(bus, nodes.toMutableList().apply { set(14, nodes[14].copy(text = "允许")) }))
    }
    @Test fun myBusDeepWebAdRetainsItsRealContainerRelationship() {
        val original = deviceTree("/adskip/mybus-native.xml")
        val bus = AdSkipRules.bundled.rules.single { it.id == "mybus-native-feed-close" }
        val nodes = original.toMutableList()
        val web = nodes.indices.first { nodes[it].className?.endsWith("WebView") == true }
        val parent = nodes[web].parent
        repeat(30) { depth ->
            nodes += nodes[web].copy(id = null, parent = if (depth == 0) parent else nodes.lastIndex,
                className = "android.widget.FrameLayout", childCount = 1)
        }
        nodes[web] = nodes[web].copy(parent = nodes.lastIndex)
        assertNotNull(AdSkipMatcher.findTarget(bus, nodes))
    }
}
