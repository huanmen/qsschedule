'use strict';
// Public feeds enrich the existing school/curated catalog. They never add cards.
let communityFeed=portalRead('community-feed',window.CommunityFeed||{schemaVersion:1,items:[],sources:[]});
if(Date.parse(window.CommunityFeed?.checkedAt||'')>Date.parse(communityFeed.checkedAt||''))communityFeed=window.CommunityFeed;
const communityUrl=value=>{try{const u=new URL(value);return u.protocol==='https:'&&!u.username&&!u.password?u.href:null;}catch(_){return null;}};
const communityDate=value=>{const m=String(value||'').match(/(20\d{2})[-/年.](\d{1,2})[-/月.](\d{1,2})/);if(!m)return null;const out=`${m[1]}-${m[2].padStart(2,'0')}-${m[3].padStart(2,'0')}`;const at=new Date(out+'T00:00:00Z');return Number.isNaN(at.valueOf())||at.toISOString().slice(0,10)!==out?null:out;};
function communityName(name){
 return normalizeCompetitionName(String(name).replace(/^\s*20\d{2}\s*年?\s*/,'').replace(/^第[一二三四五六七八九十百\d]+届\s*/,''));
}
let communityIndexFeed=null,communityIndex=new Map();
function communityMatch(record){
 const year=Number(record.year);if(!year)return null;
 const name=communityName(record.name);
 if(communityIndexFeed!==communityFeed){
  communityIndexFeed=communityFeed;communityIndex=new Map();
  for(const row of communityFeed.items||[]){
   if(row.kind!=='competition'||row.estimated||!Number(row.year)||!communityUrl(row.sourceUrl))continue;
   const key=Number(row.year)+':'+communityName(row.name);if(!communityIndex.has(key))communityIndex.set(key,[]);communityIndex.get(key).push(row);
  }
 }
 const rows=communityIndex.get(year+':'+name)||[];
 // Ambiguous editions are not automatically combined.
 const bySource=new Map();for(const row of rows){if(bySource.has(row.sourceId))return null;bySource.set(row.sourceId,row);}
 return rows.find(row=>row.sourceId==='campus')||rows[0]||null;
}
const enrichExistingCompetition=enrichCompetition;
enrichCompetition=function(record){return {...enrichExistingCompetition(record),publicSchedule:communityMatch(record)};};
const existingTimelineNodes=timelineNodes;
timelineNodes=function(record){
 const nodes=existingTimelineNodes(record),row=record.publicSchedule||communityMatch(record);
 if(!row)return nodes;
 for(const phase of row.phases||[]){
  if(phase.estimated||nodes.some(n=>n.phase===phase.name))continue;
  const exact=row.precision==='day'?communityDate(phase.date):null;
  const month=!exact&&String(phase.label||'').match(/(20\d{2})[-年]\s*(\d{1,2})(?:\s*月)?/);
  if(exact)nodes.push({year:Number(exact.slice(0,4)),month:Number(exact.slice(5,7)),day:Number(exact.slice(8,10)),phase:phase.name});
  else if(month&&Number(month[2])>=1&&Number(month[2])<=12)nodes.push({year:Number(month[1]),month:Number(month[2]),day:null,phase:phase.name});
 }
 return nodes.sort((a,b)=>(a.year-b.year)||(a.month-b.month)||((a.day||0)-(b.day||0)));
};
const existingTimelineMarkup=timelineMarkup;
timelineMarkup=function(record){
 const row=record.publicSchedule||communityMatch(record),original=existingTimelineMarkup(record);
 if(!row)return original;
 const phases=(row.phases||[]).filter(p=>p.date||p.label);
 return original+(phases.length?'<h3 class="section-label">公开来源补充 · '+esc(row.year)+' 届</h3>'+stageTimelineMarkup(phases.map(p=>[p.name,p.estimated||row.estimated?'参考日期待官方核实':communityDate(p.date)||p.label||'日期未公布'])):'');
};
const existingRecordSheet=openCompetitionSheet;
openCompetitionSheet=function(id,title,html){
 const record=findCompetition(id),row=record&&communityMatch(record);
 if(row){
  const links=[['公开数据来源',row.sourceUrl],['当届报名入口',row.registrationUrl],['当届查询入口',row.queryUrl]].filter(([,u])=>communityUrl(u));
  const sources='<div class="sources">'+links.map(([label,url])=>'<a href="'+esc(communityUrl(url))+'" target="_blank" rel="noopener">'+esc(label)+icon('external-link')+'</a>').join('')+'</div>';
  html=html.replace('<section data-competition-page="notices">','<section data-competition-page="notices">'+sources);
 }
 existingRecordSheet(id,title,html);
};
// Official exam notices update the same exam cards, scoped to the selected period
// and written/oral level. Estimated countdown feeds never become official dates.
function liveExamStages(exam){
 const period=examHistoricalPeriod||new Date().getFullYear()+'-'+(new Date().getMonth()>=7?'autumn':'spring');
 const level={cet4:/四级|CET[ -]?4/i,cet6:/六级|CET[ -]?6/i,set4:/四级|SET[ -]?4/i,set6:/六级|SET[ -]?6/i}[exam.id];
 const rows=examRecordNotices(exam).filter(n=>examNoticeMatchesPeriod(n,period)&&communityUrl(n.url)).sort((a,b)=>String(b.publishedAt).localeCompare(String(a.publishedAt)));
 const stages=[['报名',/报名时间|报名日期/],['准考证',/准考证.{0,8}打印|打印.{0,8}准考证/],['考试',/考试时间|考试日期|测试时间|笔试时间|口试时间/],['成绩与证书',/成绩查询时间|成绩发布时间|证书领取时间/]];
 return stages.map(([name,pattern])=>{
  for(const notice of rows){
   const text=String(notice.content||notice.excerpt||'').replace(/(\d)\s+(?=\d)/g,'$1').replace(/(\d)\s*([年月日时分])/g,'$1$2').replace(/([年月日])\s+(?=\d)/g,'$1').replace(/\s*([:：])\s*/g,'$1').replace(/--+/g,'-').replace(/\s+/g,' ');
   const combined=/四[、，和及\s]*六级|四六级/.test(text+' '+notice.title);
   if(level&&(!(level.test(text+' '+notice.title)||combined)||(/口语|口试/.test(notice.title)&&exam.id.startsWith('cet'))||(/笔试/.test(notice.title)&&exam.id.startsWith('set'))||(!/口语|口试|CET-SET/i.test(text+' '+notice.title)&&exam.id.startsWith('set'))))continue;
   let snippet='';
   if(level&&name==='考试'){
    // A combined CET notice contains four exams. Restrict the sentence/table
    // to the requested written/oral level before extracting its date.
    const oral=exam.id.startsWith('set'),cut=text.search(/(?:\d[.、]\s*)?口试考试时间/);
    const part=oral&&cut>=0?text.slice(cut).split(/二[、.]/)[0]:!oral&&cut>=0?text.slice(0,cut):text;
    const precise={cet4:/英语四级考试|CET4/i,cet6:/英语六级考试|CET6/i,set4:/英语四级口语考试|CET-SET4/i,set6:/英语六级口语考试|CET-SET6/i}[exam.id];
    const at=part.search(precise);
    // Older school PDFs describe both written levels in a two-column table.
    // Use the explicit heading/date and column order, never invigilation rows.
    const combinedDate=combined&&!oral&&part.match(/考试日期(?:及时间)?[:：\s]*((?:20\d{2}年\s*)?\d{1,2}月\s*\d{1,2}日)\s*(\d{1,2}:\d{2}-\d{1,2}:\d{2})\s*[、，,]\s*(\d{1,2}:\d{2}-\d{1,2}:\d{2})/);
    if(combinedDate&&/四级\s*考试\s*时间\s*六级\s*考试\s*时间/.test(part)){
     snippet=combinedDate[1]+' · '+combinedDate[exam.id==='cet4'?2:3];
    }else if(oral&&combined&&/口语|口试/.test(notice.title)&&/\d{1,2}月\s*\d{1,2}日[、，]\s*\d{1,2}日/.test(part)){
     snippet=part.match(/(?:20\d{2}年)?\d{1,2}月\s*\d{1,2}日[、，]\s*\d{1,2}日/)[0]+' · 各级场次以准考证为准';
    }else{
    if(at<0)continue;
    const before=part.slice(Math.max(0,at-65),at),after=part.slice(at,at+100).split(/[。；;]/)[0];
    const dates=[...before.matchAll(/(?:20\d{2}年\s*)?\d{1,2}月\s*\d{1,2}日/g)];
    if(!dates.length)continue;
    snippet=dates.at(-1)[0]+' · '+after;
    const other=snippet.slice(snippet.indexOf(' · ')+3).search(oral?/\d{1,2}月\s*\d{1,2}日|二[、.]/:/下午|上午|二[、.]/);
    if(other>=0)snippet=snippet.slice(0,snippet.indexOf(' · ')+3+other).trim();
    }
   }else if(level&&name==='准考证'){
    const start=text.search(/准考证.{0,8}打印|打印.{0,8}准考证/);if(start<0)continue;
    const sentences=text.slice(start).split(/[。；;]/);
    const oral=exam.id.startsWith('set');
    snippet=sentences.find(s=>/\d{1,2}月\s*\d{1,2}日/.test(s)&&/准考证/.test(s)&&
      (oral?/CET-SET|口试|口语/i.test(s):!/CET-SET|口试|口语/i.test(s)&&/CET|笔试|四六级|四、六级/i.test(s)))||'';
    if(!snippet&&/打印|准考证/.test(notice.title)&&combined&&(/口试|口语/.test(notice.title)===oral)){
     snippet=text.match(/(?:可于|于)\s*((?:20\d{2}年\s*)?\d{1,2}月\s*\d{1,2}日)[^。；;]{0,100}打印[^。；;]*准考证/)?.[0]||'';
     const printingStart=snippet.search(/可于/);
     if(printingStart>=0)snippet=snippet.slice(printingStart);
    }
   }else{
    const indices=[...text.matchAll(new RegExp(pattern.source,'g'))].map(m=>m.index);
    for(const index of indices){
     const candidate=text.slice(index,index+180).split(/[。；;]/)[0];
     // Course durations in NCRE tables (90分钟/120分钟) are not exam dates.
     if(/\d{1,2}\s*月\s*\d{1,2}\s*日|20\d{2}[-/]\d{1,2}[-/]\d{1,2}/.test(candidate)){snippet=candidate;break;}
    }
    if(!snippet&&name==='考试'){
     const candidate=text.match(/(?:将于|考试[将于时间：\s]*)(\d{1,2}\s*月\s*\d{1,2}\s*日(?:至|-|—)?\d{0,2}\s*日?[^。；;]{0,100})/);
     if(candidate)snippet=candidate[0];
    }
   }
   snippet=snippet.split(/\s+\d+[.、]\s+|一、|二、|三、|四、|为满足|报名工作安排/)[0].trim().replace(/[，,:：]$/,'');
   if(!snippet||/另行通知|待公布/.test(snippet))continue;
   if(name==='报名'&&/补报名/.test(notice.title))snippet='补报名 · '+snippet;
   return {name,text:snippet,url:notice.url,source:notice.sourceName};
  }
  return null;
 });
}
const existingExamTimeline=examTimeline;
examTimeline=function(exam){
 const base=existingExamTimeline(exam),live=liveExamStages(exam);
 return base.map(([stage,value],i)=>[stage,live[i]?.text||value]);
};
const existingExamLinks=examLinksMarkup;
examLinksMarkup=function(exam){
 const rows=liveExamStages(exam).filter(Boolean),urls=[...new Set(rows.map(r=>r.url))];
 return existingExamLinks(exam)+(urls.length?'<div class="sources">'+urls.map(url=>'<a href="'+esc(url)+'" target="_blank" rel="noopener">安排更新来源'+icon('external-link')+'</a>').join('')+'</div>':'');
};
let communitySyncPromise=null;
async function syncCommunitySchedules(){
 if(communitySyncPromise)return communitySyncPromise;
 communitySyncPromise=(async()=>{
  try{
   const response=await fetch('https://huanmen.com.cn/qsschedule/api/community-schedules',{cache:'no-store',signal:AbortSignal.timeout(20000)});
   if(!response.ok)throw Error();const data=await response.json();
   if(data.schemaVersion!==1||!Array.isArray(data.items)||data.items.length>5000)throw Error();
   communityFeed=data;portalSave('community-feed',data);return true;
  }catch(_){
   const sources=(communityFeed.sources||[]).filter(s=>['campus','ctf','radar'].includes(s.adapter));
   const results=await Promise.allSettled(sources.map(async source=>{
    const response=await fetch(source.url,{cache:'no-store',signal:AbortSignal.timeout(20000)});
    if(!response.ok)throw Error();const text=await response.text();if(text.length>2000000)throw Error();
    return normalizeCommunitySource(source,JSON.parse(text));
   }));
   let successes=0;const items=new Map((communityFeed.items||[]).map(row=>[row.id,row]));
   results.forEach((result,index)=>{
    const source=sources[index];if(result.status==='fulfilled'){
     for(const [id,row] of items)if(row.sourceId===source.id)items.delete(id);
     result.value.forEach(row=>items.set(row.id,row));source.status='ok';source.checkedAt=new Date().toISOString();successes++;
    }else source.status=[...items.values()].some(row=>row.sourceId===source.id)?'cached':'unavailable';
   });
   if(successes){communityFeed={...communityFeed,items:[...items.values()],checkedAt:new Date().toISOString()};portalSave('community-feed',communityFeed);}
   return successes>0;
  }
 })();
 try{return await communitySyncPromise;}finally{communitySyncPromise=null;}
}
const syncExistingCompetitions=syncAcademicCompetitions;
syncAcademicCompetitions=async function(){
 const results=await Promise.allSettled([syncExistingCompetitions(),syncCommunitySchedules()]);
 if(academicTab==='competitions'&&!$('#sheet').open){const y=scrollY;renderCompetitionList();scrollTo(0,y);}
 return results[0].status==='fulfilled'&&results[0].value;
};
const syncExistingNotices=syncNotices;
syncNotices=async function(){
 await syncExistingNotices();
 if(academicTab==='exams'&&!$('#sheet').open){const y=scrollY;renderExams();icons();scrollTo(0,y);}
};
async function normalizeCommunitySource(source,payload){
  const base=(record,key,name)=>({id:source.id+':'+key,name:String(name).slice(0,200),kind:'competition',sourceId:source.id,sourceName:source.name,sourceUrl:source.home,estimated:false,precision:'day'});
  const phase=(name,value,exact=true)=>({name,date:exact?communityDate(value):null,label:!exact&&communityDate(value)?communityDate(value).slice(0,7)+' 月内 · 具体日未公布':String(value||'日期未公布'),estimated:false});
  if(source.adapter==='campus'){
    if(!Array.isArray(payload.records))throw Error();
    return payload.records.map(row=>{const exact=['day','exact'].includes(row.datePrecision)&&!row.needsReview;return {...base(row,row.id,row.name),organizer:row.organizer,category:row.category,year:row.editionYear,historical:Boolean(row.editionYear&&Number(row.editionYear)<new Date().getFullYear()),officialUrl:communityUrl(row.source),registrationUrl:communityUrl(row.registrationUrl),queryUrl:communityUrl(row.officialUrl||row.source),precision:exact?'day':row.datePrecision||'unknown',note:row.dateNote,phases:[phase('报名开始',row.start,exact),phase('报名截止',row.deadline,exact)]};});
  }
  if(source.adapter==='radar'){
    if(!Array.isArray(payload.competitions))throw Error();
    return payload.competitions.map(row=>({...base(row,row.id,row.name),organizer:row.organizer,category:row.type,officialUrl:communityUrl(row.official_url),registrationUrl:communityUrl(row.registration_url||row.official_url),queryUrl:communityUrl(row.official_url),signup:row.signup,phases:[phase('开始',row.start),phase('报名/提交截止',row.deadline),phase('结束',row.end)]}));
  }
  const container=payload.data||payload.archive;if(payload.success!==true||!Array.isArray(container?.result))throw Error();
  return Promise.all(container.result.map(async row=>({...base(row,await ctfCommunityKey(row),row.name),category:'CTF',officialUrl:communityUrl(row.link),registrationUrl:communityUrl(row.link),queryUrl:communityUrl(row.link),historical:source.id.endsWith('history'),note:row.type,phases:[phase('报名开始',row.reg_time_start),phase('报名截止',row.reg_time_end),phase('比赛开始',row.comp_time_start),phase('比赛结束',row.comp_time_end)]})));
}
async function ctfCommunityKey(row){
  const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(row.name+String(row.comp_time_start??'None')));
  return [...new Uint8Array(digest)].map(n=>n.toString(16).padStart(2,'0')).join('').slice(0,16);
}
