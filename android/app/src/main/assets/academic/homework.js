(function () {
  'use strict';
  const HOME = 'https://eol.qztc.edu.cn/meol/personal.do';
  const state = window.__qztcHomework || (window.__qztcHomework = {busy:false, done:false, rows:[], courses:[], errors:[], status:'正在读取个人空间…'});
  const text = value => String(value == null ? '' : value).replace(/\s+/g,' ').trim();
  const safeUrl = value => {try {const u=new URL(value,HOME);return u.protocol==='https:'&&u.hostname==='eol.qztc.edu.cn'&&!u.username&&!u.password&&(!u.port||u.port==='443')?u.href:null;} catch (_) {return null;}};
  const resourceUrl = value => {try {const u=new URL(value,HOME);return u.protocol==='https:'&&!u.username&&!u.password&&(!u.port||u.port==='443')&&u.hostname?u.href:null;}catch(_){return null;}};
  const unwrap = html => {const source=String(html||''),d=new DOMParser().parseFromString(source,'text/html');return d.querySelector('input[type=hidden][id$="_content"]')?.value||source;};
  const plain = html => {const d=new DOMParser().parseFromString(String(html||''),'text/html');d.querySelectorAll('script,style').forEach(n=>n.remove());d.querySelectorAll('br').forEach(n=>n.replaceWith('\n'));d.querySelectorAll('p,div,li,h1,h2,h3,pre,tr').forEach(n=>n.append('\n'));return (d.body.textContent||'').replace(/[\t ]+\n/g,'\n').replace(/\n{3,}/g,'\n\n').trim();};
  const boolean = value => value===true||value===1||value==='1'||value==='true';
  function statusOf(row) {
    if(boolean(row.recensionStatus))return 'returned';
    const answer = row.answerStatus;
    if(answer===false||answer==='false'||answer===true||answer==='true')return 'submitted';
    if(answer===0||answer==='0')return 'submitted';
    if(answer===1||answer==='1')return 'submitted';
    if(answer!=null&&answer!==''&&answer!=='null')return 'unknown';
    return row.submitStruts===false||row.submitStruts===true||row.submitStruts===0||row.submitStruts==='0'||row.submitStruts===1||row.submitStruts==='1'?'pending':'unknown';
  }
  async function get(url, json=false, authorization='', metadata=false) {
    const target=safeUrl(url);if(!target)throw Error('平台地址无效');
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),25000);
    try {
      const response=await fetch(target,{credentials:'same-origin',cache:'no-store',signal:controller.signal,headers:authorization?{Authorization:authorization}:{}});
      if(!safeUrl(response.url))throw Error('登录已过期或平台跳转，请重新登录');
      if(!response.ok)throw Error('平台暂不可用');
      const body=await response.arrayBuffer();if(body.byteLength>(/\/homework\.js/.test(target)?5000000:3000000))throw Error('页面过大');
      const charset=(response.headers.get('content-type')||'').match(/charset=([^;]+)/i)?.[1]|| (json?'utf-8':'gb18030');
      const source=new TextDecoder(charset).decode(body);
      if(/您尚未登录|登录超时|尚未登录系统/.test(source))throw Error('登录已过期');
      if(!json)return metadata?{source,url:response.url}:source;
      const data=JSON.parse(source);if(Number(data.status)!==1)throw Error(text(data.error)||'作业读取失败');return data.datas;
    } finally {clearTimeout(timer);}
  }
  function documents(doc) {
    const result=[doc];for(const frame of doc.querySelectorAll('iframe,frame'))try{if(frame.contentDocument)result.push(...documents(frame.contentDocument));}catch(_){}return result;
  }
  function coursesFrom(doc,base=HOME) {
    const result=new Map();
    for(const d of documents(doc))for(const a of d.querySelectorAll('a[href],a[onclick]')) {
      const eventLink=a.getAttribute('onclick')?.match(/window\.open\(\s*['"]([^'"]+)['"]/)?.[1];
      const raw=eventLink||a.getAttribute('href');if(!raw)continue;const href=safeUrl(new URL(raw,base).href);if(!href)continue;
      const u=new URL(href),id=u.searchParams.get('courseId')||(/enter_course\.jsp/.test(href)?u.searchParams.get('lid'):null);if(!id||!/^\d+$/.test(id))continue;
      if(/homework|hwTask/.test(href))continue;
      const card=a.closest('tr,li,article,.course,.course-item,.courseList,div');
      const heading=card?.querySelector('h3,h4,h2,.course-name,.courseName,.title');
      let name=text(a.getAttribute('title')||a.textContent);
      if(!name||/进入课程|学习档案|查看|^\d+$/.test(name))name=text(heading?.textContent||card?.textContent).split(/课程编号|主讲教师|进入课程/)[0];
      if(!name||name.length>120)name='课程 '+id;
      const um=[...d.querySelectorAll('a[href]')].map(x=>(x.getAttribute('href')||'').match(/[?&]SID=(\d+)/)?.[1]).find(Boolean)||'';
      if(!result.has(id)||result.get(id).name.startsWith('课程 ')||/course_index\.jsp/.test(href)&&!result.get(id).url.includes('course_index.jsp'))result.set(id,{id,name,url:href,um});
    }
    return [...result.values()];
  }
  async function collectCourseList() {
    const links=documents(document).flatMap(d=>[...d.querySelectorAll('a[href]')]);
    const entry=links.find(a=>/blen\.student\.lesson\.list\.jsp/.test(a.getAttribute('href'))&&!/[?&]ACTION=/i.test(a.getAttribute('href')));
    if(!entry)return;
    let url=safeUrl(new URL(entry.getAttribute('href'),HOME).href);const seen=new Set();
    while(url&&!seen.has(url)&&seen.size<100){
      seen.add(url);const loaded=await get(url,false,'',true),doc=new DOMParser().parseFromString(loaded.source,'text/html');
      const merged=new Map(state.courses.map(c=>[c.id,c]));
      for(const course of coursesFrom(doc,loaded.url)){const old=merged.get(course.id);merged.set(course.id,{...old,...course,um:course.um||old?.um||state.courses.find(c=>c.um)?.um||''});}
      state.courses=[...merged.values()];
      const next=[...doc.querySelectorAll('a[href]')].find(a=>/下一页/.test(a.textContent+' '+(a.querySelector('img')?.alt||'')));
      // Course reordering links are writes despite being GET; never follow ACTION links.
      url=next&&!/[?&]ACTION=/i.test(next.getAttribute('href'))?safeUrl(new URL(next.getAttribute('href'),loaded.url).href):null;
    }
  }
  function homeworkContext(source,base) {
    const doc=new DOMParser().parseFromString(source,'text/html');
    const urls=[...doc.querySelectorAll('iframe[src],a[href]')].map(n=>n.getAttribute('src')||n.getAttribute('href'));
    const inline=[...doc.querySelectorAll('script:not([src])')].map(n=>n.textContent).join('\n');
    const candidate=urls.find(u=>/homework\.html/.test(u))||inline.match(/location\.href\s*=\s*['"]([^'"]*homework\.html[^'"]*)['"]/)?.[1];
    const um=(candidate&&new URL(candidate,base).searchParams.get('um'))||inline.match(/(?:stuId|um|userId)\s*[=:]\s*["']?(\d+)/)?.[1];
    const activity=urls.find(u=>/课程活动/.test(u));
    const authorization=inline.match(/localStorage\.setItem\(\s*['"]token['"]\s*,\s*['"]([^'"]+)['"]\s*\)/)?.[1]||'';
    return {doc,um,url:candidate?new URL(candidate,base).href:null,urls,inline,activity,authorization};
  }
  function detailRecord(course,row,detail,um,submission={}) {
    const id=String(row.id),content=unwrap(detail.content||detail.contentText||detail.contentDecodeView||'');
    const d=new DOMParser().parseFromString(content,'text/html');
    // Inline learning links belong to the body, not a second attachment list.
    const attachments=[...d.querySelectorAll('img[src],a[download],a[href]')].filter(n=>n.tagName==='IMG'||n.hasAttribute('download')||/\/attachment\//i.test(n.getAttribute('href')||'')||/\.(?:pdf|docx?|xlsx?|pptx?|zip|rar|7z)(?:[?#]|$)/i.test(n.getAttribute('href')||''))
      .map(n=>({name:text(n.textContent||n.alt)||'作业附件 / 题图',url:resourceUrl(n.getAttribute('href')||n.getAttribute('src'))})).filter(a=>a.url).slice(0,30);
    const targets=boolean(detail.pubObj)?'全体学生':(detail.pubGroupList||[]).filter(g=>boolean(g.isCheck)).map(g=>text(g.name)).join('、');
    const methods=[[detail.teaCheck,'教师批阅'],[detail.stuCheck,'学生互评'],[detail.selfCheck,'学生自评']].filter(([v])=>boolean(v)).map(([,name])=>name);
    const grade=[row.mark,row.grade].filter(v=>v!=null&&v!==''&&v!=='null').map(text).join(' · ');
    return {id:course.id+':'+id,courseId:course.id,taskId:id,courseName:course.name,title:text(row.title||detail.title),
      publisher:text(row.realName||row.publisher||row.teaName||row.pubUserName),status:statusOf(row),
      deadline:text(detail.deadlineTime||row.deadLine||row.deadline),startAt:text(detail.startDateTime||row.startDateTime),
      submittedAt:text(submission.finishTime||row.finishTime||row.submitTime),grade,
      submissionContent:plain(unwrap(submission.hwaAnswer||'')),feedback:plain(unwrap(submission.comments||'')),
      fullMark:text(detail.fullMark??detail.fullmark??submission.fullMark),content:plain(content),attachments,targets:targets||'平台未提供',
      reviewMethod:methods.join('、')||'平台未提供',reviewStart:text(detail.startDate),reviewEnd:text(detail.endDate),
      multiSubmit:detail.multiSubmit==null?null:boolean(detail.multiSubmit),
      detailUrl:HOME.replace('personal.do','common/homework.html')+'#/homeworkInfo?'+new URLSearchParams({courseId:course.id,hwTaskId:id,um}),
      detailComplete:true};
  }
  async function collectArchives() {
    const root=documents(document).map(d=>[...d.querySelectorAll('script:not([src])')].map(s=>s.textContent).join('\n')).join('\n');
    const path=root.match(/['"]([^'"]*archive_course_list_v8\.jsp[^'"]*)['"]/)?.[1];
    if(!path)return;
    let loaded=await get(new URL(path,HOME).href,false,'',true),doc=new DOMParser().parseFromString(loaded.source,'text/html');
    const all=[...doc.querySelectorAll('a[href]')].find(a=>/archiveCourseListByStu\.do/.test(a.getAttribute('href')));
    if(all){loaded=await get(new URL(all.getAttribute('href'),loaded.url).href,false,'',true);doc=new DOMParser().parseFromString(loaded.source,'text/html');}
    const links=[...doc.querySelectorAll('a[href]')].filter(a=>/archiveCourseStuViewByStu\.do/.test(a.getAttribute('href')));
    const courses=new Map();
    for(const a of links){const url=safeUrl(new URL(a.getAttribute('href'),loaded.url).href);if(!url)continue;const u=new URL(url),id=u.searchParams.get('objectId'),term=u.searchParams.get('termId'),name=text(a.textContent),label=text(a.closest('tr')?.querySelectorAll('td')[2]?.textContent);if(/^\d+$/.test(id)&&/^\d+$/.test(term)&&name&&!/查看/.test(name))courses.set(id+':'+term,{id,term,name,url,label});}
    for(const course of courses.values()) {
      if(!state.courses.some(c=>c.id===course.id))state.courses.push({id:course.id,name:course.name,url:course.url,um:''});
      state.status='正在读取归档 · '+course.name;
      try {
        const landing=await get(course.url,false,'',true),view=new DOMParser().parseFromString(landing.source,'text/html');
        const entry=[...view.querySelectorAll('a[href]')].find(a=>/archiveStuHomeworkView\.do/.test(a.getAttribute('href')));if(!entry)continue;
        let listUrl=safeUrl(new URL(entry.getAttribute('href'),landing.url).href);const visited=new Set();
        while(listUrl&&!visited.has(listUrl)&&visited.size<100) {
          visited.add(listUrl);const list=await get(listUrl,false,'',true),d=new DOMParser().parseFromString(list.source,'text/html');
          for(const tr of d.querySelectorAll('table.valuelist tr')) {
            const cells=[...tr.querySelectorAll('td')],a=cells[0]?.querySelector('a[href]');if(!a)continue;
            const url=safeUrl(new URL(a.getAttribute('href'),list.url).href);if(!url)continue;const task=new URL(url).searchParams.get('id');if(!/^\d+$/.test(task))continue;
            const record={...detailRecord(course,{id:task,title:text(a.textContent)}, {}, ''),id:course.id+':'+task+':archive:'+course.term,archiveTerm:course.term,archiveLabel:course.label,status:/未参与|未提交/.test(text(cells[1]?.textContent))?'pending':/已|参与|提交/.test(text(cells[1]?.textContent))?'submitted':'unknown',grade:text(cells[2]?.textContent).replace(/^[-—]$/,''),detailUrl:url,detailComplete:false};
            try {
              const detail=await get(url),dd=new DOMParser().parseFromString(detail,'text/html');
              const fields=new Map([...dd.querySelectorAll('table.infotable tr')].map(tr=>{const c=tr.querySelectorAll('td');return [text(c[0]?.textContent),c[1]];}));
              const contentCell=fields.get('作业内容'),content=contentCell?.querySelector('input[type=hidden][value]')?.value||contentCell?.innerHTML||'';
              const parsed=detailRecord(course,{id:task,title:record.title},{content},'');
              Object.assign(record,{content:parsed.content,attachments:parsed.attachments,fullMark:text(fields.get('总分')?.textContent),startAt:text(fields.get('发布时间')?.textContent),detailComplete:true});
            }catch(_){state.errors.push(course.name+'：归档详情未读取');}
            state.rows.push(record);await new Promise(resolve=>setTimeout(resolve,80));
          }
          const next=[...d.querySelectorAll('a[href]')].find(a=>/下一页/.test(a.textContent+' '+(a.querySelector('img')?.alt||'')));
          listUrl=next?safeUrl(new URL(next.getAttribute('href'),list.url).href):null;
        }
      }catch(_){state.errors.push(course.name+'：归档作业未读取');}
    }
  }
  async function collectCourse(course) {
    let loaded=await get(course.url,false,'',true),base=loaded.url,ctx=homeworkContext(loaded.source,base);
    // Follow only links served by this course page, never synthesize a course endpoint.
    for(let depth=0;!ctx.url&&depth<3;depth++) {
      const links=[...ctx.doc.querySelectorAll('a[href],iframe[src]')].filter(n=>/课程活动|课程作业|homework|course.*activity/i.test(text(n.textContent)+' '+(n.getAttribute('href')||n.getAttribute('src'))));
      const link=links.map(n=>safeUrl(new URL(n.getAttribute('href')||n.getAttribute('src'),base).href)).find(Boolean);
      if(!link)break;loaded=await get(link,false,'',true);base=loaded.url;ctx=homeworkContext(loaded.source,base);
    }
    // Some courses have no homework module at all.
    if(!ctx.url)return;
    const u=new URL(ctx.url);let um=u.searchParams.get('um')||ctx.um||course.um;
    if(!um)throw Error('未取得作业模块参数');
    const authorization=ctx.authorization;
    const seen=new Set();
    for(let page=1;page<=100;page++) {
      const query=new URLSearchParams({courseId:course.id,um,pagingPage:String(page),pagingNumberPer:'20',title:''});
      const data=await get('https://eol.qztc.edu.cn/meol/hw/stu/hwStuHwtList.do?'+query,true,authorization);
      if(data.hwtList==null&&Number(data.pageInfo?.totalNumberOfEntries)===0)break;
      if(!Array.isArray(data.hwtList))throw Error('作业列表格式变化');
      let added=0;
      for(const row of data.hwtList) {
        if(!/^\d+$/.test(String(row.id))||!text(row.title))continue;
        if(seen.has(String(row.id)))continue;seen.add(String(row.id));added++;
        try {
          const detail=await get('https://eol.qztc.edu.cn/meol/hw/tea/hwTaskInfo.do?'+new URLSearchParams({courseId:course.id,hwTaskId:String(row.id),um}),true,authorization);
          let submission={};
          if(['submitted','returned'].includes(statusOf(row))) {
            try {submission=await get('https://eol.qztc.edu.cn/meol/hw/stu/hwTaskAnswerView.do?'+new URLSearchParams({courseId:course.id,hwtId:String(row.id),um}),true,authorization);}
            catch (_) {state.errors.push(course.name+' · '+text(row.title)+'：提交详情未读取');}
          }
          state.rows.push(detailRecord(course,row,detail,um,submission));
        } catch(error) {
          state.rows.push({...detailRecord(course,row,{},um),detailComplete:false});state.errors.push(course.name+' · '+text(row.title)+'：详情未读取');
        }
        state.status='已读取 '+state.rows.length+' 项作业 · '+course.name;
        await new Promise(resolve=>setTimeout(resolve,80));
      }
      const total=Number(data.pageInfo?.totalNumberOfEntries);
      if(data.hwtList.length&&!added)throw Error('平台分页未更新，已保留读取结果');
      if(data.hwtList.length<20||Number.isFinite(total)&&page*20>=total)break;
      if(page===100)throw Error('课程作业过多，请分批查询');
    }
  }
  if(!state.busy&&!state.done) {
    const docs=documents(document),body=docs.map(d=>d.body?.innerText||'').join('\n');
    if(/尚未登录|登录超时/.test(body))return JSON.stringify({kind:'login'});
    const courses=coursesFrom(document);if(!courses.length)return JSON.stringify({kind:'waiting',status:'正在读取课程列表…'});
    const reminder=docs.flatMap(d=>[...d.querySelectorAll('#reminder li')]).map(n=>text(n.textContent)).filter(t=>/作业/.test(t));
    state.pendingNotice=reminder.join(' · ');state.courses=courses;state.busy=true;
    (async()=>{
      try{await collectCourseList();}catch(_){state.errors.push('完整课程列表未读取，已保留个人空间课程');}
      for(const course of state.courses){state.status='正在读取 '+course.name;try{await collectCourse(course);}catch(e){state.errors.push(course.name+'：'+text(e.message));}}
      try{await collectArchives();}catch(_){state.errors.push('归档课程未读取');}
      state.done=true;state.busy=false;state.status='作业查询完成';
    })();
  }
  return JSON.stringify({kind:state.done?'done':'progress',status:state.status,rows:state.rows,courses:state.courses,pendingNotice:state.pendingNotice,errors:state.errors});
})()
