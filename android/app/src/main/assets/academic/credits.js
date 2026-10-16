(function () {
  const docs=[],norm=s=>(s||'').replace(/\s+/g,'');
  function visit(doc){
    if(!doc||docs.includes(doc)||docs.length>=32)return;
    docs.push(doc);for(const frame of doc.querySelectorAll('iframe,frame'))try{visit(frame.contentDocument);}catch(_){}
  }
  visit(document);
  const trusted=url=>url.protocol==='https:'&&url.hostname==='jw.qztc.edu.cn';
  const target=docs.find(d=>{const u=new URL(d.URL);return trusted(u)&&u.pathname==='/academic/manager/score/studentStudyProcess.do';});
  if(!target){
    for(const doc of docs){
      if(!trusted(new URL(doc.URL)))continue;
      const entry=[...doc.querySelectorAll('a')].find(a=>/^(修读进程|学生修读进程|个人修读进程)$/.test(norm(a.textContent)));
      if(entry){const u=new URL(entry.href,doc.baseURI);
        if(!trusted(u)||!['/academic/accessModule.do','/academic/manager/score/studentStudyProcess.do'].includes(u.pathname))return {kind:'schema'};
        if(!doc.defaultView.__qnuStudyOpened){doc.defaultView.__qnuStudyOpened=true;entry.click();}
        return {kind:'opening'};
      }
    }
    return {kind:'waiting'};
  }
  if(target.readyState!=='complete')return {kind:'waiting'};
  const table=[...target.querySelectorAll('table')].find(t=>[...t.rows].some(r=>{
    const h=[...r.cells].map(c=>norm(c.textContent).replace('课组名称','课组'));
    return h.includes('课组')&&h.includes('学分要求')&&h.includes('已获学分');
  }));
  if(!table)return {kind:'schema'};
  const state=target.defaultView.__qnuCreditDetails ||= {base:table.outerHTML,started:Date.now(),blocks:[],index:0,phase:'summary'};
  if(!state.groups){
    const rows=[...table.rows],header=rows.findIndex(r=>[...r.cells].some(c=>norm(c.textContent)==='已获学分'));
    const headers=[...rows[header].cells].map(c=>norm(c.textContent).replace('课组名称','课组'));
    const spans=new Map(),occurrences=new Map();state.groups=[];
    for(const row of rows.slice(header+1)){
      if([...row.cells].some(c=>c.colSpan>1))continue;
      const values=[];let index=0;
      for(let col=0;col<headers.length;col++){
        const span=spans.get(col);
        if(span){values.push(span.text);if(--span.left===0)spans.delete(col);}
        else{const cell=row.cells[index++];if(!cell)break;values.push(cell.textContent.trim());if(cell.rowSpan>1)spans.set(col,{text:cell.textContent.trim(),left:cell.rowSpan-1});}
      }
      if(values.length!==headers.length)continue;
      const fields=Object.fromEntries(headers.map((h,i)=>[h,values[i]]));
      const identity=(fields['教学计划']||'')+'|'+fields['课组'];const count=occurrences.get(identity)||0;occurrences.set(identity,count+1);
      state.groups.push({...fields,id:identity+'|'+count});
    }
  }
  const snapshot=()=>state.base+state.blocks.join('');
  const result=(complete,status)=>snapshot().length>2000000?{kind:'schema'}:{kind:complete?'study':'study-partial',html:snapshot(),status};
  const courseTables=doc=>[...doc.querySelectorAll('table')].filter(t=>[...t.rows].some(r=>{
    const h=[...r.cells].map(c=>norm(c.textContent));return h.includes('课程号')&&h.some(v=>['课程名','课程名称'].includes(v))&&h.includes('学分');
  }));
  function appendCourses(source,groupName,owner){
    const section=target.createElement('section');section.dataset.qnuPlan='true';
    if(owner){section.dataset.qnuGroupId=owner.id;section.dataset.qnuPlanName=owner['教学计划']||'';}
    const normalized=target.createElement('table');
    if(groupName){const caption=target.createElement('caption');caption.textContent=groupName;normalized.append(caption);}
    const header=normalized.insertRow();['课程号','课程名称','学分','修读状态'].forEach(text=>{const th=target.createElement('th');th.textContent=text;header.append(th);});
    const first=[...source.rows].findIndex(r=>[...r.cells].some(c=>norm(c.textContent)==='课程号'));
    [...source.rows].slice(first+1).filter(r=>r.cells.length>=5&&!r.querySelector('th')).forEach(row=>{
      const code=row.cells[0].textContent.trim(),name=row.cells[1].textContent.trim(),credits=row.cells[3].textContent.trim();
      if(!code||!name||!/^\d+(\.\d+)?$/.test(credits))return;
      const status=row.cells[1].querySelector('img[title]')?.title||'';
      const out=normalized.insertRow();[code,name,credits,status].forEach(value=>{out.insertCell().textContent=value;});
    });
    section.append(normalized);state.blocks.push(section.outerHTML);
  }
  if(state.phase==='summary'){
    state.phase='plan';state.frame=target.createElement('iframe');state.frame.hidden=true;
    state.frame.src='https://jw.qztc.edu.cn/academic/student/studyschedule/studyschedule.jsdo';
    target.body.append(state.frame);state.pageStarted=Date.now();
    return result(false,'课组概况已保存，正在获取个人教学计划…');
  }
  let plan;try{plan=state.frame.contentDocument;}catch(_){}
  const u=plan&&new URL(plan.URL);
  if(!plan||!u||!trusted(u)||plan.readyState!=='complete')return {kind:'study-waiting',status:'课组概况已保存，正在等待教学计划…'};
  if(state.phase==='plan'){
    const flat=[...plan.querySelectorAll('a[href]')].find(a=>{
      const url=new URL(a.href,plan.baseURI);
      return trusted(url)&&((url.pathname==='/academic/manager/studyschedule/scheduleJump.jsp'&&url.searchParams.get('link')==='studentScheduleLineShow.do')||url.pathname==='/academic/manager/studyschedule/studentScheduleLineShow.do');
    });
    if(flat){state.phase='flat';state.frame.src=flat.href;state.pageStarted=Date.now();return {kind:'study-waiting',status:'正在读取教学计划平铺明细…'};}
    if(Date.now()-state.pageStarted>45000){state.phase='done';return result(true,'学校教学计划入口未返回，课组概况已保留');}
    return {kind:'study-waiting',status:'正在等待个人教学计划入口…'};
  }
  if(state.phase==='flat'){
    if(u.pathname!=='/academic/manager/studyschedule/studentScheduleLineShow.do'){
      if(Date.now()-state.pageStarted>60000){state.phase='done';return result(true,'教学计划明细未返回，课组概况已保留');}
      return {kind:'study-waiting',status:'正在等待教学计划明细…'};
    }
    state.tables=courseTables(plan);
    if(!state.tables.length){
      if(Date.now()-state.pageStarted<60000)return {kind:'study-waiting',status:'正在等待教学计划课程表…'};
      state.phase='done';return result(true,'学校未返回课程表，课组概况已保留');
    }
    state.electives=[...plan.querySelectorAll('a[href]')].flatMap(a=>{
      const url=new URL(a.href,plan.baseURI);
      if(!trusted(url)||url.pathname!=='/academic/manager/studyschedule/scheduleFreeGroupCourseList.do')return [];
      const owners=state.groups.filter(g=>norm(g['课组'])===norm(a.textContent));
      return owners.length===1?[{url:url.href,name:a.textContent.trim(),owner:owners[0]}]:[];
    });
    state.electiveIndex=0;
    const summary=target.createElement('section');
    // Preserve official totals only; never add overlapping group requirements.
    [...plan.querySelectorAll('td,th,span,p,div')].filter(e=>e.children.length===0).forEach(e=>{
      if(/^(要求总学分|总学分要求|毕业要求学分|已获总学分|已获得总学分|已修总学分)[:：]?\d/.test(norm(e.textContent))){const p=target.createElement('p');p.textContent=e.textContent;summary.append(p);}
    });
    if(summary.childElementCount)state.blocks.push(summary.outerHTML);
    state.phase='batches';
  }
  if(state.phase==='batches'){
    const end=Math.min(state.index+2,state.tables.length);
    for(;state.index<end;state.index++){
      const source=state.tables[state.index];
      let groupTable=source.previousElementSibling;
      while(groupTable&&groupTable.tagName!=='TABLE')groupTable=groupTable.previousElementSibling;
      const groupHeaders=groupTable&&[...groupTable.rows].find(r=>[...r.cells].some(c=>norm(c.textContent)==='课组名称'));
      const nameIndex=groupHeaders?[...groupHeaders.cells].findIndex(c=>norm(c.textContent)==='课组名称'):-1;
      const groupRow=groupHeaders&&[...groupTable.rows].find(r=>!r.querySelector('th')&&r.cells.length>=4&&norm(r.cells[0].textContent)!=='学分'&&r.cells[nameIndex]?.textContent.trim());
      const groupName=groupRow?.cells[nameIndex]?.textContent.trim();
      const pair=cell=>norm(cell?.textContent).match(/^(\d+(?:\.\d+)?)(?:>=|<=|>|<|=)(\d+(?:\.\d+)?)$/)?.slice(1).map(Number);
      const creditsPair=groupRow&&pair(groupRow.cells[3]),countPair=groupRow&&pair(groupRow.cells[4]);
      const owners=state.groups.filter(g=>norm(g['课组'])===norm(groupName)&&
        (!creditsPair||(Number(g['已获学分'])===creditsPair[0]&&Number(g['学分要求'])===creditsPair[1]))&&
        (!countPair||(Number(g['已通过门数'])===countPair[0]&&Number(g['门数要求'])===countPair[1])));
      appendCourses(source,groupName,owners.length===1?owners[0]:null);
    }
    const complete=state.index>=state.tables.length&&!state.electives.length;
    if(state.index>=state.tables.length)state.phase=complete?'done':'elective-open';
    return result(complete,'已保存 '+state.index+' / '+state.tables.length+' 批课组明细');
  }
  if(state.phase==='elective-open'){
    const item=state.electives[state.electiveIndex];
    state.electiveFrame ||= target.createElement('iframe');state.electiveFrame.hidden=true;
    if(!state.electiveFrame.isConnected)target.body.append(state.electiveFrame);
    state.electiveFrame.src=item.url;state.pageStarted=Date.now();state.phase='elective-read';
    return {kind:'study-waiting',status:'正在获取 '+item.name+' 已选课程…'};
  }
  if(state.phase==='elective-read'){
    const item=state.electives[state.electiveIndex];let doc;
    try{doc=state.electiveFrame.contentDocument;}catch(_){}
    const url=doc&&new URL(doc.URL);
    const tables=doc&&url&&trusted(url)&&url.pathname==='/academic/manager/studyschedule/scheduleFreeGroupCourseList.do'&&url.search===new URL(item.url).search&&doc.readyState==='complete'?courseTables(doc):[];
    if(!tables.length&&Date.now()-state.pageStarted<60000)return {kind:'study-waiting',status:'正在等待 '+item.name+' 已选课程…'};
    tables.forEach(t=>appendCourses(t,item.name,item.owner));
    state.electiveIndex++;
    const complete=state.electiveIndex>=state.electives.length;
    state.phase=complete?'done':'elective-open';
    return result(complete,'已保存 '+state.electiveIndex+' / '+state.electives.length+' 个选修课组');
  }
  return result(true,'修读进程获取完成');
})
