'use strict';
const $ = selector => document.querySelector(selector);
// Convert new placeholders only: existing SVGs keep their identity and paint layer.
const icons = (root = document) => {
  if (!window.lucide) return;
  root.querySelectorAll('i[data-lucide]').forEach(node => {
    const name = node.dataset.lucide;
    const key = name.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('');
    const definition = lucide.icons[key];
    if (!definition) return;
    const [tag, attributes, children] = definition;
    const inherited = Object.fromEntries([...node.attributes].map(attribute => [attribute.name, attribute.value]));
    const classes = ['lucide', `lucide-${name}`, node.className].filter(Boolean).join(' ');
    node.replaceWith(lucide.createElement([tag, {...attributes, ...inherited, class: classes}, children]));
  });
};
const icon = name => name==='event-repeat'
  ? '<svg viewBox="0 0 24 24" aria-hidden="true" fill="currentColor"><path d="M21,12V6c0-1.1-0.9-2-2-2h-1V2h-2v2H8V2H6v2H5C3.9,4,3,4.9,3,6v14c0,1.1,0.9,2,2,2h7v-2H5V10h14v2H21z M19,8H5V6h14V8z M15.64,20c0.43,1.45,1.77,2.5,3.36,2.5c1.93,0,3.5-1.57,3.5-3.5s-1.57-3.5-3.5-3.5c-0.95,0-1.82,0.38-2.45,1l1.45,0V18h-4v-4h1.5l0,1.43C16.4,14.55,17.64,14,19,14c2.76,0,5,2.24,5,5s-2.24,5-5,5c-2.42,0-4.44-1.72-4.9-4L15.64,20z"/></svg>'
  : `<i data-lucide="${name}"></i>`;
