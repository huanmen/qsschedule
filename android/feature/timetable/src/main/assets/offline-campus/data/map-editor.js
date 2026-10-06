(function () {
  'use strict';
  const debug = window.__campusDebug;
  const base = window.CALIBRATED_MAP;
  if (!debug || !base || !window.CampusPlaceStore) return;
  const $ = id => document.getElementById(id);
  const store = window.CampusPlaceStore.create([...base.places, ...base.pendingPlaces], window.localStorage);
  if(window.CAMPUS_INITIAL_EDITS&&!window.localStorage.getItem(window.CampusPlaceStore.KEY))store.importText(JSON.stringify(window.CAMPUS_INITIAL_EDITS));
  const EDIT_LIMITS = {minLat:24.860, maxLat:24.878, minLng:118.659, maxLng:118.682};
  const layer = L.layerGroup().addTo(debug.map);
  const routeLayer = L.layerGroup().addTo(debug.map);
  const endpointLayer = L.layerGroup().addTo(debug.map);
  const roadEditsPane=debug.map.getPane('road-edits')||debug.map.createPane('road-edits',debug.map.getPane('rotatePane'));
  roadEditsPane.style.zIndex=425;
  roadEditsPane.style.pointerEvents='none';
  const savedRoadLayer=L.layerGroup().addTo(debug.map);
  const draftRoadLayer=L.layerGroup().addTo(debug.map);
  const roadPreviewLine=L.polyline([],{pane:'road-edits',color:'#279590',weight:1.25,opacity:.8,dashArray:'6 5',interactive:false});
  const roadPreviewStart=L.circleMarker([0,0],{pane:'road-edits',radius:4,color:'#279590',fillColor:'#fff',fillOpacity:1,weight:2,interactive:false});
  const savedRegionLayer=L.layerGroup();
  const draftRegionLayer=L.layerGroup().addTo(debug.map);
  const markers = new Map();
  debug.markers=markers;
  let byId=new Map(),searchTerms=new Map(),labelFrame=null;
  let currentOrigin=null;
  let places = [], selected = null, draft = null, originalCoordinate = null, editingTags = [], creating = false, moving = false, preview = null;
  let routeStart = null, routeEnd = null, currentRoute = null, router = null;
  let routeAdjusting = false, routeWaypoints = [];
  const ROUTE_OVERRIDE_KEY = 'qztc-campus-route-overrides-v1';
  let routeOverrides = (() => { try { return JSON.parse(localStorage.getItem(ROUTE_OVERRIDE_KEY) || '{}') || {}; } catch (_) { return {}; } })();
  let drawing = false, draftRoad = null, roadCursor = null, roadDeleteId = null, penDown = false, suppressRoadClick=false;
  let regionDrawing = false, draftRegion = null, regionDeleteId = null;
  const taskAnnotations=window.CAMPUS_TASK_ANNOTATIONS||{regions:[],roads:[]};
  const allRoads=()=>[...new Map([...taskAnnotations.roads,...store.listRoads()].map(road=>[road.id,road])).values()];
  const allRegions=()=>[...new Map([...taskAnnotations.regions,...store.listRegions()].map(region=>[region.id,region])).values()];
  const inSchool=(place)=>debug.layout.contains(place.coordinate);
  const esc = value => String(value || '').replace(/[<>&"']/g, c => ({'<':'&lt;','>':'&gt;','&':'&amp;','"':'&quot;',"'":'&#39;'}[c]));
  const color = place => place.color || (place.status === 'area' ? '#15866a' : place.status === 'unverified' ? '#ba7415' : '#1675db');
  function syncPlaces(){
    places=store.list();byId=new Map(places.map(p=>[p.id,p]));
    if(currentOrigin)byId.set(currentOrigin.id,currentOrigin);
    searchTerms=new Map(places.map(p=>[p.id,[p.name,p.label,p.region,p.address,...(p.aliases||[]),...(p.tags||[])].join(' ').toLowerCase()]));
  }
  function icon(place){return L.divIcon({className:'place-icon',html:'<span class="place-dot '+(place.status==='regional'?'regional':'')+'" style="background:'+esc(color(place))+';box-shadow:0 0 0 1px '+esc(color(place))+'"></span><span class="place-hover-label" aria-hidden="true">'+esc(place.label||place.name)+'</span>',iconSize:[24,24],iconAnchor:[12,12]});}
  function find(id){return byId.get(id);}
  function popup(place){const div=document.createElement('div');const title=document.createElement('p');title.className='popup-name';title.textContent=place.name;div.append(title);const note=document.createElement('p');note.className='meta';note.textContent='点击编辑可调整位置、名称、别名、标签和颜色';div.append(note);if(place.tags?.length){const tags=document.createElement('p');tags.className='place-tags';tags.textContent='标签：'+place.tags.join('、');div.append(tags);}const actions=document.createElement('div');actions.className='popup-actions';for(const kind of ['start','end']){const button=document.createElement('button');button.textContent=kind==='start'?'设为起点':'设为终点';button.addEventListener('click',()=>setRoutePoint(kind,place.id));actions.append(button);}const edit=document.createElement('button');edit.textContent='编辑地点';edit.addEventListener('click',()=>openEditor(place.id));actions.append(edit);div.append(actions);return div;}
  function renderMarkers(){
    const visible=places.filter(p=>inSchool(p)&&(p.status!=='unverified'||$('pending').checked));
    const ids=new Set(visible.map(p=>p.id));
    for(const [id,marker] of markers)if(!ids.has(id)){layer.removeLayer(marker);markers.delete(id);}
    for(const place of visible){
      let marker=markers.get(place.id);
      const appearance=JSON.stringify([place.name,place.label,color(place),place.status]);
      if(!marker){
        marker=L.marker([place.coordinate.lat,place.coordinate.lng],{icon:icon(place),title:place.name,alt:place.name,riseOnHover:true}).addTo(layer);
        marker.bindPopup(()=>popup(find(place.id)),{maxWidth:270,autoPan:true,autoPanPadding:[24,24]});
        marker.on('click',()=>{if(!drawing&&!regionDrawing)openEditor(place.id);});markers.set(place.id,marker);
      }else{
        const ll=marker.getLatLng();
        if(ll.lat!==place.coordinate.lat||ll.lng!==place.coordinate.lng)marker.setLatLng(place.coordinate);
        if(marker._appearance!==appearance){marker.options.title=place.name;marker.options.alt=place.name;marker.setIcon(icon(place));}
      }
      marker._appearance=appearance;
    }
    renderSearch();scheduleLabels();
  }
  function renderSearch(){
    const query=$('campus-search').value.trim().toLowerCase(),list=$('campus-results');list.replaceChildren();
    const matches=places.filter(p=>inSchool(p)&&(p.status!=='unverified'||$('pending').checked)&&(!query||searchTerms.get(p.id).includes(query)));
    for(const p of matches.slice(0,query?30:6)){
      const b=document.createElement('button');b.className='result';b.dataset.campusResult=p.id;
      b.setAttribute('aria-current',String(selected===p.id));
      b.innerHTML='<span>'+esc(p.label||p.name)+'</span>'+(p.region?'<small>'+esc(p.region)+'</small>':'')+(p.tags?.length?'<small>'+esc(p.tags.join(' · '))+'</small>':'')+(p.status==='unverified'?'<small>待核对</small>':'');
      b.addEventListener('click',()=>openEditor(p.id));list.append(b);
    }
    if(!matches.length){const p=document.createElement('p');p.className='meta';p.textContent='没有找到相关地点';list.append(p);}
  }
  function scheduleLabels(){if(labelFrame!==null)return;labelFrame=requestAnimationFrame(()=>{labelFrame=null;layoutLabels();});}
  function layoutLabels(){
    const occupied=[],size=debug.map.getSize(),zoom=debug.map.getZoom();
    const crop=debug.layout.crop.corners.map(ll=>debug.map.latLngToContainerPoint(ll));
    const left=Math.max(0,Math.min(...crop.map(p=>p.x)))+6,right=Math.min(size.x,Math.max(...crop.map(p=>p.x)))-6;
    const top=Math.max(0,Math.min(...crop.map(p=>p.y)))+6,bottom=Math.min(size.y,Math.max(...crop.map(p=>p.y)))-6;
    const order=[...markers.keys()].sort((a,b)=>a===selected?-1:b===selected?1:0);
    for(const id of order){
      const marker=markers.get(id),place=find(id),label=place.label||place.name;
      const p=debug.map.latLngToContainerPoint(marker.getLatLng());let direction=null;
      if($('labels').checked&&p.x>=0&&p.y>=0&&p.x<=size.x&&p.y<=size.y&&(zoom>=16||/图书馆|食堂|体育馆/.test(label)||id===selected)){
        const maxWidth=size.x<700?130:185,width=Math.min(maxWidth,label.length*12+10),height=Math.ceil((label.length*12+10)/maxWidth)*20;
        for(const side of ['right','left','top','bottom']){
          const x=side==='left'?p.x-width-13:side==='right'?p.x+13:p.x-width/2;
          const y=side==='top'?p.y-height-13:side==='bottom'?p.y+13:p.y-height/2;
          const box={x,y,r:x+width,b:y+height};
          if(x<left||y<top||box.r>right||box.b>bottom||occupied.some(a=>x<a.r+4&&box.r>a.x-4&&y<a.b+3&&box.b>a.y-3))continue;
          direction=side;occupied.push(box);break;
        }
      }
      const key=direction?direction+'|'+label:'';
      if(marker._labelKey===key)continue;
      marker.unbindTooltip();marker._labelKey=key;
      if(direction){const text=document.createElement('span');text.textContent=label;marker.bindTooltip(text,{permanent:true,direction,className:'place-tooltip',opacity:1});}
    }
  }
  function refreshSelected(){const p=find(selected);if(!p){$('selected-panel').hidden=true;return;}$('selected-panel').hidden=false;$('selected-name').textContent=p.name;$('selected-note').textContent=p.evidence||'本地编辑地点';$('set-start').disabled=$('set-end').disabled=p.status==='unverified';}
  function refreshSelects(){for(const kind of ['start','end']){const select=$('route-'+kind),value=select.value;select.replaceChildren(new Option('选择地点',''));if(kind==='start'&&currentOrigin)select.append(new Option(currentOrigin.name,currentOrigin.id));places.filter(p=>inSchool(p)&&p.status!=='unverified').forEach(p=>select.append(new Option(p.label||p.name,p.id)));select.value=value;}}
  function renderTagEditor(){const list=$('edit-tags');list.replaceChildren();if(!editingTags.length){const empty=document.createElement('span');empty.className='meta';empty.textContent='暂未添加标签';list.append(empty);}else editingTags.forEach((tag,index)=>{const chip=document.createElement('span');chip.className='tag-chip';const text=document.createElement('span');text.textContent=tag;const remove=document.createElement('button');remove.type='button';remove.textContent='删除';remove.setAttribute('aria-label','删除标签 '+tag);remove.addEventListener('click',()=>{editingTags.splice(index,1);renderTagEditor();});chip.append(text,remove);list.append(chip);});const options=$('tag-options');options.replaceChildren();const available=store.listTags();if(!available.length){options.hidden=true;return;}options.hidden=false;const heading=document.createElement('span');heading.className='meta';heading.textContent='可复用标签（可多选）';options.append(heading);available.forEach(tag=>{const label=document.createElement('label');label.className='tag-option';const input=document.createElement('input');input.type='checkbox';input.checked=editingTags.includes(tag);input.addEventListener('change',()=>{if(input.checked){if(editingTags.length>=30){input.checked=false;$('edit-help').textContent='最多添加 30 个标签。';return;}if(!editingTags.includes(tag))editingTags.push(tag);}else editingTags=editingTags.filter(item=>item!==tag);renderTagEditor();});const text=document.createElement('span');text.textContent=tag;label.append(input,text);options.append(label);});}
  function addTags(){const input=$('edit-tag-input');const incoming=[...new Set(input.value.split(/[,，]/).map(tag=>tag.trim()).filter(Boolean))];if(!incoming.length)return;const valid=incoming.filter(tag=>{if(tag.length>40){$('edit-help').textContent='单个标签最多 40 个字。';return false;}return true;});if(!valid.length){input.value='';return;}try{store.addTags(valid);}catch(error){$('edit-help').textContent='标签保存失败：'+error.message;return;}for(const tag of valid){if(editingTags.includes(tag))continue;if(editingTags.length>=30){$('edit-help').textContent='最多添加 30 个标签。';break;}editingTags.push(tag);}input.value='';renderTagEditor();}
  function openEditor(id){const place=find(id);if(!place)return;if(drawing)cancelRoad();if(regionDrawing||draftRegion)cancelRegion();if(draft)cancelDraft();selected=id;creating=false;draft=JSON.parse(JSON.stringify(place));originalCoordinate={...place.coordinate};refreshSelected();showForm();debug.map.setView([draft.coordinate.lat,draft.coordinate.lng],Math.max(17,debug.map.getZoom()),{animate:false});debug.map.closePopup();}
  function showForm(){const p=draft;$('delete-confirm').hidden=true;$('edit-form').hidden=false;$('edit-name').value=p.name;$('edit-aliases').value=(p.aliases||[]).join(', ');editingTags=[...(p.tags||[])];renderTagEditor();setColor(color(p));$('edit-position').textContent='位置：'+p.coordinate.lng.toFixed(6)+', '+p.coordinate.lat.toFixed(6);$('delete-place').disabled=false;$('delete-place').textContent=creating?'取消新建':'删除地点';}
  function setColor(value){document.querySelectorAll('input[name="edit-color"]').forEach(input=>input.checked=input.value===value);$('edit-custom-color').value=value;if(preview)preview.getElement()?.querySelector('.edit-preview-dot')?.style.setProperty('--preview-color',value);}
  function getColor(){return document.querySelector('input[name="edit-color"]:checked')?.value||$('edit-custom-color').value;}
  function clampCoordinate(latlng){return {lat:Math.min(EDIT_LIMITS.maxLat,Math.max(EDIT_LIMITS.minLat,latlng.lat)),lng:Math.min(EDIT_LIMITS.maxLng,Math.max(EDIT_LIMITS.minLng,latlng.lng))};}
  function newPlace(){if(drawing)cancelRoad();if(regionDrawing||draftRegion)cancelRegion();if(draft)cancelDraft();const center=clampCoordinate(debug.map.getCenter());draft={id:'local-'+Date.now().toString(36),name:'新地点',label:'新地点',aliases:[],tags:[],coordinate:center,status:'user',type:'custom',color:'#1675db',evidence:'用户新增地点'};originalCoordinate=null;editingTags=[];selected=draft.id;creating=true;refreshSelected();showForm();armMove();$('edit-help').textContent='已开始新建标记；移动鼠标调整位置，右键确认后点击保存。';}
  function armMove(){if(!draft)return;moving=true;$('edit-mode').classList.add('active');$('edit-mode').textContent='位置调整中';if(preview)layer.removeLayer(preview);preview=L.marker([draft.coordinate.lat,draft.coordinate.lng],{icon:L.divIcon({className:'edit-preview',html:'<span class="edit-preview-dot" style="--preview-color:'+esc(getColor())+'"></span>',iconSize:[24,24],iconAnchor:[12,12]}),interactive:false}).addTo(layer);$('edit-help').textContent='移动鼠标显示预览；右键确认位置，左键继续拖动地图。';}
  function finishMove(){moving=false;$('edit-mode').classList.remove('active');$('edit-mode').textContent='调整位置';}
  function stopMove(){finishMove();if(preview){layer.removeLayer(preview);preview=null;}}
  function updatePosition(latlng,confirmed){if(!moving||!draft)return;const point=clampCoordinate(latlng);draft.coordinate={lat:+point.lat.toFixed(7),lng:+point.lng.toFixed(7)};$('edit-position').textContent=(confirmed?'已确认：':'位置：')+draft.coordinate.lng.toFixed(6)+', '+draft.coordinate.lat.toFixed(6);if(preview)preview.setLatLng(point);if(confirmed){finishMove();if(creating){$('edit-help').textContent='位置已确认，标记已显示在选定位置；点击保存修改。';}else{markers.get(draft.id)?.setLatLng(point);if(preview){layer.removeLayer(preview);preview=null;}$('edit-help').textContent='位置已确认，标记已显示在选定位置；点击保存修改。';}}}
  function save(){if(!draft){$('edit-help').textContent='请先点击地点或新建标记。';return;}const name=$('edit-name').value.trim();if(!name){$('edit-name').focus();$('edit-help').textContent='请输入地点名称。';return;}const item={...draft,name,label:name,aliases:$('edit-aliases').value.split(/[,，]/).map(x=>x.trim()).filter(Boolean),tags:[...editingTags],color:getColor(),status:creating?'user':draft.status};try{store.save(item);}catch(error){$('edit-help').textContent='保存失败：'+error.message;return;}selected=item.id;draft=null;creating=false;originalCoordinate=null;editingTags=[];stopMove();$('edit-form').hidden=true;syncPlaces();renderMarkers();refreshSelects();refreshSelected();renderDynamicRoute();$('edit-help').textContent='地点已保存。';}
  function remove(){if(!draft){$('edit-help').textContent='请先点击要删除的地点。';return;}if(creating){cancelDraft();$('edit-help').textContent='已取消新建标记。';return;}$('delete-confirm').hidden=false;$('edit-help').textContent='请在下方确认删除，或返回继续编辑。';}
  function confirmRemove(){if(!draft||creating)return;const id=draft.id;try{store.remove(id);}catch(error){$('edit-help').textContent='删除失败：'+error.message;return;}if(routeStart===id)routeStart=null;if(routeEnd===id)routeEnd=null;selected=null;draft=null;originalCoordinate=null;editingTags=[];stopMove();$('edit-form').hidden=true;$('delete-confirm').hidden=true;syncPlaces();renderMarkers();refreshSelects();refreshSelected();renderDynamicRoute();$('edit-help').textContent='地点已删除。';}
  function exportEdits(){const blob=new Blob([store.exportText()],{type:'application/json'}),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='泉师地图-地点编辑.json';a.click();URL.revokeObjectURL(a.href);}
  function importEdits(file){const reader=new FileReader();reader.onload=()=>{try{store.importText(reader.result);location.reload();}catch(error){$('edit-help').textContent='导入失败：'+error.message;}};reader.readAsText(file);}
  function cancelDraft(){if(!draft)return;if(!creating&&originalCoordinate)markers.get(draft.id)?.setLatLng(originalCoordinate);selected=creating?null:selected;draft=null;creating=false;originalCoordinate=null;editingTags=[];stopMove();$('edit-form').hidden=true;$('delete-confirm').hidden=true;refreshSelected();$('edit-help').textContent='已取消修改。';}
  function setRoutePoint(kind,id){if(id&&(!find(id)||find(id).status==='unverified'))return;if(kind==='start')routeStart=id||null;else routeEnd=id||null;$('route-'+kind).value=id||'';debug.map.closePopup();renderDynamicRoute();}
  function clearRouteLayers(){routeLayer.clearLayers();endpointLayer.clearLayers();currentRoute=null;$('fit-route').disabled=true;$('route-note').textContent='';$('route-status').className='';}
  function drawRoute(result){
    currentRoute=result;routeLayer.clearLayers();
    L.polyline(result.points,{pane:'routes',color:'#087fe5',weight:3.5,interactive:false,smoothFactor:0}).addTo(routeLayer);
    for(const [point,isEnd] of [[result.points[0],false],[result.points.at(-1),true]])L.marker(point,{icon:L.divIcon({className:'endpoint'+(isEnd?' end':''),html:isEnd?'终':'起',iconSize:[25,25],iconAnchor:[12.5,12.5]}),interactive:false}).addTo(endpointLayer);
    $('route-status').textContent=result.meters+' 米 · 沿白路离线规划';
    $('route-note').textContent='起点距道路 '+(result.startDistance??'已校准')+' 米，终点距道路 '+(result.endDistance??'已校准')+' 米。';
    $('fit-route').disabled=false;
  }
  function getRouter(){return router ||= window.CampusEditedRouter.create({meta:base.meta,edges:base.edges,roads:allRoads()});}
  function routeOverrideKey(){return routeStart&&routeEnd?routeStart+'>'+routeEnd:'';}
  function routeThroughWaypoints(from,to,waypoints){
    const points=[],parts=[from.coordinate,...waypoints,to.coordinate];let meters=0;
    for(let i=1;i<parts.length;i++){
      const result=getRouter().plan({coordinate:parts[i-1]},{coordinate:parts[i]});
      if(result.error)return result;
      const line=result.roadPoints||result.displayPoints||result.points;
      points.push(...(points.length?line.slice(1):line));meters+=result.meters;
    }
    return {meters,points,displayPoints:points,method:'manual-road-override'};
  }
  function renderRouteWaypoints(){
    routeLayer.clearLayers();
    if(routeWaypoints.length)L.polyline(routeWaypoints,{pane:'routes',color:'#f08a24',weight:4,dashArray:'5 7',interactive:false}).addTo(routeLayer);
    routeWaypoints.forEach(point=>L.circleMarker(point,{pane:'routes',radius:4,color:'#f08a24',fillColor:'#fff',fillOpacity:1,weight:2,interactive:false}).addTo(routeLayer));
    $('route-adjust-save').disabled=!routeWaypoints.length;
  }
  function startRouteAdjustment(){
    if(!routeStart||!routeEnd){$('route-adjust-status').textContent='请先设置起点和终点。';return;}
    try{getRouter();}catch(error){$('route-adjust-status').textContent=error.message;return;}
    routeAdjusting=true;routeWaypoints=[];debug.map.dragging.disable();debug.map.doubleClickZoom.disable();renderRouteWaypoints();updateRouteAdjustControls();$('route-adjust-status').textContent='点击道路添加经过点，点位会吸附到现有路网。';
  }
  function stopRouteAdjustment(){routeAdjusting=false;routeWaypoints=[];routeLayer.clearLayers();debug.map.dragging.enable();debug.map.doubleClickZoom.enable();updateRouteAdjustControls();}
  function updateRouteAdjustControls(){
    $('route-adjust-start').textContent=routeAdjusting?'点击地图添加经过点':'调整路线';
    $('route-adjust-start').classList.toggle('active',routeAdjusting);
    $('route-adjust-save').disabled=!routeAdjusting||!routeWaypoints.length;
    $('route-adjust-cancel').disabled=!routeAdjusting;
    $('map').classList.toggle('route-adjusting',routeAdjusting);
  }
  function routeAdjustClick(event){
    const snap=getRouter().snap(event.latlng);
    if(!snap||snap.distanceMeters>25){$('route-adjust-status').textContent='请点击白色道路，路线点会吸附到路网。';return;}
    routeWaypoints.push(snap.coordinate);renderRouteWaypoints();$('route-adjust-status').textContent='已添加 '+routeWaypoints.length+' 个路网经过点。';
  }
  function saveRouteAdjustment(){
    if(!routeAdjusting||!routeWaypoints.length)return;
    const from=find(routeStart),to=find(routeEnd),result=routeThroughWaypoints(from,to,routeWaypoints);
    if(result.error){$('route-adjust-status').textContent=result.error;return;}
    routeOverrides[routeOverrideKey()]={waypoints:routeWaypoints.map(p=>({lat:p.lat,lng:p.lng})),updatedAt:Date.now()};
    localStorage.setItem(ROUTE_OVERRIDE_KEY,JSON.stringify(routeOverrides));
    stopRouteAdjustment();renderDynamicRoute();$('route-adjust-status').textContent='路线调整已保存。';
  }
  function renderSavedRoads(){
    savedRoadLayer.clearLayers();
    const list=$('saved-roads');list.replaceChildren();
    for(const road of allRoads()){
      for(const line of debug.layout.clipLine(road.points))L.polyline(line,{pane:'road-edits',color:'#279590',weight:1.25,opacity:.65,interactive:false,smoothFactor:0}).addTo(savedRoadLayer);
      const row=document.createElement('div');row.className='saved-road';
      const name=document.createElement('span');name.textContent='新增路线 · '+Math.round(road.points.slice(1).reduce((sum,p,i)=>sum+L.latLng(road.points[i]).distanceTo(p),0))+' 米';
      const button=document.createElement('button');button.type='button';button.className=roadDeleteId===road.id?'danger-button':'secondary-button';
      button.textContent=roadDeleteId===road.id?'确认删除':'删除';button.setAttribute('aria-label',button.textContent+'新增路线');
      button.addEventListener('click',()=>{
        if(roadDeleteId!==road.id){roadDeleteId=road.id;renderSavedRoads();$('road-edit-status').textContent='再次点击“确认删除”移除这条路线。';return;}
        try{if(!store.listRoads().some(item=>item.id===road.id))throw Error('固定任务路线不能在此删除');store.removeRoad(road.id);router=null;roadDeleteId=null;renderSavedRoads();renderDynamicRoute();$('road-edit-status').textContent='路线已删除。';}
        catch(error){roadDeleteId=null;renderSavedRoads();$('road-edit-status').textContent=error.message;}
      });
      row.append(name,button);list.append(row);
    }
  }
  function renderRoadDraft(){
    if(!draftRoad?.points.length){draftRoadLayer.clearLayers();return;}
    const points=roadCursor?[...draftRoad.points,roadCursor]:draftRoad.points;
    roadPreviewLine.setLatLngs(points).addTo(draftRoadLayer);
    roadPreviewStart.setLatLng(draftRoad.points[0]).addTo(draftRoadLayer);
  }
  function renderSavedRegions(){
    savedRegionLayer.clearLayers();
    const list=$('saved-regions');list.replaceChildren();
    for(const region of allRegions()){
      const isTaskBoundary=taskAnnotations.regions.some(item=>item.id===region.id);
      const polygon=L.polygon(debug.layout.displayRegion(region),{pane:'road-edits',color:'#1675db',weight:isTaskBoundary?1:2,opacity:isTaskBoundary ? .3 : 1,fillColor:'#69a9e8',fillOpacity:isTaskBoundary?0:.2,interactive:true}).addTo(savedRegionLayer);
      const content=document.createElement('div');
      const title=document.createElement('p');title.className='popup-name';title.textContent=region.name;content.append(title);
      const note=document.createElement('p');note.className='meta';note.textContent=region.instruction||'未填写修改说明';content.append(note);
      polygon.bindPopup(content,{maxWidth:280});
      const row=document.createElement('div');row.className='saved-region';
      const name=document.createElement('span');name.textContent=region.name;
      const button=document.createElement('button');button.type='button';button.className=regionDeleteId===region.id?'danger-button':'secondary-button';
      button.disabled=!store.listRegions().some(item=>item.id===region.id);
      button.textContent=button.disabled?'任务':regionDeleteId===region.id?'确认删除':'删除';
      button.addEventListener('click',()=>{if(button.disabled)return;if(regionDeleteId!==region.id){regionDeleteId=region.id;renderSavedRegions();return;}try{store.removeRegion(region.id);regionDeleteId=null;renderSavedRegions();$('region-edit-status').textContent='修改区域已删除。';}catch(error){regionDeleteId=null;renderSavedRegions();$('region-edit-status').textContent=error.message;}});
      row.append(name,button);list.append(row);
    }
  }
  function renderRegionDraft(){
    draftRegionLayer.clearLayers();
    if(!draftRegion?.points.length)return;
    const draw=regionDrawing?L.polyline:L.polygon;
    draw(draftRegion.points,{pane:'road-edits',color:'#1675db',weight:2,dashArray:'6 5',fillOpacity:.12,interactive:false}).addTo(draftRegionLayer);
    for(const point of draftRegion.points)L.circleMarker(point,{pane:'road-edits',radius:4,color:'#1675db',fillColor:'#fff',fillOpacity:1,weight:2,interactive:false}).addTo(draftRegionLayer);
  }
  function updateRegionControls(){
    $('draw-region').textContent=regionDrawing?'结束圈选':draftRegion?'继续圈选':'圈选区域';
    $('draw-region').classList.toggle('active',regionDrawing);
    $('cancel-region').disabled=!regionDrawing&&!draftRegion;
    $('undo-region-point').disabled=!regionDrawing||!draftRegion?.points.length;
    $('save-region').disabled=!draftRegion||draftRegion.points.length<3;
    $('map').classList.toggle('region-drawing',regionDrawing);
  }
  function cancelRegion(){regionDrawing=false;draftRegion=null;draftRegionLayer.clearLayers();debug.map.dragging.enable();debug.map.doubleClickZoom.enable();$('region-form').hidden=true;updateRegionControls();$('region-edit-status').textContent='已取消圈选。';}
  function finishRegion(){
    if(!regionDrawing||!draftRegion||draftRegion.points.length<3){$('region-edit-status').textContent='至少圈出 3 个点后才能结束。';return;}
    regionDrawing=false;debug.map.dragging.enable();debug.map.doubleClickZoom.enable();$('region-form').hidden=false;renderRegionDraft();updateRegionControls();$('region-edit-status').textContent='区域已圈出，请填写名称和修改说明后保存。';
  }
  function startRegion(){
    if(regionDrawing){finishRegion();return;}
    if(drawing)cancelRoad();
    if(draft)cancelDraft();
    if(!draftRegion){
      draftRegion={id:'region-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,6),points:[]};
      $('region-name').value='';$('region-instruction').value='';
    }
    regionDrawing=true;debug.map.closePopup();debug.map.dragging.disable();debug.map.doubleClickZoom.disable();$('region-form').hidden=false;renderRegionDraft();updateRegionControls();$('region-edit-status').textContent='点击地图添加边界点，右键或双击结束圈选。';
  }
  function regionClick(event){
    if(!regionDrawing)return;
    const point=[+event.latlng.lat.toFixed(7),+event.latlng.lng.toFixed(7)];
    if(!window.CampusPlaceStore.coordinate({lat:point[0],lng:point[1]})){$('region-edit-status').textContent='请在离线地图范围内圈选。';return;}
    const previous=draftRegion.points.at(-1);if(previous&&L.latLng(previous).distanceTo(point)<2)return;
    if(draftRegion.points.length>=500){$('region-edit-status').textContent='一个区域最多 500 个边界点。';return;}
    draftRegion.points.push(point);renderRegionDraft();updateRegionControls();$('region-edit-status').textContent='已圈选 '+draftRegion.points.length+' 个点。';
  }
  function saveRegion(){
    if(!draftRegion||draftRegion.points.length<3)return;
    if(regionDrawing)finishRegion();
    const name=$('region-name').value.trim();if(!name){$('region-name').scrollIntoView({block:'nearest'});$('region-name').focus();$('region-edit-status').textContent='请填写区域名称。';return;}
    try{store.saveRegion({...draftRegion,name,instruction:$('region-instruction').value.trim()});}catch(error){$('region-edit-status').textContent='保存失败：'+error.message;return;}
    draftRegion=null;$('region-form').hidden=true;draftRegionLayer.clearLayers();updateRegionControls();renderSavedRegions();$('region-edit-status').textContent='修改区域已保存。';
  }
  function updateRoadControls(){
    $('draw-road').textContent=drawing?'退出画笔':'画路线';
    $('draw-road').classList.toggle('active',drawing);
    $('cancel-road').disabled=!drawing;
    $('undo-road-point').disabled=!drawing||!draftRoad?.points.length;
    $('save-road').disabled=!drawing||draftRoad.points.length<2;
    $('map').classList.toggle('road-drawing',drawing);
  }
  function cancelRoad(){
    drawing=false;draftRoad=null;roadCursor=null;penDown=false;suppressRoadClick=false;draftRoadLayer.clearLayers();
    debug.map.dragging.enable();debug.map.doubleClickZoom.enable();updateRoadControls();
    $('road-edit-status').textContent='已取消绘制。';
  }
  function startRoad(){
    if(drawing){cancelRoad();return;}
    if(regionDrawing||draftRegion)cancelRegion();
    if(draft)cancelDraft();
    try{getRouter();}catch(error){$('road-edit-status').textContent='路网不可用：'+error.message;return;}
    drawing=true;draftRoad={id:'road-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,6),points:[],start:null,end:null};
    debug.map.closePopup();debug.map.dragging.disable();debug.map.doubleClickZoom.disable();updateRoadControls();
    $('road-edit-status').textContent='等待道路起点；可逐点点击或按住鼠标描线。';
  }
  function roadClick(event){
    if(!drawing)return;
    const point=[event.latlng.lat,event.latlng.lng];
    if(!window.CampusPlaceStore.coordinate({lat:point[0],lng:point[1]})){
      $('road-edit-status').textContent='请在离线地图范围内绘制。';return;
    }
    if(!draftRoad.points.length){
      const snap=getRouter().snap({lat:point[0],lng:point[1]});
      if(!snap||snap.distanceMeters>12){$('road-edit-status').textContent='起点需要靠近已有道路（12 米以内）。';return;}
      draftRoad.start={edge:snap.edge,offset:snap.offset};draftRoad.points.push(snap.coordinate);roadCursor=null;
      $('road-edit-status').textContent='已接入道路，沿目标路径继续点击拐点。';
    }else{
      if(L.latLng(draftRoad.points.at(-1)).distanceTo(point)<2)return;
      if(draftRoad.points.length>=400){$('road-edit-status').textContent='一条路线最多 400 个拐点。';return;}
      draftRoad.points.push(point);roadCursor=null;
      $('road-edit-status').textContent='已绘制 '+draftRoad.points.length+' 个点，可保存或撤销上一点。';
    }
    updateRoadControls();renderRoadDraft();
  }
  function saveRoad(){
    if(!draftRoad||draftRoad.points.length<2)return;
    const last=draftRoad.points.at(-1),snap=getRouter().snap({lat:last[0],lng:last[1]});
    const item={...draftRoad,points:draftRoad.points.map(p=>p.slice()),end:null};
    if(snap&&snap.distanceMeters<=8&&L.latLng(item.points[0]).distanceTo(snap.coordinate)>3){
      item.end={edge:snap.edge,offset:snap.offset};item.points[item.points.length-1]=snap.coordinate;
    }
    try{
      const next=window.CampusEditedRouter.create({meta:base.meta,edges:base.edges,roads:[...allRoads(),item]});
      store.saveRoad(item,allRoads());router=next;
    }catch(error){$('road-edit-status').textContent='保存失败：'+error.message;return;}
    cancelRoad();renderSavedRoads();renderDynamicRoute();$('road-edit-status').textContent='路线已保存并接入路网。';
  }
  window.__mapEditor={store,get route(){return currentRoute;},setRoutePoint,confirmPosition:point=>updatePosition(point,true),destination:()=>find(routeEnd||selected),setCurrentOrigin(coordinate){
    currentOrigin={id:'current-location',name:'当前位置',coordinate,status:'user',aliases:[]};byId.set(currentOrigin.id,currentOrigin);refreshSelects();
    if(!routeStart||routeStart===currentOrigin.id)setRoutePoint('start',currentOrigin.id);
  }};
  Object.defineProperty(debug,'route',{get:()=>currentRoute});
  function renderDynamicRoute(){
    clearRouteLayers();
    if(!routeStart||!routeEnd){$('route-status').textContent='选择起点和终点';return;}
    const from=find(routeStart),to=find(routeEnd);
    if(!from||!to){$('route-status').textContent='请选择有效的起点和终点';return;}
    if(routeStart===routeEnd){$('route-status').textContent='起点和终点相同';return;}
    let result;
    try{const override=routeOverrides[routeOverrideKey()];result=override?routeThroughWaypoints(from,to,override.waypoints):getRouter().plan(from,to);}catch(error){$('route-status').textContent='路网读取失败：'+error.message;$('route-status').className='error';return;}
    if(result.error){$('route-status').textContent=result.error;$('route-status').className='error';return;}
    drawRoute(result);
  }
  debug.map.on('mousedown',event=>{if(drawing&&event.originalEvent.button===0){penDown=true;suppressRoadClick=true;roadClick(event);}});
  debug.map.on('mousemove',event=>{
    updatePosition(event.latlng,false);
    if(drawing&&draftRoad.points.length){
      const point=[event.latlng.lat,event.latlng.lng];
      if(penDown){
        const previous=debug.map.latLngToContainerPoint(draftRoad.points.at(-1));
        if(previous.distanceTo(debug.map.latLngToContainerPoint(point))>=6)roadClick(event);
      }
      roadCursor=point;renderRoadDraft();
    }
  });
  window.addEventListener('mouseup',()=>{penDown=false;});
  debug.map.on('click',event=>{if(routeAdjusting)routeAdjustClick(event);else if(regionDrawing)regionClick(event);else if(suppressRoadClick)suppressRoadClick=false;else roadClick(event);});
  debug.map.on('dblclick',event=>{if(regionDrawing){L.DomEvent.stop(event);finishRegion();}});
  debug.map.on('contextmenu',event=>{if(moving){L.DomEvent.stop(event.originalEvent);updatePosition(debug.map.mouseEventToLatLng(event.originalEvent),true);}else if(regionDrawing){L.DomEvent.stop(event.originalEvent);finishRegion();}});
  $('edit-mode').addEventListener('click',()=>moving?stopMove():draft?armMove():selected?armMove():$('edit-help').textContent='请先点击地点或新建标记');
  $('new-place').addEventListener('click',newPlace);
  $('edit-selected').addEventListener('click',()=>selected&&openEditor(selected));
  $('save-place').addEventListener('click',save);
  $('cancel-edit').addEventListener('click',cancelDraft);
  $('delete-place').addEventListener('click',remove);
  $('confirm-delete').addEventListener('click',confirmRemove);
  $('cancel-delete').addEventListener('click',()=>{$('delete-confirm').hidden=true;$('edit-help').textContent='继续编辑地点。';});
  $('draw-road').addEventListener('click',startRoad);
  $('undo-road-point').addEventListener('click',()=>{if(!draftRoad)return;draftRoad.points.pop();if(!draftRoad.points.length)draftRoad.start=null;roadCursor=null;renderRoadDraft();updateRoadControls();$('road-edit-status').textContent='已撤销上一个点。';});
  $('save-road').addEventListener('click',saveRoad);
  $('cancel-road').addEventListener('click',cancelRoad);
  $('fit-route').addEventListener('click',()=>currentRoute&&debug.map.fitBounds(currentRoute.points,{padding:[40,40],maxZoom:18,animate:false}));
  $('route-adjust-start').addEventListener('click',()=>routeAdjusting?$('route-adjust-status').textContent='请在地图上点击道路添加经过点。':startRouteAdjustment());
  $('route-adjust-save').addEventListener('click',saveRouteAdjustment);
  $('route-adjust-cancel').addEventListener('click',()=>{stopRouteAdjustment();$('route-adjust-status').textContent='已取消路线调整。';});
  $('campus-clear-route').addEventListener('click',()=>{routeStart=null;routeEnd=null;$('route-start').value='';$('route-end').value='';renderDynamicRoute();});
  $('draw-region').addEventListener('click',startRegion);
  $('undo-region-point').addEventListener('click',()=>{if(!draftRegion)return;draftRegion.points.pop();renderRegionDraft();updateRegionControls();$('region-edit-status').textContent='已撤销上一个边界点。';});
  $('save-region').addEventListener('click',saveRegion);
  $('cancel-region').addEventListener('click',cancelRegion);
  $('show-region-frames').addEventListener('change',()=>{$('show-region-frames').checked?debug.map.addLayer(savedRegionLayer):debug.map.removeLayer(savedRegionLayer);});
  $('add-tag').addEventListener('click',addTags);
  $('edit-tag-input').addEventListener('keydown',event=>{if(event.key==='Enter'||event.key===','||event.key==='，'){event.preventDefault();addTags();}});
  $('export-edits').addEventListener('click',exportEdits);
  $('import-edits').addEventListener('click',()=>$('import-file').click());
  $('import-file').addEventListener('change',event=>event.target.files[0]&&importEdits(event.target.files[0]));
  $('edit-custom-color').addEventListener('input',()=>setColor($('edit-custom-color').value));
  document.querySelectorAll('input[name="edit-color"]').forEach(input=>input.addEventListener('change',()=>setColor(input.value)));
  $('campus-search').addEventListener('input',renderSearch);
  $('pending').addEventListener('change',renderMarkers);
  $('labels').addEventListener('change',scheduleLabels);
  $('roads').addEventListener('change',()=>{for(const roadLayer of [debug.roads,savedRoadLayer])$('roads').checked?debug.map.addLayer(roadLayer):debug.map.removeLayer(roadLayer);});
  debug.map.on('moveend zoomend resize',scheduleLabels);
  $('set-start').addEventListener('click',()=>selected&&setRoutePoint('start',selected));
  $('set-end').addEventListener('click',()=>selected&&setRoutePoint('end',selected));
  for(const kind of ['start','end'])$('route-'+kind).addEventListener('change',()=>setRoutePoint(kind,$('route-'+kind).value));
  syncPlaces();
  refreshSelects();
  renderMarkers();
  renderSearch();
  refreshSelected();
  renderSavedRoads();updateRoadControls();
  renderSavedRegions();updateRegionControls();
  if(store.loadError)$('edit-help').textContent=store.loadError+'；首次保存或删除时会自动保留原始数据备份并恢复编辑。';
})();
