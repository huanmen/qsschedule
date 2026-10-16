'use strict';
let competitionPresentation = null;
let competitionAnimation = null;
// Use a regular fixed overlay: Android WebView's top-layer dialog can lose its
// nested touch scroller during long reads. Keep one explicit content scroller.
const academicSheet = document.querySelector('#sheet');
Object.defineProperty(academicSheet, 'open', {get:()=>academicSheet.hasAttribute('open')});
let sheetFocus = null;
academicSheet.showModal = () => {
  sheetFocus = document.activeElement;
  competitionObserver?.disconnect();
  academicSheet.setAttribute('open','');
  document.querySelector('#sheet-backdrop').hidden=false;
  document.querySelectorAll('.topbar,#content,.bottom-nav,.preview-controls').forEach(e=>e.inert=true);
  academicSheet.focus({preventScroll:true});
};
academicSheet.close = () => {
  academicSheet.removeAttribute('open');
  document.querySelector('#sheet-backdrop').hidden=true;
  document.querySelectorAll('.topbar,#content,.bottom-nav,.preview-controls').forEach(e=>e.inert=false);
  sheetFocus?.isConnected && sheetFocus.focus({preventScroll:true});
  academicSheet.dispatchEvent(new Event('close'));
};
document.querySelector('#sheet-backdrop').onclick=()=>closeSheet();
document.addEventListener('keydown',event=>{
  if(event.key==='Escape'&&academicSheet.open){event.preventDefault();closeSheet();}
  if(event.key==='Tab'&&academicSheet.open){
    const elements=[...academicSheet.querySelectorAll('button,a[href],input,select,summary,[tabindex="0"]')].filter(e=>!e.disabled&&e.getClientRects().length);
    const first=elements[0],last=elements.at(-1);
    if(event.shiftKey&&(document.activeElement===first||document.activeElement===academicSheet)){event.preventDefault();last?.focus();}
    else if(!event.shiftKey&&(document.activeElement===last||document.activeElement===academicSheet)){event.preventDefault();first?.focus();}
  }
});

const competitionPages = [['overview','概览'],['timeline','时间轴'],['notices','通知'],['history','往届']];
function competitionPagesMarkup(content) {
  return competitionPages.map(([key])=>`<section data-competition-page="${key}">${content[key]||'<p class="sheet-text">暂无资料</p>'}</section>`).join('');
}

function installCompetitionPages() {
  const state=competitionPresentation,scroller=$('#sheet-content');
  const panels=[...scroller.querySelectorAll('[data-competition-page]')];
  if(!panels.length)return;
  const nav=document.createElement('nav');nav.id='competition-detail-tabs';nav.role='tablist';nav.setAttribute('aria-label','赛事详情');
  nav.innerHTML=competitionPages.map(([key,label])=>`<button id="competition-tab-${key}" role="tab" data-competition-tab="${key}" aria-controls="competition-panel-${key}">${label}</button>`).join('');
  scroller.before(nav);
  panels.forEach(panel=>{panel.id='competition-panel-'+panel.dataset.competitionPage;panel.role='tabpanel';panel.setAttribute('aria-labelledby','competition-tab-'+panel.dataset.competitionPage);});
  const select=key=>{
    state.detailTab=key;
    panels.forEach(panel=>{panel.hidden=panel.dataset.competitionPage!==key;});
    nav.querySelectorAll('button').forEach(button=>{const active=button.dataset.competitionTab===key;button.setAttribute('aria-selected',String(active));button.tabIndex=active?0:-1;});
    scroller.scrollTop=state.tabOffsets[key]||0;
  };
  nav.onclick=event=>{
    const button=event.target.closest('[data-competition-tab]');if(!button)return;
    state.tabOffsets[state.detailTab]=scroller.scrollTop;
    select(button.dataset.competitionTab);
  };
  nav.onkeydown=event=>{
    if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;
    event.preventDefault();const buttons=[...nav.querySelectorAll('button')],index=buttons.indexOf(document.activeElement);
    const next=event.key==='Home'?0:event.key==='End'?buttons.length-1:(index+(event.key==='ArrowRight'?1:-1)+buttons.length)%buttons.length;
    buttons[next].click();buttons[next].focus({preventScroll:true});
  };
  select(state.detailTab);
}

