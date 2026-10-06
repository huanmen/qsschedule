(function () {
  'use strict';
  const KEY = 'qnu-offline-map-edits-v1';
  const RECOVERY_KEY = KEY + '-invalid-backup';
  const FORMAT = 'qnu-campus-place-edits';
  const MAX_TAGS = 30;
  const MAX_TAG_LENGTH = 40;
  const MAX_TAG_CATALOG = 200;
  const MAX_ROADS = 100;
  const MAX_REGIONS = 100;
  const clone = value => JSON.parse(JSON.stringify(value));
  const color = value => typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value);
  const coordinate = c => c && typeof c.lat === 'number' && typeof c.lng === 'number'
    && Number.isFinite(c.lat) && Number.isFinite(c.lng)
    && c.lat >= 24.860 && c.lat <= 24.878 && c.lng >= 118.659 && c.lng <= 118.682;
  function record(value) {
    if (!value || typeof value.id !== 'string' || !/^[\w-]{1,100}$/.test(value.id)) throw Error('地点编号无效');
    if (typeof value.name !== 'string' || !value.name.trim() || value.name.length > 80) throw Error('地点名称应为 1 至 80 个字');
    if (!coordinate(value.coordinate)) throw Error('地点超出离线地图覆盖范围');
    if (!Array.isArray(value.aliases) || value.aliases.length > 50 || value.aliases.some(x => typeof x !== 'string' || x.length > 80)) throw Error('别名格式无效（最多 50 个，每个最多 80 字）');
    const tags = value.tags === undefined ? [] : value.tags;
    if (!Array.isArray(tags) || tags.length > MAX_TAGS || tags.some(x => typeof x !== 'string' || !x.trim() || x.length > MAX_TAG_LENGTH)) throw Error('标签格式无效（最多 30 个，每个最多 40 字）');
    if (!color(value.color)) throw Error('标记颜色无效');
    if (!['verified', 'area', 'regional', 'unverified', 'user'].includes(value.status)) throw Error('地点状态无效');
    return {id:value.id, name:value.name.trim(), label:String(value.label || value.name).trim().slice(0,80),
      aliases:[...new Set(value.aliases.map(x => x.trim()).filter(Boolean))],
      tags:[...new Set(tags.map(x => x.trim()).filter(Boolean))],
      coordinate:{lat:value.coordinate.lat, lng:value.coordinate.lng}, color:value.color.toLowerCase(), status:value.status};
  }
  function roadRecord(value) {
    if (!value || typeof value.id !== 'string' || !/^road-[\w-]{1,80}$/.test(value.id)) throw Error('路线编号无效');
    if (!Array.isArray(value.points) || value.points.length < 2 || value.points.length > 400 ||
        value.points.some(p => !Array.isArray(p) || p.length !== 2 || !coordinate({lat:p[0],lng:p[1]}))) throw Error('路线拐点无效或超出地图范围');
    function anchor(a) {
      if (a === null) return null;
      if (!a || typeof a.edge !== 'string' || !/^(base:\d+|user:road-[\w-]{1,80})$/.test(a.edge) ||
          !Number.isFinite(a.offset) || a.offset < 0) throw Error('路线接入点无效');
      return {edge:a.edge,offset:a.offset};
    }
    const start = anchor(value.start), end = anchor(value.end ?? null);
    if (!start) throw Error('路线必须从已有道路开始');
    return {id:value.id,points:value.points.map(p=>[p[0],p[1]]),start,end};
  }
  function regionRecord(value) {
    if (!value || typeof value.id !== 'string' || !/^region-[\w-]{1,80}$/.test(value.id)) throw Error('区域编号无效');
    if (typeof value.name !== 'string' || !value.name.trim() || value.name.length > 80) throw Error('区域名称应为 1 至 80 个字');
    if (typeof value.instruction !== 'string' || value.instruction.length > 2000) throw Error('修改说明最多 2000 个字');
    if (!Array.isArray(value.points) || value.points.length < 3 || value.points.length > 500 || value.points.some(p => !Array.isArray(p) || p.length !== 2 || !coordinate({lat:p[0],lng:p[1]}))) throw Error('圈选区域无效或超出地图范围');
    return {id:value.id,name:value.name.trim(),instruction:value.instruction.trim(),points:value.points.map(p=>[p[0],p[1]])};
  }
  function validate(value) {
    if (!value || value.format !== FORMAT || value.version !== 1 || !Array.isArray(value.places) || !Array.isArray(value.deleted)) throw Error('不是有效的地点编辑文件');
    if (value.places.length + value.deleted.length > 10000) throw Error('文件包含的地点过多');
    const places = value.places.map(record), ids = new Set(places.map(p => p.id));
    if (ids.size !== places.length) throw Error('文件中包含重复地点编号');
    const deleted = [...new Set(value.deleted)];
    if (deleted.some(id => typeof id !== 'string' || !/^[\w-]{1,100}$/.test(id) || ids.has(id))) throw Error('删除记录无效');
    const tagCatalog = value.tagCatalog === undefined ? [] : value.tagCatalog;
    if (!Array.isArray(tagCatalog) || tagCatalog.length > MAX_TAG_CATALOG || tagCatalog.some(x => typeof x !== 'string' || !x.trim() || x.length > MAX_TAG_LENGTH)) throw Error('标签库格式无效（最多 200 个，每个最多 40 字）');
    const normalizedCatalog = [...new Set([...tagCatalog.map(x => x.trim()), ...places.flatMap(p => p.tags)])];
    if (normalizedCatalog.length > MAX_TAG_CATALOG) throw Error('标签库格式无效（最多 200 个，每个最多 40 字）');
    const incomingRoads = value.roads === undefined ? [] : value.roads;
    if (!Array.isArray(incomingRoads) || incomingRoads.length > MAX_ROADS) throw Error('新增路线最多 100 条');
    const roads = incomingRoads.map(roadRecord), known = new Set();
    for (const road of roads) {
      if (known.has(road.id) || [road.start,road.end].some(a=>a?.edge.startsWith('user:')&&!known.has(a.edge.slice(5)))) throw Error('路线连接记录无效');
      known.add(road.id);
    }
    const incomingRegions = value.regions === undefined ? [] : value.regions;
    if (!Array.isArray(incomingRegions) || incomingRegions.length > MAX_REGIONS) throw Error('修改区域最多 100 个');
    const regions = incomingRegions.map(regionRecord), regionIds = new Set();
    for (const region of regions) { if (regionIds.has(region.id)) throw Error('区域编号重复'); regionIds.add(region.id); }
    const display = value.displayPreferences || {defaultTags:['学院','教学楼'],includeUntagged:true,overrides:{}};
    if(!Array.isArray(display.defaultTags)||display.defaultTags.length>200||display.defaultTags.some(t=>typeof t!=='string'||t.length>40)||typeof display.includeUntagged!=='boolean'||!display.overrides||typeof display.overrides!=='object'||Array.isArray(display.overrides)||Object.entries(display.overrides).some(([id,v])=>!(/^[\w-]{1,100}$/.test(id))||!['show','hide'].includes(v)))throw Error('默认显示设置无效');
    return {format:FORMAT, version:1, places, deleted, tagCatalog:normalizedCatalog, roads, regions,displayPreferences:clone(display)};
  }
  function create(base, storage) {
    const originals = new Map(base.map(p => [p.id, clone(p)]));
    let state = {format:FORMAT, version:1, places:[], deleted:[], tagCatalog:[], roads:[], regions:[]}, loadError = null, invalidText = null, undo = null;
    try { const text = storage.getItem(KEY); if (text) state = validate(JSON.parse(text)); }
    catch (error) {
      loadError = '本地编辑数据读取失败：' + error.message;
      try { invalidText = storage.getItem(KEY); } catch (_) { invalidText = null; }
    }
    function commit(next, allowRecovery = false) {
      if (loadError && !allowRecovery) throw Error(loadError + '。请先导出备份或导入有效文件。');
      if (loadError && invalidText) {
        try { storage.setItem(RECOVERY_KEY, invalidText); } catch (_) { /* keep editing available if backup storage is full */ }
      }
      const valid = validate(next);
      storage.setItem(KEY, JSON.stringify(valid));
      state = valid; loadError = null; invalidText = null;
    }
    function list() {
      const merged = new Map([...originals].map(([id,p]) => [id,clone(p)]));
      for (const p of state.places) merged.set(p.id, {...merged.get(p.id), ...clone(p), edited:true});
      for (const id of state.deleted) merged.delete(id);
      return [...merged.values()];
    }
    return {
      list, get loadError(){return loadError;}, get canUndo(){return !!undo;},
      preferences(){return clone(state.displayPreferences||{defaultTags:['学院','教学楼'],includeUntagged:true,overrides:{}});},
      setPreferences(displayPreferences){commit({...state,displayPreferences});},
      renameTag(tag,replacement){
        const name=String(replacement||'').trim();
        if(name.length>MAX_TAG_LENGTH)throw Error('标签最多 40 个字');
        const updated=list().filter(p=>(p.tags||[]).includes(tag)).map(p=>record({...p,aliases:p.aliases||[],color:p.color||'#1675db',tags:[...new Set(p.tags.flatMap(t=>t===tag?(name?[name]:[]):[t]))]}));
        const changes=new Map(state.places.map(p=>[p.id,p]));updated.forEach(p=>changes.set(p.id,p));
        const preferences=this.preferences();preferences.defaultTags=[...new Set(preferences.defaultTags.flatMap(t=>t===tag?(name?[name]:[]):[t]))];
        commit({...state,places:[...changes.values()],tagCatalog:[...new Set(this.listTags().flatMap(t=>t===tag?(name?[name]:[]):[t]))],displayPreferences:preferences});
      },
      listRoads(){return clone(state.roads);},
      listRegions(){return clone(state.regions);},
      saveRegion(region){
        const item = regionRecord(region);
        if (state.regions.some(r=>r.id===item.id)) throw Error('区域编号重复');
        commit({...state,regions:[...state.regions,item]},true);
      },
      removeRegion(id){
        if (!state.regions.some(r=>r.id===id)) throw Error('修改区域不存在');
        commit({...state,regions:state.regions.filter(r=>r.id!==id)},true);
      },
      saveRoad(road,dependencies=[]){
        const item = roadRecord(road);
        if (state.roads.some(r=>r.id===item.id)) throw Error('路线编号重复');
        const roads=new Map([...dependencies,...state.roads].map(r=>[r.id,r]));
        commit({...state,roads:[...roads.values(),item]},true);
      },
      removeRoad(id){
        if (!state.roads.some(r=>r.id===id)) throw Error('路线不存在');
        if (state.roads.some(r=>r.id!==id&&[r.start,r.end].some(a=>a?.edge==='user:'+id))) throw Error('其他路线接在这条路上，请先删除分支路线');
        commit({...state,roads:state.roads.filter(r=>r.id!==id)},true);
      },
      listTags() {
        return [...new Set([...state.tagCatalog, ...list().flatMap(p => p.tags || [])])];
      },
      addTags(tags) {
        const incoming = [...new Set((Array.isArray(tags) ? tags : [tags]).map(x => String(x).trim()).filter(Boolean))];
        if (!incoming.length) return;
        commit({...state,tagCatalog:[...new Set([...state.tagCatalog,...incoming])]}, true);
      },
      save(p) {
        const item = record(p);
        commit({...state, places:[...state.places.filter(x=>x.id!==item.id),item],deleted:state.deleted.filter(id=>id!==item.id),tagCatalog:[...new Set([...state.tagCatalog,...item.tags])]}, true);
      },
      saveWithPreferences(p,displayPreferences){
        const item=record(p);
        commit({...state,places:[...state.places.filter(x=>x.id!==item.id),item],deleted:state.deleted.filter(id=>id!==item.id),tagCatalog:[...new Set([...state.tagCatalog,...item.tags])],displayPreferences},true);
      },
      remove(id) {
        if (!list().some(p=>p.id===id)) throw Error('地点已不存在');
        const previous = clone(state);
        commit({...state,places:state.places.filter(p=>p.id!==id),deleted:[...new Set([...state.deleted,id])]}, true);
        undo = {id, patch:previous.places.find(p=>p.id===id)};
      },
      undoDelete() {
        if (!undo) return;
        const places = state.places.filter(p=>p.id!==undo.id);
        if (undo.patch) places.push(undo.patch);
        commit({...state,places,deleted:state.deleted.filter(id=>id!==undo.id)}); undo = null;
      },
      exportText(){ return JSON.stringify(state,null,2); },
      importText(text) {
        if (text.length > 5*1024*1024) throw Error('文件大于 5 MB');
        const raw=JSON.parse(text),incoming = validate(raw);
        const places = new Map(state.places.map(p=>[p.id,p])), deleted = new Set(state.deleted);
        for (const id of incoming.deleted){places.delete(id);deleted.add(id);}
        for (const p of incoming.places){places.set(p.id,p);deleted.delete(p.id);}
        const roads = new Map(state.roads.map(r=>[r.id,r]));
        for(const road of incoming.roads) roads.set(road.id,road);
        const regions = new Map(state.regions.map(r=>[r.id,r]));
        for(const region of incoming.regions) regions.set(region.id,region);
        commit({...state,places:[...places.values()],deleted:[...deleted],tagCatalog:[...new Set([...state.tagCatalog,...incoming.tagCatalog])],roads:[...roads.values()],regions:[...regions.values()],displayPreferences:raw.displayPreferences?incoming.displayPreferences:this.preferences()},true);undo = null;
      }
    };
  }
  window.CampusPlaceStore = {create,validate,record,coordinate,color,KEY};
})();
