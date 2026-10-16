'use strict';
let competitionObserver;
function recognitionLabel(c) {
  if (c.grade) return c.grade;
  const school=CompetitionCatalog.items.find(x=>x.year===competitionYear&&normalizeCompetitionName(x.name)===normalizeCompetitionName(c.name));
  return school?.grade || (school ? '学校目录未标等级' : '非学校认定');
}
function availableCompetitionGrades() {
  const rows=competitionScope==='school'?CompetitionCatalog.items.filter(x=>x.year===competitionYear):
    competitionScope==='national'?[...nationalCatalog,...CompetitionEditions]:[...CompetitionCatalog.items.filter(x=>x.year===competitionYear),...nationalCatalog,...CompetitionEditions];
  const labels=new Set(rows.filter(x=>competitionKind==='全部'||x.kind===competitionKind).map(recognitionLabel));
  return ['全部',...['A+','A','B','C','D','E','F','学校目录未标等级','非学校认定'].filter(x=>labels.has(x))];
}
function updateCompetitionGrades() {
  const select=$('#competition-grade');if(!select)return;
  const available=availableCompetitionGrades();
  if(!available.includes(competitionGrade))competitionGrade='全部';
  select.replaceChildren(...available.map(x=>new Option(x,x,false,x===competitionGrade)));
  const trigger=select.parentElement.querySelector('.inline-select-trigger');
  if(trigger){trigger.innerHTML=`<span>${esc(competitionGrade)}</span>${icon('chevron-down')}`;icons();}
}
function installInlineSelects(root) {
  if (!root) return;
  root.querySelectorAll('select:not([data-inline-ready])').forEach(select=>{
    select.dataset.inlineReady='true';
    const wrap=document.createElement('div');wrap.className='inline-select';
    select.replaceWith(wrap);wrap.append(select);select.classList.add('inline-select-native');select.tabIndex=-1;select.setAttribute('aria-hidden','true');
    const trigger=document.createElement('button');trigger.type='button';trigger.className='inline-select-trigger';
    trigger.setAttribute('role','combobox');trigger.setAttribute('aria-haspopup','listbox');trigger.setAttribute('aria-expanded','false');
    trigger.setAttribute('aria-label',select.closest('label')?.querySelector('.field-label')?.textContent||select.getAttribute('aria-label')||select.id);
    const menu=document.createElement('div');menu.className='inline-select-menu';menu.role='listbox';menu.hidden=true;
    menu.id=select.id+'-options';trigger.setAttribute('aria-controls',menu.id);
    wrap.append(trigger,menu);
    const update=()=>{trigger.innerHTML=`<span>${esc(select.selectedOptions[0]?.textContent||'请选择')}</span>${icon('chevron-down')}`;trigger.disabled=select.disabled;icons();};
    const close=()=>{menu.hidden=true;trigger.setAttribute('aria-expanded','false');};
    const open=()=>{
      document.querySelectorAll('.inline-select-menu').forEach(m=>{m.hidden=true;m.previousElementSibling.setAttribute('aria-expanded','false');});
      menu.replaceChildren();
      [...select.options].forEach(o=>{const b=document.createElement('button');b.type='button';b.role='option';b.textContent=o.textContent;b.disabled=o.disabled;
        b.setAttribute('aria-selected',String(o.selected));b.onclick=()=>{select.value=o.value;close();update();select.dispatchEvent(new Event('change',{bubbles:true}));if(trigger.isConnected)trigger.focus();};menu.append(b);});
      menu.hidden=false;trigger.setAttribute('aria-expanded','true');
    };
    trigger.onclick=e=>{e.preventDefault();menu.hidden?open():close();};
    wrap.onkeydown=e=>{if(e.key==='Escape'){close();trigger.focus();}if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();if(menu.hidden)open();const buttons=[...menu.querySelectorAll('button:not(:disabled)')];const i=buttons.indexOf(document.activeElement);buttons[(i+(e.key==='ArrowDown'?1:-1)+buttons.length)%buttons.length]?.focus();}};
    select.addEventListener('change',update);update();
  });
}
document.addEventListener('click',e=>{document.querySelectorAll('.inline-select').forEach(w=>{if(!w.contains(e.target)){w.querySelector('.inline-select-menu').hidden=true;w.querySelector('.inline-select-trigger').setAttribute('aria-expanded','false');}});});
function shiftCalendarMonth(delta) {
  const year=calendarYear+(calendarMonth<9?1:0),date=new Date(year,calendarMonth-1+delta,1);
  calendarMonth=date.getMonth()+1;calendarYear=date.getFullYear()-(calendarMonth<9?1:0);
  portalSave('calendar-year',calendarYear);portalSave('calendar-month',calendarMonth);
  const content=$('#content');
  if(content){content.classList.remove('calendar-slide-next','calendar-slide-prev');void content.offsetWidth;content.classList.add(delta>0?'calendar-slide-next':'calendar-slide-prev');}
  renderCalendar();icons();
  if(content)window.setTimeout(()=>content.classList.remove('calendar-slide-next','calendar-slide-prev'),320);
}
function calendarToolsMarkup() {
  return '<div class="calendar-tools"><button class="secondary" data-school-calendar>校历与课程活动</button></div>';
}
function schoolCalendarEntries() {
  const activities=window.nativeCalendarActivities||[];
  return (window.nativeSchoolEvents||[]).filter(e=>!activities.some(a=>a.title===e.title&&a.date===e.date)).flatMap(e=>{
    const rows=[],end=e.endDate||e.date;let date=e.date;
    while(date<=end&&rows.length<366){rows.push({...e,id:`${e.id}:${date}`,date});const d=new Date(date+'T12:00:00');d.setDate(d.getDate()+1);date=`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
    return rows;
  });
}
document.addEventListener('click',e=>{
  if(e.target.closest('[data-school-calendar]')&&!window.AcademicHost)sheet('校历与课程活动','<p class="sheet-text">在 APP 的学年日历中查看学校校历、加入校历活动，并添加或编辑课程活动。</p>');
});
function competitionHistoryMarkup(c) {
  const same=x=>normalizeCompetitionName(x.name)===normalizeCompetitionName(c.name);
  const records=CompetitionCatalog.items.filter(x=>same(x)&&x.year<c.year).sort((a,b)=>b.year-a.year);
  const editions=CompetitionEditions.filter(x=>same(x)&&x.year<c.year);
  if (!records.length&&!editions.length) return '<h3 class="section-label">往届参考</h3><p class="footnote compact">尚未取得可核实的往届章程或日程，请通过下方官网查看历史公告。</p>';
  return `<h3 class="section-label">往届参考</h3><div class="competition-history">${records.map(x=>{const source=CompetitionCatalog.sources.find(s=>s.id===x.sourceId);return `<details><summary>${x.year} 年 · ${esc(x.grade||'学校目录未标等级')}</summary><dl class="facts">${facts([['主办单位',x.organizer],['参赛范围',x.scope],...(x.content?[['比赛内容',x.content]]:[]),...(x.note?[['目录说明',x.note]]:[])])}</dl>${source?`<a href="${esc(source.attachmentUrl||source.url)}" target="_blank" rel="noopener">${esc(source.file)}${icon('external-link')}</a>`:''}<p class="footnote compact">往届认定与参赛范围仅供参考；本届资格、组队和日程按本届章程执行。</p></details>`;}).join('')}${editions.map(x=>`<details><summary>${x.year} 届公告</summary><p class="notice-excerpt compact">${esc(x.content)}</p>${x.phases.map(p=>`<p>${esc(p.name)} · ${esc(p.date||p.label)}</p>`).join('')}<a href="${esc(x.source)}" target="_blank" rel="noopener">查看往届原文</a></details>`).join('')}</div>`;
}
function editCalendarEvent(id) {
  const entry=calendarEvents.find(e=>e.id===id);if(!entry)return;
  sheet('编辑安排',`<label class="field-label">名称</label><input id="edit-event-title" class="sheet-input" maxlength="160" value="${esc(entry.title)}"><label class="field-label">日期</label><input id="edit-event-date" class="sheet-input" type="date" value="${esc(entry.date)}" required><label class="field-label">时间</label><input id="edit-event-time" class="sheet-input" type="time" value="${esc(entry.time||'')}"><label class="field-label">阶段 / 备注</label><input id="edit-event-stage" class="sheet-input" maxlength="160" value="${esc(entry.stage||'')}"><div class="sheet-actions"><button class="primary" id="edit-event-save">保存安排</button></div>`);
  $('#edit-event-save').onclick=()=>{if(!$('#edit-event-date').reportValidity())return;const title=$('#edit-event-title').value.trim();if(!title){$('#edit-event-title').focus();return;}
    const next={...entry,title,date:$('#edit-event-date').value,time:$('#edit-event-time').value,stage:$('#edit-event-stage').value.trim(),timetable:false,origin:'用户确认'};
    calendarEvents=calendarEvents.map(e=>e.id===id?next:e);portalSave('calendar',calendarEvents);closeSheet();renderCalendar();icons();};
}
document.addEventListener('click',e=>{const b=e.target.closest('[data-edit-event]');if(b)editCalendarEvent(b.dataset.editEvent);});