function openCompetitionSheet(id, title, html) {
  const previous = competitionPresentation;
  const same = previous?.id === id;
  const contentOffset = same ? $('#sheet-content').scrollTop : 0;
  competitionPresentation = {
    id, title, expanded: same && previous.expanded,
    origin: previous?.origin ?? {x: window.scrollX, y: window.scrollY},
    detailTab: same ? previous.detailTab : 'overview',
    tabOffsets: same ? previous.tabOffsets : {},
  };
  sheet(title, html, true);
  installCompetitionPages();
  applyCompetitionPresentation();
  $('#sheet-content').scrollTop = contentOffset;
}

function applyCompetitionPresentation() {
  const expanded = !!competitionPresentation?.expanded;
  $('#sheet').classList.toggle('competition-detail-page', expanded);
  $('#sheet').classList.toggle('competition-sheet', !!competitionPresentation);
  $('.grip').setAttribute('role', 'button');
  $('.grip').setAttribute('aria-label', '查看赛事详情');
  $('.grip').tabIndex = competitionPresentation ? 0 : -1;
  $('#close-sheet').setAttribute('aria-label', expanded ? '返回赛事列表' : '关闭');
  $('#close-sheet').innerHTML = icon(expanded ? 'arrow-left' : 'x');
  window.resizeAcademicSheet?.();
  if (window.AcademicHost) AcademicHost.postMessage(JSON.stringify({
    action: 'detail', title: expanded ? competitionPresentation.title : null,
  }));
  icons();
}

function expandCompetitionDetail() {
  if (!competitionPresentation || competitionPresentation.expanded) return;
  const dialog = $('#sheet');
  const before = dialog.getBoundingClientRect();
  competitionPresentation.expanded = true;
  applyCompetitionPresentation();
  const after = dialog.getBoundingClientRect();
  if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
    competitionAnimation?.cancel();
    competitionAnimation = dialog.animate([
      {transform:`translateY(${before.top-after.top}px)`,height:`${before.height}px`,borderRadius:'10px 10px 0 0'},
      {transform:'translateY(0)',height:`${after.height}px`,borderRadius:'0'},
    ],{duration:300,easing:'cubic-bezier(.22,1,.36,1)'});
  }
}

function resetCompetitionPresentation() {
  competitionAnimation?.cancel();
  competitionAnimation = null;
  const origin = competitionPresentation?.origin;
  competitionPresentation = null;
  $('#competition-detail-tabs')?.remove();
  $('#sheet').classList.remove('competition-detail-page', 'competition-sheet');
  if (!academicSheet.open) document.documentElement.classList.remove('sheet-open');
  if (window.AcademicHost) AcademicHost.postMessage(JSON.stringify({action:'detail', title:null}));
  if (origin) requestAnimationFrame(()=>window.scrollTo(origin.x, origin.y));
  competitionObserver?.disconnect();
}

function academicHandleBack() {
  if (!$('#sheet').open) return false;
  closeSheet();
  return true;
}

document.addEventListener('DOMContentLoaded',()=>{
  const grip = $('.grip');
  let start = null;
  grip.addEventListener('pointerdown',event=>{
    if (!competitionPresentation) return;
    start = {x:event.clientX,y:event.clientY};
    grip.setPointerCapture(event.pointerId);
  });
  grip.addEventListener('pointerup',event=>{
    if (start && start.y-event.clientY>35 && Math.abs(start.x-event.clientX)<100) expandCompetitionDetail();
    start = null;
  });
  grip.addEventListener('pointercancel',()=>{start=null;});
  grip.addEventListener('click',expandCompetitionDetail);
  grip.addEventListener('keydown',event=>{
    if (event.key==='Enter'||event.key===' ') {event.preventDefault();expandCompetitionDetail();}
  });
  $('#sheet').addEventListener('cancel',event=>{event.preventDefault();closeSheet();});
});
