'use strict';
// Compact schedules use the same catalog/notice adapters; no additional events.
function competitionDateSummary(record){
 const nodes=[...new Map(timelineNodes(record).map(n=>[`${n.phase}:${n.year}-${n.month}-${n.day}`,n])).values()];
 const conflicts=[...new Set(nodes.filter(n=>n.day&&nodes.some(other=>other.day&&other.phase===n.phase&&(other.year!==n.year||other.month!==n.month||other.day!==n.day))).map(n=>n.phase))];
 const exact=nodes.filter(n=>n.day&&!conflicts.includes(n.phase)),coarse=nodes.filter(n=>!n.day&&!conflicts.includes(n.phase));
 const label=n=>`${n.year}.${String(n.month).padStart(2,'0')}${n.day?'.'+String(n.day).padStart(2,'0'):''}`;
 const descriptions=exact.length?exact.map(n=>`${n.phase} ${label(n)}`):coarse.map(n=>`${n.phase} ${label(n)}（月份参考）`);
 const text=descriptions.slice(0,3).join(' · ')+(descriptions.length>3?` · 另有 ${descriptions.length-3} 个阶段`:'');
 return {exact,coarse,conflicts,crossYear:exact.some(n=>n.year!==Number(record.year)),text:(text||'本届日期待公布')+(conflicts.length?' · 部分日期冲突待核实':'')};
}
// Replace the looping month tracks with a bounded, scrollable date list.
multiTimelineMarkup=function(competitions){
 const rows=competitions.flatMap(c=>{const summary=competitionDateSummary(c);return [...summary.exact,...summary.coarse].filter(n=>Number.isFinite(n.year)&&n.month>=1&&n.month<=12).map(n=>({c,n}));})
  .sort((a,b)=>a.n.year-b.n.year||a.n.month-b.n.month||(a.n.day||32)-(b.n.day||32));
 if(!rows.length)return '<p class="timeline-empty">暂无已获取的阶段日期，可通过官方通知查看。</p>';
 return `<ol class="schedule-timeline">${rows.slice(0,60).map(({c,n})=>`<li><time>${n.year}.${String(n.month).padStart(2,'0')}${n.day?'.'+String(n.day).padStart(2,'0'):' · 月份参考'}</time><button data-competition="${esc(c.id)}"><strong>${esc(n.phase)}</strong>${competitions.length>1?`<small>${esc(c.name)}</small>`:''}</button></li>`).join('')}</ol>${rows.length>60?`<p class="footnote">共 ${rows.length} 个节点，其余请在赛事详情中查看。</p>`:''}`;
};
// Existing adapters had repeated the same phases after the date diagram.
timelineMarkup=function(record){
 const summary=competitionDateSummary(record);
 if(summary.exact.length||summary.coarse.length)return (summary.conflicts.length?`<p class="timeline-empty">${esc(summary.conflicts.join('、'))}的来源日期不一致，暂不列为确定安排，请核对通知原文。</p>`:'')+(summary.crossYear?'<p class="footnote">赛程包含跨年日期，请按节点标注的年份查看。</p>':'')+multiTimelineMarkup([record]);
 return '<p class="timeline-empty">本届报名与比赛日期尚未获取。查看“通知”中的本届公告或“往届”参考。</p>';
};

