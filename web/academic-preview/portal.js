'use strict';
const portalRead=(key,fallback)=>{try{return JSON.parse(localStorage.getItem('qnu-portal-'+key))??fallback;}catch(_){return fallback;}};
let portalSave=(key,value)=>{try{localStorage.setItem('qnu-portal-'+key,JSON.stringify(value));}catch(_){toast('浏览器未保存此项');}};
let pins=portalRead('pins',[]),followed=portalRead('followed',[]),calendarEvents=portalRead('calendar',[]);
let competitionYear=2026,competitionScope='school',competitionKind='全部',competitionGrade='全部',competitionQuery='',competitionFollowOnly=false,competitionLimit=15,competitionListRendering=false;
const calendarNow=new Date();
let calendarYear=portalRead('calendar-year',calendarNow.getFullYear()),calendarMonth=portalRead('calendar-month',calendarNow.getMonth()+1),calendarFollowing=false,calendarMonthsExpanded=false;
let competitionFeed=portalRead('competition-feed',null);
let previewBackground='',previewFont=14;
const previewSemesters=[{id:'2026-autumn',label:'2026 秋',start:'08 / 31',week:6,weeks:18},{id:'2026-spring',label:'2026 春',start:'03 / 02',week:18,weeks:18}];
let previewSemester=portalRead('active-semester','2026-autumn');
const plugins=[{id:'adskip',name:'广告跳过',icon:'scan-line',description:'无障碍服务 · 内置规则',installed:true}];
const nationalCatalog=[{id:'open-comap',name:'美国大学生数学建模竞赛 MCM / ICM',organizer:'COMAP',scope:'国际 · 大学生',kind:'学科',grade:null,year:2026,official:'https://www.comap.com/contests/mcm-icm',content:'围绕真实问题建立数学模型，并提交英文论文。',team:'通常三人组队；当届资格与报名费用以官网规则为准。'},{id:'open-cumcm',name:'全国大学生数学建模竞赛',organizer:'中国工业与应用数学学会',scope:'全国 · 大学生',kind:'学科',grade:null,year:2026,official:'https://www.mcm.edu.cn/',content:'选题、建模、计算与论文撰写；由学校组织报名。',team:'通常三人组队，跨专业组队要求以本校当届通知为准。'},{id:'open-icpc',name:'ICPC 国际大学生程序设计竞赛',organizer:'ICPC Foundation',scope:'国际 · 大学生',kind:'学科',grade:null,year:2026,official:'https://icpc.global/',content:'团队在限时内解决算法与程序设计问题。',team:'三人团队；学籍、参赛年限与赛区资格须核对官网。'}];
for(const [id,name,match] of [['lanqiao','蓝桥杯全国软件和信息技术专业人才大赛','蓝桥'],['innovation','中国国际大学生创新大赛','中国国际大学生创新'],['challenge','“挑战杯”全国大学生课外学术科技作品竞赛','挑战杯'],['career','全国大学生职业规划大赛','职业规划'],['computer-design','中国大学生计算机设计大赛','计算机设计'],['electronics','全国大学生电子设计竞赛','电子设计'],['english','全国大学生英语竞赛','英语竞赛'],['survey','全国大学生市场调查与分析大赛','市场调查']]){
  const school=CompetitionCatalog.items.find(c=>c.year===2026&&c.name.includes(match));
  const profile=CompetitionProfiles.find(p=>p.match.includes(match));
  nationalCatalog.push({id:'open-'+id,name,organizer:school?.organizer||'详见官网',scope:'全国 · 资格以当届章程为准',kind:'学科',grade:null,year:2026,content:profile?.content,team:profile?.team,official:profile?.official});
}
for(const item of (window.JingsaiCatalog?.items||[])){
  const sections=item.sections||{};
  nationalCatalog.push({id:item.id,name:item.name,organizer:item.organizer||'详见原文',scope:'参赛范围以当届通知为准',kind:'学科',grade:null,year:2026,content:item.summary||'',team:item.eligibility||'',official:item.url,jingsai:item});
}
const portalLink=(title,subtitle,symbol,target)=>`<button class="portal-row" data-go="${target}"><span class="row-symbol">${icon(symbol)}</span><span><strong>${title}</strong><small>${subtitle}</small></span>${icon('chevron-right')}</button>`;
function renderPortal(target){({personal:renderPersonal,competitions:renderCompetitions,calendar:renderCalendar,plugins:renderPlugins,settings:renderSettings,grades:renderGrades,arrangement:renderArrangement,homework:renderHomeworkPreview})[target]?.();}
function renderPersonal(){
  $('#content').innerHTML=`<section class="term-preview personal-heading"><div class="term-preview-heading"><div><h2>2026—2027 学年 · 秋季</h2><p>当前第 6 周 · 还有 14 周结课</p></div><button class="secondary" data-go="import">导入课表</button></div><div class="progress-line"><span style="width:30%"></span></div><div class="term-values"><span>开学日期<b>08 / 31</b></span><span>本周<b>第 6 周</b></span><span>课程教学周<b>20 周</b></span></div></section>
  <div class="toolbar"><h2>学业与安排</h2></div><div class="portal-shortcuts"><button data-go="changes">${icon('event-repeat')}<span>停课与调课</span></button><button data-go="grades">${icon('graduation-cap')}<span>成绩与学分</span></button><button data-go="competitions">${icon('trophy')}<span>赛事与考证</span></button><button data-school-calendar>${icon('calendar-cog')}<span>校历与活动</span></button></div>
  <div class="portal-list">${portalLink('作业查询','教学平台作业与提交记录','clipboard-list','homework')}<button class="portal-row" data-demo="在 APP 中查看校区、教学楼与路线"><span class="row-symbol">${icon('map')}</span><span><strong>校园地图</strong><small>校区、教学楼与路线</small></span>${icon('chevron-right')}</button></div>
  ${pins.length?`<div class="toolbar"><h2>插件市场</h2><button class="text-button" data-go="plugins">查看更多${icon('chevron-right')}</button></div><div class="pinned-list">${pins.map(id=>{const p=plugins.find(x=>x.id===id);return p?`<button class="pinned-plugin" data-plugin="${p.id}">${icon(p.icon)}<strong>${p.name}</strong>${icon('chevron-right')}</button>`:'';}).join('')}</div>`:`<div class="portal-list">${portalLink('插件市场',`${plugins.filter(x=>x.installed).length} 个已安装`,'puzzle','plugins')}</div>`}`;
  if(previewBackground){$('.personal-heading').style.backgroundImage=`url("${previewBackground}")`;$('.personal-heading').style.backgroundSize='cover';}
  const term=previewSemesters.find(s=>s.id===previewSemester)||previewSemesters[0];
  $('.term-preview h2').textContent=term.label;
  $('.term-preview h2').insertAdjacentHTML('beforeend','<button id="semester-switch" class="icon-button" aria-label="切换学期">⇋</button>');
  $('.term-preview p').textContent=`当前第 ${term.week} 周 · 还有 ${term.weeks-term.week} 周结课`;
  $('.term-preview .progress-line span').style.width=`${term.week/term.weeks*100}%`;
  const values=document.querySelectorAll('.term-values b');values[0].textContent=term.start;values[1].textContent=`第 ${term.week} 周`;values[2].textContent=`${term.weeks} 周`;
}
function switchPreviewSemester(){
  sheet('切换学期',previewSemesters.map(s=>`<button class="option" data-preview-semester="${s.id}">${esc(s.label)}${s.id===previewSemester?' · 当前':''}</button>`).join(''));
}
document.addEventListener('click',event=>{
  if(event.target.closest('#semester-switch'))switchPreviewSemester();
  const choice=event.target.closest('[data-preview-semester]');if(choice){previewSemester=choice.dataset.previewSemester;portalSave('active-semester',previewSemester);closeSheet();render();}
});
function renderPlugins(){
  $('#content').innerHTML=`<div class="toolbar"><h2>已安装</h2><span class="count">${plugins.filter(x=>x.installed).length} 个</span></div><div class="plugin-list">${plugins.map(p=>`<article class="plugin-row"><button class="plugin-main" data-plugin="${p.id}"><span class="row-symbol">${icon(p.icon)}</span><span><strong>${p.name}</strong><small>${p.description}</small></span></button>${p.installed?`<button class="icon-button pin-button ${pins.includes(p.id)?'selected':''}" data-pin="${p.id}" title="${pins.includes(p.id)?'取消置顶':'置顶到个人页'}" aria-label="${pins.includes(p.id)?'取消置顶':'置顶到个人页'}" aria-pressed="${pins.includes(p.id)}">${icon('pin')}</button>`:''}<button class="text-button" data-install="${p.id}">${p.installed?'删除':'下载'}</button></article>`).join('')}</div>`;
}
function pluginDetail(id){const p=plugins.find(x=>x.id===id);if(!p)return;sheet(p.name,`<div class="plugin-detail-heading">${icon(p.icon)}<p>${p.description}</p></div>${id==='exam-schedule'?`<button class="primary" data-go="exams" data-close>查看考试日程</button>`:id==='schedule-qa'?`<label class="field-label">API 地址</label><input class="sheet-input" placeholder="https://api.example.com/v1"><label class="field-label">模型名称</label><input class="sheet-input" placeholder="模型"><label class="field-label">密钥</label><input class="sheet-input" type="password" autocomplete="off"><button class="primary" data-demo="配置已保留在本次预览中">保存</button>`:`<label class="option"><span>${id==='adskip'?'开启服务':'启用插件'}</span><input type="checkbox"></label>${id==='adskip'?'<label class="option"><span>无障碍服务</span><input type="checkbox"></label><label class="option"><span>不限制电池使用</span><input type="checkbox"></label>':''}`}`);}
function renderSettings(){
  $('#content').innerHTML=`<div class="toolbar"><h2>显示</h2></div><div class="portal-list">${portalLink('主题与配色',$('#theme').selectedOptions[0].textContent,'palette','theme')}${portalLink('字体与显示','字号、课程信息显示','type','display')}${portalLink('背景图片','默认背景与自定义背景','image','background')}</div><div class="toolbar"><h2>通知与数据</h2></div><div class="portal-list">${portalLink('课程提醒','提醒时间与通知','bell','notifications')}${portalLink('备份与恢复','课表与设置','database','backup')}${portalLink('公告与版本','泉师课程表 1.0.0','info','about')}</div>`;
}
function renderArrangement(){
  $('#content').innerHTML=`<div class="portal-list">${portalLink('教务课表导入','选择学期并导入教务课表','download','import')}</div>`;
}
function renderCreditAdvice(){
  const cross=groups.find(g=>g.id==='cross'),art=groups.find(g=>g.id==='art');
  const example=$('#credit-example').value,values={done:[7,2,2],none:[0,0,0],partial:[4,0,0],art:[6,0,2],limit:[8,0,4]}[example];
  const [total,arts,online]=values;
  cross.earned=total;cross.ok=total>=7;art.earned=arts;art.ok=arts>=2;
  cross.items=example==='done'?[['音乐欣赏',2,'已通过'],['生活中的经济学',2,'已通过'],['数字摄影',1,'已通过'],['科学与生活（线上）',2,'已通过']]:total?[['跨学科课程合计',total,'已通过']]:[];
  art.items=arts?[['音乐欣赏',arts,'已通过']]:[];
  cross.passed=example==='done'?4:Math.ceil(total/2);art.passed=arts?1:0;
  const pending=groups.filter(g=>!g.ok).length;
  const earned=137.5-7+total,progress=earned/160*100;
  $('.ring').style.setProperty('--progress',progress);$('.ring').setAttribute('aria-label',`总学分进度 ${progress.toFixed(1)}%`);$('.ring strong').textContent=earned;
  document.querySelectorAll('.stat b')[0].textContent=(160-earned).toFixed(1);document.querySelectorAll('.stat b')[2].textContent=progress.toFixed(1)+'%';
  $('.summary-copy').innerHTML=`<h2>${pending} 个课组待完成</h2><p>集中实践尚差 20 学分</p><p>专业核心尚差 4 学分 · 素质拓展尚差 2 门</p><div class="cross-preview"><span>跨学科 <b>${total} / 7</b></span>${total<7||arts<2?'<button class="text-button" id="elective-advice" aria-expanded="false">选课建议'+icon('chevron-down')+'</button>':'<span class="tag">已达标</span>'}</div><p>其中艺术 ${arts} / 2 · 线上 ${Math.min(online,4)} / 4</p>`;
  document.querySelectorAll('.stat b')[1].textContent=`${9-pending} / 9`;
  $('#elective-advice')?.remove();
  if(total<7||arts<2)$('.summary-top').insertAdjacentHTML('afterend','<button class="text-button elective-summary-help" id="elective-advice">选修学分解读与选课建议'+icon('chevron-right')+'</button>');
  if($('#elective-advice'))$('#elective-advice').onclick=()=>{
    sheet('选修学分解读与选课建议','<section id="elective-modal-advice" class="inline-advice"></section>'+electiveRulesMarkup());
    const host=$('#elective-modal-advice');
    const result=ElectivePlanner.plan({total,art:arts,online,allowOnline:true});
    const choices=result.alternatives||[];
    host.innerHTML=`<div class="advice-head"><h3>当前进程建议</h3><small>每学期 ≤4 分 · 线上累计 ≤4 分</small></div><p class="sheet-text">已获跨学科 ${total} / 7 分，其中艺术 ${arts} / 2 分。先考虑已选、成绩未出的课程，再安排后续补选。</p>${result.state==='plan'?`<div class="advice-columns">${choices.map((p,i)=>`<article><span class="strategy-number">0${i+1}</span><h3>${p.count} 门 · ${p.semesters.length} 学期</h3><p>${esc(ElectivePlanner.combination(p.courses))}</p>${p.semesters.map(s=>`<small>${s.offset===0?'下学期':`第 ${s.offset+1} 学期`}：${esc(ElectivePlanner.combination(s.items))}</small>`).join('')}<b>修完 ${p.finalTotal} 分 · 艺术 ${p.finalArt} 分</b></article>`).join('')}</div>`:`<p class="advice-warning">${esc(result.message)}</p>`}`;
  };
  renderGroups();
}
function electiveRulesMarkup(){return '<ol class="elective-rules">'+[
 ['毕业要求','本科阶段需修够跨学科选修学分，通常为 7–8 分，以本人培养方案为准。艺术类至少 2 分，包含在跨学科总学分内，不重复加总。'],
 ['学期额度','每学期最多选 4 学分，不限制课程门数。课程通常为 1 或 2 分，可采用 2+2、2+1+1 或 1+1+1+1。从零修满 7 分至少需两个学期，例如 4+3。'],
 ['艺术类课程','以选课表的“艺术类”分类为准。可以选一门 2 分艺术课或两门 1 分艺术课，不能仅凭课程名称判断。'],
 ['纯线上上限','纯线上课程累计最多计入 4 分，超出部分不能抵扣毕业缺口。若已计入 4 分线上学分，剩余至少 3 分应选线下或线上线下结合课程。'],
 ['混合授课','线上线下结合按线下课程计算，不占纯线上 4 分额度。选课时核对授课方式与实际课表。'],
 ['选课策略','优先补艺术类，再补总学分。结合兴趣、考核方式、课程难度和时间选择能完成的课，核对周二、周四节次，减少不必要的门数。'],
 ['在修课程','“已选课程成绩未知”表示已选或在修，并非未选课。建议应先考虑这些课程预计可得的学分；只有及格并认定后才计入已获学分，成绩未出时避免重复选课。']
 ].map(([title,body])=>'<li><h3>'+title+'</h3><p>'+body+'</p></li>').join('')+'</ol>';}
