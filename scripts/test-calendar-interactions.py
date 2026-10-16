"""Calendar gesture regression with synthetic events; no user storage or network."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
    page = context.new_page()
    page.route("**/*", lambda route: route.continue_() if route.request.url.startswith("http://127.0.0.1:18515/") else route.abort())
    page.add_init_script("window.AcademicHost={postMessage:raw=>(window.qaMessages||=[]).push(JSON.parse(raw))};")
    page.goto("http://127.0.0.1:18515/web/academic-preview/index.html#calendar")
    page.wait_for_function("typeof window.nativeAcademicConfigure==='function'")
    page.evaluate("""() => nativeAcademicConfigure({colors:{'--base':'#f6f8f7','--surface':'#ffffff'},
        academicPluginInstalled:false,activities:Array.from({length:30},(_,i)=>({
        id:'native:fixture-'+i,activityId:'fixture-'+i,title:'合成安排 '+i,date:calendarToday(),
        time:i%2?'09:00':'10:30',timeRange:i%2?'09:00 — 10:00':null,content:'内容预览',location:'合成地点'}))})""")
    page.locator(".agenda-card").first.wait_for()
    cdp = context.new_cdp_session(page)

    def drag(x, y, dy, dx=0):
        cdp.send("Input.dispatchTouchEvent", {"type":"touchStart", "touchPoints":[{"x":x,"y":y}]})
        for step in range(1, 9):
            cdp.send("Input.dispatchTouchEvent", {"type":"touchMove", "touchPoints":[{"x":x+dx*step/8,"y":y+dy*step/8}]})
            page.wait_for_timeout(24)
        cdp.send("Input.dispatchTouchEvent", {"type":"touchEnd", "touchPoints":[]})
        page.wait_for_timeout(350)

    def collapsed():
        return page.locator("#calendar-panel").evaluate("e=>e.classList.contains('collapsed')")

    y = page.locator(".agenda-card-body").first.bounding_box()["y"]+22
    drag(180, y, -90)
    assert collapsed(), "Top-of-list upward drag must collapse"
    assert page.evaluate("scrollY") < 5
    y = page.locator(".agenda-card-body").first.bounding_box()["y"]+22
    drag(180, y, 90)
    assert not collapsed(), "Top-of-list downward drag must expand"
    page.evaluate("scrollTo(0,450)")
    page.wait_for_timeout(200)
    before = page.evaluate("scrollY")
    drag(180, 710, -130)
    assert not collapsed(), "Scrolling a long list must not collapse"
    assert page.evaluate("scrollY") > before, "Long list must still scroll"
    page.evaluate("calendarSetCollapsed(true);scrollTo(0,450)")
    page.wait_for_timeout(200)
    before = page.evaluate("scrollY")
    drag(180, 540, 110)
    assert collapsed(), "Downward scrolling away from top must not expand"
    assert page.evaluate("scrollY") < before
    # A gesture that reaches the top remains a scroll; the next gesture unfolds.
    page.evaluate("scrollTo(0,35)")
    page.wait_for_timeout(150)
    drag(180, 450, 150)
    assert collapsed()
    page.evaluate("scrollTo(0,0)")
    y = page.locator(".agenda-card-body").first.bounding_box()["y"]+22
    drag(180, y, 100)
    assert not collapsed()
    # Horizontal swipes and tiny movements must not fold the calendar.
    y = page.locator(".agenda-card-body").first.bounding_box()["y"]+22
    drag(180, y, -8, 75)
    assert not collapsed()
    edit = page.locator('[data-native-activity="fixture-0"]')
    edit.tap()
    assert page.evaluate("qaMessages.some(e=>e.action==='edit-activity'&&e.activityId==='fixture-0')")
    assert not edit.evaluate("e=>e===document.activeElement"), "Editor trigger must release focus"
    assert edit.evaluate("e=>getComputedStyle(e).backgroundColor") == 'rgba(0, 0, 0, 0)'
    for width in (320, 390, 480):
        page.set_viewport_size({"width":width,"height":844})
        page.evaluate("scrollTo(0,0)")
        assert page.evaluate("document.documentElement.scrollWidth<=innerWidth"), f"Overflow at {width}px"
        size = page.locator('.agenda-card-time').first.evaluate("e=>parseFloat(getComputedStyle(e).fontSize)")
        assert size >= 14
    page.set_viewport_size({"width":390,"height":844})
    page.screenshot(path=str(ROOT/'output/v150-calendar-gestures.png'), full_page=False)
    (ROOT/'output/v150-calendar-gestures.json').write_text(json.dumps({
        'passed':True,'checks':['top collapse','top expand','long-list scroll','reach-top then expand',
        'horizontal gesture ignored','editor focus released','touch hover cleared','320/390/480px layout']},ensure_ascii=False,indent=2),encoding='utf-8')
    print('Calendar interaction checks passed (8 scenarios, 3 viewport widths).')
    context.close()
    browser.close()
