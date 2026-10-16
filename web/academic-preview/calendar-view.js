'use strict';
function calendarDate(d){return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}
function calendarToday(){return calendarDate(new Date());}
let calendarSource=portalRead('calendar-source','all'),calendarSelectedDate=calendarToday(),calendarCollapsed=false;
calendarMonth=new Date().getMonth()+1;calendarYear=new Date().getFullYear()-(calendarMonth<9?1:0);
const calendarSources={all:'全部',school:'校历',homework:'作业',activity:'活动',records:'赛事与考证'};
function calendarAvailableSources(){return window.AcademicHost&&window.nativeAcademicPluginInstalled===false?{all:'全部',school:'校历',homework:'作业',activity:'活动'}:calendarSources;}
const calendarHolidaySource='https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm',statutoryDays={};
for(const [start,end,name] of [['2026-01-01','2026-01-03','元旦'],['2026-02-15','2026-02-23','春节'],['2026-04-04','2026-04-06','清明节'],['2026-05-01','2026-05-05','劳动节'],['2026-06-19','2026-06-21','端午节'],['2026-09-25','2026-09-27','中秋节'],['2026-10-01','2026-10-07','国庆节']]){
 const d=new Date(start+'T12:00:00');while(calendarDate(d)<=end){statutoryDays[calendarDate(d)]={holiday:true,name};d.setDate(d.getDate()+1);}
}
for(const d of ['2026-01-04','2026-02-14','2026-02-28','2026-05-09','2026-09-20','2026-10-10'])statutoryDays[d]={workday:true};
const holidayRequests=new Map();
function parseCalendarHolidays(root,year){
 if(root?.year!==year||!Array.isArray(root.days)||root.days.length<1||root.days.length>100)throw Error('年度安排尚未公布');
 const days={};for(const item of root.days){
  const date=communityDate(item.date);
  if(!date||!date.startsWith(year+'-')||typeof item.isOffDay!=='boolean'||typeof item.name!=='string'||!item.name.trim()||days[date])throw Error('节假日数据格式不正确');
  days[date]={holiday:item.isOffDay,workday:!item.isOffDay,name:item.name};
 }return days;
}
function applyCalendarHolidays(year,days){
 for(const date of Object.keys(statutoryDays))if(date.startsWith(year+'-'))delete statutoryDays[date];
 Object.assign(statutoryDays,days);
 if(academicTab!=='calendar')return;
 // Update only the date labels: no calendar rebuild, scroll jump or entrance replay.
 document.querySelectorAll('[data-day]').forEach(button=>{
  const date=button.dataset.day,marker=calendarDayMarker(date),label=marker.holiday?marker.name:lunarLabel(date);
  button.classList.toggle('holiday',!!marker.holiday);button.classList.toggle('workday',!!marker.workday);
  button.querySelector('.calendar-mark').textContent=marker.holiday?'休':marker.workday?'调':'';
  button.querySelector('small').textContent=label;
  button.setAttribute('aria-label',date+' '+label+(marker.holiday?' 休':marker.workday?' 调':''));
 });
}
function ensureCalendarHolidays(year){
 if(holidayRequests.has(year))return holidayRequests.get(year);
 const cached=portalRead('holiday:'+year,null);
 if(cached){try{applyCalendarHolidays(year,parseCalendarHolidays(cached.data,year));}catch(_){}}
 if(cached&&Date.now()-cached.checkedAt<86400000){holidayRequests.set(year,Promise.resolve());return;}
 const pending=(async()=>{
  for(const url of ['https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/'+year+'.json','https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/'+year+'.json']){
   try{
    const response=await fetch(url,{cache:'no-store',signal:AbortSignal.timeout(10000)});if(!response.ok)throw Error();
    const body=await response.text();if(body.length>200000)throw Error();
    const data=JSON.parse(body),days=parseCalendarHolidays(data,year);
    portalSave('holiday:'+year,{data,checkedAt:Date.now(),source:url});applyCalendarHolidays(year,days);return;
   }catch(_){}
  }
 })();holidayRequests.set(year,pending);return pending;
}
const lunarFormatter=new Intl.DateTimeFormat('zh-CN-u-ca-chinese',{month:'numeric',day:'numeric'});
const lunarDays=['','初一','初二','初三','初四','初五','初六','初七','初八','初九','初十','十一','十二','十三','十四','十五','十六','十七','十八','十九','二十','廿一','廿二','廿三','廿四','廿五','廿六','廿七','廿八','廿九','三十'];
function lunarLabel(date){
 const p=lunarFormatter.formatToParts(new Date(date+'T12:00:00')),m=p.find(x=>x.type==='month')?.value,d=Number(p.find(x=>x.type==='day')?.value);
 return ({'1-1':'春节','1-15':'元宵节','5-5':'端午节','7-7':'七夕','8-15':'中秋节','9-9':'重阳节','12-8':'腊八节'})[m+'-'+d]||(d===1?(['','正月','二月','三月','四月','五月','六月','七月','八月','九月','十月','冬月','腊月'][Number(m)]||m+'月'):lunarDays[d]||'');
}
let calendarData=[],calendarByDate=new Map();
function calendarEventSource(e){return e.kind==='homework'||e.id.startsWith('homework:')?'homework':e.id.startsWith('school:')||e.id.startsWith('school-day:')?'school':e.id.startsWith('exam-')?'records':e.id.startsWith('native:')||!e.key?'activity':'records';}
function calendarVisibleEvents(){const prefix=(calendarYear+(calendarMonth<9?1:0))+'-'+String(calendarMonth).padStart(2,'0');return calendarData.filter(e=>e.date.startsWith(prefix)&&(calendarSource==='all'||calendarEventSource(e)===calendarSource));}
function calendarDayMarker(date){
 const school=(calendarByDate.get(date)||[]).find(e=>e.id.startsWith('school-day:'));
 if(school){const holiday=/放假/.test(school.stage);return {holiday,workday:!holiday,name:school.title};}
 return statutoryDays[date]||{};
}
function calendarTopbar(){
 if(page!=='calendar'&&academicTab!=='calendar')return;
 const today=calendarToday(),selected=calendarSelectedDate||today,parts=selected.split('-'),label=Number(parts[1])+'.'+Number(parts[2]);
 if(window.AcademicHost){AcademicHost.postMessage(JSON.stringify({action:'calendar-date',label,away:calendarSelectedDate!==today}));return;}
 if(!$('#calendar-title-group')){const group=document.createElement('div');group.id='calendar-title-group';$('#page-title').before(group);group.append($('#page-title'));}
 $('#page-title').textContent=label;$('#page-title').onclick=calendarGoToday;$('#page-title').setAttribute('role','button');$('#page-title').tabIndex=0;
 $('#page-title').onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();calendarGoToday();}};
 $('#back').hidden=true;$('#refresh').hidden=true;$('#calendar-return')?.remove();
 if(calendarSelectedDate!==today){$('#page-title').insertAdjacentHTML('afterend','<button id="calendar-return" class="text-button">回今日</button>');$('#calendar-return').onclick=calendarGoToday;}
}
function calendarGoToday(){const d=new Date();calendarMonth=d.getMonth()+1;calendarYear=d.getFullYear()-(calendarMonth<9?1:0);calendarSelectedDate=calendarToday();calendarCollapsed=false;calendarMonthsExpanded=false;renderCalendar();scrollTo(0,0);}
function calendarDayAgenda(){
 const selected=(calendarByDate.get(calendarSelectedDate)||[]).filter(e=>calendarSource==='all'||calendarEventSource(e)===calendarSource);
 $('#calendar-agenda-title').textContent=calendarSelectedDate.slice(5).replace('-',' 月 ')+' 日安排';$('#calendar-agenda-count').textContent=selected.length+' 项';$('#calendar-agenda').innerHTML=agendaRows(selected);
 const d=new Date(calendarSelectedDate+'T12:00:00');d.setDate(d.getDate()-(d.getDay()+6)%7);const start=calendarDate(d);d.setDate(d.getDate()+6);const end=calendarDate(d);
 document.querySelectorAll('.calendar-week').forEach(w=>w.classList.remove('selected-week'));
 document.querySelectorAll('[data-day]').forEach(b=>{b.classList.toggle('selected',b.dataset.day===calendarSelectedDate);b.setAttribute('aria-pressed',String(b.dataset.day===calendarSelectedDate));if(b.dataset.day>=start&&b.dataset.day<=end)b.closest('.calendar-week').classList.add('selected-week');});
 if(['all','activity'].includes(calendarSource)&&(window.nativeUndatedActivities||[]).length){
  $('#calendar-agenda').insertAdjacentHTML('beforeend','<h3 class="memo-heading">未定时间</h3>'+agendaRows(window.nativeUndatedActivities.map(e=>({...e,id:'native:'+e.activityId,date:'',origin:'手动活动'}))));
 }
 calendarTopbar();icons($('#calendar-agenda'));window.saveAcademicNavigation?.();
}
function calendarSetCollapsed(value){calendarCollapsed=value;$('#calendar-panel').classList.toggle('collapsed',value);$('#calendar-fold').setAttribute('aria-expanded',String(!value));$('#calendar-fold').setAttribute('aria-label',value?'展开月历':'收起为周历');window.saveAcademicNavigation?.();}
function calendarBindAgendaGestures(agenda){
 let gesture=null;
 const atTop=()=>Math.max(window.scrollY,document.scrollingElement?.scrollTop||0)<=4;
 agenda.addEventListener('touchstart',e=>{
  if(e.touches.length!==1||e.target.closest('button,a,input,select,textarea')){gesture=null;return;}
  gesture={x:e.touches[0].clientX,y:e.touches[0].clientY,atTop:atTop(),handled:false};
 },{passive:true});
 agenda.addEventListener('touchmove',e=>{
  if(!gesture||e.touches.length!==1)return;
  const dx=e.touches[0].clientX-gesture.x,dy=e.touches[0].clientY-gesture.y;
  // A drag that starts in the list stays a normal scroll until the next gesture.
  if(!gesture.atTop||Math.abs(dy)<=Math.abs(dx)*1.3)return;
  if(gesture.handled){if(e.cancelable)e.preventDefault();return;}
  if((dy<0&&!calendarCollapsed)||(dy>0&&calendarCollapsed)){
   if(Math.abs(dy)>8&&e.cancelable)e.preventDefault();
   if(Math.abs(dy)>45){gesture.handled=true;calendarSetCollapsed(dy<0);}
  }
 },{passive:false});
 const finish=()=>{gesture=null;};
 agenda.addEventListener('touchend',finish,{passive:true});
 agenda.addEventListener('touchcancel',finish,{passive:true});
}
function calendarAdd(){
 if(window.AcademicHost){window.saveAcademicNavigation?.();AcademicHost.postMessage(JSON.stringify({action:'add-activity',date:calendarSelectedDate}));return;}
 sheet('添加活动','<p class="sheet-text">活动在日历内添加和编辑，支持节次、多时段、备注和颜色；手动活动只显示在日历页。此网页预览可添加单次日历活动。</p><label class="field-label">名称</label><input id="custom-event-name" class="sheet-input" maxlength="100"><button class="primary" id="custom-event-next">选择时间</button>');$('#custom-event-next').onclick=()=>{const name=$('#custom-event-name').value.trim();if(name){eventForm({name});$('#event-date').value=calendarSelectedDate;}};
}
let calendarManagementShowSchool=false;
function calendarManage(showSchool=false){
 calendarManagementShowSchool=showSchool;
 const school=e=>e.id.startsWith('school:')||e.id.startsWith('school-day:')||e.activityId?.startsWith('calendar-event:');
 const groups=new Map();
 for(const event of allCalendarEvents().filter(e=>school(e)?showSchool:!e.id.startsWith('homework:')&&!e.id.startsWith('exam-')&&!e.phaseId)){
  const key=event.activityId||event.id,previous=groups.get(key);
  if(!previous)groups.set(key,{...event});
  else{previous.endDate=[previous.endDate||previous.date,event.endDate||event.date].sort().at(-1);if(event.date<previous.date)previous.date=event.date;}
 }
 for(const e of window.nativeUndatedActivities||[])groups.set(e.activityId,{...e,id:"native:"+e.activityId,date:"",origin:"手动活动"});
 const managed=[...groups.values()].sort((a,b)=>(a.date+(a.time||'')).localeCompare(b.date+(b.time||'')));
 const rows=managed.length?managed.map(e=>`<div class="agenda-row"><time>${esc(e.date||"未定时间")}<small>${esc(e.time||'')}</small></time><span><strong>${esc(e.title)}</strong><small>${esc(e.stage||'自定提醒')} · ${esc(e.origin||'用户确认')}</small></span>${school(e)?'':`<button class="text-button" ${e.activityId?`data-native-activity="${esc(e.activityId)}"`:`data-edit-event="${esc(e.id)}"`}>编辑</button>`}</div>`).join(''):'<p class="sheet-text">暂无自定义活动。</p>';
 sheet('活动管理',rows);
 $('#sheet-title').insertAdjacentHTML('afterend',`<button class="icon-button calendar-management-eye" aria-label="${showSchool?'隐藏校历节点':'显示校历节点'}" aria-pressed="${showSchool}">${icon(showSchool?'eye':'eye-off')}</button>`);
 $('.calendar-management-eye').onclick=()=>calendarManage(!calendarManagementShowSchool);
 icons();
}
function calendarMoreMenu(){
 sheet('日历更多',`<div class="sheet-actions"><button class="secondary" data-calendar-menu="add">添加活动</button><button class="secondary" data-calendar-menu="school">查看校历</button><button class="secondary" data-calendar-menu="manage">活动管理</button></div>`);
 document.querySelectorAll('[data-calendar-menu]').forEach(b=>b.onclick=()=>{const action=b.dataset.calendarMenu;closeSheet();if(action==='add')calendarAdd();else if(action==='manage')calendarManage();else document.querySelector('[data-school-calendar]')?.click();});
}
renderCalendar=function(){
 if(!(calendarSource in calendarAvailableSources()))calendarSource='all';
 queueMicrotask(()=>{const years=new Set([...document.querySelectorAll('[data-day]')].map(b=>Number(b.dataset.day.slice(0,4))));years.forEach(ensureCalendarHolidays);});
 calendarData=allCalendarEvents();calendarByDate=new Map();for(const e of calendarData){if(!calendarByDate.has(e.date))calendarByDate.set(e.date,[]);calendarByDate.get(e.date).push(e);}
 const year=calendarYear+(calendarMonth<9?1:0),prefix=year+'-'+String(calendarMonth).padStart(2,'0');
 if(!calendarSelectedDate.startsWith(prefix))calendarSelectedDate=calendarToday().startsWith(prefix)?calendarToday():prefix+'-01';
 const first=new Date(year,calendarMonth-1,1),offset=(first.getDay()+6)%7,days=new Date(year,calendarMonth,0).getDate(),start=new Date(first);start.setDate(1-offset);
 const weeks=Array.from({length:Math.ceil((offset+days)/7)},(_,week)=>'<div class="calendar-week">'+Array.from({length:7},(_,day)=>{
 const d=new Date(start);d.setDate(start.getDate()+week*7+day);const date=calendarDate(d),marker=calendarDayMarker(date),has=(calendarByDate.get(date)||[]).some(e=>calendarSource==='all'||calendarEventSource(e)===calendarSource),label=marker.holiday?marker.name:lunarLabel(date);
 return '<button data-day="'+date+'" class="'+(date===calendarToday()?'today ':'')+(marker.holiday?'holiday ':'')+(marker.workday?'workday ':'')+(d.getMonth()+1!==calendarMonth?'outside-month ':'')+(d.getDay()===0||d.getDay()===6?'weekend':'')+'" aria-label="'+date+' '+esc(label)+(marker.holiday?' 休':marker.workday?' 调':'')+(has?' 有安排':'')+'"><span class="date-number">'+d.getDate()+'</span><em class="calendar-mark">'+(marker.holiday?'休':marker.workday?'调':'')+'</em><small>'+esc(label)+'</small><span class="calendar-dots">'+(has?'<i></i>':'')+'</span></button>';
 }).join('')+'</div>');
 $('#content').innerHTML='<section id="calendar-panel" class="calendar-panel '+(calendarCollapsed?'collapsed':'')+'"><div class="calendar-year"><button id="year-prev" class="icon-button" aria-label="上个月">'+icon('chevron-left')+'</button><button id="calendar-year-toggle" class="calendar-year-toggle" aria-expanded="'+calendarMonthsExpanded+'">'+year+' 年 '+calendarMonth+' 月'+icon(calendarMonthsExpanded?'chevron-up':'chevron-down')+'</button><button id="year-next" class="icon-button" aria-label="下个月">'+icon('chevron-right')+'</button></div><div class="academic-months" '+(calendarMonthsExpanded?'':'hidden')+'>'+Array.from({length:12},(_,i)=>{const m=(i+8)%12+1,y=calendarYear+(m<9?1:0),has=calendarData.some(e=>e.date.startsWith(y+'-'+String(m).padStart(2,'0')));return '<button data-month="'+m+'" class="'+(calendarMonth===m?'active':'')+'">'+m+'月<span class="month-dot">'+(has?'•':'')+'</span></button>';}).join('')+'</div><div class="month-grid"><div class="calendar-weekdays">'+['一','二','三','四','五','六','日'].map(d=>'<span>'+d+'</span>').join('')+'</div><div class="calendar-weeks">'+weeks.join('')+'</div></div><button id="calendar-fold" class="calendar-fold" aria-label="收起为周历" aria-expanded="'+!calendarCollapsed+'"><i></i></button></section><div class="category-tabs calendar-source-tabs" aria-label="安排来源">'+Object.entries(calendarAvailableSources()).map(([key,label])=>'<button data-calendar-source="'+key+'" class="'+(calendarSource===key?'active':'')+'" aria-pressed="'+(calendarSource===key)+'"><i class="source-dot source-'+key+'"></i>'+label+'</button>').join('')+'</div><div class="toolbar"><h2 id="calendar-agenda-title"></h2><span id="calendar-agenda-count" class="count"></span></div><div id="calendar-agenda"></div>';
 $('#year-prev').onclick=()=>shiftCalendarMonth(-1);$('#year-next').onclick=()=>shiftCalendarMonth(1);$('#calendar-year-toggle').onclick=()=>{calendarMonthsExpanded=!calendarMonthsExpanded;renderCalendar();};
 document.querySelectorAll('[data-month]').forEach(b=>b.onclick=()=>{calendarMonth=Number(b.dataset.month);calendarMonthsExpanded=false;renderCalendar();});
 document.querySelectorAll('[data-calendar-source]').forEach(b=>b.onclick=()=>{if(b.dataset.calendarSource==='plugin'){AcademicHost.postMessage(JSON.stringify({action:'plugin-market'}));return;}calendarSource=b.dataset.calendarSource;portalSave('calendar-source',calendarSource);renderCalendar();});
 document.querySelectorAll('[data-day]').forEach(b=>b.onclick=()=>{calendarSelectedDate=b.dataset.day;if(!calendarSelectedDate.startsWith(prefix)){calendarMonth=Number(calendarSelectedDate.slice(5,7));calendarYear=Number(calendarSelectedDate.slice(0,4))-(calendarMonth<9?1:0);renderCalendar();}else calendarDayAgenda();});
 $('#calendar-fold').onclick=()=>calendarSetCollapsed(!calendarCollapsed);
 let touch=null;$('#calendar-panel').addEventListener('touchstart',e=>{touch={x:e.touches[0].clientX,y:e.touches[0].clientY};},{passive:true});
 $('#calendar-panel').addEventListener('touchend',e=>{if(!touch)return;const dx=e.changedTouches[0].clientX-touch.x,dy=e.changedTouches[0].clientY-touch.y;touch=null;if(Math.abs(dx)>45&&Math.abs(dx)>Math.abs(dy)*1.3){shiftCalendarMonth(dx<0?1:-1);window.saveAcademicNavigation?.();}else if(Math.abs(dy)>45&&Math.abs(dx)<Math.abs(dy))calendarSetCollapsed(dy<0);},{passive:true});
 $('#calendar-panel').addEventListener('wheel',e=>{if(e.deltaY>15&&!calendarCollapsed){e.preventDefault();calendarSetCollapsed(true);}else if(e.deltaY< -15&&calendarCollapsed&&scrollY<5){e.preventDefault();calendarSetCollapsed(false);}},{passive:false});
 calendarBindAgendaGestures($('#calendar-agenda'));
 calendarDayAgenda();calendarPaintRanges();updateCalendarExport();icons();
};
const calendarActivityColors=portalRead('calendar-activity-colors',{});
function calendarCardTimes(e){
 const range=e.startDate&&e.endDate&&e.startDate<e.endDate;
 if(range){
  const date=d=>d.slice(5).replace('-','/');
  return [date(e.startDate)+(e.startTime?' '+e.startTime:''),date(e.endDate)+(e.endTime?' '+e.endTime:'')];
 }
 if(e.timeRange){
  const parts=e.timeRange.split(/\s*(?:—|至|–)\s*/);
  if(parts.length===2)return parts;
 }
 return [e.time||(e.date?'全天':'未定时间')];
}
agendaRows=function(events){return events.length?events.slice().sort((a,b)=>Number(b.kind==='homework')-Number(a.kind==='homework')||(a.date+(a.time||'')).localeCompare(b.date+(b.time||''))).map(e=>{
 const color=calendarEventColor(e);
 const school=e.id.startsWith('school:')||e.id.startsWith('school-day:')||e.activityId?.startsWith('calendar-event:');
 const detail=e.content??e.stage??'',location=e.location||'',times=calendarCardTimes(e);
 const edit=school?'':e.id.startsWith('native:')?`<button class="icon-button" data-native-activity="${esc(e.activityId||'')}" aria-label="编辑活动">${icon('pencil')}</button>`:e.id.startsWith('homework:')?'<span class="count">作业</span>':!e.id.startsWith('exam-')?`<button class="icon-button" data-edit-event="${esc(e.id)}" aria-label="编辑安排">${icon('pencil')}</button>`:'';
 const surface=school?'#ffffff':color?calendarCardSurface(color):null;
 return `<article class="agenda-card" ${surface?`style="--card-color:${surface};--card-ink:${calendarColorForeground(surface)}"`:''}><div class="agenda-card-time${times.length>1?' is-range':''}">${times.map(t=>`<span>${esc(t)}</span>`).join('')}</div><div class="agenda-card-body"><strong>${esc(e.title)}</strong>${detail?`<small class="agenda-card-content">${esc(detail)}</small>`:''}${location?`<small class="agenda-card-location">${esc(location)}</small>`:''}</div><div class="agenda-card-actions">${edit}</div></article>`;
 }).join(''):'<div class="empty">没有安排</div>';};