const esc = value => String(value).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const groups = [
  {id:'practice',name:'集中实践课程',attr:'必修',required:32,earned:12,courses:8,passed:4,ok:false,items:[['毕业实习',6,'未修读'],['毕业设计',10,'未修读'],['专业综合实践',4,'修读中']]},
  {id:'quality',name:'素质拓展课程',attr:'必修',required:0,earned:0,courses:8,passed:6,ok:false,items:[['创新创业实践',0,'未完成'],['劳动教育实践',0,'修读中']]},
  {id:'general',name:'通识课程',attr:'必修',required:36,earned:36,courses:20,passed:20,ok:true,items:[['大学英语',4,'已通过'],['高等数学',5,'已通过']]},
  {id:'foundation',name:'学科基础课程',attr:'必修',required:34,earned:34,courses:12,passed:12,ok:true,items:[['程序设计基础',4,'已通过'],['离散数学',3,'已通过']]},
  {id:'major',name:'专业核心课程',attr:'必修',required:26,earned:22,courses:10,passed:9,ok:false,items:[['软件工程综合实验',4,'修读中']]},
  {id:'limited',name:'专业拓展课程',attr:'限选',required:16,earned:16.5,courses:6,passed:7,ok:true,items:[['移动应用开发',3,'已通过'],['人工智能基础',3.5,'已通过']]},
  {id:'elective',name:'专业任选课程',attr:'任选',required:8,earned:8,courses:3,passed:3,ok:true,items:[['人机交互',3,'已通过'],['开源软件实践',2,'已通过']]},
  {id:'cross',name:'跨学科课程',attr:'任选',required:7,earned:7,courses:null,passed:4,ok:true,items:[['音乐欣赏',2,'已通过'],['生活中的经济学',2,'已通过'],['数字摄影',1,'已通过'],['科学与生活（线上）',2,'已通过']]},
  {id:'art',name:'艺术类校选课',attr:'任选',required:2,earned:2,courses:1,passed:1,ok:true,items:[['音乐欣赏',2,'已通过']]}
];
const examSources = {
  cet:{name:'教育部教育考试院',url:'https://cet.neea.edu.cn/html1/report/2609/1-1.htm'},
  ncre:{name:'教育部教育考试院',url:'https://ncre.neea.edu.cn/html1/report/2606/16-1.htm'},
  fj:{name:'福建省教育考试院',url:'https://www.eeafj.cn/fxlks/'},
  school:{name:'泉师教务处',url:'https://www.qztc.edu.cn/jwc/ksbm/list.htm'},
  psc:{name:'泉师教务处',url:'https://www.qztc.edu.cn/jwc/2026/0921/c7784a295587/page.htm'},
  cltt:{name:'普通话在线报名系统',url:'https://bm.cltt.org/'},
  software:{name:'中国计算机技术职业资格网',url:'https://www.ruankao.org.cn/article/content/2609211354254868000700041.html'},
  teacher:{name:'教育部教育考试院',url:'https://ntce.neea.edu.cn/'},
  accounting:{name:'财政部会计财务评价中心',url:'https://kzp.mof.gov.cn/'},
  cpa:{name:'中国注册会计师协会',url:'https://cpaexam.cicpa.org.cn/'},
  catti:{name:'全国翻译专业资格考试网',url:'https://www.catticenter.com/'},
  ielts:{name:'教育部教育考试院雅思报名网',url:'https://ielts.neea.cn/'},
  toefl:{name:'教育部教育考试院托福报名网',url:'https://toefl.neea.cn/'}
};
const exams = [
  {id:'psc',type:'普通话',title:'普通话水平测试',symbol:'mic',level:'本校',area:'泉州',date:'2026 年 10—11 月',time:'具体日期另行通知',registration:'9 月 22 日—9 月 27 日',source:'psc',state:'待公布',start:null,upcoming:true,detail:'本校大二及以上在校学生可报名。报名已结束，具体测试日程待学校通知。',region:'福建 · 泉州师范学院'},
  {id:'set4',type:'四六级',title:'英语四级口语 CET-SET4',symbol:'languages',level:'全国',area:'全国',date:'11 月 21 日 · 周六',time:'具体场次见准考证',print:'11 月 17 日 09:00 起',source:'cet',state:'待考试',start:'2026-11-21',upcoming:true,region:'全国统一日期'},
  {id:'set6',type:'四六级',title:'英语六级口语 CET-SET6',symbol:'languages',level:'全国',area:'全国',date:'11 月 22 日 · 周日',time:'具体场次见准考证',print:'11 月 17 日 09:00 起',source:'cet',state:'待考试',start:'2026-11-22',upcoming:true,region:'全国统一日期'},
  {id:'cet4',type:'四六级',title:'英语四级 CET4',symbol:'languages',level:'全国',area:'全国',date:'12 月 12 日 · 周六',time:'09:00—11:20',print:'12 月 1 日 09:00 起',source:'cet',state:'待考试',start:'2026-12-12',upcoming:true,region:'全国统一日期'},
  {id:'cet6',type:'四六级',title:'英语六级 CET6',symbol:'languages',level:'全国',area:'全国',date:'12 月 12 日 · 周六',time:'15:00—17:25',print:'12 月 1 日 09:00 起',source:'cet',state:'待考试',start:'2026-12-12',upcoming:true,region:'全国统一日期'},
  {id:'ncre',type:'计算机等级',title:'全国计算机等级考试 NCRE',symbol:'monitor',level:'福建',area:'福建',date:'9 月 19 日—9 月 21 日',time:'个人考试时间以准考证为准',registration:'福建：6 月 25 日 09:00—7 月 15 日 24:00',source:'ncre',state:'已结束',start:'2026-09-19',upcoming:false,detail:'此处为首轮报名时间；补报名以福建省教育考试院通告为准。考试场次与考场须查看个人准考证。',region:'福建省（泉州适用）'}
];
exams.push(
  {id:'software',type:'软考',title:'计算机技术与软件专业技术资格（软考）',symbol:'monitor',level:'全国',area:'全国',date:'10 月 24 日—10 月 25 日',time:'具体资格与批次以准考证为准',source:'software',state:'待考试',start:'2026-10-24',upcoming:true,detail:'常见资格：程序员、软件设计师、网络工程师、信息安全工程师。下半年考试采用机考，官方批次安排已公布。'},
  {id:'teacher',type:'教师资格',title:'中小学教师资格考试',symbol:'graduation-cap',level:'全国',area:'全国',date:'以当期公告为准',time:'笔试、面试分别报名',source:'teacher',state:'待核实',start:null,upcoming:true,detail:'可查询幼儿园、小学、初中和高中学段。福建在校生报名年级及专业条件以省级公告为准。'},
  {id:'accounting',type:'财会',title:'初级会计专业技术资格考试',symbol:'calculator',level:'全国',area:'全国',date:'新一期日程待核实',time:'初级会计实务、经济法基础',source:'accounting',state:'待公布',start:null,upcoming:true,detail:'适合关注财会方向的学生，报名与考试安排以财政部及福建省公告为准。'},
  {id:'cpa',type:'财会',title:'注册会计师全国统一考试 CPA',symbol:'calculator',level:'全国',area:'全国',date:'新一期日程待核实',time:'专业阶段、综合阶段',source:'cpa',state:'待公布',start:null,upcoming:true,detail:'报考资格、应届毕业生学历审核及各阶段科目安排见当年报名简章。'},
  {id:'catti',type:'翻译',title:'翻译专业资格（水平）考试 CATTI',symbol:'languages',level:'全国',area:'全国',date:'新一期日程待核实',time:'口译、笔译分级报考',source:'catti',state:'待公布',start:null,upcoming:true,detail:'常见报考方向为二、三级笔译或口译，语种、报名安排及考区以官方公告为准。'},
  {id:'ielts',type:'留学语言',title:'雅思 IELTS',symbol:'languages',level:'全国',area:'全国',date:'按考点选择场次',time:'日期与余位见官方报名系统',source:'ielts',state:'按场次报名',start:null,upcoming:true,detail:'可按留学或其他申请要求选择考试类型和考点。'},
  {id:'toefl',type:'留学语言',title:'托福 TOEFL iBT',symbol:'languages',level:'全国',area:'全国',date:'按考点选择场次',time:'日期与余位见官方报名系统',source:'toefl',state:'按场次报名',start:null,upcoming:true,detail:'可在官方报名系统核对开放场次、考点与考试要求。'}
);
let saved = {};
try { saved = JSON.parse(localStorage.getItem('qnu-academic-preview-reminders') || '{}'); } catch (_) {}
const pageFromHash = () => ['personal','credits','exams','competitions','calendar','plugins','settings','grades','arrangement','homework'].includes(location.hash.slice(1)) ? location.hash.slice(1) : 'personal';
let page = pageFromHash();
let groupStatus = 'all', groupType = 'all', groupQuery = '', examRegion = '泉州', examPeriod = 'upcoming', examType = '全部', gradeFilterField = '全部', gradeFilterValue = '全部';
let toastTimer;
let electiveInput={offline:5,online:2,art:2,firstCredits:4,maxCourses:3,artTwo:true,normalTwo:true,allowOnline:false,normalOffline:true,artOffline:true};
const noticesEndpoint = 'https://huanmen.com.cn/qsschedule/api/exam-notices';
let noticeFeed = window.BundledExamNotices?.schemaVersion === 1 ? window.BundledExamNotices : null, syncAt = null;
try { const cache = JSON.parse(localStorage.getItem('qnu-exam-notices') || 'null'); if (cache?.feed?.schemaVersion === 1 && (!noticeFeed || Date.parse(cache.feed.checkedAt) >= Date.parse(noticeFeed.checkedAt))) { noticeFeed=cache.feed;syncAt=cache.syncAt; } } catch (_) {}
function toast(message) { $('#toast').textContent=message; $('#toast').classList.add('visible'); clearTimeout(toastTimer); toastTimer=setTimeout(()=>$('#toast').classList.remove('visible'),2600); }
const sheetStack=[];
function sheet(title, html, competition = false) {
  document.querySelector('.calendar-management-eye')?.remove();
  if (!competition && $('#sheet').open && $('#sheet-title').textContent !== title) {
    sheetStack.push({title:$('#sheet-title').textContent,nodes:[...$('#sheet-content').childNodes],footer:$('#sheet-footer'),tabs:$('#competition-detail-tabs'),scroll:$('#sheet-content').scrollTop,competition:competitionPresentation});
  }
  if (!competition) resetCompetitionPresentation();
  $('#sheet-title').textContent=title; $('#sheet-content').innerHTML=html;
  $('#sheet-content').scrollTop=0;
  $('#sheet-footer')?.remove();
  $('#competition-detail-tabs')?.remove();
  const actions=$('#sheet-content .sheet-actions');
  if(actions){actions.id='sheet-footer';$('#sheet').append(actions);}
  document.documentElement.classList.add('sheet-open');
  window.resizeAcademicSheet?.();
  if (!$('#sheet').open) $('#sheet').showModal(); icons(); installInlineSelects($('#sheet-content'));
}
function closeSheet() {
  if (sheetStack.length) {
    const previous=sheetStack.pop(); competitionPresentation=previous.competition;
    $('#sheet-footer')?.remove();$('#competition-detail-tabs')?.remove();
    $('#sheet-title').textContent=previous.title; $('#sheet-content').replaceChildren(...previous.nodes);
    if(previous.footer)$('#sheet').append(previous.footer);
    if(previous.tabs)$('#sheet-content').before(previous.tabs);
    $('#sheet-content').scrollTop=previous.scroll||0;
    document.documentElement.classList.add('sheet-open'); applyCompetitionPresentation(); icons();
    $('#sheet-content').scrollTop=previous.scroll||0; return;
  }
  $('#sheet').close(); resetCompetitionPresentation();
}
function updateNav() { document.querySelectorAll('[data-page]').forEach(button=>{button.classList.toggle('active',button.dataset.page===page);button.setAttribute('aria-current',button.dataset.page===page?'page':'false');}); $('#page-title').textContent=({personal:'我的',credits:'学习情况',exams:'考证安排',competitions:'赛事与考证',calendar:'学年日历',plugins:'插件市场',settings:'设置',grades:'我的成绩',arrangement:'课程安排',homework:'作业查询'})[page]; $('#scope-label').textContent=page==='credits'?'学分':''; }
const studyViews = new Map();
let renderedStudyPage = null;
function render(refreshStudy = false) {
  if(renderedStudyPage && !(refreshStudy && renderedStudyPage===page))studyViews.set(renderedStudyPage,[...$('#content').childNodes]);
  $('#top-settings')?.remove();$('#top-announcements')?.remove();
  updateNav();
  $('.bottom-nav').hidden=page==='settings';
  if(!refreshStudy && studyViews.has(page))$('#content').replaceChildren(...studyViews.get(page));
  else if(page==='credits'){renderCredits();renderCreditAdvice();}
  else if(['competitions','exams','calendar'].includes(page))renderAcademicArea(page);
  else renderPortal(page);
  renderedStudyPage=['grades','credits'].includes(page)?page:null;
  updateCalendarExport();installInlineSelects($('#content'));
  $('#back').hidden=page==='personal'||page==='calendar';$('#refresh').hidden=page==='personal'||page==='calendar';
  if(page==='personal'){
    $('#refresh').insertAdjacentHTML('afterend',`<button id="top-settings" class="icon-button" data-go="settings" aria-label="设置">${icon('settings')}</button>`);
    $('#page-title').insertAdjacentHTML('beforebegin',`<button id="top-announcements" class="icon-button" data-go="about" aria-label="公告">${icon('bell')}</button>`);
  }
  $('.topbar').classList.toggle('personal-topbar',page==='personal');icons();
}
function renderCredits() {
  $('#content').innerHTML=`<div class="view-tabs" aria-label="学习情况"><button id="grade-tab">成绩</button><button class="active" aria-current="page">学分</button></div>
  <section class="summary"><div class="summary-top"><div class="ring" style="--progress:85.9375" aria-label="总学分进度 85.9%"><div class="ring-inner"><strong>137.5</strong><small>已获学分</small></div></div><div class="summary-copy"><h2>3 个课组待完成</h2><p>集中实践尚差 20 学分</p><p>专业核心尚差 4 学分 · 素质拓展尚差 2 门</p><button class="summary-action" id="elective-advice">选修课建议${icon('arrow-right')}</button></div></div><div class="stats"><div class="stat"><b>22.5</b><span>尚差总学分</span></div><div class="stat"><b>6 / 9</b><span>已达标课组</span></div><div class="stat"><b>85.9<span style="display:inline;font-size:11px;color:inherit">%</span></b><span>总学分进度</span></div></div></section>
  <div class="toolbar"><h2>课组进度<span class="count">9 个课组</span></h2><button class="text-button" id="plan-info">教学计划${icon('chevron-right')}</button></div>
  <div class="filters"><label><span class="field-label">完成情况</span><select id="group-status"><option value="all">全部课组</option><option value="pending">未达标</option><option value="done">已达标</option></select></label><label><span class="field-label">课程属性</span><select id="group-type"><option value="all">全部属性</option><option>必修</option><option>限选</option><option>任选</option></select></label></div>
  <label class="search">${icon('search')}<input id="group-search" aria-label="搜索课组或课程" placeholder="搜索课组或课程" value="${esc(groupQuery)}"></label><div class="group-list" id="group-list"></div><p class="footnote">修读进程 · 合成示例<br>课组须同时满足学分与门数要求；超出的学分不自动抵扣其他课组。</p>`;
  $('#group-status').value=groupStatus; $('#group-type').value=groupType;
  $('#group-status').onchange=event=>{groupStatus=event.target.value;renderGroups();};
  $('#group-type').onchange=event=>{groupType=event.target.value;renderGroups();};
  $('#group-search').oninput=event=>{groupQuery=event.target.value;renderGroups();};
  $('#grade-tab').onclick=()=>{location.hash='grades';};
  $('#plan-info').onclick=()=>sheet('教学计划',`<p class="sheet-text">合成示例 · 官方要求与修读进程分开保留。</p><dl class="facts">${facts([['教学计划','专业教学计划'],['要求总学分','160'],['已获总学分','137.5'],['课组数','9'],['是否合格','未通过'],['专业方向计入','以学校审查规则为准'],['课组外任选课','以学校审查规则为准']])}</dl><button class="primary" data-close>完成</button>`);
  renderGroups();
}
function facts(rows) { return rows.map(([key,value])=>`<div class="fact-row"><dt>${esc(key)}</dt><dd>${esc(value)}</dd></div>`).join(''); }
const electiveScenarios = [
  {name:'还没修选修课',offline:0,online:0,art:0},
  {name:'先修满 4 分线上课',offline:0,online:4,art:0},
  {name:'已修 4 分，其中艺术 2 分',offline:4,online:0,art:2},
  {name:'已修 5 分，艺术还差 1 分',offline:3,online:2,art:1},
  {name:'已修 6 分，没修艺术',offline:4,online:2,art:0},
  {name:'已修 7 分，艺术还差 1 分',offline:5,online:2,art:1},
  {name:'已修 7 分，没修艺术',offline:3,online:4,art:0},
  {name:'已修 8 分，没修艺术',offline:4,online:4,art:0},
  {name:'线上已修 6 分，超出不计',offline:1,online:6,art:1},
  {name:'艺术只有 1 分课可选',offline:0,online:0,art:0,artTwo:false},
  {name:'所有可选课程都只有 1 分',offline:0,online:0,art:0,artTwo:false,normalTwo:false},
  {name:'下次仅剩 1 分额度，分期补修',offline:3,online:0,art:0,firstCredits:1},
  {name:'线下没合适时间，搭配线上',offline:3,online:0,art:2,normalOffline:false,allowOnline:true},
  {name:'线上额度满，线下也冲突',offline:0,online:4,art:0,normalOffline:false,artOffline:false,allowOnline:true},
  {name:'总学分和艺术类均已完成',offline:5,online:2,art:2}
];
function electiveValues(input=electiveInput) { return {...input,total:input.offline+Math.min(input.online,4)}; }
function renderElectives() {
  $('#content').innerHTML=`<section class="advice-intro"><h2>补齐选修学分</h2><div class="rule-stats"><span><b>7–8</b>总学分</span><span><b>2</b>含艺术类</span><span><b>≤4</b>线上计入</span><span><b>≤4</b>每学期</span></div></section>
  <div class="toolbar"><h2>我的情况</h2><span class="count">合成示例</span></div><label class="field-label" for="elective-scenario">情况对照</label><select class="sheet-select" id="elective-scenario"><option value="custom">按下方情况计算</option>${electiveScenarios.map((s,i)=>`<option value="${i}">${s.name}</option>`).join('')}</select>
  <div class="planner-fields"><label><span>线下已获学分</span><input id="elective-offline" type="number" min="0" max="12" step="1" value="${electiveInput.offline}"></label><label><span>线上已获学分</span><input id="elective-online" type="number" min="0" max="20" step="1" value="${electiveInput.online}"></label><label><span>其中艺术类学分</span><input id="elective-art" type="number" min="0" max="12" step="1" value="${electiveInput.art}"></label><label><span>下次可选学分额度</span><select id="elective-firstCredits">${[4,3,2,1,0].map(n=>`<option value="${n}" ${electiveInput.firstCredits===n?'selected':''}>${n} 分</option>`).join('')}</select></label></div><p class="footnote compact">线上线下结合计入线下；艺术学分已包含在总学分内。未通过、尚未取得的学分不计入。</p>
  <details class="planner-options"><summary>可选课程与时间${icon('chevron-down')}</summary>${[['artTwo','有 2 学分艺术课'],['normalTwo','有 2 学分其他选修课'],['artOffline','艺术课有合适时间'],['normalOffline','其他线下课有合适时间'],['allowOnline','也考虑线上课']].map(([key,label])=>`<label class="option"><span>${label}</span><input type="checkbox" id="elective-${key}" ${electiveInput[key]?'checked':''}></label>`).join('')}<label class="option"><span>每学期建议最多门数</span><select id="elective-maxCourses"><option value="2" ${electiveInput.maxCourses===2?'selected':''}>2 门</option><option value="3" ${electiveInput.maxCourses===3?'selected':''}>3 门</option></select></label><p class="footnote compact">建议门数用于分学期安排，不是学校限选门数。选课集中在周二、周四，最终按具体节次排除冲突。</p></details>
  <div id="elective-result" aria-live="polite"></div>
  <section class="strategy-section"><div class="toolbar"><h2>各种情况怎么选</h2></div><div class="category-tabs" id="strategy-tabs">${[['matrix','学分搭配'],['special','特殊情况']].map(([key,name])=>`<button data-strategy="${key}" class="${key==='matrix'?'active':''}">${name}</button>`).join('')}</div><div id="strategy-matrix"><label class="matrix-filter"><span>已计入的线上学分</span><select id="matrix-online">${[0,1,2,3,4].map(n=>`<option value="${n}">${n} 分</option>`).join('')}</select></label><div id="matrix-content"></div></div><div id="strategy-special" hidden></div></section>
  <details class="planner-options"><summary>学分计入与时间安排${icon('chevron-down')}</summary><ul class="rule-list"><li>总学分 7 分起、最多 8 分；艺术类至少 2 分已包含在其中。</li><li>线上总共最多计入 4 分；已修 6 分线上课，也只按 4 分算。</li><li>每学期最多 4 学分，不限选课门数。建议优先选 2 学分课，减少门数。</li><li>同样门数先选能较早完成的组合，再减少多修的总学分、艺术学分，最后保留线上额度。</li><li>艺术类缺口优先补。找不到 2 分艺术课时，可分开选两门 1 分课；若只有艺术类提供 2 分课，也可用于减少选课门数。</li><li>有时间冲突或没选上课，下学期接着补，不限定两学期修完。艺术课在线上能否计入艺术类别，需要核对课程分类；这里新增艺术课按线下推荐。</li></ul></details>`;
  $('#elective-scenario').onchange=event=>{if(event.target.value!=='custom')applyElectiveScenario(Number(event.target.value));};
  document.querySelectorAll('[id^="elective-"]').forEach(control=>{
    if(control.id==='elective-scenario'||control.id==='elective-result')return;
    control.onchange=()=>{const key=control.id.slice(9);electiveInput[key]=control.type==='checkbox'?control.checked:Number(control.value);$('#elective-scenario').value='custom';renderElectiveResult();};
  });
  document.querySelectorAll('[data-strategy]').forEach(button=>button.onclick=()=>{const matrix=button.dataset.strategy==='matrix';$('#strategy-matrix').hidden=!matrix;$('#strategy-special').hidden=matrix;document.querySelectorAll('[data-strategy]').forEach(b=>b.classList.toggle('active',b===button));});
  $('#matrix-online').onchange=renderStrategyMatrix;
  renderElectiveResult();renderStrategyMatrix();renderSpecialStrategies();
}
function applyElectiveScenario(index) {
  const scenario=electiveScenarios[index];
  electiveInput={offline:0,online:0,art:0,firstCredits:4,maxCourses:3,artTwo:true,normalTwo:true,allowOnline:false,normalOffline:true,artOffline:true,...scenario};
  renderElectives();$('#elective-scenario').value=String(index);icons();
}
function renderElectiveResult() {
  const value=electiveValues(),result=ElectivePlanner.plan(value),ignored=Math.max(0,value.online-4);
  $('#elective-result').innerHTML=`<div class="advice-progress"><span>计入总学分 <b>${value.total} / 7</b></span><span>其中艺术 <b>${value.art} / 2</b></span></div>${ignored?`<p class="advice-warning">线上超出的 ${ignored} 学分不计入，不能用来抵扣缺口。</p>`:''}${result.state==='plan'?`<div class="recommendation"><h3>再选 ${result.count} 门 · ${result.added} 学分</h3><p>${ElectivePlanner.combination(result.courses)}</p><small>完成后总计 ${result.finalTotal} 学分，其中艺术类 ${result.finalArt} 学分</small></div><ol class="semester-plan">${result.semesters.map(bin=>`<li><div><b>${bin.offset===0?'下次选课':bin.offset===1?'再下一学期':`之后第 ${bin.offset+1} 个学期`}</b><span>${bin.credits} 分 · ${bin.items.length} 门</span></div><p>${ElectivePlanner.combination(bin.items)}</p></li>`).join('')}</ol>${value.firstCredits===0?'<p class="footnote compact">下次额度已用完，从再下一学期开始补选。</p>':''}`:`<div class="recommendation ${result.state==='done'?'':'needs-check'}"><h3>${result.state==='done'?'选修课已修够':result.state==='invalid'?'请核对学分':result.state==='blocked'?'留到下学期补选':'需要确认认定方式'}</h3><p>${esc(result.message)}</p></div>`}<p class="footnote compact">组合只规划学分与门数，不代表学校下次一定开设这些课。遇到时间冲突可改用线上剩余额度、拆成 1 分课或顺延；拆课后须重新检查每学期 4 分上限。</p>`;
}
function strategyOutcome(result) {
  return result.state==='plan'?`${result.count} 门 · ${result.semesters.length} 学期`:result.state==='done'?'已完成':result.state==='blocked'?'顺延补修':'先核对认定';
}
function renderStrategyMatrix() {
  const online=Number($('#matrix-online').value),rows=[];
  for(let total=online;total<=8;total++)for(let art=0;art<=Math.min(2,total);art++) {
    const result=ElectivePlanner.plan({total,art,online});
    rows.push(`<tr><td><b>${total} 分</b><small>艺术 ${art} 分</small></td><td>${result.state==='plan'?ElectivePlanner.combination(result.courses):result.state==='done'?'不用补选':esc(result.message)}</td><td><button class="text-button" data-matrix-total="${total}" data-matrix-art="${art}" data-matrix-online="${online}">${strategyOutcome(result)}${icon('chevron-right')}</button></td></tr>`);
  }
  $('#matrix-content').innerHTML=`<p class="footnote compact">艺术是总学分的一部分。此表按有 1/2 分线下课、每学期 4 分额度计算；可查看每种组合的分期安排。</p><table class="strategy-table"><thead><tr><th>已获学分</th><th>最少门数的补修组合</th><th>安排</th></tr></thead><tbody>${rows.join('')}</tbody></table>`;
  document.querySelectorAll('[data-matrix-total]').forEach(button=>button.onclick=()=>{electiveInput={offline:Number(button.dataset.matrixTotal)-online,online,art:Number(button.dataset.matrixArt),firstCredits:4,maxCourses:3,artTwo:true,normalTwo:true,allowOnline:false,normalOffline:true,artOffline:true};renderElectives();icons();$('#elective-result').scrollIntoView({block:'center'});});
  icons();
}
function renderSpecialStrategies() {
  $('#strategy-special').innerHTML=electiveScenarios.map((scenario,index)=>{const input={firstCredits:4,maxCourses:3,artTwo:true,normalTwo:true,normalOffline:true,allowOnline:false,...scenario},result=ElectivePlanner.plan(electiveValues(input));return `<button class="strategy-case" data-case="${index}"><strong>${scenario.name}</strong><span>${result.state==='plan'?ElectivePlanner.combination(result.courses):esc(result.message)}</span><small>${strategyOutcome(result)}${icon('chevron-right')}</small></button>`;}).join('');
  document.querySelectorAll('[data-case]').forEach(button=>button.onclick=()=>{applyElectiveScenario(Number(button.dataset.case));$('#elective-result').scrollIntoView({block:'center'});});
}
function renderGroups() {
  const selected=groups.filter(g=>(groupStatus==='all'||(groupStatus==='done'?g.ok:!g.ok))&&(groupType==='all'||g.attr===groupType)&&(!groupQuery||g.name.includes(groupQuery)||g.items.some(c=>c[0].includes(groupQuery))));
  $('#group-list').innerHTML=selected.length?selected.map(g=>`<details class="group ${g.ok?'':'pending'}" data-group="${g.id}"><summary><div class="group-title"><h3>${g.name}<span style="font-size:10px;color:var(--mut);font-weight:400;margin-left:7px">${g.attr}</span></h3><span class="tag ${g.ok?'':'pending'}">${g.ok?'已达标':'未达标'}</span>${icon('chevron-right')}</div><div class="group-metrics"><span><b>${g.earned}</b> / ${g.required} 学分</span><span>${g.courses===null?`已通过 <b>${g.passed}</b> 门`:`<b>${g.passed}</b> / ${g.courses} 门`}</span></div><div class="meter"><span style="width:${g.required?Math.min(100,g.earned/g.required*100):g.passed/g.courses*100}%"></span></div></summary><div class="group-body"><div class="course-list">${g.items.map(c=>`<div class="course-row"><span class="name">${c[0]}</span><span class="credit">${c[1]} 学分</span><span class="tag ${c[2]==='已通过'?'':'pending'}">${c[2]==='已通过'?'已及格':(c[2]==='已选'||c[2]==='修读中')?'已选课程成绩未知':(c[2]==='未修'||c[2]==='未修读')?'未选课':c[2]}</span></div>`).join('')}</div><div class="group-foot"><small>课程为合成示例</small><button class="text-button" data-fields="${g.id}">详情${icon('chevron-right')}</button></div></div></details>`).join(''):`<div class="empty">${icon('search')}没有符合条件的课组</div>`;
  $('#content .toolbar .count').textContent=`${selected.length} 个课组`;
  icons();
}
function renderExams() {
  const upcomingSaved=exams.filter(e=>saved[e.id]&&e.upcoming);
  const nextSaved=upcomingSaved.find(e=>e.start);
  const next=nextSaved||exams.find(e=>e.start&&e.upcoming);
  const days=Math.round((new Date(next.start+'T00:00:00+08:00')-new Date('2026-10-09T00:00:00+08:00'))/86400000);
  $('#content').innerHTML=`<section class="exam-summary"><div class="preview-card-head"><h2>${nextSaved?'下一场已关注考试':'近期考试'}</h2><button id="exam-refresh" class="icon-button" aria-label="刷新考试" title="刷新考试">${icon('refresh-cw')}</button></div><div class="next-exam"><div><h2>${next.title}</h2><p>${next.date} · ${next.time}</p></div><div class="countdown"><strong>${days}</strong><small>天</small><span>距考试日期</span></div></div></section>
    <div class="toolbar"><h2>考试日程</h2><button class="text-button" id="show-sources">来源${icon('external-link')}</button></div>
    <div class="filters"><label><span class="field-label">适用地区</span><select id="exam-region"><option>泉州</option><option>福建</option><option>全国</option></select></label><label><span class="field-label">查看范围</span><select id="exam-period"><option value="upcoming">近期与待公布</option><option value="saved">已关注</option><option value="all">全部（含往期）</option></select></label></div>
    <div class="category-tabs" aria-label="考试分类">${['全部',...new Set(exams.map(e=>e.type))].map(type=>`<button data-exam-type="${type}" class="${type===examType?'active':''}" aria-pressed="${type===examType}">${type}</button>`).join('')}</div><div class="exam-list" id="exam-list"></div><section class="notice-section" aria-label="官方通知"><div class="toolbar"><h2>官方通知</h2><button class="text-button" id="notice-health">同步状态${icon('chevron-right')}</button></div><p class="sync-status" id="notice-status"></p><div id="notice-list"></div></section><button class="bottom-link" id="station-link"><span>普通话测试站 · 福建 / 泉州</span>${icon('chevron-right')}</button><p class="footnote">已核实的日程附官方来源，未确认的日期显示待核实。<br>个人场次、考场和报名资格以准考证及考点通知为准。</p>`;
  $('#exam-region').value=examRegion; $('#exam-period').value=examPeriod;
  $('#exam-region').onchange=event=>{examRegion=event.target.value;renderExamList();};
  $('#exam-period').onchange=event=>{examPeriod=event.target.value;renderExamList();};
  document.querySelectorAll('[data-exam-type]').forEach(button=>button.onclick=()=>{examType=button.dataset.examType;if(examPeriod==='upcoming'&&examType!=='全部'&&!exams.some(e=>e.type===examType&&e.upcoming)){examPeriod='all';$('#exam-period').value=examPeriod;}document.querySelectorAll('[data-exam-type]').forEach(b=>{b.classList.toggle('active',b.dataset.examType===examType);b.setAttribute('aria-pressed',b.dataset.examType===examType);});renderExamList();});
  $('#show-sources').onclick=showSources;
  $('#exam-refresh').onclick=syncNotices;
  $('#notice-health').onclick=showNoticeHealth;
  $('#station-link').onclick=()=>sheet('普通话测试计划',`<p class="sheet-text">普通话由测试站分批安排。本校 2026 年 10—11 月的测试日期尚未具体公布；社会考生可在报名系统选择福建及泉州测试站，核对开放计划。</p><div class="sources"><a href="https://bm.cltt.org/" target="_blank" rel="noopener noreferrer">查看福建测试站${icon('external-link')}</a><a href="https://jyj.quanzhou.gov.cn/" target="_blank" rel="noopener noreferrer">泉州市教育局${icon('external-link')}</a><a href="${examSources.psc.url}" target="_blank" rel="noopener noreferrer">泉师普通话报名通知${icon('external-link')}</a></div><button class="primary" data-close>完成</button>`);
  renderExamList();
  academicTabs();
}
function renderExamList() {
  const selected=exams.filter(e=>(examType==='全部'||e.type===examType)&&(examPeriod==='all'||(examPeriod==='saved'?saved[e.id]:e.upcoming))&&(e.area==='全国'||examRegion==='泉州'||(examRegion==='福建'&&e.area==='福建')));
  $('#exam-list').innerHTML=selected.length?selected.map(e=>`<article class="exam"><div class="exam-head"><span class="exam-symbol">${icon(e.symbol)}</span><h3>${e.title}</h3><span class="tag ${e.state==='待公布'?'pending':''}">${e.state}</span></div><div class="exam-sub"><span>${e.level==='本校'?'泉州师范学院':e.level==='福建'?'福建 · 泉州适用':'全国统一'}</span><span>2026 年${e.id==='ncre'?'9月':'下半年'}</span></div><dl class="exam-dates"><dt>考试时间</dt><dd>${e.date}<small>${e.time}</small></dd>${e.print?`<dt>准考证</dt><dd>${e.print}</dd>`:''}${e.registration?`<dt>报名时间</dt><dd>${e.registration}${e.id==='ncre'?'<small>首轮已结束，另有省级补报名通告</small>':'<small>已结束</small>'}</dd>`:''}</dl><div class="exam-foot"><a class="source-name" href="${examSources[e.source].url}" target="_blank" rel="noopener noreferrer">${icon('external-link')}${examSources[e.source].name}</a><button class="remind ${saved[e.id]?'saved':''}" data-reminder="${e.id}">${icon(saved[e.id]?'bell-ring':'bell-plus')}${saved[e.id]?'已关注':'关注提醒'}</button></div></article>`).join(''):`<div class="empty">${icon('calendar-search')}暂无符合条件的考试</div>`;
  icons();
  renderNotices();
}
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}) : '尚无成功记录'; }
function visibleNotices() { return (noticeFeed?.items||[]).filter(item=>(examType==='全部'||item.category===examType)&&(examRegion==='泉州'||item.region==='全国'||(examRegion==='福建'&&item.region==='福建'))); }
function renderNotices() {
  if (!$('#notice-list')) return;
  const entries=visibleNotices();
  $('#notice-status').textContent=noticeFeed ? `${noticeFeed.degraded?'部分来源待更新 · ':''}检查于 ${formatTime(noticeFeed.checkedAt)} · ${entries.length} 条` : '尚未同步';
  $('#notice-list').innerHTML=noticeFeed ? (entries.length ? entries.slice(0,6).map(noticeRow).join('')+(entries.length>6?'<button class="text-button notice-more" id="notice-more">查看全部通知'+icon('chevron-down')+'</button>':'') : '<p class="sync-status">暂无此分类的通知</p>') : '<button class="bottom-link" id="sync-notices"><span>获取最新官方通知</span>'+icon('refresh-cw')+'</button>';
  if ($('#sync-notices')) $('#sync-notices').onclick=syncNotices;
  if ($('#notice-more')) $('#notice-more').onclick=()=>sheet('官方通知',`<div class="notice-list">${entries.map(noticeRow).join('')}</div>`);
  icons();
}
function noticeRow(item) { return `<button class="notice-row" data-notice="${esc(item.id)}"><span><strong>${esc(item.title)}</strong><small>${esc(item.sourceName)} · ${esc(item.publishedAt||'发布日期待核实')}</small></span>${icon('chevron-right')}</button>`; }
function noticeDetail(id) {
  const item=noticeFeed?.items.find(item=>item.id===id); if(!item)return;
  const official=item.officialUrl||item.url;
  const pdfs=(item.assets||[]).filter(a=>/\.pdf(?:$|\?)/i.test(a.url));
  sheet(item.title,`<p class="sheet-text">${esc(item.sourceName)} · ${esc(item.publishedAt||'发布日期待核实')}</p><p class="notice-excerpt notice-full-content">${esc(item.content||item.excerpt||'正文暂未获取，请查看原文')}</p>${item.assets?.length?'<p class="sheet-text">正文含图片或附件：</p>':''}<div class="sources">${pdfs.map(a=>`<a href="${esc(a.url)}" target="_blank" rel="noopener noreferrer">${esc(a.name||'预览 PDF 附件')}${icon('file-text')}</a>`).join('')}<a target="_blank" rel="noopener noreferrer" href="${esc(official)}">查看官方原文${icon('external-link')}</a></div><button class="primary" data-close>完成</button>`);
}
function showNoticeHealth() {
  sheet('同步状态',`<dl class="facts">${facts([['本次同步',formatTime(syncAt)],['服务器检查',formatTime(noticeFeed?.checkedAt)],['检查间隔','每小时']])}</dl><div class="source-health">${(noticeFeed?.sources||[]).map(source=>`<div class="health-row"><strong>${esc(source.name)}</strong><span class="tag ${source.state!=='ok'||source.stale?'pending':''}">${source.stale?'数据已过期':source.state==='ok'?'正常':source.state==='partial'?'部分正文待更新':'暂不可用'}</span><small>最近成功 ${esc(formatTime(source.lastSuccessAt))}</small></div>`).join('')}</div><button class="primary" data-close>完成</button>`);
}
async function syncNotices() {
  const button=$('#exam-refresh')||$('#refresh'); if(button.disabled)return;
  button.disabled=true;button.classList.add('spin');
  try {
    const response=await fetch(noticesEndpoint,{cache:'no-store',signal:AbortSignal.timeout(15000)});
    if(!response.ok)throw new Error('HTTP '+response.status);
    const data=await response.json();
    if(data.schemaVersion!==1||!Array.isArray(data.items)||!Array.isArray(data.sources)||!data.lastSuccessAt)throw new Error('Invalid feed');
    // Only official HTTPS links from the collector's allowlist are rendered as links.
    const allowed=new Set(['www.qztc.edu.cn','cet.neea.edu.cn','ncre.neea.edu.cn','www.eeafj.cn']);
    data.items=data.items.filter(item=>{try{const url=new URL(item.url);return url.protocol==='https:'&&allowed.has(url.hostname)&&typeof item.title==='string';}catch(_){return false;}});
    noticeFeed=data;syncAt=new Date().toISOString();
    try{localStorage.setItem('qnu-exam-notices',JSON.stringify({feed:data,syncAt}));}catch(_){}
    if($('#notice-list'))renderNotices();
    toast(data.degraded?'已同步，部分来源暂不可用':'官方通知已同步');
  } catch (_) { toast('同步失败，保留上次内容'); }
  finally {button.disabled=false;button.classList.remove('spin');}
}
function showSources() { sheet('官方考试来源',`<p class="sheet-text">全国考试日期、省级报名安排和本校考场通知分别保留来源。</p><div class="sources">${[['四六级 · 全国公告',examSources.cet.url],['计算机等级 · 全国公告',examSources.ncre.url],['福建省教育考试院',examSources.fj.url],['泉师教务处 · 考试报名',examSources.school.url],['普通话 · 地区测试站',examSources.cltt.url]].map(([title,url])=>`<a target="_blank" rel="noopener noreferrer" href="${url}">${title}${icon('external-link')}</a>`).join('')}</div><button class="primary" data-close>完成</button>`); }
function reminder(id) {
  const exam=exams.find(e=>e.id===id), value=saved[id]||{days:'7',changes:true,print:true};
  sheet(exam.title,`<p class="sheet-text">${exam.date} · ${exam.time}</p>${exam.start?'<label class="field-label" for="reminder-days">考前提醒</label><select id="reminder-days" class="sheet-select"><option value="7">提前 7 天</option><option value="3">提前 3 天</option><option value="1">提前 1 天</option><option value="0">考试当天</option></select>':''}<label class="option"><span>时间或考场更新</span><input id="reminder-changes" type="checkbox" ${value.changes?'checked':''}></label>${exam.print?`<label class="option"><span>准考证开放打印</span><input id="reminder-print" type="checkbox" ${value.print?'checked':''}></label>`:''}${!exam.start?'<p class="sheet-text" style="margin-top:12px">具体测试日期待公布，当前关注日程更新。</p>':''}<button class="primary" id="save-reminder">保存提醒</button>${saved[id]?'<button class="text-button" style="margin:12px auto 0" id="remove-reminder">取消关注</button>':''}`);
  if ($('#reminder-days')) $('#reminder-days').value=value.days;
  $('#save-reminder').onclick=()=>{saved[id]={days:$('#reminder-days')?.value||'7',changes:$('#reminder-changes').checked,print:$('#reminder-print')?.checked??false};persist();closeSheet();renderExams();icons();toast('已保存演示提醒，不会发送系统通知');};
  if ($('#remove-reminder')) $('#remove-reminder').onclick=()=>{delete saved[id];persist();closeSheet();renderExams();icons();toast('已取消关注');};
}
function persist(){try{localStorage.setItem('qnu-academic-preview-reminders',JSON.stringify(saved));}catch(_){toast('当前浏览器无法保存，关闭后将重置');}}
document.addEventListener('click',event=>{
  const fields=event.target.closest('[data-fields]');
  if(fields){const g=groups.find(x=>x.id===fields.dataset.fields);sheet(g.name+' · 详情',`<dl class="facts">${facts([['课组名称',g.name],['课程属性',g.attr],['学分要求',g.required],['已获学分',g.earned],...(g.courses===null?[]:[['门数要求',g.courses]]),['已通过门数',g.passed],['完成情况',g.ok?'已达标':'未达标']])}</dl>${g.id==='cross'||g.id==='art'?'<p class="sheet-text" style="margin-top:12px">艺术类的 2 学分计入跨学科课程的 7 学分，不重复相加。线上课最多计入 4 学分，混合授课按线下课计算。</p>':''}<button class="primary" data-close>完成</button>`);}
  const remind=event.target.closest('[data-reminder]');if(remind)reminder(remind.dataset.reminder);
  const notice=event.target.closest('[data-notice]');if(notice)noticeDetail(notice.dataset.notice);
  if(event.target.closest('[data-close]'))closeSheet();
});
document.querySelectorAll('[data-page]').forEach(button=>button.onclick=()=>{location.hash=button.dataset.page;});
const previewPositions=new Map();
window.addEventListener('hashchange',()=>{previewPositions.set(page,scrollY);page=pageFromHash();render();requestAnimationFrame(()=>window.scrollTo(0,previewPositions.get(page)||0));});
$('#theme').onchange=event=>{$('.app').dataset.theme=event.target.value;};
$('#close-sheet').onclick=closeSheet;
$('#sheet').addEventListener('click',event=>{if(event.target===$('#sheet')){const rect=event.target.getBoundingClientRect();if(event.clientY<rect.top||event.clientX<rect.left||event.clientX>rect.right)closeSheet();}});
$('#back').onclick=()=>{location.hash='personal';};
$('#refresh').onclick=()=>['competitions','exams'].includes(page)?(academicTab==='exams'?syncNotices():academicTab==='competitions'?syncAcademicCompetitions():toast('日历已保存在本机')):toast('此页使用本地预览数据');
function renderGrades(){
  const courses=[{name:'数据结构',score:91,credit:3,type:'必修',dept:'计算机学院',code:'10021031',classNo:'01',term:'26.春',scores:[['平时',90],['期末',91],['总评',91]],fields:[['学分',3],['绩点',4.0],['学时',48],['开课院系','计算机学院'],['选课类型','必修'],['考核方式','考试'],['考试性质','正常考试'],['是否缓考','否']]},{name:'大学英语',score:86,credit:2,type:'必修',dept:'外国语学院',code:'10011012',classNo:'02',term:'26.春',scores:[['平时',88],['期末',84],['总评',86]],fields:[['学分',2],['绩点',3.7],['学时',32],['开课院系','外国语学院'],['选课类型','必修'],['考核方式','考试'],['考试性质','正常考试'],['是否缓考','否']]},{name:'离散数学',score:82,credit:3,type:'必修',dept:'计算机学院',code:'10021018',classNo:'01',term:'26.春',scores:[['平时',80],['期末',84],['总评',82]],fields:[['学分',3],['绩点',3.0],['学时',48],['开课院系','计算机学院'],['选课类型','必修'],['考核方式','考试'],['考试性质','补考'],['是否缓考','否']]},{name:'音乐欣赏',score:94,credit:2,type:'校选',dept:'音乐与舞蹈学院',code:'10051008',classNo:'03',term:'26.春',scores:[['平时',94],['期末',94],['总评',94]],fields:[['学分',2],['绩点',4.0],['学时',32],['开课院系','音乐与舞蹈学院'],['选课类型','跨学科'],['考核方式','考查'],['考试性质','正常考试'],['是否缓考','否'],['实验',null],['口语',null],['过程性成绩',null]]}];
  const fields=['开课院系','选课类型','考试性质','是否缓考','学分','绩点','学时'];
  const valuesFor=f=>[...new Set(courses.map(c=>f==='开课院系'?c.dept:f==='选课类型'?c.type:c.fields.find(x=>x[0]===f)?.[1]).filter(v=>v!==undefined&&v!==null&&v!=='').map(String))].sort();
  const scoreBlock=c=>`<h3>成绩构成</h3><div class="grade-fact-grid score-facts">${c.scores.map(([k,v])=>`<span><small>${k}</small><b>${v}</b></span>`).join('')}</div>`;
  const courseBlock=c=>`<h3>课程与教学</h3><div class="grade-fact-grid">${[['课程号',c.code],['课序号',c.classNo],['开课院系',c.dept],['选课类型',c.type]].map(([k,v])=>`<span><small>${k}</small><b>${v}</b></span>`).join('')}</div>`;
  const creditBlock=c=>`<h3>学分与课时</h3><div class="grade-fact-grid">${[['学分',c.credit],['绩点',c.fields.find(x=>x[0]==='绩点')?.[1]||'—'],['学时',c.fields.find(x=>x[0]==='学时')?.[1]||'—']].map(([k,v])=>`<span><small>${k}</small><b>${v}</b></span>`).join('')}</div>`;
  const examBlock=c=>`<h3>考核与考试</h3><div class="grade-fact-grid">${c.fields.filter(x=>['考核方式','考试性质','是否缓考'].includes(x[0])).map(([k,v])=>`<span><small>${k}</small><b>${v}</b></span>`).join('')}</div>`;
  const optionalBlock=c=>{const f=c.fields.filter(x=>['实验','口语','过程性成绩'].includes(x[0])&&x[1]!=null);return f.length?`<h3>专项成绩</h3><div class="grade-fact-grid">${f.map(([k,v])=>`<span><small>${k}</small><b>${v}</b></span>`).join('')}</div>`:'';};
  const renderRows=()=>{const q=($('#grade-search')?.value||'').trim();document.querySelectorAll('.grade-preview').forEach(row=>{const c=courses.find(x=>x.name===row.dataset.name);const fieldValue=gradeFilterField==='开课院系'?c.dept:gradeFilterField==='选课类型'?c.type:c.fields.find(x=>x[0]===gradeFilterField)?.[1];row.hidden=Boolean(q&&!c.name.includes(q))||(gradeFilterField!=='全部'&&gradeFilterValue!=='全部'&&String(fieldValue)!==String(gradeFilterValue));});};
  const fieldChips=()=>`<div class="grade-filter-block"><small>筛选字段</small><div class="grade-select" data-grade-select="field" role="combobox" tabindex="0" aria-label="筛选字段" aria-haspopup="listbox" aria-expanded="false"><span>${esc(gradeFilterField)}</span>${icon('chevron-down')}</div><div class="grade-select-menu" role="listbox" hidden>${['全部',...fields].map(f=>`<div tabindex="0" role="option" data-grade-field="${f}" aria-selected="${gradeFilterField===f}">${f}</div>`).join('')}</div></div>`;
  const valueChips=()=>{const vals=gradeFilterField==='全部'?[]:valuesFor(gradeFilterField);if(vals.length===1)gradeFilterValue=vals[0];return `<div class="grade-filter-block"><small>筛选值</small><div class="grade-select" data-grade-select="value" role="combobox" tabindex="${vals.length?0:-1}" aria-label="筛选值" aria-disabled="${!vals.length}" aria-haspopup="listbox" aria-expanded="false"><span>${esc(gradeFilterValue)}</span>${icon('chevron-down')}</div><div class="grade-select-menu" role="listbox" hidden>${['全部',...vals].map(v=>`<div tabindex="0" role="option" data-grade-value="${esc(v)}" aria-selected="${String(gradeFilterValue)===String(v)}">${esc(v)}</div>`).join('')}</div></div>`;};
  $('#content').innerHTML=`<div class="view-tabs"><button class="active">成绩</button><button data-go="credits">学分</button></div><section class="summary"><div class="toolbar"><h2>我的成绩 <span class="count">${courses.length} 条成绩</span></h2></div><div class="stats"><div class="stat"><b>3.42</b><span>平均绩点</span></div><div class="stat"><b>18</b><span>获得学分</span></div><div class="stat"><b>88.2</b><span>加权均分</span></div></div><div class="grade-summary-controls"><label class="search">${icon('search')}<input id="grade-search" placeholder="搜索课程" aria-label="搜索课程"></label><div class="grade-filter-panes" id="grade-filter-panes">${fieldChips()}${valueChips()}</div></div></section><div class="category-tabs"><button class="active">26.春</button><button data-demo="此预览仅提供合成的 26.春成绩">25.秋</button><button data-demo="此预览仅提供合成的 26.春成绩">25.春</button></div><div id="grade-preview-list">${courses.map(c=>`<details class="grade-preview" data-name="${c.name}"><summary><span class="score-ring" style="--score:${c.score}%"><b>${c.score}</b></span><span><strong>${c.name}${c.fields.some(x=>x[0]==='考试性质'&&x[1]==='补考')?' · 补考':''}</strong><small>${c.credit} 学分 · ${c.type}</small></span>${icon('chevron-down')}</summary><div class="grade-facts">${scoreBlock(c)}${courseBlock(c)}${creditBlock(c)}${examBlock(c)}${optionalBlock(c)}</div></details>`).join('')}</div>`;
  $('#grade-search').oninput=renderRows;
  const panes=$('#grade-filter-panes');
  const closeMenus=()=>{panes.querySelectorAll('.grade-select-menu').forEach(m=>m.hidden=true);panes.querySelectorAll('[data-grade-select]').forEach(s=>s.setAttribute('aria-expanded','false'));panes.classList.remove('value-focus');};
  const openMenu=type=>{closeMenus();const select=panes.querySelector(`[data-grade-select="${type}"]`);if(select.getAttribute('aria-disabled')==='true')return;select.setAttribute('aria-expanded','true');select.nextElementSibling.hidden=false;panes.classList.toggle('value-focus',type==='value');};
  const renderFilters=()=>{panes.innerHTML=fieldChips()+valueChips();icons();};
  panes.onclick=e=>{
    e.stopPropagation();
    const field=e.target.closest('[data-grade-field]'),value=e.target.closest('[data-grade-value]'),select=e.target.closest('[data-grade-select]');
    if(field){gradeFilterField=field.dataset.gradeField;const vals=gradeFilterField==='全部'?[]:valuesFor(gradeFilterField);gradeFilterValue=vals.length===1?vals[0]:'全部';renderFilters();renderRows();if(vals.length>1)openMenu('value');else closeMenus();}
    else if(value){gradeFilterValue=value.dataset.gradeValue;renderFilters();closeMenus();renderRows();}
    else if(select){if(select.getAttribute('aria-expanded')==='true')closeMenus();else openMenu(select.dataset.gradeSelect);}
  };
  panes.onkeydown=e=>{if(['Enter',' '].includes(e.key)&&e.target.matches('[role=combobox],[role=option]')){e.preventDefault();e.target.click();}if(e.key==='Escape')closeMenus();};
  window.gradeMenuOutside?.abort();const outside=new AbortController();window.gradeMenuOutside=outside;
  document.addEventListener('click',e=>{if(!panes.contains(e.target))closeMenus();},{signal:outside.signal});
};
document.addEventListener('DOMContentLoaded',()=>{if(!window.AcademicHost)render();});