function examPeriodLabel(period){return period?period.replace('-spring',' 春季').replace('-autumn',' 秋季'):'最新安排';}
function examHistoryPeriods(){const now=new Date(),year=now.getFullYear();return [...(now.getMonth()>=7?[`${year}-spring`]:[]),String(year-1),String(year-2)];}
function examCachedPeriod(period){
 const all=[...(examHistoricalFeed?.items||[]),...(noticeFeed?.items||[]),...(window.BundledExamNotices?.items||[])];
 return [...new Map(all.filter(n=>examNoticeMatchesPeriod(n,period)).map(n=>[n.id||n.url,n])).values()];
}
let examPeriodRequest=0;
async function selectExamPeriod(period){
 const request=++examPeriodRequest;
 examHistoricalPeriod=period;
 examHistoricalFeed=period?{schemaVersion:1,items:examCachedPeriod(period),period}:null;
 closeSheet();renderExams();icons();
 if(!period)return;
 try{
  const response=await fetch(`${noticesEndpoint}?period=${encodeURIComponent(period)}`,{cache:'no-store',signal:AbortSignal.timeout(15000)});
  if(!response.ok)throw Error('request failed');const feed=await response.json();
  if(feed.schemaVersion!==1||!Array.isArray(feed.items))throw Error('invalid feed');
  if(request!==examPeriodRequest||examHistoricalPeriod!==period)return;
  const items=[...new Map([...examHistoricalFeed.items,...feed.items.filter(n=>examNoticeMatchesPeriod(n,period))].map(n=>[n.id||n.url,n])).values()];
  examHistoricalFeed={...feed,items,period};if(academicTab==='exams'&&!$('#sheet').open)renderNotices();
 }catch(_){if(request===examPeriodRequest&&examHistoricalPeriod===period)toast(examHistoricalFeed.items.length?'网络未更新，已显示本地往期资料':'该期尚无已收录公告，可查看卡片内官方入口');}
}
syncExamPeriod=selectExamPeriod;
function showExamPeriodSelector(){
 const periods=[null,...examHistoryPeriods()];
 sheet('安排期次',`<div class="schedule-period-list">${periods.map(period=>{
  const notices=period?examCachedPeriod(period):(noticeFeed?.items||[]);
  return `<button data-schedule-period="${period||''}" aria-pressed="${examHistoricalPeriod===period}"><span><strong>${esc(examPeriodLabel(period))}</strong><small>${notices.length?`${notices.length} 条已收录公告`:'暂无已收录公告'}</small>${notices.slice(0,2).map(n=>`<span class="schedule-period-preview">${esc(n.title)}</span>`).join('')}</span>${icon('chevron-right')}</button>`;
 }).join('')}</div>`);
 document.querySelectorAll('[data-schedule-period]').forEach(button=>button.onclick=()=>selectExamPeriod(button.dataset.schedulePeriod||null));
}
const scheduleRenderExams=renderExams;
renderExams=function(){
 scheduleRenderExams();$('#exam-current')?.remove();
 const button=$('#exam-history');button.textContent=examPeriodLabel(examHistoricalPeriod);button.onclick=showExamPeriodSelector;
 $('#exam-refresh').onclick=()=>examHistoricalPeriod?selectExamPeriod(examHistoricalPeriod):syncNotices();
};
const scheduleExamCard=examCardMarkup;
examHistoricalReference=function(exam){
 if(examHistoricalPeriod||!/待公布|待核实|新一期|按场次/.test(`${exam.state||''}${exam.date||''}`))return '';
 const now=new Date(),year=now.getFullYear();
 const notice=(noticeFeed?.items||[]).filter(n=>n.category===exam.type&&(Number(n.title?.match(/20\d{2}/)?.[0])<year||(now.getMonth()>=7&&examNoticeMatchesPeriod(n,`${year}-spring`)))).sort((a,b)=>String(b.publishedAt).localeCompare(String(a.publishedAt)))[0];
 if(!notice)return '';
 return `<details class="exam-information"><summary>往期参考 · ${esc(notice.title)}</summary><p class="footnote">${esc(compactExamStage(notice.content||notice.excerpt)||'原文未提取到具体阶段日期')}</p><a class="source-name" href="${esc(notice.url)}" target="_blank" rel="noopener">往期原文${icon('external-link')}</a></details>`;
};
function compactExamStage(value){
 const text=String(value).replace(/\s+/g,' ');
 const pattern=/(?:20\d{2}\s*年\s*)?\d{1,2}\s*月\s*\d{1,2}\s*日(?:\s*(?:至|到|—|–|-)\s*(?:\d{1,2}\s*月\s*)?\d{1,2}\s*日)?|20\d{2}[-/.]\d{1,2}[-/.]\d{1,2}/g;
 const parts=[...text.matchAll(pattern)].map(match=>{
  const after=text.slice(match.index+match[0].length,match.index+match[0].length+32);
  const times=after.match(/^\s*(?:起|开始|截止|开放)?\s*(?:[上下]午\s*)?\d{1,2}(?:[：:]\d{2}|时(?:\d{1,2}分)?)(?:\s*[-—至]\s*\d{1,2}[：:]\d{2})?/);
  const extraDay=after.match(/^[、，]\s*\d{1,2}\s*日/);
  return match[0].replace(/\s+/g,'')+(extraDay?extraDay[0].replace(/\s+/g,''):'')+(times?times[0].trim():'');
 });
 if(!parts.length)return '';
 const clock=text.match(/\d{1,2}[：:]\d{2}(?:\s*[-—至]\s*\d{1,2}[：:]\d{2})?/g)||[];
 const result=[...new Set(parts)].slice(0,4).join(' · ');
 return result+(clock.length&&!/[：:]\d{2}/.test(result)?' · '+[...new Set(clock)].join(' · '):'');
}
examCardMarkup=function(exam){
 const original=scheduleExamCard(exam),rows=examTimeline(exam);
 const live=liveExamStages(exam);
 const known=rows.map(([label,value],index)=>[label,compactExamStage(examHistoricalPeriod?(live[index]?.text||''):value)]).filter(([,value])=>value);
 const unknown=examHistoricalPeriod?[]:rows.filter(([label])=>!known.some(([stage])=>stage===label));
 const timeline=known.length?stageTimelineMarkup(known):'<p class="exam-dates-empty">'+(examHistoricalPeriod?'该期暂无具体阶段日期':'具体日期尚未公布')+'</p>';
 const extras=unknown.length?`<details class="exam-undated"><summary>其他阶段说明 · ${unknown.length}</summary>${stageTimelineMarkup(unknown)}</details>`:'';
 return original.replace(/<ol class="record-timeline">[\s\S]*?<\/ol>/,timeline+extras);
};
// Historical notification buttons must resolve against the displayed period.
const scheduleNoticeDetail=noticeDetail;
noticeDetail=function(id){
 const historical=examHistoricalFeed?.items?.find(n=>n.id===id);
 if(!historical)return scheduleNoticeDetail(id);
 const current=noticeFeed;
 try{noticeFeed={...current,items:[historical]};scheduleNoticeDetail(id);}finally{noticeFeed=current;}
};
const scheduleRenderNotices=renderNotices;
renderNotices=function(){scheduleRenderNotices();if(examHistoricalPeriod&&$('#notice-status'))$('#notice-status').textContent=`往期参考 ${examPeriodLabel(examHistoricalPeriod)} · ${examHistoricalFeed?.items?.length||0} 条公告`;};
const scheduleStyle=document.createElement('style');scheduleStyle.textContent=`
.schedule-date-filter{display:flex;align-items:center;gap:12px;margin:10px 0 18px}.schedule-date-filter>button{border:1px solid var(--line);background:var(--surface);border-radius:10px;padding:9px 12px;color:var(--ink);white-space:nowrap}.schedule-date-filter>button.active{background:var(--soft);color:var(--accent)}.schedule-date-filter>span{font-size:11px;color:var(--mut)}.competition-date-summary{display:block!important;color:var(--accent)!important;font-size:12px!important;line-height:1.6;margin-top:8px}.schedule-timeline{list-style:none;margin:0;padding:0}.schedule-timeline li{display:grid;grid-template-columns:110px 1fr;gap:12px;padding:12px 0;border-bottom:1px solid var(--line)}.schedule-timeline li:last-child{border-bottom:0}.schedule-timeline time{font-size:12px;color:var(--accent)}.schedule-timeline button{border:0;background:none;text-align:left;padding:0;color:var(--ink);font:inherit}.schedule-timeline small{display:block;color:var(--mut);font-size:12px;margin-top:4px}.schedule-period-list{display:grid;gap:10px}.schedule-period-list>button{display:flex;align-items:center;text-align:left;justify-content:space-between;width:100%;border:1px solid var(--line);border-radius:16px;background:var(--surface);color:var(--ink);padding:16px;gap:12px}.schedule-period-list>button[aria-pressed=true]{background:var(--soft)}.schedule-period-list strong,.schedule-period-list small,.schedule-period-preview{display:block}.schedule-period-list small,.schedule-period-preview{font-size:12px;color:var(--mut);margin-top:7px;line-height:1.5}.exam-dates-empty{font-size:13px;color:var(--mut)}.exam-undated{font-size:12px;color:var(--mut);margin:8px 0}.exam-undated summary{cursor:pointer}.exam-summary .preview-card-head>span{display:flex;align-items:center;gap:8px}.exam-summary .sheet-text{font-size:13px}
`;document.head.append(scheduleStyle);