function calendarColorKey(e){return e.activityId||(e.id.startsWith('school-day:')?'school:'+e.title+':'+e.date.slice(0,4):e.id.replace(/:\d{4}-\d{2}-\d{2}$/,''));}
function calendarEventColor(e){
 const key=calendarColorKey(e),manual=e.activityId&&!e.activityId.startsWith('calendar-event:');
 const pending=window.calendarPendingColors||{};
 const value=window.AcademicHost?(manual?(Object.hasOwn(pending,key)?pending[key]:e.color):(window.nativeActivityColors?.[key]||e.color)):(calendarActivityColors[key]||e.color);
 return /^#[0-9a-f]{6}$/i.test(value||'')?value:null;
}
function calendarCardSurface(color){
 const base=getComputedStyle(document.querySelector('.app')||document.documentElement).getPropertyValue('--surface').trim();
 const probe=document.createElement('span');probe.style.color=base||'#ffffff';document.body.append(probe);
 const rgb=getComputedStyle(probe).color.match(/[\d.]+/g)?.slice(0,3).map(Number)||[255,255,255];probe.remove();
 return '#'+color.slice(1).match(/../g).map((v,i)=>Math.round(parseInt(v,16)*.22+rgb[i]*.78).toString(16).padStart(2,'0')).join('');
}
function calendarColorForeground(hex){const linear=v=>{v/=255;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);};const rgb=hex.slice(1).match(/../g).map(v=>linear(parseInt(v,16)));const l=rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722;return (l+.05)/.05>=1.05/(l+.05)?'#172126':'#ffffff';}
function calendarPaintRanges(){
 const visible=e=>calendarSource==='all'||calendarEventSource(e)===calendarSource;
 for(const button of document.querySelectorAll('[data-day]')){
  const date=button.dataset.day,event=(calendarByDate.get(date)||[]).find(e=>visible(e)&&e.startDate&&e.endDate&&e.startDate<e.endDate&&calendarEventColor(e));
  button.classList.remove('activity-range','range-start','range-end');if(!event)continue;
  button.classList.add('activity-range');button.style.setProperty('--activity-color',calendarEventColor(event));
  const weekday=new Date(date+'T12:00:00').getDay();
  button.classList.toggle('range-start',date===event.startDate||weekday===1);button.classList.toggle('range-end',date===event.endDate||weekday===0);
 }
}
function calendarPickColor(id){
 const e=[...allCalendarEvents(),...(window.nativeUndatedActivities||[]).map(e=>({...e,id:'native:'+e.activityId,date:''}))].find(e=>e.id===id);if(!e)return;
 if(e.id.startsWith('school:')||e.id.startsWith('school-day:')||e.activityId?.startsWith('calendar-event:'))return;
 const presets=window.nativeActivityPresetColors||['#2e8b6f','#3a7cb8','#c08a2d','#7a6bc6','#5273b8','#c96a57','#7c9a3d','#3d9e96','#b66b95'];
 sheet('编辑活动',`<p class="sheet-text">${esc(e.title)}</p><p class="field-label">安排背景色</p><div class="calendar-color-presets">${presets.map(c=>`<button data-color-value="${esc(c)}" style="background:${esc(c)};color:${calendarColorForeground(c)}" aria-label="选择颜色 ${esc(c)}">${c===calendarEventColor(e)?'✓':''}</button>`).join('')}</div><div class="sheet-actions"><button id="calendar-reset-color" class="secondary">恢复默认</button></div>`);
 const apply=color=>{const key=calendarColorKey(e);if(color)calendarActivityColors[key]=color;else delete calendarActivityColors[key];portalSave('calendar-activity-colors',calendarActivityColors);if(window.AcademicHost){if(e.activityId&&!e.activityId.startsWith('calendar-event:')){window.calendarPendingColors=window.calendarPendingColors||{};calendarPendingColors[key]=color||null;AcademicHost.postMessage(JSON.stringify({action:'activity-color',activityId:e.activityId,color:color||null}));}else {window.nativeActivityColors=window.nativeActivityColors||{};if(color)nativeActivityColors[key]=color;else delete nativeActivityColors[key];AcademicHost.postMessage(JSON.stringify({action:'calendar-node-color',key,color:color||null}));}}closeSheet();renderCalendar();};
 document.querySelectorAll('[data-color-value]').forEach(b=>b.onclick=()=>apply(b.dataset.colorValue));$('#calendar-reset-color').onclick=()=>apply(null);
}
document.addEventListener('click',e=>{const button=e.target.closest('[data-school-edit]');if(button){e.preventDefault();e.stopPropagation();calendarPickColor(button.dataset.schoolEdit);}});
const originalUpdateNav=updateNav;
updateNav=function(){originalUpdateNav();document.querySelectorAll('.bottom-nav [data-go]').forEach(b=>b.classList.toggle('active',b.dataset.go===page));};
