package cn.qztc.timetable.qnu

/** Capture only course detail tables from the entry page and accessible frames. */
internal const val READ_COURSE_PROFILES_SCRIPT = """
(() => {
  const docs = [];
  function visit(doc) {
    if (!doc || docs.includes(doc) || docs.length >= 32) return;
    docs.push(doc);
    for (const frame of doc.querySelectorAll('iframe,frame')) {
      try { visit(frame.contentDocument); } catch (_) {}
    }
  }
  visit(document);
  const result = document.implementation.createHTMLDocument('course-profiles');
  for (const doc of docs) for (const table of doc.querySelectorAll('table')) {
    const rows = Array.from(table.querySelectorAll('tr')).filter(row => row.closest('table') === table);
    const recognized = rows.some(row => {
      const headers = Array.from(row.children).map(c => c.textContent.replace(/[\s\u00a0]+/g,''));
      return headers.some(h => /课程名|课程名称/.test(h)) &&
        headers.some(h => /学分|选课属性|选课类别|课程属性|课程性质|课程类型|课程类别|课程分类|考核方式|考核类型|考核性质|考核形式|考试方式|考试性质|考试形式/.test(h));
    });
    if (!recognized) continue;
    const copy = table.cloneNode(true);
    copy.querySelectorAll('script,style,input,select,button').forEach(el => el.remove());
    result.body.append(copy);
  }
  const html = result.documentElement.outerHTML;
  return html.length > 2000000 ? '__QZTC_TOO_LARGE__' : html;
})()
"""