function competitionKey(c){return c.name.replace(/\s+/g,'');}
function normalizeCompetitionName(name){
  let text=name.toLowerCase().replace(/（[^）]*）|\([^)]*\)/g,'').replace(/[\s“”"·・—–-]/g,'');
  if(text.startsWith('美国大学生数学建模竞赛'))text='美国大学生数学建模竞赛';
  if(text==='icpc国际大学生程序设计竞赛')text='acmicpc国际大学生程序设计竞赛';
  return text;
}
function enrichCompetition(c){
  if(c.jingsai)return c;
  const matches=nationalCatalog.filter(x=>x.jingsai&&Number(x.year)===Number(c.year)&&normalizeCompetitionName(x.name)===normalizeCompetitionName(c.name));
  return matches.length===1?{...c,jingsai:matches[0].jingsai}:c;
}
let academicTab='competitions', competitionView='catalog';
function academicTabs(){
  if(!['competitions','exams'].includes(academicTab))return;
  $('#content').insertAdjacentHTML('afterbegin',`<div class="view-tabs" aria-label="赛事与考证">${[['competitions','赛事'],['exams','考证']].map(([id,label])=>`<button data-academic-tab="${id}" class="${academicTab===id?'active':''}" aria-pressed="${academicTab===id}">${label}</button>`).join('')}</div>`);
}
function updateCalendarExport(){
  $('#calendar-export')?.remove();
  $('#calendar-add-top')?.remove();$('#calendar-return')?.remove();
  $('.topbar').classList.toggle('calendar-topbar',page==='calendar');
  if(page!=='calendar'){const group=$('#calendar-title-group');if(group){group.before($('#page-title'));group.remove();}$('#page-title').onclick=null;$('#page-title').onkeydown=null;$('#page-title').removeAttribute('role');$('#page-title').removeAttribute('tabindex');}
  if(!window.AcademicHost&&academicTab==='calendar'&&['calendar','competitions','exams'].includes(page)){
    $('.topbar').insertAdjacentHTML('afterbegin',`<button id="calendar-export" class="icon-button" title="分享日历" aria-label="分享日历">${icon('share-2')}</button>`);
    $('.topbar').insertAdjacentHTML('beforeend',`<button id="calendar-add-top" class="icon-button" aria-label="添加安排">${icon('ellipsis-vertical')}</button>`);
    $('#calendar-export').onclick=exportCalendar;
    $('#calendar-add-top').onclick=calendarMoreMenu;
    calendarTopbar();
  }
}
function renderAcademicArea(tab=academicTab){
 if(window.AcademicHost&&window.nativeAcademicPluginInstalled===false&&tab!=='calendar'){
  academicTab=tab;$('#content').innerHTML='<section class="panel"><h2>赛事与考证</h2><p>从插件市场下载后，可查看赛事资料、考证安排和关注更新。</p><button class="primary" data-plugin-market>前往插件市场</button></section>';icons();return;
 }
 academicTab=tab;({competitions:renderCompetitions,exams:renderExams,calendar:renderCalendar})[tab]();updateCalendarExport();icons();installInlineSelects($('#content'));window.scrollTo(0,0);}
function competitionSources(c,source,profile,j){
  const links=[];
  const add=(url,label)=>{if(!url||links.some(x=>x.url===url))return;try{const u=new URL(url);if(!['https:','http:'].includes(u.protocol))return;}catch(_){return;}links.push({url,label});};
  add(j?.officialUrl||(!j?c.official:null)||profile?.official,'查看官网');
  add(source?.url,'查看学校通知');
  add(source?.attachmentUrl,'下载官方附件');
  add(j?.url,'查看赛事资料');
  return links.map(x=>`<a href="${esc(x.url)}" target="_blank" rel="noopener">${x.label}${icon('external-link')}</a>`).join('');
}
function findCompetition(id){return [...CompetitionCatalog.items,...nationalCatalog,...CompetitionEditions].find(c=>c.id===id);}
function editionDetail(id){
  const c=CompetitionEditions.find(x=>x.id===id);if(!c)return;
  openCompetitionSheet(c.id,c.name,competitionPagesMarkup({
    overview:`<div class="competition-tags"><span class="tag">${c.year} 届</span><span class="tag neutral">日期已核对原文</span></div><dl class="facts">${facts([['主办单位',c.organizer],['范围',c.scope],['比赛内容',c.content],['参赛资格',c.team],['地点',c.location],['费用',c.fee]])}</dl>`,
    timeline:`<h3 class="section-label">阶段安排</h3><div class="phase-list">${c.phases.map((p,i)=>`<div><span>${esc(p.name)}<small class="phase-date">${esc(p.date||p.label)}</small></span>${p.date?`<button class="text-button" data-add-phase="${c.id}" data-phase-index="${i}">${calendarEvents.some(e=>e.phaseId===c.id+'-'+i)?'已加入':'加入日历'}${icon('calendar-plus')}</button>`:''}</div>`).join('')}</div><p class="footnote compact">具体日未公布的阶段不生成日历事件。</p>`,
    notices:`<p class="footnote compact">核实于 ${c.checkedAt} · PDF ${c.sourcePages}</p><div class="sources"><a href="${c.source}" target="_blank" rel="noopener">查看学校通知${icon('external-link')}</a><a href="${c.pdf}" target="_blank" rel="noopener">赛事公告 PDF${icon('external-link')}</a>${c.official?`<a href="${c.official}" target="_blank" rel="noopener">查看官网${icon('external-link')}</a>`:''}</div>`,
    history:competitionHistoryMarkup(c),
  })+`<div class="sheet-actions"><button class="primary" data-detail-follow="${c.id}">${followed.includes(competitionKey(c))?'取消关注':'关注赛事'}</button><button class="secondary" data-plan-event="${c.id}">自定安排</button></div>`);
}
function renderCompetitionNotices(){if(!$('#competition-notice-list'))return;$('#competition-health').textContent=competitionFeed?`${competitionFeed.degraded?'部分来源待更新 · ':''}检查于 ${formatTime(competitionFeed.checkedAt)}`:'点击同步获取官方通知';$('#competition-notice-list').innerHTML=(competitionFeed?.items||[]).slice(0,10).map(n=>`<button class="notice-row" data-competition-notice="${esc(n.id)}"><span><strong>${esc(n.title)}</strong><small>${esc(n.publishedAt||'日期待核实')} · ${esc(n.sourceName)}</small></span>${icon('chevron-right')}</button>`).join('');icons();}
async function syncCompetitionNotices(){const b=$('#competition-sync')||$('#refresh');if(b.disabled)return;b.disabled=true;b.classList.add('spin');try{const r=await fetch('https://huanmen.com.cn/qsschedule/api/competition-notices',{cache:'no-store',signal:AbortSignal.timeout(15000)});if(!r.ok)throw Error();const feed=await r.json();if(feed.schemaVersion!==1||!Array.isArray(feed.items)||!feed.lastSuccessAt)throw Error();feed.items=feed.items.filter(n=>{try{const u=new URL(n.url);return u.protocol==='https:'&&u.hostname==='www.qztc.edu.cn'&&typeof n.title==='string';}catch(_){return false;}});competitionFeed=feed;portalSave('competition-feed',feed);renderCompetitionNotices();toast(feed.degraded?'已同步，部分来源待更新':'赛事通知已同步');}catch(_){toast('同步失败，保留上次通知');}finally{b.disabled=false;b.classList.remove('spin');}}
function allCalendarEvents(){const events=[...calendarEvents,...(window.nativeHomeworkEvents||[]),...schoolCalendarEntries(),...(window.nativeCalendarActivities||[]),...exams.filter(e=>e.start&&saved[e.id]).map(e=>({id:'exam-'+e.id,key:e.title,title:e.title,date:e.start,time:e.id==='cet4'?'09:00':e.id==='cet6'?'15:00':'',stage:'考试',origin:'官方公告'}))];return [...new Map(events.map(e=>[e.id,e])).values()].filter(e=>!(window.AcademicHost&&window.nativeAcademicPluginInstalled===false&&["competition","exam","records"].includes(calendarEventSource(e))));}
function agendaRows(events){return events.length?events.slice().sort((a,b)=>Number(b.kind==='homework')-Number(a.kind==='homework')||(a.date+(a.time||'')).localeCompare(b.date+(b.time||''))).map(e=>`<div class="agenda-row"><time>${e.date.slice(5)}<small>${e.time||'全天'}</small></time><span><strong>${esc(e.title)}</strong><small>${esc(e.stage)} · ${esc(e.origin)}${e.timetable?' · 课程活动':''}</small></span>${e.id.startsWith('school:')||e.id.startsWith('school-day:')||e.activityId?.startsWith('calendar-event:')?'':e.id.startsWith('native:')?`<button class="icon-button" data-native-activity="${esc(e.activityId||'')}" aria-label="编辑活动">${icon('pencil')}</button>`:e.id.startsWith('homework:')?`<span class="count">作业</span>`:!e.id.startsWith('exam-')?`<button class="icon-button" data-edit-event="${e.id}" title="编辑安排" aria-label="编辑安排">${icon('pencil')}</button><button class="icon-button" data-delete-event="${e.id}" title="删除安排" aria-label="删除安排">${icon('trash-2')}</button>`:''}</div>`).join(''):'<div class="empty">没有安排</div>';}
function exportCalendar(){
  const clean=s=>String(s).replace(/\\/g,'\\\\').replace(/\r?\n/g,'\\n').replace(/,/g,'\\,').replace(/;/g,'\\;');
  const format=date=>date.toISOString().replace(/[-:]/g,'').replace(/\.\d{3}/,''),stamp=format(new Date());
  const lines=['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//QSSchedule//Academic Preview//ZH','CALSCALE:GREGORIAN'];
  for(const e of allCalendarEvents()){
    const start=e.time?'DTSTART:'+format(new Date(e.date+'T'+e.time+':00+08:00')):'DTSTART;VALUE=DATE:'+e.date.replace(/-/g,'');
    lines.push('BEGIN:VEVENT','UID:'+clean(e.id)+'@qsschedule','DTSTAMP:'+stamp,start,'SUMMARY:'+clean(e.title+' · '+e.stage),'DESCRIPTION:'+clean(e.origin),'END:VEVENT');
  }
  lines.push('END:VCALENDAR');
  const url=URL.createObjectURL(new Blob([lines.join('\r\n')+'\r\n'],{type:'text/calendar;charset=utf-8'})),a=document.createElement('a');a.href=url;a.download='泉师学年日历.ics';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);toast('已导出日历');
}
document.addEventListener('click',e=>{
  const academic=e.target.closest('[data-academic-tab]');if(academic){renderAcademicArea(academic.dataset.academicTab);return;}
  const go=e.target.closest('[data-go]');if(go){const target=go.dataset.go;if(['personal','credits','exams','competitions','calendar','plugins','settings','grades','arrangement','homework'].includes(target)){if($('#sheet').open)closeSheet();location.hash=target;}else showSetting(target);}
  const pin=e.target.closest('[data-pin]');if(pin){const id=pin.dataset.pin;pins=pins.includes(id)?pins.filter(x=>x!==id):[...pins,id];portalSave('pins',pins);renderPlugins();icons();}
  const install=e.target.closest('[data-install]');if(install){const p=plugins.find(x=>x.id===install.dataset.install);p.installed=!p.installed;if(!p.installed){pins=pins.filter(x=>x!==p.id);portalSave('pins',pins);}renderPlugins();icons();toast(p.installed?'已下载预览插件':'已删除预览插件');}
  const p=e.target.closest('[data-plugin]');if(p)pluginDetail(p.dataset.plugin);
  const demo=e.target.closest('[data-demo]');if(demo)toast(demo.dataset.demo);
  const detail=e.target.closest('[data-competition]');if(detail){if(detail.closest('#sheet')&&competitionPresentation?.id===detail.dataset.competition)return;const c=findCompetition(detail.dataset.competition);if(c.phases)editionDetail(c.id);else competitionDetail(c.id);}
  const edition=e.target.closest('[data-edition]');if(edition)editionDetail(edition.dataset.edition);
  const phase=e.target.closest('[data-add-phase]');if(phase){const c=CompetitionEditions.find(x=>x.id===phase.dataset.addPhase),i=Number(phase.dataset.phaseIndex),p=c.phases[i],phaseId=c.id+'-'+i;if(!calendarEvents.some(x=>x.phaseId===phaseId)){calendarEvents.push({id:crypto.randomUUID(),phaseId,key:competitionKey(c),title:c.name,stage:p.name,date:p.date,time:'',timetable:false,kind:'competition',origin:'官方 PDF 已核实'});portalSave('calendar',calendarEvents);toast('已加入日历');editionDetail(c.id);}}
  const follow=e.target.closest('[data-follow],[data-detail-follow]');if(follow){const c=findCompetition(follow.dataset.follow||follow.dataset.detailFollow),key=competitionKey(c);followed=followed.includes(key)?followed.filter(x=>x!==key):[...followed,key];portalSave('followed',followed);if($('#competition-list'))renderCompetitionList();if($('#competition-timeline'))renderCompetitionTimeline();if(follow.dataset.detailFollow){if(c.phases)editionDetail(c.id);else competitionDetail(c.id);}}
  const planned=e.target.closest('[data-plan-event]');if(planned)eventForm(findCompetition(planned.dataset.planEvent));
  const notice=e.target.closest('[data-competition-notice]');if(notice){const n=competitionFeed.items.find(x=>x.id===notice.dataset.competitionNotice);const pdfs=(n.assets||[]).filter(a=>/\.pdf(?:$|\?)/i.test(a.url));const body=n.content||n.excerpt||'官方正文暂未获取，当前只能查看原文。';sheet(n.title,`<p class="sheet-text">${esc(n.sourceName)} · ${esc(n.publishedAt||'日期待核实')}</p><p class="notice-excerpt notice-full-content">${esc(body)}</p><div class="sources">${pdfs.map(a=>`<a href="${esc(a.url)}" target="_blank" rel="noopener">${esc(a.name||'预览 PDF 附件')}${icon('file-text')}</a>`).join('')}<a href="${esc(n.officialUrl||n.url)}" target="_blank" rel="noopener">查看官方原文${icon('external-link')}</a></div>`);}
  const del=e.target.closest('[data-delete-event]');if(del){calendarEvents=calendarEvents.filter(x=>x.id!==del.dataset.deleteEvent);portalSave('calendar',calendarEvents);renderCalendar();icons();}
  if($('#inline-advice')&&!e.target.closest('#inline-advice,#elective-advice')){$('#inline-advice').hidden=true;$('#elective-advice')?.setAttribute('aria-expanded','false');}
});
function showSetting(target){const names={theme:'主题与配色',display:'字体与显示',background:'背景图片',notifications:'课程提醒',backup:'备份与恢复',about:'公告与版本',import:'教务课表导入',changes:'停课与调课'};
  if(target==='import'){
    sheet('检查校园网','<p class="sheet-text">校园网检查在 APP 中完成，此处预览教务登录界面。</p><button class="primary" id="preview-login-next">查看登录页</button>');
    $('#preview-login-next').onclick=previewImportLogin;return;
  }
  const html=target==='theme'?`<div class="theme-options">${['green','soda','lime'].map((x,i)=>`<button data-theme-choice="${x}">${['森林青苔','海盐汽水','青柠晴空'][i]}</button>`).join('')}</div>`:target==='display'?'<label class="field-label">字号</label><input id="preview-font" type="range" min="12" max="18" value="14"><label class="option"><span>显示课程教师</span><input type="checkbox" checked></label>':target==='import'?'<label class="field-label">教务账号</label><input class="sheet-input" autocomplete="off"><label class="field-label">密码</label><input class="sheet-input" type="password" autocomplete="off"><button class="primary" data-demo="HTML 预览不上传账号密码">导入</button>':target==='changes'?'<p class="sheet-text">当前示例：国庆节 · 10 月 1 日—7 日</p>':target==='background'?'<input type="file" id="preview-background" accept="image/*">':target==='notifications'?'<label class="field-label">提前提醒</label><select class="sheet-select"><option>10 分钟</option><option>15 分钟</option><option>30 分钟</option></select>':target==='backup'?'<button class="primary" id="preview-backup">导出本地安排</button>':'<p class="sheet-text">泉师课程表 · 1.0.0</p>';
  sheet(names[target]||'设置',html);
  document.querySelectorAll('[data-theme-choice]').forEach(b=>b.onclick=()=>{$('#theme').value=b.dataset.themeChoice;$('.app').dataset.theme=b.dataset.themeChoice;closeSheet();if(page==='settings')renderSettings();icons();});
  if($('#preview-font')){$('#preview-font').value=previewFont;$('#preview-font').oninput=e=>{previewFont=Number(e.target.value);$('.app').style.setProperty('--preview-font',previewFont+'px');};}
  if($('#preview-background'))$('#preview-background').onchange=e=>{const file=e.target.files[0];if(!file)return;if(previewBackground)URL.revokeObjectURL(previewBackground);previewBackground=URL.createObjectURL(file);closeSheet();location.hash='personal';};
  if($('#preview-backup'))$('#preview-backup').onclick=exportCalendar;
}
function previewImportLogin(){
  sheet('教务登录','<label class="field-label" for="preview-account">教务账号</label><div class="credential-preview"><input id="preview-account" autocomplete="off"><button data-clear-credential="preview-account" aria-label="清空教务账号">×</button></div><label class="field-label" for="preview-password">密码</label><div class="credential-preview"><input id="preview-password" type="password" autocomplete="off"><button data-clear-credential="preview-password" aria-label="清空密码">×</button></div><div class="credential-actions"><button class="text-button" id="preview-credential-save">加密保存</button><button class="text-button" id="preview-relogin">重新登录</button></div><p class="footnote compact">页面预览不保存账号或密码。</p>');
  document.querySelectorAll('[data-clear-credential]').forEach(b=>b.onclick=()=>document.getElementById(b.dataset.clearCredential).value='');
  $('#preview-credential-save').onclick=()=>toast('请在 APP 中加密保存');
  $('#preview-relogin').onclick=previewImportLogin;
}
document.addEventListener('DOMContentLoaded',()=>{$('#credit-example').onchange=()=>{studyViews.delete('credits');if(page==='credits')render(true);};});

