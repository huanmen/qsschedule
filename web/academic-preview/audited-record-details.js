'use strict';
const auditedRecords = new Map(AuditedRecordData.records.map(row => [row.id, row]));
const auditedNames = new Map();
for (const row of auditedRecords.values()) {
  const key = normalizeCompetitionName(row.name);
  if (!auditedNames.has(key)) auditedNames.set(key, []);
  auditedNames.get(key).push(row);
}
function auditedRecord(record) {
  const direct = auditedRecords.get(AuditedRecordData.aliases[record.id] || 'exam-' + record.id);
  if (direct) return direct;
  const matches = auditedNames.get(normalizeCompetitionName(record.name || record.title || '')) || [];
  return matches.length === 1 ? matches[0] : null;
}
function currentAuditedRecord(competition) {
  const savedRecord = auditedRecord(competition);
  if (!savedRecord) return null;
  const live = enrichCompetition(competition).jingsai;
  const checkedAt = String(live?.retrievedAt || '').slice(0, 10);
  if (checkedAt <= AuditedRecordData.checkedAt || !/^https:\/\/jingsaitongzhi\.com\/competitions\//.test(live?.url || '')) return savedRecord;
  const source = {url:live.url,label:'竞赛通知网 · 最近同步正文',checkedAt};
  const year = Number(String(live.edition || '').match(/20\d{2}/)?.[0] || competition.year);
  if (year < savedRecord.current.year) return savedRecord;
  const current = {label:live.edition || `${year} 届 · 最近同步资料`,year,fields:{...savedRecord.current.fields},stages:[],notes:[]};
  for (const [key,value] of [['主办单位',live.organizer],['比赛内容',live.summary],['资格与组队',live.eligibility]]) {
    if (value && !/以.{0,32}(通知|章程|规则).{0,8}为准/.test(value)) current.fields[key] = {value,known:true,edition:current.label,sources:[source]};
  }
  for (const phase of live.phases || []) {
    const value = phase.note || phase.date || phase.label || '';
    if (!/\d{1,2}\s*月|20\d{2}-\d{2}-\d{2}/.test(value) || /预计|暂定|待公布/.test(value)) continue;
    current.stages.push({name:phase.name,value,edition:current.label,precision:/\d[:：]\d|\d时/.test(value)?'分钟':/\d日|-\d{2}-\d{2}/.test(value)?'日':'月 / 区间',certainty:'公开聚合正文；未独立核实',sources:[source]});
  }
  if (year === savedRecord.current.year && !current.stages.length) current.stages = savedRecord.current.stages;
  // Fields from an older edition keep their explicit edition labels.
  return {...savedRecord,current,references:[source,...savedRecord.references],history:year > savedRecord.current.year?[savedRecord.current,...savedRecord.history]:savedRecord.history};
}
function recordSubscription(record) { return auditedRecord(record)?.updateStatus === 'rolling'; }
function currentAuditedExam(exam) {
  const record = auditedRecord(exam), checkedAt = String(noticeFeed?.checkedAt || '').slice(0, 10);
  if (!record || checkedAt <= AuditedRecordData.checkedAt || examHistoricalPeriod) return record;
  const stages = liveExamStages(exam).filter(stage => stage && /^https?:\/\//.test(stage.url));
  if (!stages.length) return record;
  const current = {...record.current,stages:[...record.current.stages]};
  for (const stage of stages) {
    const updated = {name:stage.name,value:stage.text,edition:record.current.label,
      precision:/\d[:：]\d|\d时/.test(stage.text)?'分钟':'日',certainty:'最近同步的官方通知正文',
      sources:[{url:stage.url,label:stage.source || '官方通知',checkedAt}]};
    current.stages = [...current.stages.filter(previous => previous.name !== stage.name),updated];
  }
  return {...record,current};
}
function subscriptionLabel(record) { return recordSubscription(record) ? '可订阅' : '暂无订阅'; }
function auditedSources(sources) {
  return (sources || []).filter(source => /^https?:\/\//.test(source.url)).map(source =>
    `<a href="${esc(source.url)}" target="_blank" rel="noopener">${esc(source.label || '公开来源')}${icon('external-link')}</a><small>核对日期 ${esc(source.checkedAt || AuditedRecordData.checkedAt)}</small>`).join('');
}
function auditedFacts(edition, exam = false) {
  const labels = exam ? {'参赛范围':'报考范围','比赛内容':'考试内容','资格与组队':'报考资格'} : {};
  return `<ul class="audited-facts">${Object.entries(edition.fields || {}).map(([name, fact]) =>
    `<li><h3>${esc(labels[name] || name)}</h3><p>${esc(fact.value)}</p>${fact.edition ? `<small>${esc(fact.edition)}</small>` : ''}<div class="audited-sources">${auditedSources(fact.sources)}</div></li>`).join('')}</ul>`;
}
function auditedStages(edition) {
  const stages = edition.stages || [];
  return stages.length ? `<ol class="audited-stages">${stages.map(stage =>
    `<li><h3>${esc(stage.name)}</h3><p>${esc(stage.value)}</p><small>${esc(stage.edition || edition.label)} · 精度：${esc(stage.precision)} · ${esc(stage.certainty || '')}</small><div class="audited-sources">${auditedSources(stage.sources)}</div></li>`).join('')}</ol>` :
    `<p class="sheet-text">截至 ${esc(AuditedRecordData.checkedAt)}，未查到可核实的公开时间节点。</p>`;
}
function auditedChannels(record) {
  const rolling = record.updateStatus === 'rolling';
  const channels = record.channels || [];
  return `<h3 class="section-label">${rolling ? '可订阅' : '暂无订阅'}</h3>${channels.length ?
    `<ul class="audited-channels">${channels.map(channel => `<li><div class="audited-sources">${auditedSources([channel])}</div><p>${esc(channel.reason || '')}</p><p>可获取字段：${esc(channel.fields || '尚未核实')}</p><p>更新方式：${esc(channel.method || '人工查看')}</p><p>稳定性：${esc(channel.stability || '尚未验证')}</p></li>`).join('')}</ul>` :
    `<p class="sheet-text">截至 ${esc(AuditedRecordData.checkedAt)}，未核实到可持续读取的新通知渠道。</p>`}`;
}
function usefulEdition(edition) {
  return !!edition.body || !!edition.stages?.length || Object.values(edition.fields || {}).some(fact => fact.known);
}
function auditedHistory(record) {
  const history = (record.history || []).filter(usefulEdition);
  return history.length ? `<div class="competition-history">${history.map(edition =>
    `<details><summary>${esc(edition.label)}</summary>${auditedFacts(edition, record.category === '考证')}${edition.stages?.length ? auditedStages(edition) : ''}${edition.body ? `<p class="notice-excerpt">${esc(edition.body)}</p>` : ''}<div class="audited-sources">${auditedSources(edition.sources || (edition.source ? [edition.source] : []))}</div></details>`).join('')}</div>` :
    `<p class="sheet-text">截至 ${esc(AuditedRecordData.checkedAt)}，未查到可核实的往期资料。</p>`;
}
function auditedPages(record, portals = '') {
  const exam = record.category === '考证';
  return competitionPagesMarkup({
    overview: `<h3 class="section-label">报名与结果查询</h3>${portals}<div class="competition-tags"><span class="tag neutral">${esc(record.current.label)}</span><span class="tag">${record.updateStatus === 'rolling' ? '可订阅' : '暂无订阅'}</span></div>${auditedFacts(record.current, exam)}`,
    timeline: `<h3 class="section-label">时间节点</h3>${auditedStages(record.current)}`,
    notices: auditedChannels(record) + `<h3 class="section-label">资料来源</h3><div class="audited-sources">${auditedSources(record.references)}</div>` +
      (record.noticeBodies || []).filter(notice => notice.body).map(notice => `<details class="audited-notice"><summary>${esc(notice.label)}</summary><p class="notice-excerpt">${esc(notice.body)}</p><div class="audited-sources">${auditedSources(notice.source ? [notice.source] : [])}</div></details>`).join(''),
    history: `<h3 class="section-label">往期资料</h3>${auditedHistory(record)}`,
  });
}
const previousCompetitionDetail = competitionDetail;
const previousEditionDetail = editionDetail;
function auditedCompetitionDetail(id, fallback) {
  const competition = findCompetition(id), record = competition && currentAuditedRecord(competition);
  if (!record) return fallback(id);
  const portals = competitionPortalMarkup(competition).replace(/<h3 class="section-label">报名与结果查询<\/h3>/, '');
  showRecordSheet(id, competition.name, auditedPages(record, portals) +
    `<div class="sheet-actions"><button class="primary" data-detail-follow="${esc(id)}">${followed.includes(competitionKey(competition)) ? '取消关注' : '关注赛事'}</button><button class="secondary" data-plan-event="${esc(id)}">加入安排</button></div>`);
}
competitionDetail = id => auditedCompetitionDetail(id, previousCompetitionDetail);
editionDetail = id => auditedCompetitionDetail(id, previousEditionDetail);

const previousTimelineNodes = timelineNodes;
timelineNodes = function(competition) {
  const live = enrichCompetition(competition).jingsai;
  if (String(live?.retrievedAt || '').slice(0, 10) > AuditedRecordData.checkedAt && /^https:\/\/jingsaitongzhi\.com\/competitions\//.test(live?.url || '')) return previousTimelineNodes(competition);
  const record = auditedRecord(competition);
  if (!record) return previousTimelineNodes(competition);
  const nodes = [];
  for (const stage of record.current.stages || []) {
    const year = Number(String(stage.edition || record.current.label).match(/20\d{2}/)?.[0] || record.current.year);
    const dates = [...String(stage.value).matchAll(/(?:(20\d{2})年\s*)?(\d{1,2})月\s*(\d{1,2})日|(20\d{2})-(\d{2})-(\d{2})/g)];
    for (const date of dates) nodes.push({year:Number(date[1] || date[4] || year),month:Number(date[2] || date[5]),day:Number(date[3] || date[6]),phase:stage.name});
    if (!dates.length && /月/.test(stage.precision)) {
      const month = stage.value.match(/(\d{1,2})\s*月/);
      if (month) nodes.push({year,month:Number(month[1]),day:null,phase:stage.name});
    }
  }
  return [...new Map(nodes.filter(node => node.month >= 1 && node.month <= 12).map(node => [JSON.stringify(node), node])).values()];
};

let competitionSubscription = 'all';
const auditedCompetitionRows = competitionRows;
competitionRows = () => auditedCompetitionRows().filter(record => competitionSubscription === 'all' ||
  (recordSubscription(record) ? 'available' : 'unavailable') === competitionSubscription);
const auditedRenderCompetitions = renderCompetitions;
renderCompetitions = function() {
  auditedRenderCompetitions();
  $('#follow-filter').textContent = '只显示已关注';
  $('#follow-filter').setAttribute('aria-pressed', String(competitionFollowOnly));
  $('#competition-search').closest('label').insertAdjacentHTML('beforebegin',
    `<div class="category-tabs subscription-filter" aria-label="订阅能力">${[['all','全部'],['available','可订阅'],['unavailable','暂无订阅']].map(([value, label]) => `<button data-subscription="${value}" class="${competitionSubscription === value ? 'active' : ''}" aria-pressed="${competitionSubscription === value}">${label}</button>`).join('')}</div>`);
  document.querySelectorAll('[data-subscription]').forEach(button => button.onclick = () => {
    competitionSubscription = button.dataset.subscription;
    renderCompetitions();icons();
  });
};
// Tags are derived from the audit; following remains a separate, combinable condition.
const auditedRenderCompetitionList = renderCompetitionList;
renderCompetitionList = function() {
  auditedRenderCompetitionList();
  document.querySelectorAll('.competition-main').forEach(button => {
    if (button.querySelector('.subscription-tag')) return;
    const record = findCompetition(button.dataset.competition);
    button.querySelector('.competition-tags').insertAdjacentHTML('beforeend',
      `<span class="tag neutral subscription-tag">${subscriptionLabel(record)}</span>`);
  });
};
const auditedExamCard = examCardMarkup;
examCardMarkup = function(exam) {
  return auditedExamCard(exam).replace('</h3>', `</h3><span class="tag neutral subscription-tag">${subscriptionLabel(exam)}</span>`) .replace('</article>',
    `<button class="text-button exam-detail-button" data-exam-detail="${esc(exam.id)}">查看详情${icon('chevron-right')}</button></article>`);
};
document.addEventListener('click', event => {
  const button = event.target.closest('[data-exam-detail]');
  if (!button) return;
  const exam = exams.find(row => row.id === button.dataset.examDetail), record = exam && currentAuditedExam(exam);
  if (record) showRecordSheet(record.id, exam.title, auditedPages(record, examLinksMarkup(exam)));
});
