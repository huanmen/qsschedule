'use strict';
function competitionOfficialPage(record) {
  if(record.jingsai)return record.jingsai.officialUrl||null;
  const linked=enrichCompetition(record);
  if(linked.jingsai)return linked.jingsai.officialUrl||null;
  if(record.official)return record.official;
  const profile=(window.CompetitionProfiles||[]).find(p=>p.match.some(m=>record.name.toLowerCase().includes(m.toLowerCase())));
  return profile?.official||null;
}
function competitionPortalMarkup(record) {
  const official=competitionOfficialPage(record),links=window.RecordPortalLinks?.[official]||[];
  const currentLinks=links.filter(x=>![...(x.url+' '+x.text).matchAll(/20\d{2}/g)].some(m=>Number(m[0])<record.year));
  const registration=currentLinks.find(x=>x.kind==='registration'&&/报名系统|报名入口|参赛注册|选手注册/.test(x.text))||currentLinks.find(x=>x.kind==='registration');
  const query=links.find(x=>x.kind==='query');
  const source=CompetitionCatalog.sources.find(s=>s.id===record.sourceId),reference=official||source?.url||record.source||record.jingsai?.url;
  const link=(url,label)=>`<a href="${esc(url)}" target="_blank" rel="noopener">${esc(label)}${icon('external-link')}</a>`;
  return `<section class="competition-portals"><h3 class="section-label">报名与结果查询</h3><div class="record-portals">
    ${registration?link(registration.url,registration.text):official?link(official,'报名说明（官网）'):'<span>报名入口待公布</span>'}
    ${query?link(query.url,/查询/.test(query.text)?'结果查询':'获奖公示'):official?link(official,'结果公告（官网）'):'<span>查询入口待公布</span>'}
    </div>${!registration?'<p class="footnote compact">校内推荐或报名资格请先核对本届公告。</p>':''}${!official&&reference?`<div class="sources">${link(reference,'查看报名与结果通知')}</div>`:''}</section>`;
}
const showRecordSheet=openCompetitionSheet;
openCompetitionSheet=function(id,title,html){
  const record=findCompetition(id);
  if(record)html=html.replace('<section data-competition-page="overview">',`<section data-competition-page="overview">${competitionPortalMarkup(enrichCompetition(record))}`);
  showRecordSheet(id,title,html);
};
const knownCompetitionTimeline=timelineMarkup;
timelineMarkup=function(record){
  if(timelineNodes(record).length)return knownCompetitionTimeline(record);
  const phases=record.jingsai?.phases||record.phases||[];
  return stageTimelineMarkup(phases.length?phases.map(p=>[p.name,p.date||p.label||p.note||'日期待公布']):[
    ['报名','本届入口与时间待公布'],['选拔与提交','安排以主办方及校内通知为准'],['比赛','日期与地点待公布'],['结果','公示及证书以官方公告为准']
  ]);
};
