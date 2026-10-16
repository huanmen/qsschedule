'use strict';
// Persistent exam records: yearly notices and dates update these same cards.
const examPortals = {
  psc:{registration:'https://bm.cltt.org/',query:'https://www.cltt.org/#/scoreQuery',eligibility:'本校报名条件见教务通知；社会考生可选福建、泉州测试站。'},
  set4:{registration:'https://cet-bm.neea.edu.cn/',query:'https://cjcx.neea.edu.cn/',eligibility:'须符合 CET4 笔试报考条件，口试报名要求见当次通知。'},
  set6:{registration:'https://cet-bm.neea.edu.cn/',query:'https://cjcx.neea.edu.cn/',eligibility:'须符合 CET6 笔试报考条件，口试报名要求见当次通知。'},
  cet4:{registration:'https://cet-bm.neea.edu.cn/',query:'https://cjcx.neea.edu.cn/',eligibility:'在校生通过学校规定的方式报名；校内名额和安排见教务通知。'},
  cet6:{registration:'https://cet-bm.neea.edu.cn/',query:'https://cjcx.neea.edu.cn/',eligibility:'通常须达到 CET4 成绩要求，具体资格见当次报名通知。'},
  ncre:{registration:'https://ncre-bm.neea.cn/',query:'https://cjcx.neea.edu.cn/',eligibility:'按报考级别选择科目，考点、机位与时间以省级通知和准考证为准。'},
  software:{registration:'https://bm.ruankao.org.cn/',query:'https://www.ruankao.org.cn/',queryLabel:'成绩查询（官网）',eligibility:'可关注程序员、软件设计师、网络工程师等资格；分批机考。'},
  teacher:{registration:'https://ntce.neea.edu.cn/',query:'https://ntce.neea.edu.cn/',registrationLabel:'报名入口（官网）',queryLabel:'成绩查询（官网）',eligibility:'笔试、面试分别报考；福建在校生年级和学籍条件见省级公告。'},
  accounting:{registration:'https://ausm.mof.gov.cn/',query:'https://kzp.mof.gov.cn/',queryLabel:'成绩查询（官网）',eligibility:'报考学历及材料要求见当期简章。'},
  cpa:{registration:'https://cpaexam.cicpa.org.cn/',query:'https://cpaexam.cicpa.org.cn/',eligibility:'应届毕业生学历审核及专业阶段报考资格见当年简章。'},
  catti:{registration:'https://www.cpta.com.cn/',query:'https://www.cpta.com.cn/',registrationLabel:'报名入口（人事考试网）',queryLabel:'成绩查询（人事考试网）',eligibility:'二、三级笔译或口译，语种及考区按各省当年公告选择。'},
  ielts:{registration:'https://ielts.neea.cn/',query:'https://ielts.neea.cn/',eligibility:'按申请要求选择学术类或其他考试类型、考点与场次。'},
  toefl:{registration:'https://toefl.neea.cn/',query:'https://toefl.neea.cn/',eligibility:'按申请要求及官方报名系统选择考点和场次。'}
};
let examQuery = '', examFollowOnly = false;
let examHistoricalPeriod = null, examHistoricalFeed = null;
examPeriod = 'all';
function examNoticeMatchesPeriod(notice, period) {
  const [year,term]=period.split('-'), title=notice.title||'', titleYear=title.match(/20\d{2}/)?.[0];
  if((titleYear||String(notice.publishedAt||'').slice(0,4))!==year)return false;
  if(!term)return true;
  if(/下半年|秋|下学期/.test(title))return term==='autumn';
  if(/上半年|春|上学期/.test(title))return term==='spring';
  const month=Number(title.match(/(?:20\d{2}\s*年\s*)?(\d{1,2})\s*月/)?.[1]||String(notice.publishedAt||'').slice(5,7));
  return month>0&&(term==='spring'?month<8:month>=8);
}
function examRecordNotices(exam) {
  const category=exam.type==='普通话'?'普通话':exam.type;
  return ((examHistoricalPeriod?examHistoricalFeed:noticeFeed)?.items||[]).filter(n=>n.category===category);
}
function examTimeline(exam) {
  if(examHistoricalPeriod){
    const notices=examRecordNotices(exam);
    return [['报名',/报名/],['准考证',/准考证/],['考试',/考试时间|考试日期|测试时间|笔试时间/],['成绩与证书',/成绩查询|成绩发布|证书/]].map(([stage,pattern])=>{
      for(const notice of notices){
        const text=(notice.content||notice.excerpt||notice.title||'').replace(/\s+/g,' '),index=text.search(pattern);
        if(index>=0)return [stage,text.slice(index,index+180)];
      }
      return [stage,'该期已收录公告未提供此阶段安排，详见下方官方通知'];
    });
  }
  return [
    ['报名',exam.registration||'按当期公告或考点场次报名'],
    ['准考证',exam.print||'开放时间以当期公告为准'],
    ['考试',exam.date+' · '+exam.time],
    ['成绩与证书','发布时间以官方查询入口为准']
  ];
}
function examHistoricalReference(exam) {
  if(examHistoricalPeriod)return '';
  if (!/待公布|待核实|新一期|按场次/.test(`${exam.state||''}${exam.date||''}`)) return '';
  const category = exam.type==='普通话'?'普通话':exam.type;
  const currentYear = new Date().getFullYear();
  const rows = (noticeFeed?.items||[]).filter(n=>n.category===category && /20\d{2}/.test(n.title||''))
    .map(n=>({notice:n,year:Number((n.title.match(/20\d{2}/)||[])[0])}))
    // “往期”只引用已结束的期次：当前为 2026 秋时保留 2026 春及更早资料，
    // 不把正在发布的 2026 秋通知误显示为历史安排。
    .filter(x=>x.year<currentYear || (new Date().getMonth()>=8 && examNoticeMatchesPeriod(x.notice,`${currentYear}-spring`)))
    .sort((a,b)=>b.year-a.year);
  if (!rows.length) return '';
  const latest=rows[0];
  return `<div class="exam-history-note"><span class="tag neutral">当前安排待公布</span><p>可先参考 ${latest.year} 年公告：${esc(latest.notice.excerpt||latest.notice.title)}</p><a href="${esc(latest.notice.url)}" target="_blank" rel="noopener">查看往期原文${icon('external-link')}</a></div>`;
}
function stageTimelineMarkup(rows) {
  return `<ol class="record-timeline">${rows.map(([label,value])=>`<li><strong>${esc(label)}</strong><span>${esc(value)}</span></li>`).join('')}</ol>`;
}
function examLinksMarkup(exam) {
  const portals=examPortals[exam.id];
  return `<div class="record-portals"><a href="${esc(portals.registration)}" target="_blank" rel="noopener">${esc(portals.registrationLabel||'报名入口')}${icon('external-link')}</a><a href="${esc(portals.query)}" target="_blank" rel="noopener">${esc(portals.queryLabel||'成绩查询')}${icon('external-link')}</a></div>`;
}
function examCardMarkup(exam) {
  const entries=examRecordNotices(exam), portals=examPortals[exam.id];
  return `<article class="exam" data-exam-record="${exam.id}">
    <div class="exam-head"><span class="exam-symbol">${icon(exam.symbol)}</span><h3>${esc(exam.title)}</h3><button class="icon-button ${saved[exam.id]?'selected':''}" data-exam-follow="${exam.id}" aria-label="${saved[exam.id]?'取消关注':'关注考试'}" aria-pressed="${!!saved[exam.id]}">${icon(saved[exam.id]?'bookmark-check':'bookmark')}</button></div>
    <div class="exam-sub"><span>${esc(exam.region||'全国 · 福建适用')}</span><span>${examHistoricalPeriod?`往期参考 · ${esc(examHistoricalPeriod)}`:esc(exam.state)}</span></div>
    ${stageTimelineMarkup(examTimeline(exam))}${examHistoricalReference(exam)}${examLinksMarkup(exam)}
    <details class="exam-information"><summary>报考说明与官方通知${entries.length?' · '+entries.length+' 条':''}</summary><p class="notice-excerpt compact">${esc(portals.eligibility)}</p>${exam.detail?`<p class="notice-excerpt compact">${esc(exam.detail)}</p>`:''}<a class="source-name" href="${esc(examSources[exam.source].url)}" target="_blank" rel="noopener">${esc(examSources[exam.source].name)}${icon('external-link')}</a>${entries.slice(0,4).map(noticeRow).join('')}${entries.length>4?`<button class="text-button" data-exam-notices="${exam.id}">查看全部通知${icon('chevron-right')}</button>`:''}</details>
  </article>`;
}
async function syncExamPeriod(period) {
  try {
    const response=await fetch(`${noticesEndpoint}?period=${encodeURIComponent(period)}`,{cache:'no-store',signal:AbortSignal.timeout(15000)});
    if(!response.ok)throw Error('request failed');
    const feed=await response.json();
    if(feed.schemaVersion!==1||!Array.isArray(feed.items))throw Error('invalid feed');
    // Older deployed servers may ignore the period parameter. Always verify the
    // returned notices locally before calling any of them historical.
    const items=feed.items.filter(n=>examNoticeMatchesPeriod(n,period));
    if(!items.length){toast('该期暂无已收录公告，可通过官方入口查看');return;}
    examHistoricalFeed={...feed,items,period};examHistoricalPeriod=period;
    closeSheet();renderExams();toast(`已载入 ${period} 往期公告 · ${items.length} 条`);
  } catch (_) { toast('历史安排获取失败，保留当前资料'); }
}
renderExams = function() {
  $('#content').innerHTML=`<section class="exam-summary"><div class="preview-card-head"><h2>考证资料</h2><span><button id="exam-history" class="text-button">往期安排</button><button id="exam-refresh" class="icon-button" aria-label="刷新考试">${icon('refresh-cw')}</button></span></div><p class="sheet-text">报名、考试与成绩查询，持续更新在同一张卡片中。</p><p id="notice-status" class="sync-status">${noticeFeed?.period?`已载入 ${esc(noticeFeed.period)} · `:''}${noticeFeed?'官方通知更新于 '+formatTime(noticeFeed.checkedAt):'尚未同步'}</p></section>
    <div class="toolbar"><h2>考试与证书</h2><button class="text-button" id="exam-follow-filter" aria-pressed="${examFollowOnly}">${examFollowOnly?'显示全部':'只看关注'}</button></div>
    <label class="search">${icon('search')}<input id="exam-search" aria-label="搜索考证" placeholder="考试名称、证书" value="${esc(examQuery)}"></label>
    <div class="category-tabs" aria-label="考试分类">${['全部',...new Set(exams.map(e=>e.type))].map(type=>`<button data-exam-type="${type}" class="${type===examType?'active':''}" aria-pressed="${type===examType}">${esc(type)}</button>`).join('')}</div><div class="exam-list" id="exam-list"></div>`;
  $('#exam-refresh').onclick=syncNotices;
  $('#exam-history').onclick=()=>{
    const now=new Date(),year=now.getFullYear();
    const terms=now.getMonth()>=8?[[`${year}-spring`,`${year} 春季`]]:[];
    const periods=[...terms,...[year-1,year-2].map(y=>[String(y),`${y} 年`])];
    sheet('选择历史期',`<p class="sheet-text">当前期尚未发布时，可读取上一学期或更早的公告。</p><div class="sheet-actions">${periods.map(([period,label])=>`<button class="secondary" data-exam-period="${period}">${label}</button>`).join('')}</div>`);
    document.querySelectorAll('[data-exam-period]').forEach(button=>button.onclick=()=>syncExamPeriod(button.dataset.examPeriod));
  };
  if(examHistoricalPeriod){
    $('#notice-status').textContent=`往期参考 ${examHistoricalPeriod} · ${examHistoricalFeed.items.length} 条官方公告`;
    $('#exam-history').insertAdjacentHTML('beforebegin','<button id="exam-current" class="text-button">当前安排</button>');
    $('#exam-current').onclick=()=>{examHistoricalPeriod=null;examHistoricalFeed=null;renderExams();};
  }
  $('#exam-follow-filter').onclick=()=>{examFollowOnly=!examFollowOnly;renderExams();icons();};
  $('#exam-search').oninput=event=>{examQuery=event.target.value;renderExamList();};
  document.querySelectorAll('[data-exam-type]').forEach(button=>button.onclick=()=>{
    examType=button.dataset.examType;
    document.querySelectorAll('[data-exam-type]').forEach(b=>{b.classList.toggle('active',b.dataset.examType===examType);b.setAttribute('aria-pressed',String(b.dataset.examType===examType));});
    renderExamList();
  });
  renderExamList();academicTabs();icons();
};
renderExamList = function() {
  const selected=exams.filter(e=>(examType==='全部'||e.type===examType)&&(!examFollowOnly||saved[e.id])&&(e.title+e.type).toLowerCase().includes(examQuery.toLowerCase()));
  $('#exam-list').innerHTML=selected.length?selected.map(examCardMarkup).join(''):'<div class="empty">没有符合条件的考试</div>';
  icons();
};
renderNotices = function() {
  if(!$('#exam-list'))return;
  const expanded=[...document.querySelectorAll('.exam-information[open]')].map(e=>e.closest('[data-exam-record]').dataset.examRecord);
  renderExamList();expanded.forEach(id=>document.querySelector(`[data-exam-record="${id}"] details`)?.setAttribute('open',''));
  $('#notice-status').textContent='官方通知更新于 '+formatTime(noticeFeed?.checkedAt);
};
document.addEventListener('click',event=>{
  const follow=event.target.closest('[data-exam-follow]');
  if(follow){const id=follow.dataset.examFollow;if(saved[id])delete saved[id];else saved[id]={};persist();follow.setAttribute('aria-pressed',String(!!saved[id]));follow.setAttribute('aria-label',saved[id]?'取消关注':'关注考试');follow.innerHTML=icon(saved[id]?'bookmark-check':'bookmark');icons();}
  const notice=event.target.closest('[data-exam-notices]');
  if(notice){const exam=exams.find(e=>e.id===notice.dataset.examNotices);sheet(exam.title+' · 官方通知',examRecordNotices(exam).map(noticeRow).join(''));}
});
