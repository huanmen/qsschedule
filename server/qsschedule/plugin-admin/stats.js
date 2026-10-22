'use strict';
const $=id=>document.getElementById(id), names={app:'安装包',plugin:'插件资料包',resource:'更新资源'};
let data;
function node(tag,text,cls){const el=document.createElement(tag);if(text!==undefined)el.textContent=text;if(cls)el.className=cls;return el;}
async function load(){
  $('refresh').disabled=true;$('status').textContent='正在读取统计…';
  try{
    const response=await fetch('/api/download-stats?days='+$('days').value,{cache:'no-store'});
    const value=await response.json();if(!response.ok)throw Error(value.error||'读取失败');data=value;
    $('summary').replaceChildren();
    for(const [kind,s] of Object.entries(data.totals)){const card=node('article',undefined,'stat-card');card.append(node('h2',names[kind]),node('p',s.total.toLocaleString(),'stat-total'),node('p',`今日 ${s.today} · 近 7 天 ${s.last7Days} · 所选时段 ${s.selectedPeriod}`));$('summary').append(card);}
    $('files').replaceChildren();for(const row of data.files){const tr=node('tr');for(const text of [row.file,names[row.kind],row.total,row.today,row.last7Days,row.selectedPeriod])tr.append(node('td',text));$('files').append(tr);}
    if(!data.files.length){const tr=node('tr'),td=node('td','暂无下载记录');td.colSpan=6;tr.append(td);$('files').append(tr);}
    $('trend').replaceChildren(node('p','绿色：安装包　蓝色：插件资料包　金色：更新资源','legend'));
    const max=Math.max(1,...data.daily.map(r=>r.app+r.plugin+r.resource));
    for(const day of [...data.daily].reverse()){const row=node('div',undefined,'trend-row'),track=document.createElementNS('http://www.w3.org/2000/svg','svg');track.setAttribute('viewBox','0 0 100 14');track.setAttribute('preserveAspectRatio','none');track.setAttribute('class','trend-track');track.setAttribute('aria-label',`安装包 ${day.app}，插件 ${day.plugin}，资源 ${day.resource}`);let x=0;for(const kind of ['app','plugin','resource']){const bar=document.createElementNS('http://www.w3.org/2000/svg','rect'),width=day[kind]/max*100;for(const [key,value] of Object.entries({x,y:0,width,height:14,class:'trend-bar '+kind}))bar.setAttribute(key,value);track.append(bar);x+=width;}row.append(node('span',day.day),track,node('span',day.app+day.plugin+day.resource));$('trend').append(row);}
    $('completions').replaceChildren();for(const [kind,count] of Object.entries(data.uniqueInstallations))$('completions').append(node('p',`${names[kind]||kind}：累计 ${count} 个匿名安装标识`));
    for(const row of data.completedDownloads)$('completions').append(node('p',`${names[row.kind]||row.kind} · 版本 ${row.version}：${row.count} 次完成回报`));
    if(!data.completedDownloads.length)$('completions').append(node('p','暂无完成回报','empty'));
    $('status').textContent='更新于 '+new Date(data.generatedAt).toLocaleString('zh-CN')+' · 手动刷新';
  }catch(e){$('status').textContent='统计读取失败：'+e.message;}finally{$('refresh').disabled=false;}
}
$('refresh').addEventListener('click',load);$('days').addEventListener('change',load);
$('export').addEventListener('click',()=>{if(!data)return;const escape=v=>'"'+String(v).replaceAll('"','""')+'"';const rows=[['文件','类型','累计','今日','近7天','所选时段'],...data.files.map(r=>[r.file,names[r.kind],r.total,r.today,r.last7Days,r.selectedPeriod])];const url=URL.createObjectURL(new Blob(['\ufeff'+rows.map(r=>r.map(escape).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'}));const a=node('a');a.href=url;a.download='泉师课程表-下载统计.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});load();
