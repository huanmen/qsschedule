'use strict';
const $ = id => document.getElementById(id);
const clone = value => JSON.parse(JSON.stringify(value));
const labels = {id:'标识',name:'名称',title:'标题',summary:'简介',category:'分类',current:'当前资料',history:'往期资料',fields:'事实字段',value:'内容',known:'已核实',sources:'来源',url:'来源网址',label:'名称 / 届次',checkedAt:'核对日期',edition:'年份 / 届次',stages:'时间节点',notes:'备注',channels:'更新渠道',references:'参考资料',year:'年份',date:'日期',start:'开始',end:'结束',time:'时分',precision:'时间精度',status:'状态',statusLabel:'状态说明',reason:'说明',method:'更新方式',stability:'稳定性',evidence:'核对依据',updateStatus:'更新状态',aliases:'名称别名',packageName:'应用包名',activityNames:'限定页面',targetId:'目标控件',textPrefix:'文字前缀',selectors:'匹配条件',requiredSelectors:'必要条件',cooldownMs:'冷却时间（毫秒）',matchWindowMs:'匹配窗口（毫秒）',source:'来源说明',rules:'规则条目',appLabel:'应用名称',version:'规则包版本',pluginVersion:'插件版本',schemaVersion:'格式版本',data:'资料',records:'赛事与考证条目'};
let state, active, dirty=false, packageData=null, packageDirty=false, rawPending=false, selectedRecord=0, showLimit=40, filter='all', tab='metadata', noticeTimer, fragmentApply;
const statusName = {published:'已发布',draft:'草稿',unpublished:'已下架'};

