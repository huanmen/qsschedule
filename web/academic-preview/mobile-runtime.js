'use strict';
if(window.AcademicHost){
  document.body.classList.add('native-academic');
  const resizeSheet=()=>{
    const height=window.visualViewport?.height||window.innerHeight;
    if(height<=0)return;
    document.querySelector('.app').style.setProperty('--sheet-viewport',`${height}px`);
    // Android WebView can retain a zero calc() height on a top-layer dialog.
    const full=document.querySelector('#sheet').classList.contains('competition-detail-page');
    const pixels=`${full?Math.ceil(height):Math.floor(height*.84)}px`;
    document.querySelector('#sheet').style.maxHeight=pixels;
    document.querySelector('#sheet').style.height=pixels;
  };
  window.resizeAcademicSheet=resizeSheet;
  resizeSheet();
  window.addEventListener('resize',resizeSheet);
  window.visualViewport?.addEventListener('resize',resizeSheet);
  const host=message=>AcademicHost.postMessage(JSON.stringify(message));
  window.restoreAcademicNavigation=data=>{
    competitionScope=data.scope||competitionScope;competitionKind=data.kind||competitionKind;competitionGrade=data.grade||competitionGrade;
    competitionQuery=data.query||'';competitionView=['catalog','timeline'].includes(data.view)?data.view:'catalog';
    competitionYear=data.year||competitionYear;competitionFollowOnly=!!data.followOnly;
    competitionSubscription=['all','available','unavailable'].includes(data.subscription)?data.subscription:competitionSubscription;
    examQuery=data.examQuery||'';examType=data.examType||'全部';examFollowOnly=!!data.examFollowOnly;
    calendarMonth=data.month||calendarMonth;calendarYear=data.calendarYear||calendarYear;
    calendarFollowing=!!data.calendarFollowing;calendarMonthsExpanded=!!data.monthsExpanded;
    calendarSelectedDate=data.selectedDate||calendarSelectedDate;calendarCollapsed=!!data.collapsed;calendarSource=data.calendarSource||calendarSource;
    renderAcademicArea(data.tab||academicTab);icons();
    if(academicTab==='competitions'){competitionLimit=Math.max(15,Math.min(data.limit||15,2000));renderCompetitionList();}
    host({action:'tab',tab:academicTab});
    requestAnimationFrame(()=>requestAnimationFrame(()=>window.scrollTo(0,data.y||0)));
  };
  let positionTimer;
  window.saveAcademicNavigation=()=>{
    if(!$('#sheet').open)host({action:'position',tab:academicTab,y:scrollY,scope:competitionScope,kind:competitionKind,grade:competitionGrade,query:competitionQuery,limit:competitionLimit,view:competitionView,year:competitionYear,followOnly:competitionFollowOnly,subscription:competitionSubscription,examQuery,examType,examFollowOnly,month:calendarMonth,calendarYear,calendarFollowing,monthsExpanded:calendarMonthsExpanded,selectedDate:calendarSelectedDate,collapsed:calendarCollapsed,calendarSource});
  };
  window.addEventListener('scroll',()=>{
    clearTimeout(positionTimer);positionTimer=setTimeout(()=>{
      saveAcademicNavigation();
    },80);
  },{passive:true});
  window.nativeAcademicConfigure=(config,navigation)=>{
    const app=document.querySelector('.app');
    const themeChanged=JSON.stringify(window.nativeAcademicColors)!==JSON.stringify(config.colors);
    window.nativeAcademicColors=config.colors;
    for(const [key,value] of Object.entries(config.colors))app.style.setProperty(key,value);
    document.documentElement.style.background=config.colors['--base'];
    window.nativeAcademicSemester=config.semester;
    window.nativeActivityPresetColors=config.activityPresetColors||[];
    const colorsChanged=JSON.stringify(window.nativeActivityColors)!==JSON.stringify(config.activityColors||{});
    window.nativeActivityColors=config.activityColors||{};
    const pluginChanged=window.nativeAcademicPluginInstalled!==!!config.academicPluginInstalled;
    const memosChanged=JSON.stringify(window.nativeUndatedActivities)!==JSON.stringify(config.undatedActivities||[]);
    window.nativeAcademicPluginInstalled=!!config.academicPluginInstalled;
    window.nativeUndatedActivities=config.undatedActivities||[];
    if(!window.nativeAcademicPluginInstalled&&['competition','exam','records','plugin'].includes(calendarSource))calendarSource='all';
    const schoolChanged=JSON.stringify(window.nativeSchoolEvents)!==JSON.stringify(config.schoolEvents||[])||JSON.stringify(window.nativeCalendarActivities)!==JSON.stringify(config.activities||[])||JSON.stringify(window.nativeHomeworkEvents)!==JSON.stringify(config.homeworkEvents||[]);
    if(schoolChanged)window.calendarPendingColors={};
    window.nativeHomeworkEvents=config.homeworkEvents||[];
    window.nativeSchoolEvents=config.schoolEvents||[];
    window.nativeCalendarActivities=config.activities||[];
    if(!window.nativeAcademicRendered){
      window.nativeAcademicRendered=true;
      if(navigation)restoreAcademicNavigation(navigation);else renderAcademicArea(allowed.has(page)?page:'competitions');
    } else if((schoolChanged||memosChanged||pluginChanged||colorsChanged||themeChanged)&&academicTab==='calendar'&&!$('#sheet').open){const y=scrollY;renderCalendar();requestAnimationFrame(()=>scrollTo(0,y));}
    window.nativeCompetitionNotifications=config.notificationsEnabled;
    requestAnimationFrame(()=>host({action:'rendered'}));
  };
  window.nativeAcademicRefresh=()=>{
    if(academicTab==='calendar')return;
    if(academicTab==='exams')syncNotices();else syncAcademicCompetitions();
  };
  window.nativeAcademicEnsureContent=navigation=>{
    if(!$('#content').childElementCount){
      if(navigation)restoreAcademicNavigation(navigation);else renderAcademicArea(allowed.has(page)?page:'competitions');
    }
    resizeSheet();
  };
  window.nativeOpenCalendarDate=date=>{
    if(!/^\d{4}-\d{2}-\d{2}$/.test(date))return;
    calendarSelectedDate=date;calendarMonth=Number(date.slice(5,7));
    calendarYear=Number(date.slice(0,4))-(calendarMonth<9?1:0);
    calendarSource='all';calendarFollowing=false;
    renderAcademicArea('calendar');saveAcademicNavigation();
  };
  document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')window.nativeAcademicEnsureContent();else saveAcademicNavigation();});
  $('#back').onclick=()=>{if(!academicHandleBack())host({action:'back'});};
  const allowed=new Set(['competitions','exams','calendar']);
  if(!allowed.has(page)){location.hash='competitions';}
  document.addEventListener('click',event=>{
    if(event.target.closest('[data-school-calendar]')){saveAcademicNavigation();host({action:'school-calendar'});}
    if(event.target.closest('[data-plugin-market]')){host({action:'plugin-market'});}
    const activity=event.target.closest('[data-native-activity]');
    if(activity){activity.blur();closeSheet();saveAcademicNavigation();host({action:'edit-activity',activityId:activity.dataset.nativeActivity});}
    const tabButton=event.target.closest('[data-academic-tab]');
    if(tabButton)host({action:'tab',tab:tabButton.dataset.academicTab});
    const link=event.target.closest('a[href]');
    if(link){event.preventDefault();host({action:'open',url:link.href});}
  },true);
  const originalSave=portalSave;
  let agendaSignature='';
  const competitionAgendaKeys=new Set([...CompetitionCatalog.items,...CompetitionEditions,...nationalCatalog].map(competitionKey));
  const publishAgenda=()=>{
    const events=allCalendarEvents().filter(e=>!e.id.startsWith('homework:')&&!e.id.startsWith('native:')&&!e.id.startsWith('school:')&&!e.id.startsWith('school-day:')).map(e=>({...e,timetable:false,kind:['activity','exam','competition'].includes(e.kind)?e.kind:e.id.startsWith('exam-')||String(e.origin||'').includes('考试')?'exam':e.phaseId||competitionAgendaKeys.has(e.key)||String(e.origin||'').includes('赛事')?'competition':'activity'}));
    const signature=JSON.stringify(events);
    if(signature!==agendaSignature){agendaSignature=signature;host({action:'events',events});}
  };
  portalSave=(key,value)=>{
    originalSave(key,value);
    if(key==='calendar')publishAgenda();
  };
  const originalRead=localStorage.getItem('qnu-academic-preview-reminders');
  if(originalRead)host({action:'reminders',reminders:JSON.parse(originalRead)});
  persist=()=>{
    try{localStorage.setItem('qnu-academic-preview-reminders',JSON.stringify(saved));}catch(_){}
    host({action:'reminders',reminders:saved});
    publishAgenda();
  };
  const previewToast=toast;
  toast=message=>previewToast(message==='已保存演示提醒，不会发送系统通知'?'已保存关注':message);
  const renderPreviewExams=renderExams;
  renderExams=()=>{
    renderPreviewExams();
    document.querySelectorAll('[data-reminder]').forEach(button=>{
      button.innerHTML=icon(saved[button.dataset.reminder]?'bookmark-check':'bookmark')+(saved[button.dataset.reminder]?'已关注':'关注考试');
    });
    document.querySelector('#content > .footnote')?.remove();
    icons();
    publishAgenda();
  };
  reminder=id=>{
    const exam=exams.find(e=>e.id===id);
    sheet(exam.title,`<p class="sheet-text">${esc(exam.date)} · ${esc(exam.time)}</p><button class="primary" id="native-exam-follow">${saved[id]?'取消关注':'关注考试'}</button>`);
    $('#native-exam-follow').onclick=()=>{
      if(saved[id])delete saved[id];else saved[id]={};
      persist();closeSheet();renderAcademicArea('exams');toast(saved[id]?'已关注考试，已知日期已加入学年日历':'已取消关注');
    };
  };
  exportCalendar=()=>host({action:'export',events:allCalendarEvents()});
  host({action:'ready'});
  publishAgenda();
  notifyFollowedCompetitions();
}
