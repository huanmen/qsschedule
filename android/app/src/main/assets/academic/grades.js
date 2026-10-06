// Runs only inside the school's trusted WebView. Never prints personal content.
(function (alreadySubmitted) {
  const norm = s => (s || '').replace(/\s+/g, '').trim();
  const docs = [];
  function visit(doc) {
    if (!doc || docs.includes(doc) || docs.length >= 32) return;
    docs.push(doc);
    for (const frame of doc.querySelectorAll('iframe,frame')) {
      try { visit(frame.contentDocument); } catch (_) {}
    }
  }
  visit(document);
  const gradePath = '/academic/manager/score/studentOwnScore.do';
  const gradeDoc = docs.find(d => {
    const u = new URL(d.URL);
    return u.protocol === 'https:' && u.hostname === 'jw.qztc.edu.cn' && u.pathname === gradePath && d.querySelector('select[name="year"]');
  });
  if (!gradeDoc) {
    for (const doc of docs) {
      if (new URL(doc.URL).hostname !== 'jw.qztc.edu.cn') continue;
      const entry = [...doc.querySelectorAll('a')].find(e => norm(e.textContent) === '个人成绩查询');
      if (entry) {
        const target = new URL(entry.href, doc.baseURI);
        if (target.protocol !== 'https:' || target.hostname !== 'jw.qztc.edu.cn' || target.pathname !== '/academic/accessModule.do') return {kind:'schema'};
        if (!doc.defaultView.__qnuGradesOpened) { doc.defaultView.__qnuGradesOpened = true; entry.click(); }
        return {kind:'opening'};
      }
    }
    return {kind:'waiting'};
  }
  if (gradeDoc.readyState !== 'complete') return {kind:'waiting'};
  if (gradeDoc.defaultView.__qnuGradesPending) {
    if (gradeDoc.defaultView.__qnuGradesPendingDocument === gradeDoc) return {kind:'waiting'};
    gradeDoc.defaultView.__qnuGradesPending = false;
    gradeDoc.defaultView.__qnuGradesPendingDocument = null;
  }
  const form = [...gradeDoc.forms].find(f => f.querySelector('select[name="year"]') && f.querySelector('select[name="term"]'));
  if (!form) return {kind:'schema'};
  const action = new URL(form.action, gradeDoc.baseURI);
  if (action.protocol !== 'https:' || action.hostname !== 'jw.qztc.edu.cn' || action.pathname !== gradePath || form.method.toLowerCase() !== 'post') return {kind:'schema'};
  if (!alreadySubmitted) {
    for (const name of ['year', 'term', 'prop', 'groupName']) {
      const select = form.elements.namedItem(name);
      if (!select || !select.options) return {kind:'schema'};
      const all = [...select.options].find(o => norm(o.textContent) === '全部');
      if (!all) return {kind:'schema'};
      select.value = all.value;
    }
    for (const name of ['failedStatus', 'passedStatus', 'maxStatus']) {
      const input = form.elements.namedItem(name);
      if (!input || input.type !== 'checkbox') return {kind:'schema'};
      input.checked = false;
    }
    const submit = form.querySelector('input[type="submit"],button[type="submit"]');
    if (!submit || norm(submit.value || submit.textContent) !== '查询') return {kind:'schema'};
    gradeDoc.defaultView.__qnuGradesPending = true;
    gradeDoc.defaultView.__qnuGradesPendingDocument = gradeDoc;
    submit.click();
    return {kind:'querying'};
  }
  // Never accept a filtered response as complete history.
  for (const name of ['year', 'term', 'prop', 'groupName']) {
    const select = form.elements.namedItem(name);
    if (!select || norm(select.selectedOptions[0]?.textContent) !== '全部') return {kind:'incomplete'};
  }
  if (['failedStatus', 'passedStatus', 'maxStatus'].some(n => form.elements.namedItem(n)?.checked)) return {kind:'incomplete'};
  const table = [...gradeDoc.querySelectorAll('table')].find(t => [...t.rows].some(r => {
    if (r.closest('table') !== t) return false;
    const labels = [...r.cells].map(c => norm(c.textContent));
    return labels.includes('学年') && labels.includes('学期') && labels.includes('课程名') && labels.includes('总评');
  }));
  if (!table) return {kind:'schema'};
  const rows = [...table.rows].filter(r => r.closest('table') === table);
  const headerIndex = rows.findIndex(r => [...r.cells].some(c => norm(c.textContent) === '课程名'));
  const width = rows[headerIndex].cells.length;
  const dataRows = rows.slice(headerIndex + 1).filter(r => r.cells.length === width && ![...r.cells].some(c => c.colSpan > 1));
  const pager = [...gradeDoc.querySelectorAll('a,button,input[type="button"]')].find(e => /^(下一页|下页|Next|›|»)$/.test(norm(e.textContent || e.value)) && !e.disabled && e.getAttribute('aria-disabled') !== 'true' && (e.hasAttribute('href') || e.hasAttribute('onclick')));
  // Confirmed school all-history endpoint is unpaginated. Fail if it becomes partial.
  if (pager) return {kind:'incomplete'};
  const summary = (gradeDoc.body.textContent || '').match(/共\s*(\d+)\s*条/);
  if (summary && Number(summary[1]) !== dataRows.length) return {kind:'incomplete'};
  if (table.outerHTML.length > 2000000) return {kind:'incomplete'};
  return {kind:'grades', count:dataRows.length, html:table.outerHTML};
})