/* Dense academic portal refinements.  Keep the preview data model intact while
   presenting followed competitions and their public stages first. */
competitionScope='all';
function replaceJingsaiCatalog(feed){
  window.JingsaiCatalog=feed;
  for(let i=nationalCatalog.length-1;i>=0;i--)if(nationalCatalog[i].jingsai)nationalCatalog.splice(i,1);
  for(const j of feed.items)nationalCatalog.push({id:j.id,name:j.name,organizer:j.organizer,scope:j.eligibility,kind:'学科',year:Number(j.academicYear?.slice(0,4))||competitionYear,grade:null,content:j.summary,official:j.url,jingsai:j});
}
const storedJingsai=portalRead('jingsai-feed',null);
if(storedJingsai?.schemaVersion===2&&Array.isArray(storedJingsai.items))replaceJingsaiCatalog(storedJingsai);
let catalogRefreshing=false;
async function syncAcademicCompetitions(){
  if(catalogRefreshing)return;
  catalogRefreshing=true;
  const button=$('#timeline-refresh')||$('#refresh');button.disabled=true;button.classList.add('spin');
  const before=new Map(followedTimelineItems().map(({c})=>[competitionKey(c),JSON.stringify({nodes:timelineNodes(c),place:c.location||c.jingsai?.sections?.['比赛地点']||''})]));
  let success=false;
  try{
    const r=await fetch('https://huanmen.com.cn/qsschedule/api/competition-catalog',{cache:'no-store',signal:AbortSignal.timeout(20000)});
    if(!r.ok)throw Error();
    const feed=await r.json();
    if(feed.schemaVersion!==2||!Array.isArray(feed.items))throw Error();
    feed.items=feed.items.filter(i=>typeof i.name==='string'&&typeof i.url==='string'&&i.url.startsWith('https://jingsaitongzhi.com/competitions/'));
    replaceJingsaiCatalog(feed);portalSave('jingsai-feed',feed);success=true;
    const changes=followedTimelineItems().filter(({c})=>before.has(competitionKey(c))&&before.get(competitionKey(c))!==JSON.stringify({nodes:timelineNodes(c),place:c.location||c.jingsai?.sections?.['比赛地点']||''}));
    if($('#competition-timeline')){renderCompetitionTimeline();renderCompetitionList();}
    if(changes.length)sheet('关注赛事有更新',changes.map(({c})=>`<p class="notice-excerpt">${esc(c.name)}：阶段时间或地点资料已更新</p><button class="text-button" data-competition="${esc(c.id)}">查看详情</button>`).join(''));
    else toast(feed.degraded?'已同步，部分赛事资料暂未获取':'赛事资料已更新，未发现关注赛事有新安排');
    notifyFollowedCompetitions();
  }catch(_){toast('赛事资料获取失败，保留上次资料；未公开的时间地点无法查询');}
  finally{catalogRefreshing=false;button.disabled=false;button.classList.remove('spin');}
  await syncCompetitionNotices();
  return success;
}
let pullStart=null;
document.addEventListener('touchstart',e=>{pullStart=!$('#sheet').open&&window.scrollY<=2&&['competitions','exams'].includes(academicTab)?e.touches[0].clientY:null;},{passive:true});
document.addEventListener('touchend',e=>{if(pullStart!==null&&e.changedTouches[0].clientY-pullStart>85){academicTab==='exams'?syncNotices():syncAcademicCompetitions();}pullStart=null;},{passive:true});
function notifyFollowedCompetitions(){
  if(window.AcademicHost){
    const names=[...new Set([...followed,...followedTimelineItems().map(({c})=>c.jingsai?.name).filter(Boolean)])];
    AcademicHost.postMessage(JSON.stringify({action:'competition-follow',names}));
  }
}
document.addEventListener('click',e=>{if(e.target.closest('[data-follow],[data-detail-follow]'))notifyFollowedCompetitions();});
function competitionRows(){
  const catalog=competitionScope==='national'?[...nationalCatalog,...CompetitionEditions]:competitionScope==='school'?CompetitionCatalog.items.filter(c=>c.year===competitionYear):[...CompetitionCatalog.items.filter(c=>c.year===competitionYear),...nationalCatalog,...CompetitionEditions];
  const unique=[...new Map(catalog.map(enrichCompetition).reverse().map(c=>[normalizeCompetitionName(c.name),c])).values()].reverse();
  return unique.filter(c=>(competitionKind==='全部'||c.kind===competitionKind)&&(competitionGrade==='全部'||recognitionLabel(c)===competitionGrade)&&(!competitionFollowOnly||followed.includes(competitionKey(c)))&&[c.name,c.organizer,c.note,c.content].join(' ').toLowerCase().includes(competitionQuery.toLowerCase()));
}
function visibleCompetitions(){return competitionRows();}
function timelineNodes(c){
  const j=c.jingsai;
  const nodes=j?.calendarNodes?.filter(n=>n&&n.month).map(n=>({year:n.year,month:n.month,day:n.day,phase:n.phase||'阶段'}))||[];
  if(c.phases)c.phases.forEach(p=>{
    const exact=p.date&&String(p.date).match(/(\d{4})[-./](\d{1,2})[-./](\d{1,2})/),coarse=!exact&&p.label?.match(/(\d{4})\s*年\s*(\d{1,2})\s*月/),m=exact||coarse;
    if(m&&!nodes.some(n=>n.phase===p.name))nodes.push({year:+m[1],month:+m[2],day:exact?+m[3]:null,phase:p.name});
  });
  return nodes.sort((a,b)=>(a.year-b.year)||(a.month-b.month)||((a.day||0)-(b.day||0))||a.phase.localeCompare(b.phase));
}
const timelineColors=['#187a68','#947047','#665daf','#b45575','#467e9b','#687c38'];
function mixedTimelineColor(colors){
  if(!colors.length)return '#d6dedd';
  const rgb=colors.map(c=>[1,3,5].map(i=>parseInt(c.slice(i,i+2),16)));
  return '#'+[0,1,2].map(i=>Math.round(rgb.reduce((sum,c)=>sum+c[i],0)/rgb.length).toString(16).padStart(2,'0')).join('');
}
function timelineMarkup(c){return multiTimelineMarkup([c]);}
function multiTimelineMarkup(competitions){
  const rows=competitions.map((c,i)=>({c,color:timelineColors[i%timelineColors.length],nodes:timelineNodes(c)}));
  const nodes=rows.flatMap(r=>r.nodes), unknown=rows.filter(r=>!r.nodes.length);
  if(!nodes.length)return '<p class="timeline-empty">未获取到公开的阶段时间，请查看官网；地点以当届通知为准。</p>';
  const positions=nodes.map(n=>n.year*12+n.month-1),start=Math.min(...positions),end=Math.max(...positions);
  const runs=Math.min(24,Math.ceil((end-start+1)/3));
  const months=Array.from({length:runs*3},(_,i)=>start+i);
  const runMarkup=Array.from({length:runs},(_,i)=>{
    const slice=months.slice(i*3,i*3+3),reverse=i%2===1;
    return `<div class="timeline-run ${reverse?'reverse':''}">${slice.map(pos=>{
      const y=Math.floor(pos/12),m=pos%12+1;
      const active=rows.filter(r=>r.nodes.length&&pos>=Math.min(...r.nodes.map(n=>n.year*12+n.month-1))&&pos<=Math.max(...r.nodes.map(n=>n.year*12+n.month-1)));
      const color=mixedTimelineColor(active.map(r=>r.color));
      const marks=rows.flatMap(r=>r.nodes.filter(n=>n.year===y&&n.month===m).map(n=>({r,n})));
      return `<div class="timeline-month" style="--track-color:${color}"><div class="time-track"><i class="axis-arrow" aria-hidden="true"></i></div><time>${y}.${String(m).padStart(2,'0')}</time><div class="time-marks">${marks.map(({r,n})=>`<button class="time-mark" title="${esc(r.c.name)}" aria-label="${esc(r.c.name+' '+n.phase)}" data-competition="${esc(r.c.id)}" style="--event-color:${r.color}"><i></i><span><strong>${esc(n.phase)}</strong><small>${n.day?`${m}月${n.day}日`:'月内 · 日期未公布'}</small></span></button>`).join('')}</div></div>`;
    }).join('')}${i<runs-1?'<span class="timeline-turn" aria-hidden="true"></span>':''}</div>`;
  }).join('');
  return `<div class="timeline-legend">${rows.map(r=>`<span><i style="background:${r.color}"></i>${esc(r.c.name)}</span>`).join('')}</div><div class="folded-timeline">${runMarkup}</div>${unknown.map(r=>`<p class="timeline-status">${esc(r.c.name)}：时间暂未获取到</p>`).join('')}`;
}
function followedTimelineItems(){
  return followed.map(key=>[...CompetitionEditions,...nationalCatalog,...CompetitionCatalog.items].find(c=>competitionKey(c)===key)).filter(Boolean).map(c=>({c:enrichCompetition(c),nodes:timelineNodes(enrichCompetition(c))}));
}
function renderCompetitionTimeline(){
  const host=$('#competition-timeline');if(!host)return;
  const rows=followedTimelineItems();
  host.innerHTML=rows.length?multiTimelineMarkup(rows.map(r=>r.c)):`<div class="timeline-empty">还没有关注的比赛</div>`;
  if(rows.length&&window.nativeCompetitionNotifications===false)host.insertAdjacentHTML('beforeend','<p class="timeline-status">系统通知未开启；更新会在刷新时显示，后台无法弹窗提醒。</p>');
  icons();
}
function renderCompetitions(){
  competitionLimit=15;
  $('#content').innerHTML=`<section class="competition-overview followed-overview"><div class="toolbar"><h2>近期关注的比赛</h2><button class="icon-button" id="timeline-refresh" aria-label="刷新赛事" title="刷新赛事">${icon('refresh-cw')}</button></div><div id="competition-timeline"></div></section><div class="view-tabs competition-view-tabs"><button data-competition-view="catalog" class="${competitionView==='catalog'?'active':''}">赛事目录</button><button data-competition-view="notices" class="${competitionView==='notices'?'active':''}">本校赛事通知</button></div><div id="competition-catalog-pane" ${competitionView==='catalog'?'':'hidden'}><div class="category-tabs competition-scopes"><button data-scope="all" class="${competitionScope==='all'?'active':''}">全部</button><button data-scope="school" class="${competitionScope==='school'?'active':''}">学校认定</button><button data-scope="national" class="${competitionScope==='national'?'active':''}">全国与国际</button><button id="follow-filter" class="${competitionFollowOnly?'active':''}">关注</button></div><div class="filters competition-filters"><label><span class="field-label">目录年份</span><select id="competition-year">${[2026,2025,2024].map(y=>`<option ${competitionYear===y?'selected':''}>${y}</option>`).join('')}</select></label><label><span class="field-label">类别</span><select id="competition-kind">${['全部','学科','体育'].map(x=>`<option ${competitionKind===x?'selected':''}>${x}</option>`).join('')}</select></label><label><span class="field-label">认定等级</span><select id="competition-grade">${availableCompetitionGrades().map(x=>`<option ${competitionGrade===x?'selected':''}>${x}</option>`).join('')}</select></label></div><label class="search">${icon('search')}<input id="competition-search" placeholder="比赛、主办方、学科" aria-label="搜索赛事" value="${esc(competitionQuery)}"></label><div class="toolbar"><h2>赛事目录</h2><span id="competition-count" class="count"></span><button class="text-button" id="recognition-info">认定与综测${icon('chevron-right')}</button></div><div id="competition-list"></div></div><section id="competition-notices-pane" class="competition-notices" ${competitionView==='notices'?'':'hidden'}><div class="toolbar"><h2>本校最新赛事通知</h2></div><p id="competition-health" class="sync-status"></p><div id="competition-notice-list"></div></section>`;
  document.querySelectorAll('[data-scope]').forEach(b=>b.onclick=()=>{competitionScope=b.dataset.scope;competitionGrade='全部';renderCompetitions();icons();});
  $('#competition-year').onchange=e=>{competitionYear=Number(e.target.value);renderCompetitions();icons();};
  $('#competition-kind').onchange=e=>{competitionKind=e.target.value;renderCompetitionList();};$('#competition-grade').onchange=e=>{competitionGrade=e.target.value;renderCompetitionList();};$('#competition-search').oninput=e=>{competitionQuery=e.target.value;renderCompetitionList();};
  $('#follow-filter').onclick=()=>{competitionFollowOnly=!competitionFollowOnly;renderCompetitions();icons();};
  $('#recognition-info').onclick=()=>sheet('认定与综测',`<p class="notice-excerpt">学校目录中的 A+ / A—F 是项目认定等级，不等于获奖级别。全国可参加的赛事也不一定列入学校认定目录。</p><div class="sources"><a href="https://www.qztc.edu.cn/jwc/2026/0722/c5501a294773/page.htm" target="_blank" rel="noopener">查看学校通知${icon('external-link')}</a></div>`);
  document.querySelectorAll('[data-competition-view]').forEach(b=>b.onclick=()=>{competitionView=b.dataset.competitionView;renderCompetitions();});
  renderCompetitionList();renderCompetitionNotices();renderCompetitionTimeline();academicTabs();
  $('#timeline-refresh').onclick=syncAcademicCompetitions;installInlineSelects($('#content'));
};
function renderCompetitionList(){
  if(competitionListRendering)return;
  competitionListRendering=true;
  updateCompetitionGrades();const rows=visibleCompetitions(),host=$('#competition-list');
  if(!host){competitionListRendering=false;return;}
  $('#competition-count').textContent=rows.length+' 项';competitionObserver?.disconnect();
  host.innerHTML=rows.length?'':'<div class="empty">暂无符合条件的赛事</div>';
  let displayed=0;
  const append=()=>{
    if(!host.isConnected)return;
    $('#competition-more')?.remove();
    const limit=Math.min(competitionLimit,rows.length);
    host.insertAdjacentHTML('beforeend',rows.slice(displayed,limit).map(c=>`<article class="competition-item"><button class="competition-main" data-competition="${c.id}"><span class="competition-tags"><span class="tag ${c.grade?'':'neutral'}">${esc(recognitionLabel(c))}</span><small>${c.kind} · ${c.year}</small>${typeof subscriptionLabel==='function'?`<span class="tag neutral subscription-tag">${subscriptionLabel(c)}</span>`:''}</span><strong>${esc(c.name)}</strong><small>${esc(c.organizer)}</small>${typeof competitionDateSummary==='function'?`<span class="competition-date-summary">${esc(competitionDateSummary(c).text)}</span>`:''}</button><button class="icon-button follow-button ${followed.includes(competitionKey(c))?'selected':''}" data-follow="${c.id}" aria-label="${followed.includes(competitionKey(c))?'取消关注':'关注'}" aria-pressed="${followed.includes(competitionKey(c))}">${icon('bookmark')}</button></article>`).join(''));
    displayed=limit;
    if(rows.length>displayed){
      host.insertAdjacentHTML('beforeend',`<div class="bottom-link competition-more" id="competition-more" role="status"><span>加载更多 · 还有 ${rows.length-displayed} 项</span>${icon('chevron-down')}</div>`);
      $('#competition-more').setAttribute('role','button');$('#competition-more').tabIndex=0;const loadMore=()=>{competitionLimit+=15;append();};$('#competition-more').onclick=loadMore;$('#competition-more').onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();loadMore();}};
    }
    icons(host);
    competitionListRendering=false;
  };
  append();
};
function eventForm(c){sheet('加入安排',`<p class="notice-excerpt">${esc(c.name)}</p><label class="field-label" for="event-date">日期</label><input id="event-date" class="sheet-input" type="date" required><div class="event-stage-row"><label class="field-label" for="event-stage">阶段</label><select id="event-stage" class="sheet-select"><option>报名截止</option><option>作品提交</option><option>比赛</option><option>自定提醒</option></select><input id="event-stage-note" class="sheet-input" placeholder="备注名称" hidden></div><label class="field-label" for="event-time">时间</label><input id="event-time" class="sheet-input" type="time" value="09:00"><p class="footnote compact">赛事日期尚未核实，此处保存你自己确认的日期。</p><button class="primary" id="save-calendar-event">保存安排</button>`);$('#event-stage').onchange=e=>{$('#event-stage-note').hidden=e.target.value!=='自定提醒';};$('#save-calendar-event').onclick=()=>{const date=$('#event-date');if(!date.reportValidity())return;const stage=$('#event-stage').value,note=$('#event-stage-note').value.trim();if(stage==='自定提醒'&&!note){$('#event-stage-note').hidden=false;$('#event-stage-note').focus();return;}calendarEvents.push({id:crypto.randomUUID(),key:competitionKey(c),title:c.name,stage:stage==='自定提醒'?note:stage,date:date.value,time:$('#event-time').value,timetable:false,kind:c.id?'competition':'activity',origin:'用户确认'});portalSave('calendar',calendarEvents);closeSheet();toast('已加入本地日历'+(calendarEvents.at(-1).timetable?'与课程活动':''));};};
function competitionDetail(id){
  let c=findCompetition(id);if(!c)return;c=enrichCompetition(c);const source=CompetitionCatalog.sources.find(s=>s.id===c.sourceId),profile=(window.CompetitionProfiles||[]).find(p=>p.match.some(m=>c.name.toLowerCase().includes(m.toLowerCase()))),j=c.jingsai,sections=j?.sections||{},versions=CompetitionCatalog.items.filter(x=>competitionKey(x)===competitionKey(c)&&x.grade).sort((a,b)=>b.year-a.year);
  const details=Object.entries(sections).filter(([k,v])=>v&&k!=='最新通知'&&!(k==='常见问题'&&j?.faqs?.length)).map(([k,v])=>`<h3 class="section-label">${esc(k)}</h3><p class="notice-excerpt compact">${esc(String(v).slice(0,5000))}${String(v).length>5000?'…':''}</p>`).join('')+(j?.faqs?.length?`<h3 class="section-label">常见问题</h3><div class="competition-faq">${j.faqs.slice(0,20).map(f=>`<details><summary>${esc(f.question)}</summary><p>${esc(String(f.answer||'该问题的答案暂未获取').slice(0,2000))}</p></details>`).join('')}</div>`:'');
  openCompetitionSheet(c.id,c.name,competitionPagesMarkup({
    overview:`<div class="competition-tags"><span class="tag">${esc(c.grade||'认定待核实')}</span><span class="tag neutral">${j?`${esc(j.academicYear)} 学年月历`:`${c.year} 年目录`}</span></div>${j?`<p class="notice-excerpt compact">${esc(c.content||j.summary||'')}</p>`:`<dl class="facts">${facts([['主办单位',c.organizer],['参赛范围',c.scope],['比赛内容',c.content||profile?.content||c.note||'目录未提供，待当届章程补充'],['能力方向',profile?.skills||'以当届章程为准'],['资格与组队',c.team||profile?.team||'以当届章程及校内选拔通知为准'],['举办地点','以当届通知为准'],['报名与费用','以当届通知为准']])}</dl>`}${details}`,
    timeline:`<h3 class="section-label">参考时间轴</h3>${timelineMarkup(c)}`,
    notices:`<p class="footnote compact">${source?`${esc(source.file)} · ${esc(c.sheet)} · 第 ${c.row} 行`:j?`竞赛通知网公开详情 · 采集于 ${esc(j.retrievedAt)}`:'全国赛事资料'}。日期或地点未公开时会明确标注。</p><div class="sources">${competitionSources(c,source,profile,j)}</div>`,
    history:`${competitionHistoryMarkup(c)}${versions.length?`<h3 class="section-label">学校认定记录</h3><div class="version-tags">${versions.map(x=>`<span>${x.year} · ${x.grade}</span>`).join('')}</div>`:''}`,
  })+`<div class="sheet-actions"><button class="primary" data-detail-follow="${c.id}">${followed.includes(competitionKey(c))?'取消关注':'关注赛事'}</button><button class="secondary" data-plan-event="${c.id}">加入安排</button></div>`);
};
function renderCalendar(){
  const months=Array.from({length:12},(_,i)=>({year:calendarYear+(i>=4?1:0),month:(i+8)%12+1})),all=allCalendarEvents();
  const current=months.find(x=>x.month===calendarMonth)||months[0],date=new Date(current.year,current.month-1,1),offset=(date.getDay()+6)%7,days=new Date(current.year,current.month,0).getDate();
  const monthEvents=(y,m)=>all.filter(e=>(!calendarFollowing||followed.includes(e.key)||e.id.startsWith('exam-'))&&e.date.startsWith(`${y}-${String(m).padStart(2,'0')}`));
  const events=monthEvents(current.year,current.month);
  $('#content').innerHTML=`<div class="calendar-year"><button id="year-prev" class="icon-button" title="上个月" aria-label="上个月">${icon('chevron-left')}</button><button id="calendar-year-toggle" class="calendar-year-toggle" aria-expanded="${calendarMonthsExpanded}">${calendarYear}—${calendarYear+1} 学年${icon(calendarMonthsExpanded?'chevron-up':'chevron-down')}</button><button id="year-next" class="icon-button" title="下个月" aria-label="下个月">${icon('chevron-right')}</button></div><div class="academic-months" ${calendarMonthsExpanded?'':'hidden'}>${months.map(x=>`<button data-month="${x.month}" class="${x.month===calendarMonth?'active':''}"><small>${x.month===9||x.month===1?x.year:''}</small><strong>${x.month}月 · ${monthEvents(x.year,x.month).length} 项</strong></button>`).join('')}</div>${calendarToolsMarkup()}<div class="calendar-heading"><h3>${current.year} 年 ${current.month} 月 · ${events.length} 项</h3><label><input id="calendar-followed" type="checkbox" ${calendarFollowing?'checked':''}>只看关注</label></div><div class="month-grid"><div class="calendar-weekdays">${['一','二','三','四','五','六','日'].map(d=>`<span>${d}</span>`).join('')}</div><div class="calendar-days">${Array.from({length:offset},()=>'<span></span>').join('')}${Array.from({length:days},(_,i)=>{const ds=`${current.year}-${String(current.month).padStart(2,'0')}-${String(i+1).padStart(2,'0')}`,has=events.some(e=>e.date===ds);return `<button data-day="${ds}" class="${has?'has-event':''}" aria-label="${ds}${has?' 有安排':''}">${i+1}${has?'<i></i>':''}</button>`;}).join('')}</div></div><div class="toolbar"><h2>本月安排</h2><span class="count">${events.length} 项</span><button class="icon-button" id="calendar-add" title="添加安排" aria-label="添加安排">${icon('plus')}</button></div><div id="calendar-agenda">${agendaRows(events)}</div>`;
  $('#year-prev').onclick=()=>shiftCalendarMonth(-1);$('#year-next').onclick=()=>shiftCalendarMonth(1);$('#calendar-year-toggle').onclick=()=>{calendarMonthsExpanded=!calendarMonthsExpanded;renderCalendar();icons();};document.querySelectorAll('[data-month]').forEach(b=>b.onclick=()=>{calendarMonth=Number(b.dataset.month);portalSave('calendar-month',calendarMonth);renderCalendar();icons();});$('#calendar-followed').onchange=e=>{calendarFollowing=e.target.checked;renderCalendar();icons();};$('#calendar-add').onclick=()=>{sheet('添加安排','<label class="field-label">名称</label><input id="custom-event-name" class="sheet-input" maxlength="100"><button class="primary" id="custom-event-next">选择时间</button>');$('#custom-event-next').onclick=()=>{const name=$('#custom-event-name').value.trim();if(name)eventForm({name});};};updateCalendarExport();academicTabs();document.querySelectorAll('[data-day]').forEach(b=>b.onclick=()=>{document.querySelectorAll('[data-day]').forEach(x=>x.classList.toggle('selected',x===b));$('#calendar-agenda').innerHTML=agendaRows(events.filter(e=>e.date===b.dataset.day));icons();});
};
