'use strict';
// One calendar and wheel surface for all embedded and standalone editing forms.
(() => {
 const css=document.createElement('style');css.textContent=`.datetime-shade{position:fixed;inset:0;background:#0005;z-index:500;display:flex;align-items:flex-end;justify-content:center}.datetime-panel{width:min(100%,480px);background:var(--surface,#fff);color:var(--ink,#17352f);border-radius:24px 24px 0 0;padding:20px;box-sizing:border-box}.datetime-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.datetime-head button,.datetime-grid button{border:0;background:transparent;color:inherit;font:inherit;padding:10px}.datetime-grid{display:grid;grid-template-columns:repeat(7,1fr);text-align:center;gap:3px}.datetime-grid button{aspect-ratio:1;padding:0;border-radius:9px}.datetime-grid button.selected{background:var(--accent);color:white}.datetime-grid button.outside{opacity:.45}.datetime-wheels{display:flex;align-items:center;justify-content:center;gap:20px;margin:18px 0;position:relative}.datetime-wheel{height:220px;overflow-y:auto;scroll-snap-type:y mandatory;overscroll-behavior:contain;width:100%;padding:88px 0;box-sizing:border-box;scrollbar-width:none;position:relative;z-index:1}.datetime-wheel::-webkit-scrollbar{display:none}.datetime-wheel button{display:block;width:100%;height:44px;scroll-snap-align:center;border:0;background:none;color:var(--mut);font-size:24px;padding:0}.datetime-wheel button.selected{color:var(--accent);font-weight:600}.datetime-wheels:before{content:'';position:absolute;left:0;right:0;top:88px;height:44px;border-radius:12px;background:var(--soft)}.datetime-column{flex:1;min-width:0;text-align:center}.datetime-input{cursor:pointer}`;document.head.append(css);
 let active=null;
 function choose(input,kind){
  active?.remove();
  const now=new Date(),day=((input.value||'').split('T')[0]||`${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`);
  let selected=/^\d{4}-\d{2}-\d{2}$/.test(day)?day:new Date().toISOString().slice(0,10),month=new Date(selected+'T12:00:00');
  let stage=kind==='time'||kind==='duration'?'time':'date';
  const overlay=document.createElement('div');overlay.className='datetime-shade';overlay.setAttribute('role','dialog');overlay.setAttribute('aria-label',kind==='time'?'选择时间':'选择日期');active=overlay;
  const panel=document.createElement('div');panel.className='datetime-panel';overlay.append(panel);document.querySelector('.app').append(overlay);
  const close=()=>{overlay.remove();active=null;input.focus();};overlay.addEventListener('click',e=>{if(e.target===overlay)close();});overlay.addEventListener('keydown',e=>{if(e.key==='Escape')close();});
  const commit=value=>{
   if(kind==='duration'){const [h,m]=value.split(':');document.querySelector('#homework-hours').value=+h;document.querySelector('#homework-minutes').value=+m;input.textContent=`截止前 ${+h} 小时 ${+m} 分钟`;}
   else {input.value=value;input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));}
   close();
  };
  function draw(){
   panel.innerHTML=`<div class="datetime-head"><strong>${stage==='date'?'选择日期':'选择时间'}</strong><button data-close aria-label="取消">取消</button></div>`;panel.querySelector('[data-close]').onclick=close;
   if(stage==='date'){
    const y=month.getFullYear(),m=month.getMonth(),first=new Date(y,m,1),start=new Date(y,m,1-(first.getDay()+6)%7);
    panel.insertAdjacentHTML('beforeend',`<div class="datetime-head"><button data-prev aria-label="上个月">‹</button><strong>${y}年${m+1}月</strong><button data-next aria-label="下个月">›</button></div><div class="datetime-grid">${['一','二','三','四','五','六','日'].map(d=>`<small>${d}</small>`).join('')}${Array.from({length:42},(_,i)=>{const d=new Date(start);d.setDate(start.getDate()+i);const date=`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;return `<button data-date="${date}" class="${date===selected?'selected ':''}${d.getMonth()!==m?'outside':''}">${d.getDate()}</button>`;}).join('')}</div><button class="primary" data-confirm>确定</button>`);
    panel.querySelector('[data-prev]').onclick=()=>{month=new Date(y,m-1,1);draw();};panel.querySelector('[data-next]').onclick=()=>{month=new Date(y,m+1,1);draw();};panel.querySelectorAll('[data-date]').forEach(b=>b.onclick=()=>{selected=b.dataset.date;draw();});
    panel.querySelector('[data-confirm]').onclick=()=>{if(kind==='datetime-local'){stage='time';draw();}else commit(selected);};
   }else{
    const time=kind==='duration'?`${document.querySelector('#homework-hours').value}:${document.querySelector('#homework-minutes').value}`:(kind==='datetime-local'?input.value.split('T')[1]:input.value)||'09:00',parts=time.split(':').map(Number);
    panel.insertAdjacentHTML('beforeend',`<div class="datetime-wheels">${['小时','分钟'].map((label,index)=>`<div class="datetime-column"><small>${label}</small><div class="datetime-wheel" aria-label="${label}，滑动选择">${Array.from({length:index?60:kind==='duration'?8761:24},(_,n)=>`<button data-value="${n}">${String(n).padStart(2,'0')}</button>`).join('')}</div></div>`).join('')}</div><button class="primary" data-confirm>确定</button>`);
    const wheels=[...panel.querySelectorAll('.datetime-wheel')];wheels.forEach((w,i)=>{w.scrollTop=(parts[i]||0)*44;const mark=()=>{const n=Math.round(w.scrollTop/44);w.querySelectorAll('button').forEach(b=>b.classList.toggle('selected',+b.dataset.value===n));};mark();w.addEventListener('scroll',mark,{passive:true});w.querySelectorAll('button').forEach(b=>b.onclick=()=>w.scrollTo({top:+b.dataset.value*44,behavior:'smooth'}));});
    panel.querySelector('[data-confirm]').onclick=()=>{const value=wheels.map(w=>String(Math.round(w.scrollTop/44)).padStart(2,'0')).join(':');commit(kind==='datetime-local'?selected+'T'+value:value);};
   }
  }
  draw();panel.querySelector('button').focus();
 }
 function bind(){
  const hours=document.querySelector('#homework-hours'),minutes=document.querySelector('#homework-minutes');
  if(hours&&minutes&&!document.querySelector('#homework-duration')){
   const holder=hours.closest('.homework-offset'),button=document.createElement('button');button.type='button';button.id='homework-duration';button.className='sheet-input';button.textContent=`截止前 ${hours.value} 小时 ${minutes.value} 分钟`;button.onclick=()=>choose(button,'duration');holder.querySelectorAll('label').forEach(label=>label.hidden=true);holder.append(button);
  }
  document.querySelectorAll('input[type="date"],input[type="time"],input[type="datetime-local"]').forEach(input=>{
  const kind=input.type;input.dataset.pickerKind=kind;input.type='text';input.readOnly=true;input.classList.add('datetime-input');input.setAttribute('aria-haspopup','dialog');
  input.reportValidity=()=>{const valid=!!input.value||!input.required;if(!valid){input.focus();toast(kind==='time'?'请选择时间':'请选择日期');}return valid;};
  input.addEventListener('click',()=>choose(input,kind));input.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();choose(input,kind);}});
 });}
 new MutationObserver(bind).observe(document.querySelector('.app'),{childList:true,subtree:true});bind();
})();