function notice(message, error=false) { $('notice').textContent=message; $('notice').className='visible'+(error?' error':''); clearTimeout(noticeTimer); noticeTimer=setTimeout(()=>$('notice').className='',error?10000:4500); }
async function api(path, body, raw=false) {
  const response=await fetch(path,body===undefined?{cache:'no-store'}:{method:'POST',headers:{'Content-Type':'application/json','X-QSS-CSRF':state.csrf},body:raw?body:JSON.stringify(body)});
  const value=await response.json(); if(response.status===401&&location.pathname.startsWith('/qsschedule/manage/')) location.assign('/qsschedule/manage/login'); if(!response.ok) throw Error(value.error||'操作失败'); return value;
}
function on(id, event, fn) { $(id).addEventListener(event,async e=>{try{await fn(e);}catch(error){notice(error.message,true);}}); }
function button(text,fn,cls='') {const b=document.createElement('button'); b.type='button'; b.textContent=text;b.className=cls;b.addEventListener('click',async()=>{try{await fn();}catch(e){notice(e.message,true);}});return b;}
function el(tag,text,cls) {const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;}
function mark() {dirty=true; $('dirty-state').textContent='有未保存修改';}
async function confirmAction(title,text,ok='确认') {$('confirm-title').textContent=title;$('confirm-text').textContent=text;$('confirm-ok').textContent=ok;const dialog=$('confirm-dialog');dialog.showModal();return new Promise(resolve=>dialog.addEventListener('close',()=>resolve(dialog.returnValue==='confirm'),{once:true}));}
async function mayLeave() {return !dirty||await confirmAction('放弃未保存修改？','当前修改尚未保存，离开后将丢失。','放弃修改');}
function draftFromForm() {
  const data=active.draft, form=$('metadata-form');
  for(const key of ['id','name','summary','kind','rulesUrl','notes']) data[key]=form.elements.namedItem(key).value.trim();
  for(const key of ['version','minAppVersion','minAdapterVersion']) data[key]=Number(form.elements.namedItem(key).value);
  return data;
}
function fillForm() {const form=$('metadata-form');for(const [key,value] of Object.entries(active.draft)){const field=form.elements.namedItem(key);if(field)field.value=value??'';}form.elements.namedItem('id').disabled=active.revision>0;form.elements.namedItem('kind').disabled=active.revision>0||active.draft.id in {'adskip':1,'academic-records':1};}
function packageInfo() {
  const p=active.draft.package;
  $('package-info').textContent=p?`${(p.bytes/1024).toFixed(1)} KB · SHA-256 ${p.sha256.slice(0,16)}…${packageDirty?' · 内容已修改':''}`:packageData?'已创建内容，保存草稿时生成资料包':'尚未上传';
  const native=active.draft.kind==='builtin'&&active.draft.id!=='adskip';
  $('kind-help').textContent=native?'此插件使用已编译的原生页面，可管理名称和上架状态；更改功能需要随 App 发版。':active.draft.id==='academic-records'?'可编辑各项赛事事实、时间轴、来源与核对日期。修改已发布资料后，请准备更高版本再发布。':'可编辑宿主 v1/v2 格式的广告规则。App 安装时还会执行完整的兼容性检查，受保护的引擎规则仍由宿主管理。';
  $('upload').disabled=native;$('export').disabled=!p&&!packageData;$('clear-package').disabled=!p&&!packageData;
}
function renderList() {
  const box=$('plugins');box.replaceChildren();const query=$('search').value.toLowerCase();
  const rows=state.plugins.filter(p=>(filter==='all'||p.status===filter)&&`${p.draft.name} ${p.id}`.toLowerCase().includes(query));
  $('count').textContent=`${rows.length} / ${state.plugins.length}`;
  for(const row of rows) {
    const b=button('',()=>select(row),'plugin-row'+(active?.id===row.id?' selected':''));
    b.append(el('strong',row.draft.name),el('p',row.draft.summary));const foot=el('div',undefined,'row-foot');foot.append(el('span',statusName[row.status],'badge '+row.status),el('span',`v${row.published?.version??row.draft.version}`,'mono'));b.append(foot);if(state.downloads)b.append(el('p',`完成下载 ${state.downloads[row.id]||0} 次`));box.append(b);
  }
  if(!rows.length)box.append(el('p','没有符合条件的插件。','no-records'));
}
function renderHeader() {
  $('empty').hidden=true;$('editor').hidden=false;$('plugin-id').textContent=active.draft.id||'新插件';$('plugin-title').textContent=active.draft.name||'添加插件';
  $('state-badge').textContent=statusName[active.status];$('state-badge').className='badge '+active.status;
  $('published-info').textContent=active.status==='published'?`市场正在使用 v${active.published.version} · ${active.updatedAt}`:active.status==='unpublished'?'已从市场下架，已安装用户的数据保留。':'尚未发布到市场';
  if(active.status==='published'&&active.draft.version!==active.published.version)$('published-info').textContent+=` · 草稿 v${active.draft.version}`;
  $('unpublish').disabled=active.status!=='published';$('publish').textContent=active.status==='published'?'发布更新':'发布';
  $('dirty-state').textContent=dirty?'有未保存修改':'草稿已保存';packageInfo();
}
async function select(row,force=false) {
  if(!force&&!await mayLeave())return;
  active=clone(row);dirty=false;packageData=null;packageDirty=false;rawPending=false;selectedRecord=0;showLimit=40;
  $('record-search').value='';$('raw-wrap').hidden=true;$('structured').hidden=false;$('raw-toggle').textContent='完整 JSON';fillForm();renderList();renderHeader();await switchTab('metadata');
}
async function reload(keep=true) {
  const id=keep?active?.id:null;state=await api('/api/state');
  $('environment').textContent=`本机管理 · 数据目录：${state.dataDirectory} · 发布目标：${state.publicBase}`;
  const row=state.plugins.find(p=>p.id===id);if(row)await select(row,true);else renderList();
}
async function switchTab(next) {
  tab=next;for(const name of ['metadata','content','history'])$(name).hidden=name!==next;
  for(const b of $('tabs').children)b.setAttribute('aria-pressed',String(b.dataset.tab===next));
  if(next==='content'){await loadPackage();renderRecords();}
  if(next==='history')renderHistory();
}
function defaultPackage() {
  if(active.draft.id==='academic-records')return {schemaVersion:1,pluginId:'academic-records',pluginVersion:active.draft.version,data:{records:[],aliases:{}}};
  return {schemaVersion:2,version:1,name:active.draft.name,source:'管理员维护',rules:[]};
}
async function loadPackage() {
  if(packageData)return;
  packageData=active.draft.package?await api('/api/package?sha='+active.draft.package.sha256):defaultPackage();
}
function collection() {return packageData?.data?.records??packageData?.rules??null;}
function contentMark() {packageDirty=true;mark();packageInfo();}
function editFragment(title,value,apply) {
  $('fragment-title').textContent=title;$('fragment').value=JSON.stringify(value,null,2);$('fragment-error').textContent='';fragmentApply=apply;$('fragment-dialog').showModal();
}
function tree(value,key,set,path=[],depth=0) {
  const wrap=el('div',undefined,'tree-field');const title=labels[key]||String(key);
  if(value!==null&&typeof value==='object') {
    const details=el('details'),summary=el('summary',`${title} · ${Array.isArray(value)?value.length+' 项':Object.keys(value).length+' 个字段'}`);details.append(summary);
    let built=false;details.addEventListener('toggle',()=>{if(!details.open||built)return;built=true;const children=el('div',undefined,'tree-children');
      children.append(button('修改此段 JSON',()=>editFragment(title,value,updated=>{set(updated);contentMark();renderRecordEditor();}),'tiny'));
      if(depth>15)children.append(el('p','内容层级较深，请使用此段 JSON 编辑。','muted'));
      else for(const childKey of Object.keys(value).slice(0,100))children.append(tree(value[childKey],childKey,next=>{value[childKey]=next;},[...path,key],depth+1));
      if(Object.keys(value).length>100)children.append(el('p','这里只展示前 100 项，可使用此段 JSON 编辑全部内容。','muted'));details.append(children);
    });wrap.append(details);if(depth===0)details.open=true;
  } else {
    const label=el('label',title),fieldPath=el('div',[...path,key].join(' / '),'field-path');wrap.append(fieldPath,label);
    let input;
    if(typeof value==='boolean') {input=document.createElement('select');for(const [v,t] of [['true','是'],['false','否']]){const option=el('option',t);option.value=v;input.append(option);}input.value=String(value);}
    else if(typeof value==='number') {input=document.createElement('input');input.type='number';input.value=String(value);}
    else {input=document.createElement('textarea');input.rows=typeof value==='string'&&value.length>140?4:2;input.value=value===null?'null':String(value);}
    input.setAttribute('aria-label',[...path,title].join(' / '));label.append(input);
    input.addEventListener('input',()=>{set(typeof value==='boolean'?input.value==='true':typeof value==='number'?Number(input.value):value===null&&input.value==='null'?null:input.value);contentMark();});
  }return wrap;
}
function recordTitle(item,index) {return item.name||item.title||item.id||`条目 ${index+1}`;}
function renderRecords() {
  const records=collection(),list=$('record-list');list.replaceChildren();$('package-settings').disabled=!packageData;
  const native=active.draft.kind==='builtin'&&active.draft.id!=='adskip';$('add-record').disabled=native;$('raw-toggle').disabled=native;
  if(native){$('record-editor').replaceChildren(el('p','此插件没有可替换的规则或资料。','help'));return;}
  if(!records){list.append(el('p','没有标准条目列表，可编辑完整 JSON。','help'));renderRecordEditor(true);return;}
  const query=$('record-search').value.toLowerCase(),matches=records.map((r,i)=>[r,i]).filter(([r])=>!query||JSON.stringify(r).toLowerCase().includes(query));
  for(const [record,index] of matches.slice(0,showLimit)){list.append(button(recordTitle(record,index),()=>{selectedRecord=index;renderRecords();},index===selectedRecord?'selected':''));}
  $('more-records').hidden=matches.length<=showLimit;
  if(!matches.length)list.append(el('p','没有符合条件的条目。','no-records'));
  renderRecordEditor();
}
function renderRecordEditor(root=false) {
  const box=$('record-editor');box.replaceChildren();const records=collection();
  if(root||selectedRecord===-1){box.append(tree(packageData,'资料包',next=>{packageData=next;}));return;}
  if(!records||!records[selectedRecord]){box.append(el('p','选择一个条目，或添加新条目。','help'));return;}
  const index=selectedRecord,item=records[index],actions=el('div',undefined,'record-actions');
  actions.append(button('复制条目',()=>{const copy=clone(item);copy.id=(copy.id||'entry')+'-copy-'+Date.now().toString(36);records.push(copy);selectedRecord=records.length-1;contentMark();renderRecords();},'tiny'),
    button('删除条目',async()=>{if(await confirmAction('删除这条资料？',recordTitle(item,index)+'。修改将在保存和发布后生效。','删除条目')){records.splice(index,1);selectedRecord=Math.max(0,index-1);contentMark();renderRecords();}},'tiny danger'));
  box.append(actions,tree(item,recordTitle(item,index),next=>{records[index]=next;}));
}
function applyRaw() {if(!rawPending)return;try{const value=JSON.parse($('raw').value);if(!value||Array.isArray(value)||typeof value!=='object')throw Error('资料包须为 JSON 对象');packageData=value;rawPending=false;contentMark();}catch(e){throw Error('完整 JSON 有误：'+e.message);}}
async function preparePackage() {
  applyRaw();
  if(packageDirty){
    if(active.draft.kind==='academic-records')packageData.pluginVersion=active.draft.version;
    active.draft.package=await api('/api/upload',JSON.stringify(packageData),true);packageDirty=false;
  }
}
async function save() {
  if(!$('metadata-form').reportValidity())throw Error('请补全基本信息');draftFromForm();await preparePackage();
  await api('/api/save',{data:active.draft,revision:active.revision});const id=active.draft.id;dirty=false;state=await api('/api/state');await select(state.plugins.find(p=>p.id===id),true);notice('草稿已保存，市场版本未改变');
}
async function publish() {
  if(!await confirmAction('发布到插件市场？','发布会使当前草稿对用户可见。已发布资料的内容变化需要增加版本号。','发布'))return;
  await save();await api('/api/publish',{data:{id:active.id},revision:active.revision});await reload();notice('已发布到当前服务的数据目录');
}
function renderHistory() {
  const box=$('history-list');box.replaceChildren();const rows=state.audit.filter(a=>a.plugin===active.id);
  for(const row of rows){const div=el('div',undefined,'history-row'),info=el('div');info.append(el('p',row.action),el('p',new Date(row.at).toLocaleString('zh-CN'),'muted'));div.append(info,button('恢复为草稿',async()=>{
    if(!await mayLeave())return;active.draft=await api('/api/snapshot?seq='+row.seq);packageData=null;packageDirty=false;rawPending=false;$('raw-wrap').hidden=true;$('structured').hidden=false;$('raw-toggle').textContent='完整 JSON';fillForm();mark();renderHeader();await switchTab('metadata');notice('已载入历史内容，请保存草稿；发布需要兼容的更高版本');
  }));box.append(div);}
  if(!rows.length)box.append(el('p','保存后会在这里留下版本记录。','help'));
}
async function actionBusy(fn) {const controls=['save','publish','unpublish','reload','new'];controls.forEach(id=>$(id).disabled=true);try{await fn();}finally{controls.forEach(id=>$(id).disabled=false);if(active)renderHeader();}}
on('metadata-form','submit',e=>e.preventDefault());on('metadata-form','input',()=>{mark();});on('metadata-form','change',()=>{draftFromForm();packageInfo();});
on('search','input',renderList);on('record-search','input',()=>{showLimit=40;renderRecords();});on('more-records','click',()=>{showLimit+=40;renderRecords();});
on('filters','click',e=>{if(!e.target.dataset.status)return;filter=e.target.dataset.status;for(const b of $('filters').children)b.setAttribute('aria-pressed',String(b.dataset.status===filter));renderList();});
on('tabs','click',async e=>{if(e.target.dataset.tab)await switchTab(e.target.dataset.tab);});
on('reload','click',async()=>{if(await mayLeave())await actionBusy(()=>reload());});
on('new','click',async()=>{if(!await mayLeave())return;await select({id:'',draft:{id:'',name:'',summary:'',kind:'adskip-rule-pack',version:1,minAppVersion:13,minAdapterVersion:1,rulesUrl:'',package:null,notes:''},published:null,status:'draft',revision:0,updatedAt:''},true);$('metadata-form').elements.namedItem('name').focus();});
on('next-version','click',async()=>{draftFromForm();active.draft.version=Math.max(active.draft.version,active.published?.version||0)+1;await loadPackage();if(active.draft.kind==='academic-records')packageData.pluginVersion=active.draft.version;else if(active.draft.id==='adskip'||active.draft.kind==='adskip-rule-pack')packageData.version=(packageData.version||0)+1;packageDirty=active.draft.kind!=='builtin'||active.draft.id==='adskip';fillForm();mark();packageInfo();notice('已准备新版本，填写修改后保存草稿');});
on('upload','click',()=>$('file').click());
on('file','change',async()=>{const file=$('file').files[0];if(!file)return;try{if(file.size>4000000)throw Error('资料包须小于 4 MB');const raw=await file.text();const parsed=JSON.parse(raw);if(!parsed||Array.isArray(parsed)||typeof parsed!=='object')throw Error('资料包须为 JSON 对象');active.draft.package=await api('/api/upload',raw,true);packageData=parsed;packageDirty=false;rawPending=false;$('raw-wrap').hidden=true;$('structured').hidden=false;$('raw-toggle').textContent='完整 JSON';mark();packageInfo();if(tab==='content')renderRecords();notice('资料包已导入草稿，请核对插件版本');}finally{$('file').value='';}});
on('export','click',async()=>{await loadPackage();applyRaw();const raw=JSON.stringify(packageData,null,2),url=URL.createObjectURL(new Blob([raw],{type:'application/json'})),link=document.createElement('a');link.href=url;link.download=`${active.draft.id||'plugin'}-v${active.draft.version}.json`;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});
on('clear-package','click',async()=>{if(!await confirmAction('移除草稿资料包？','已发布版本仍然保留。','移除'))return;active.draft.package=null;packageData=null;packageDirty=false;rawPending=false;$('raw-wrap').hidden=true;$('structured').hidden=false;$('raw-toggle').textContent='完整 JSON';mark();packageInfo();if(tab==='content'){await loadPackage();renderRecords();}});
on('raw-toggle','click',async()=>{await loadPackage();if($('raw-wrap').hidden){$('raw').value=JSON.stringify(packageData,null,2);$('raw-wrap').hidden=false;$('structured').hidden=true;$('raw-toggle').textContent='逐字段编辑';}else{applyRaw();$('raw-wrap').hidden=true;$('structured').hidden=false;$('raw-toggle').textContent='完整 JSON';renderRecords();}});
on('raw','input',()=>{rawPending=true;mark();});on('apply-raw','click',()=>{applyRaw();notice('JSON 修改已应用，尚未保存');});on('format-raw','click',()=>{$('raw').value=JSON.stringify(JSON.parse($('raw').value),null,2);});
on('package-settings','click',()=>{selectedRecord=-1;renderRecordEditor();});
on('add-record','click',async()=>{await loadPackage();const records=collection();if(!records)throw Error('请使用完整 JSON 添加条目列表');const item=active.draft.kind==='academic-records'?{id:'record-'+Date.now().toString(36),name:'新赛事',category:'赛事',current:{label:'',year:new Date().getFullYear(),fields:{},stages:[],notes:[]},history:[],channels:[],references:[],updateStatus:'unknown'}:{id:'rule-'+Date.now().toString(36),name:'新规则',packageName:'com.example.app',activityNames:[],selectors:[{text:'跳过'}]};records.push(item);selectedRecord=records.length-1;contentMark();$('record-search').value='';showLimit=records.length;renderRecords();});
on('fragment-form','submit',e=>{e.preventDefault();try{const value=JSON.parse($('fragment').value);fragmentApply(value);$('fragment-dialog').close();}catch(error){$('fragment-error').textContent=error.message;}});on('fragment-cancel','click',()=>$('fragment-dialog').close());
on('save','click',()=>actionBusy(save));on('publish','click',()=>actionBusy(publish));on('unpublish','click',()=>actionBusy(async()=>{if(!await confirmAction('将插件下架？','市场停止展示和分发此插件。已安装用户可以继续使用和删除现有版本。','下架'))return;await api('/api/unpublish',{data:{id:active.id},revision:active.revision});await reload();notice('插件已下架');}));
on('preview','click',()=>{const box=$('preview-list');box.replaceChildren();for(const row of state.plugins.filter(p=>p.status==='published')){const p=row.published,card=el('article',undefined,'preview-card');card.append(el('h3',p.name),el('p',p.summary),el('p',`${p.id} · v${p.version}`,'mono'));box.append(card);}if(!box.children.length)box.append(el('p','市场暂无已发布插件。','help'));$('preview-dialog').showModal();});on('preview-close','click',()=>$('preview-dialog').close());
window.addEventListener('beforeunload',e=>{if(dirty){e.preventDefault();e.returnValue='';}});
reload(false).then(()=>{if(state.plugins.length)return select(state.plugins[0],true);}).catch(e=>notice(e.message,true));
