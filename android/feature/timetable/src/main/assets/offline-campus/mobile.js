(function(){
  'use strict';
  const $=id=>document.getElementById(id),native=window.CampusNative;
  const map=window.campusMap;
  if(!map){
    const status=document.getElementById('app-status');
    if(status)status.textContent='校园地图初始化失败，请返回后重试';
    return;
  }
  const storage={getItem(key){return key===CampusPlaceStore.KEY&&native?native.readEdits():localStorage.getItem(key);},setItem(key,value){
    if(key===CampusPlaceStore.KEY&&native){const error=native.writeEdits(value);if(error)throw Error(error);}else localStorage.setItem(key,value);
  }};
  const data=window.CALIBRATED_MAP;
  if(!data||!Array.isArray(data.places)){document.body.dataset.mapError='missing-map-data';throw Error('校园地图数据未加载');}
  document.addEventListener('pointerdown',event=>{
    const target=event.target;
    if(native&&target&&target.matches&&target.matches('input,textarea,[contenteditable="true"]'))native.requestKeyboard();
  },true);
  const AUTHOR_DEFAULT_TAG='默认展示';
  const store=CampusPlaceStore.create([...data.places,...(data.pendingPlaces||[])],storage);
  if(!storage.getItem(CampusPlaceStore.KEY)&&window.CAMPUS_INITIAL_EDITS)store.importText(JSON.stringify(CAMPUS_INITIAL_EDITS));
  const markers=new Map(),routeLayer=L.layerGroup().addTo(map),endpointLayer=L.layerGroup().addTo(map),endpointMarkers=new Map();
  let mode='view',selected=null,draft=null,positioning=false,pickingNew=false,following=true,heading=null,angle=0;
  let temporary=[],places=[],byId=new Map(),filters=null,showSchedule=true,locationMarker=null,currentOrigin=null,locationCanNavigate=false;
  let routeStart=null,routeEnd=null,routeResult=null,router=null,routerPromise=null,requestVersion=0,lastRouteFix=null,lastRouteTime=0,expiryTimer=null,routeBusy=false;
  let routeExpanded=false,activeSearchInput='start';
  // setIcon replaces marker elements; keep gesture listeners on the stable map.
  let markerPress=null,markerPressTimer=null,suppressMarkerClickUntil=0;
  function cancelMarkerPress(){clearTimeout(markerPressTimer);markerPressTimer=null;markerPress=null;}
  const mapElement=map.getContainer();
  function markerIdFromTarget(target){return target?.closest?.('[data-place-id]')?.dataset.placeId||null;}
  function startMarkerPress(event){
    cancelMarkerPress();
    const id=markerIdFromTarget(event.target);
    if(!id||event.isPrimary===false)return;
    if(event.pointerType==='mouse'&&event.button!==0)return;
    markerPress={id,x:event.clientX,y:event.clientY,ready:false};
    markerPressTimer=setTimeout(()=>{
      if(markerPress){markerPress.ready=true;suppressMarkerClickUntil=Date.now()+900;}
    },550);
  }
  function finishMarkerPress(){
    const press=markerPress;cancelMarkerPress();
    if(press?.ready){
      suppressMarkerClickUntil=Date.now()+900;
      // Finish Leaflet's active touch before changing the icon or map height.
      setTimeout(()=>attempt(()=>select(press.id,true)),0);
    }
  }
  mapElement.addEventListener('pointerdown',startMarkerPress,{passive:true,capture:true});
  // Some older Android WebViews expose touch events without a complete
  // PointerEvent implementation. Keep long-press details available there too.
  // Android WebView versions differ: some expose PointerEvent but still
  // deliver marker gestures through the legacy touch stream. Listen to both
  // streams so a long press never falls through to map panning/selection.
  mapElement.addEventListener('touchstart',event=>{
    if(event.touches.length!==1)return;
    const touch=event.touches[0];
    startMarkerPress({target:touch.target,clientX:touch.clientX,clientY:touch.clientY,isPrimary:true,pointerType:'touch'});
  },{passive:true,capture:true});
  mapElement.addEventListener('touchmove',event=>{
    const touch=event.touches[0];
    if(markerPress&&touch&&Math.hypot(touch.clientX-markerPress.x,touch.clientY-markerPress.y)>10)cancelMarkerPress();
  },{passive:true,capture:true});
  document.addEventListener('touchend',finishMarkerPress,{passive:true,capture:true});
  // Android terminates a stationary long press with touchcancel after its
  // contextmenu event. A moved/short gesture was already cancelled above.
  document.addEventListener('touchcancel',finishMarkerPress,{passive:true,capture:true});
  mapElement.addEventListener('pointermove',event=>{
    if(markerPress&&Math.hypot(event.clientX-markerPress.x,event.clientY-markerPress.y)>10)cancelMarkerPress();
  },{passive:true});
  document.addEventListener('pointerup',finishMarkerPress,{passive:true,capture:true});
  document.addEventListener('pointercancel',finishMarkerPress,{passive:true,capture:true});
  mapElement.addEventListener('contextmenu',event=>{if(event.target.closest?.('[data-place-id]'))event.preventDefault();});
  const details=$('map-details');
  function syncDetailsLayout(){
    const empty=[...details.children].every(child=>child.hidden);
    if(details.hidden!==empty)details.hidden=empty;
    document.body.classList.toggle('map-details-empty',empty);
  }
  new MutationObserver(syncDetailsLayout).observe(details,{attributes:true,subtree:true,attributeFilter:['hidden']});
  syncDetailsLayout();
  // AndroidView can retain its previous display list after a WebView-only
  // layout change. Request a bounded native redraw when visible UI changes.
  let nativeRedrawPending=false;
  new MutationObserver(()=>{
    if(nativeRedrawPending||!native?.requestRedraw)return;
    nativeRedrawPending=true;
    setTimeout(()=>{nativeRedrawPending=false;native.requestRedraw();},32);
  }).observe(document.body,{attributes:true,childList:true,subtree:true,characterData:true});
  let aliases=[],searchEntries=[];
  map.attributionControl?.remove();
  const normalize=text=>String(text||'').replace(/泉州师范学院|东海校区|主校区|\s/g,'').toLowerCase();
  const splitNames=value=>String(value||'').split(/[\\/、,，]/).map(item=>item.trim()).filter(Boolean);
  const isRoomName=text=>/[a-z]?\d{2,4}(?:教室|室)?$/i.test(normalize(text));
  function buildingKey(text){
    const value=normalize(text).replace(/(?:楼)?[a-z]?\d{2,4}(?:教室|室)?$/,'').replace(/栋$/,'');
    if(/^(黄)?朝阳(教学)?楼?$/.test(value))return '朝阳楼';
    if(/^(荣茂|荣茂综合)楼?$/.test(value))return '荣茂楼';
    if(/^(苏)?千墅(教学)?楼?$/.test(value))return '千墅楼';
    return value.endsWith('楼')?value:value+'楼';
  }
  function indexSearch(){
    const permanentBuildings=new Map();
    const namesFor=p=>[p.name,...(p.aliases||[]),...aliases.filter(a=>a.name===p.name).flatMap(a=>a.aliases||[])].flatMap(splitNames);
    for(const p of places.filter(p=>!p.temporary)){
      for(const name of namesFor(p))permanentBuildings.set(buildingKey(name),p);
    }
    searchEntries=places.flatMap(p=>{
      const groups=new Map();
      for(const name of namesFor(p)){
        const room=isRoomName(name),target=p.temporary&&room?permanentBuildings.get(buildingKey(name))||p:p;
        const label=target===p&&isRoomName(p.name)?[...new Set(splitNames(p.name).map(buildingKey))].join(' / '):target.name;
        if(!groups.has(target.id))groups.set(target.id,{place:target,label,names:[]});
        groups.get(target.id).names.push({text:normalize(name),building:buildingKey(name)});
      }
      return [...groups.values()];
    });
  }
  const status=text=>{$('app-status').textContent=text;};
  function tool(element,name,label){
    element.replaceChildren();element.title=label;element.setAttribute('aria-label',label);
    const icon=document.createElement('span');icon.className='tool-icon';icon.setAttribute('aria-hidden','true');
    if(name==='↔'){icon.textContent='↔';icon.classList.add('glyph-icon');}
    else icon.style.setProperty('--icon',`url('./vendor/lucide/icons/${name}.svg')`);
    element.append(icon);
  }
  function setLocationPending(pending){
    const button=$('app-locate');
    if(!button)return;
    button.classList.toggle('is-locating',Boolean(pending));
    button.title=pending?'正在定位':'定位';
    button.setAttribute('aria-label',pending?'正在定位':'定位');
    button.setAttribute('aria-busy',String(Boolean(pending)));
  }
  for(const [id,name,label] of [['app-locate','locate-fixed','定位'],['fit-campus','maximize','校区全览'],['filter-open','sliders-horizontal','筛选地点'],['route-expand','chevron-down','展开终点'],['route-swap','↔','交换起终点'],['route-go','navigation','开始导航'],['settings-open','pencil','编辑地图']]){
    const element=$(id);if(element)tool(element,name,label);
  }
  const actions=L.control({position:'topleft'});
  actions.onAdd=()=>{const el=document.querySelector('.map-actions');L.DomEvent.disableClickPropagation(el);L.DomEvent.disableScrollPropagation(el);return el;};actions.addTo(map);
  const header=document.querySelector('.map-header');
  if(header){L.DomEvent.disableClickPropagation(header);L.DomEvent.disableScrollPropagation(header);}
  tool(document.querySelector('.leaflet-control-zoom-in'),'plus','放大');tool(document.querySelector('.leaflet-control-zoom-out'),'minus','缩小');
  function attempt(action){try{return action();}catch(error){status(error.message||'操作失败，原数据保留');}}
  function button(label,action){const el=document.createElement('button');el.type='button';el.textContent=label;el.addEventListener('click',()=>attempt(action));return el;}
  function close(id){$(id).close();}
  function show(id){if(!$(id).open)$(id).showModal();}
  function choice(text,checked,change){const label=document.createElement('label');label.className='choice';const box=document.createElement('input');box.type='checkbox';box.checked=checked;box.addEventListener('change',()=>change(box.checked));label.append(box,document.createTextNode(text));return label;}
  function refresh(){
    const groups=new Map();
    for(const p of temporary.filter(p=>p.expiresAt>Date.now())){
      const key=p.coordinate.lat.toFixed(7)+','+p.coordinate.lng.toFixed(7);
      if(!groups.has(key))groups.set(key,{...p,id:'schedule-'+key,names:[],details:[]});
      const group=groups.get(key);group.names.push(p.name);group.details.push(p.detail);
    }
    const events=[...groups.values()].map(p=>({...p,name:[...new Set(p.names)].join(' / '),detail:[...new Set(p.details)].join('\n')}));
    places=[...store.list(),...events].filter(p=>p.status!=='unverified'&&CampusLayout.contains(p.coordinate));
    indexSearch();
    byId=new Map(places.map(p=>[p.id,p]));if(currentOrigin)byId.set(currentOrigin.id,currentOrigin);
    if(selected&&!byId.has(selected)&&!draft){selected=null;$('selected-panel').hidden=true;}
    if((routeEnd&&!byId.has(routeEnd))||(routeStart&&!byId.has(routeStart)))clearRoute();
    renderEndpointFlags();renderRouteHeader();render();
  }
  function shown(place,defaults,includeSelected=true){
    if(includeSelected&&(place.id===selected||place.id===routeStart||place.id===routeEnd))return true;
    if(place.temporary)return showSchedule;
    if(!filters&&defaults.overrides[place.id])return defaults.overrides[place.id]==='show';
    const initialDefaults=defaults.includeUntagged&&defaults.defaultTags.length===2&&defaults.defaultTags.includes('学院')&&defaults.defaultTags.includes('教学楼');
    if(!filters && initialDefaults && places.some(p=>(p.tags||[]).includes(AUTHOR_DEFAULT_TAG)))return (place.tags||[]).includes(AUTHOR_DEFAULT_TAG);
    const selectedTags=filters?.tags||defaults.defaultTags,untagged=filters?.untagged??defaults.includeUntagged;
    const tags=(place.tags||[]).filter(t=>t!==AUTHOR_DEFAULT_TAG);return tags.length?tags.some(tag=>selectedTags.includes(tag)):untagged;
  }
  function icon(place){const pin=document.createElement('span');pin.className='map-pin'+(place.temporary?' schedule-pin':'')+(place.id===selected?' selected':'');pin.style.setProperty('--pin-color',place.color||'#1682ea');return L.divIcon({className:'pin-host',html:pin.outerHTML,iconSize:[18,18],iconAnchor:[9,9]});}
  function render(){
    const defaults=store.preferences(),visible=places.filter(p=>shown(p,defaults));
    if(draft&&!positioning&&!visible.some(p=>p.id===draft.id))visible.push({...draft,name:draft.name||$('edit-name').value||'新地点'});
    const ids=new Set(visible.map(p=>p.id));
    for(const [id,marker] of markers)if(!ids.has(id)){map.removeLayer(marker);markers.delete(id);}
    const boxes=[];
    for(const p of visible.sort((a,b)=>Number(b.id===selected)-Number(a.id===selected))){
      let marker=markers.get(p.id);
      if(!marker){
        marker=L.marker(p.coordinate,{icon:icon(p),keyboard:true,title:p.name}).addTo(map);
        marker.on('click',()=>{if(Date.now()<suppressMarkerClickUntil)return;select(p.id);});
        markers.set(p.id,marker);
      }
      marker.options.title=p.name;marker.options.alt=p.name;
      const coordinate=draft?.id===p.id?draft.coordinate:p.coordinate;
      if(!marker.getLatLng().equals(coordinate))marker.setLatLng(coordinate);
      const appearance=JSON.stringify([p.name,p.label,p.color,p.temporary,p.id===selected]);
      if(marker._appearance!==appearance){marker.setIcon(icon(p));marker._appearance=appearance;}
      marker.getElement().dataset.placeId=p.id;
      const point=map.latLngToContainerPoint(p.coordinate),width=Math.min(140,p.name.length*12+6),box={x:point.x-width/2,y:point.y-30,w:width,h:25};
      const permanent=(map.getZoom()>=16||p.id===selected)&&!boxes.some(b=>b.x<box.x+box.w&&box.x<b.x+b.w&&b.y<box.y+box.h&&box.y<b.y+b.h);
      if(permanent)boxes.push(box);
      const tooltipKey=p.name+'|'+permanent;
      if(marker._tooltipKey!==tooltipKey||!marker.getTooltip()){
        marker.unbindTooltip().bindTooltip(document.createTextNode(p.name),{permanent,direction:'top',offset:[0,-8],className:'place-label'});
        marker._tooltipKey=tooltipKey;
      }
    }
  }
  function select(id,showScheduleDetails=false){
    if(positioning)return;
    const p=byId.get(id);if(!p)return;selected=id;following=false;
    const scheduleDetails=[...new Set(places.filter(item=>item.temporary&&(item.id===p.id||buildingKey(item.name)===buildingKey(p.name)))
      .flatMap(item=>(item.detail||'').split('\n')).filter(Boolean))];
    const note=$('selected-note');note.replaceChildren();
    if(showScheduleDetails&&scheduleDetails.length){
      note.className='schedule-list';
      const head=document.createElement('div');head.className='schedule-list-head';head.textContent='课程安排 · '+scheduleDetails.length+' 门';note.append(head);
      scheduleDetails.forEach((detail,index)=>{
        const item=document.createElement('div');item.className='schedule-item';
        const separator=detail.indexOf(' · '),course=separator>=0?detail.slice(0,separator):detail,room=separator>=0?detail.slice(separator+3):'';
        const number=document.createElement('span');number.className='schedule-index';number.textContent=String(index+1).padStart(2,'0');item.append(number);
        const title=document.createElement('span');title.className='schedule-course';title.textContent=course;
        item.append(title);
        if(room){const badge=document.createElement('span');badge.className='schedule-room';badge.textContent=room;item.append(badge);}
        note.append(item);
      });
    }else{
      note.className='selected-note-text';
      note.textContent=showScheduleDetails?'此地点暂无课程或活动':(scheduleDetails.length?'长按标记点查看课程与教室':p.detail||(p.tags||[]).filter(t=>t!==AUTHOR_DEFAULT_TAG).join(' · '));
    }
    $('selected-name').textContent=p.name;
    $('selected-panel').hidden=false;$('route-panel').hidden=true;$('edit-selected').hidden=!!p.temporary||mode!=='edit';
    $('pick-selected').hidden=mode!=='pick';$('set-start').hidden=$('set-end').hidden=mode==='pick';
    document.activeElement?.blur();$('campus-results').hidden=true;render();
  }
  function search(){
    const input=activeSearchInput==='end'?$('route-end-search'):$('campus-search');
    const raw=input.value,key=normalize(raw),container=$('campus-results');container.replaceChildren();container.hidden=!key;
    if(!key)return;
    const queryBuilding=buildingKey(raw);
    const defaultPreferences=store.preferences();
    const origin=currentOrigin?.coordinate||map.getCenter();
    const distanceToOrigin=place=>{
      try{return map.distance(L.latLng(origin.lat,origin.lng),L.latLng(place.coordinate.lat,place.coordinate.lng));}
      catch(_){return Number.POSITIVE_INFINITY;}
    };
    const grouped=new Map();
    for(const entry of searchEntries){
      if(!entry.names.some(n=>n.text.includes(key)||n.building===queryBuilding))continue;
      if(!grouped.has(entry.place.id))grouped.set(entry.place.id,{
        ...entry,distance:distanceToOrigin(entry.place),visible:shown(entry.place,defaultPreferences,false)?0:1
      });
    }
    const ranked=[...grouped.values()].sort((a,b)=>{
      return a.visible-b.visible||a.distance-b.distance||a.label.localeCompare(b.label,'zh-CN');
    }).slice(0,12);
    for(const {place:p,label} of ranked)container.append(button(label,()=>{
      if(activeSearchInput==='end')routeEnd=p.id;else routeStart=p.id;
      routeExpanded=activeSearchInput==='end'||routeExpanded;
      select(p.id);map.setView(p.coordinate,Math.max(17,map.getZoom()),{animate:false});
      renderEndpointFlags();
      if(routeStart&&routeEnd)route();
    }));
    if(!ranked.length){const text=document.createElement('p');text.textContent='没有匹配地点';container.append(text);}
  }
  function renderRouteHeader(){
    const startInput=$('campus-search'),endInput=$('route-end-search'),endRow=document.querySelector('.end-row');
    const from=byId.get(routeStart),to=byId.get(routeEnd);
    if(document.activeElement!==startInput)startInput.value=from?.name||'';
    if(document.activeElement!==endInput)endInput.value=to?.name||'';
    const showEnd=routeExpanded;
    endRow.hidden=!showEnd;
    const expand=$('route-expand');
    if(expand){tool(expand,showEnd?'chevron-up':'chevron-down',showEnd?'收起终点':'展开终点');expand.setAttribute('aria-expanded',String(showEnd));}
  }
  function renderFilters(){
    const defaults=store.preferences(),tags=filters?.tags||defaults.defaultTags,untagged=filters?.untagged??defaults.includeUntagged;
    const box=$('filter-tags');box.replaceChildren();
    function edit(tag,checked){if(!filters)filters={tags:[...defaults.defaultTags],untagged:defaults.includeUntagged};if(tag===null)filters.untagged=checked;else filters.tags=checked?[...new Set([...filters.tags,tag])]:filters.tags.filter(t=>t!==tag);render();}
    box.append(choice('无标签',untagged,checked=>edit(null,checked)));
    for(const tag of store.listTags().filter(t=>t!==AUTHOR_DEFAULT_TAG))box.append(choice(tag,tags.includes(tag),checked=>edit(tag,checked)));
  }
  function renderTags(){
    $('edit-tags').replaceChildren();if(!draft)return;
    for(const tag of store.listTags().filter(t=>t!==AUTHOR_DEFAULT_TAG))$('edit-tags').append(choice(tag,draft.tags.includes(tag),checked=>{draft.tags=checked?[...new Set([...draft.tags,tag])]:draft.tags.filter(t=>t!==tag);}));
  }
  function openEditor(id){
    const p=byId.get(id);if(!p||p.temporary)return;
    draft=JSON.parse(JSON.stringify({...p,aliases:p.aliases||[],tags:p.tags||[],color:p.color||'#1682ea'}));
    $('edit-name').value=draft.name;$('default-visible').value=store.preferences().overrides[draft.id]||'inherit';
    $('delete-place').hidden=false;$('delete-confirm').hidden=true;renderTags();show('editor');
  }
  function previewPosition(){
    const rect=map.getContainer().getBoundingClientRect();$('center-preview').style.left=rect.left+rect.width/2+'px';$('center-preview').style.top=rect.top+rect.height/2+'px';
  }
  function position(){
    following=false;positioning=true;map._campusFreePan=true;$('position-bar').hidden=false;$('center-preview').hidden=false;
    $('center-preview').style.setProperty('--pin-color',draft?.color||'#1682ea');
    $('selected-panel').hidden=true;$('route-panel').hidden=true;document.activeElement?.blur();close('editor');
    if(draft)map.panTo(draft.coordinate,{animate:false});previewPosition();
  }
  function endPosition(){positioning=false;map._campusFreePan=false;$('position-bar').hidden=true;$('center-preview').hidden=true;}
  function cancelDraft(){
    close('editor');endPosition();draft=null;refresh();
    // Returning from a cancelled edit must restore the selected place actions;
    // otherwise the next edit/delete tap appears to do nothing.
    if(selected&&byId.has(selected))select(selected);
  }
  function newPlace(){
    if(mode==='pick'){pickingNew=true;position();return;}
    mode='edit';
    draft={id:'local-'+Date.now().toString(36),name:'',label:'',aliases:[],tags:[],coordinate:{lat:map.getCenter().lat,lng:map.getCenter().lng},status:'user',color:'#1682ea'};
    $('edit-name').value='';$('default-visible').value='inherit';$('delete-place').hidden=true;$('delete-confirm').hidden=true;renderTags();position();
  }
  function save(){
    if(!draft)return;const name=$('edit-name').value.trim();if(!name){$('edit-name').focus();status('请填写地点名称');return;}
    if(draft.name&&draft.name!==name)draft.aliases=[...new Set([...draft.aliases,draft.name])];
    const preferences=store.preferences(),display=$('default-visible').value;
    if(display==='inherit')delete preferences.overrides[draft.id];else preferences.overrides[draft.id]=display;
    const next={...draft,name,label:name};store.saveWithPreferences(next,preferences);selected=next.id;draft=null;close('editor');refresh();select(selected);status('地点已保存');
  }
  function renderTagManager(){
    const list=$('tag-list');list.replaceChildren();
    for(const tag of store.listTags().filter(t=>t!==AUTHOR_DEFAULT_TAG)){
      const row=document.createElement('div');row.className='tag-row';const input=document.createElement('input');input.value=tag;input.maxLength=40;input.setAttribute('aria-label','标签 '+tag);
      let deleting=false;const remove=button('删除',()=>{if(!deleting){deleting=true;remove.textContent='确认删除';return;}store.renameTag(tag,'');filters=null;refresh();renderTagManager();});
      row.append(input,button('改名',()=>{const name=input.value.trim();if(!name)throw Error('标签名称不能为空');store.renameTag(tag,name);filters=null;refresh();renderTagManager();}),remove);list.append(row);
    }
  }
  async function ensureRouter(){
    if(router)return router;
    if(!routerPromise)routerPromise=fetch('./data/road-network.json').then(response=>{if(!response.ok)throw Error('无法读取本地路网');return response.json();}).then(graph=>{
      const roads=[...new Map([...CAMPUS_TASK_ANNOTATIONS.roads,...store.listRoads()].map(r=>[r.id,r])).values()].filter(r=>r.id!=='road-campus-zs-shortcut');
      return router=CampusEditedRouter.create({...graph,roads,regions:store.listRegions()});
    }).catch(error=>{routerPromise=null;throw error;});
    return routerPromise;
  }
  async function route(){
    if(routeBusy)return;
    const version=++requestVersion,from=byId.get(routeStart),to=byId.get(routeEnd);
    routeResult=null;routeLayer.clearLayers();
    if(!from||!to){status('请选择起点，或点击定位');return;}
    routeBusy=true;
    $('route-panel').hidden=false;$('route-status').textContent='正在规划路线';$('route-note').hidden=true;
    $('fit-route').disabled=true;$('app-external').disabled=true;
    try{
      const routing=await ensureRouter();if(version!==requestVersion)return;
      await new Promise(resolve=>setTimeout(resolve,0));
      if(version!==requestVersion)return;
      const result=routing.plan(from,to);
      if(version!==requestVersion)return;
      if(result.error){routeLayer.clearLayers();routeResult=null;$('route-status').textContent=result.error;return;}
      routeResult=result;routeLayer.clearLayers();
      // Draw only the snapped road geometry. The place-to-road connectors stay
      // out of the visible line so the route never appears to cut across a lawn.
      const points=result.roadPoints?.length?result.roadPoints:result.displayPoints||result.points;
      L.polyline(points,{pane:'routes',renderer:__campusDebug.geometryRenderer,color:'#fff',weight:7,interactive:false,smoothFactor:0}).addTo(routeLayer);
      L.polyline(points,{pane:'routes',renderer:__campusDebug.geometryRenderer,color:'#1682ea',weight:4,interactive:false,smoothFactor:0}).addTo(routeLayer);
      renderEndpointFlags();
      $('route-status').textContent=Math.max(1,Math.ceil(result.meters/75))+' 分钟 · '+Math.round(result.meters)+' 米';
      $('route-note').hidden=false;$('fit-route').disabled=false;$('app-external').disabled=false;
      $('selected-panel').hidden=true;status('');
    }catch(error){if(version===requestVersion)$('route-status').textContent=error.message;}
    finally{routeBusy=false;}
  }
  function renderEndpointFlags(){
    const from=byId.get(routeStart),to=byId.get(routeEnd);
    renderRouteHeader();
    for(const [id,kind] of [[routeStart,'start'],[routeEnd,'end']]){
      const place=id&&byId.get(id);let marker=endpointMarkers.get(kind);
      // The live location is already represented by the small blue arrow. It
      // must not create a second start flag; only a chosen start gets one.
      if(!place||place.id==='current-location'){
        if(marker)endpointLayer.removeLayer(marker);
        endpointMarkers.delete(kind);
        continue;
      }
      if(marker){marker.setLatLng(place.coordinate);continue;}
      const color=kind==='start'?'#15966e':'#1675db';
      const icon=L.divIcon({className:'route-flag-host',html:'<span class="route-flag '+kind+'" style="--flag-color:'+color+'"><i></i></span>',iconSize:[24,28],iconAnchor:[2,22]});
      marker=L.marker(place.coordinate,{pane:'markerPane',icon,interactive:false,title:kind==='start'?'起点':'终点',zIndexOffset:1100}).addTo(endpointLayer);
      endpointMarkers.set(kind,marker);
    }
  }
  function clearRoute(){requestVersion++;routeStart=null;routeEnd=null;routeResult=null;routeExpanded=false;routeLayer.clearLayers();renderEndpointFlags();$('route-panel').hidden=true;render();}
  function rotateArrow(){
    const arrow=locationMarker?.getElement()?.querySelector('.location-arrow');if(!arrow)return;
    arrow.classList.toggle('uncertain',heading===null);
    const target=(heading??0)+map.getBearing();angle+=((target-angle+540)%360+360)%360-180;arrow.style.transform='rotate('+angle+'deg)';
  }
  function clearLocation(){
    if(locationMarker){map.removeLayer(locationMarker);locationMarker=null;}
    locationCanNavigate=false;
    byId.delete('current-location');currentOrigin=null;
    if(routeStart==='current-location')routeStart=null;
    renderEndpointFlags();
    scheduleRender();
  }
  function setLocation(point){
    if(!Number.isFinite(point?.lat)||!Number.isFinite(point?.lng))return;
    if(!point.cached&&(!point.timestamp||Date.now()-point.timestamp>60000||point.timestamp>Date.now()+2000)){status('正在获取新位置');return;}
    if(!Number.isFinite(point.accuracy)||point.accuracy<=0||point.accuracy>10000){status('正在获取位置范围');return;}
    if(!CampusLayout.contains(point)){
      clearLocation();
      status('当前位置不在东海校区地图范围内');
      return;
    }
    const cachedClass=point.cached?' cached':'';
    if(!locationMarker)locationMarker=L.marker(point,{icon:L.divIcon({className:'location-icon',html:'<div class="location-arrow'+cachedClass+'"></div>',iconSize:[14,17],iconAnchor:[7,8.5]}),interactive:false,zIndexOffset:1000}).addTo(map);else{locationMarker.setLatLng(point);const element=locationMarker.getElement()?.querySelector('.location-arrow');if(element)element.classList.toggle('cached',Boolean(point.cached));}
    locationCanNavigate=point.canNavigate!==false;
    rotateArrow();currentOrigin={id:'current-location',name:'当前位置',coordinate:point};byId.set(currentOrigin.id,currentOrigin);
    if(!routeStart)routeStart=currentOrigin.id;
    renderEndpointFlags();
    if(following&&!positioning)map.setView(point,Math.max(map.getZoom(),17),{animate:false});
    if(point.cached){status('正在获取实时位置，当前显示上次校内位置');return;}
    if(!locationCanNavigate){status(point.precisePermission===false?'当前位置已显示，开启精确定位后才能规划当前位置路线':'当前位置已显示，正在等待更高精度定位');return;}
    if(routeEnd&&routeStart===currentOrigin.id&&Date.now()-lastRouteTime>=5000&&(!lastRouteFix||map.distance(lastRouteFix,point)>=Math.max(5,point.accuracy))){
      lastRouteFix=point;lastRouteTime=Date.now();route();
    }
    if((point.accuracy??0)>50)status('当前位置精度较低，路线可能有偏差');
  }
  function setDestination(value){
    if(!value)return;const input=typeof value==='string'?{name:value}:value;if(!input.name&&!input.placeId)return;
    let place=input.placeId?byId.get(input.placeId):null;
    if(!place){
      const key=normalize(input.name),building=buildingKey(input.name);
      const matches=places.filter(p=>[p.name,...(p.aliases||[]),...aliases.filter(a=>a.name===p.name).flatMap(a=>a.aliases||[])].some(n=>normalize(n)===key||buildingKey(n)===building||normalize(n)===building+'楼'));
      if(matches.length===1)place=matches[0];
    }
    // Older native catalogues can use IDs that do not exist in the calibrated map.
    // Resolve by name first, then use a supplied coordinate as a last resort.
    if(!place&&input.coordinate){
      place={id:'requested-location',name:input.name,coordinate:input.coordinate,tags:[],aliases:[],temporary:true,status:'user'};
      if(CampusLayout.contains(place.coordinate)){places.push(place);byId.set(place.id,place);}else{status('地点不在地图范围内');return;}
    }
    if(!place){status(input.placeId?'关联地点已删除，请重新选择':'没有找到对应的校园地点');return;}
    $('route-end-search').value=input.name||place?.name||'';
    if(place){select(place.id);map.setView(place.coordinate,Math.max(17,map.getZoom()),{animate:false});if(mode!=='pick'){routeEnd=place.id;routeExpanded=true;renderEndpointFlags();if(routeStart)route();}}
    else{search();status('请选择对应地点');}
  }
  function setScheduleMarkers(values){
    temporary=Array.isArray(values)?values:[];refresh();clearTimeout(expiryTimer);
    const next=Math.min(...temporary.map(p=>p.expiresAt).filter(t=>t>Date.now()));
    if(Number.isFinite(next))expiryTimer=setTimeout(()=>setScheduleMarkers(temporary.filter(p=>p.expiresAt>Date.now())),Math.min(60000,Math.max(0,next-Date.now()+10)));
  }
  // Search only rebuilds the lightweight result list; keep it synchronous so
  // a tap immediately after typing cannot select a stale result. Map layers
  // are not touched until a result is chosen.
  let searchPress=null,lastSearchTap=0,collapseSearchUntil=0,searchIntent=false;
  const searchInput=$('campus-search'),endSearchInput=$('route-end-search');
  new ResizeObserver(()=>{
    document.documentElement.style.setProperty('--search-bottom',document.querySelector('.map-header').getBoundingClientRect().bottom+'px');
  }).observe(document.querySelector('.map-header'));
  function collapseAutomaticSelection(){
    const input=activeSearchInput==='end'?endSearchInput:searchInput;
    if(performance.now()>collapseSearchUntil||document.activeElement!==input)return;
    if(input.value&&input.selectionStart===0&&input.selectionEnd===input.value.length){
      const end=input.value.length;input.setSelectionRange(end,end);
    }
  }
  function bindSearchInput(input,kind){
    input.addEventListener('pointerdown',event=>{
      activeSearchInput=kind;searchIntent=true;
      searchPress={time:performance.now(),x:event.clientX,y:event.clientY};collapseSearchUntil=0;
    });
    input.addEventListener('pointerup',event=>{
      activeSearchInput=kind;
      const now=performance.now(),press=searchPress;searchPress=null;
      if(press&&now-press.time<300&&now-lastSearchTap>350&&Math.hypot(event.clientX-press.x,event.clientY-press.y)<10){
        collapseSearchUntil=now+300;requestAnimationFrame(collapseAutomaticSelection);setTimeout(collapseAutomaticSelection,120);
      }
      lastSearchTap=now;
    });
    input.addEventListener('pointercancel',()=>{searchPress=null;collapseSearchUntil=0;});
    input.addEventListener('select',collapseAutomaticSelection);
    input.addEventListener('input',()=>{
      activeSearchInput=kind;
      if(kind==='end'&&!input.value.trim()){
        routeEnd=null;routeResult=null;routeExpanded=false;routeLayer.clearLayers();renderEndpointFlags();
      }else if(kind==='start'&&!input.value.trim()){
        routeStart=null;routeResult=null;routeLayer.clearLayers();renderEndpointFlags();
      }
      search();
    });
    input.addEventListener('focus',()=>{activeSearchInput=kind;if(!searchIntent){input.blur();return;}search();});
    input.addEventListener('blur',()=>{searchIntent=false;});
    input.addEventListener('keydown',event=>{if(event.key==='Enter'){event.preventDefault();input.blur();}});
  }
  bindSearchInput(searchInput,'start');
  bindSearchInput(endSearchInput,'end');
  document.addEventListener('keydown',event=>{if(event.key==='Tab')searchIntent=true;},true);
  $('filter-open').onclick=()=>{renderFilters();show('filters');};$('show-schedule').onchange=()=>{showSchedule=$('show-schedule').checked;render();};
  $('filter-defaults').onclick=()=>{filters=null;renderFilters();render();};$('save-defaults').onclick=()=>attempt(()=>{const p=store.preferences();if(filters){p.defaultTags=filters.tags;p.includeUntagged=filters.untagged;}store.setPreferences(p);filters=null;status('默认显示已保存');close('filters');render();});
  $('settings-open')?.addEventListener('click',()=>mode==='pick'?attempt(newPlace):show('settings'));
  $('start-edit').onclick=()=>{close('settings');attempt(newPlace);};
  $('manage-defaults').onclick=()=>{close('settings');filters=null;renderFilters();show('filters');};$('manage-tags').onclick=()=>{close('settings');renderTagManager();show('tag-manager');};
  $('edit-selected').onclick=()=>attempt(()=>openEditor(selected));$('move-place').onclick=position;
  $('position-confirm').onclick=()=>attempt(()=>{
    const point=map.getCenter();if(!CampusLayout.contains(point))throw Error('请选择校区内的位置');
    if(pickingNew){native?.chooseLocation(JSON.stringify({name:$('campus-search').value||'临时地点',coordinate:point}));pickingNew=false;endPosition();return;}
    if(draft){draft.coordinate={lat:point.lat,lng:point.lng};endPosition();render();show('editor');}
  });
  $('position-cancel').onclick=()=>{endPosition();pickingNew=false;if(draft){if(byId.has(draft.id))show('editor');else cancelDraft();}};
  $('save-place').onclick=()=>attempt(save);$('cancel-edit').onclick=cancelDraft;$('editor').addEventListener('cancel',event=>{event.preventDefault();cancelDraft();});
  $('delete-place').onclick=()=>{$('delete-confirm').hidden=false;};$('cancel-delete').onclick=()=>{$('delete-confirm').hidden=true;};
  $('confirm-delete').onclick=()=>attempt(()=>{if(!draft)return;store.remove(draft.id);draft=null;selected=null;close('editor');$('selected-panel').hidden=true;$('undo-delete').disabled=false;refresh();status('地点已删除，可在编辑设置中撤销');});
  $('undo-delete').onclick=()=>attempt(()=>{store.undoDelete();$('undo-delete').disabled=true;refresh();status('地点已恢复');});
  $('add-tag').onclick=()=>attempt(()=>{const tag=$('tag-name').value.trim();if(!tag)return;store.addTags(tag);$('tag-name').value='';renderTagManager();});
  $('selected-close').onclick=()=>{selected=null;$('selected-panel').hidden=true;$('route-panel').hidden=!routeResult;render();};
  $('set-start').onclick=()=>{routeStart=selected;renderEndpointFlags();if(routeEnd)route();status('起点已设置');};$('set-end').onclick=()=>{routeEnd=selected;routeExpanded=true;if(!routeStart&&currentOrigin)routeStart=currentOrigin.id;renderEndpointFlags();route();render();};
  function bindHeaderAction(id, action){
    const element=$(id);if(!element)return;
    element.onclick=event=>{event.preventDefault();event.stopPropagation();attempt(action);};
  }
  bindHeaderAction('route-expand',()=>{routeExpanded=!routeExpanded;renderRouteHeader();});
  bindHeaderAction('route-swap',()=>{const next=routeStart;routeStart=routeEnd;routeEnd=next;routeExpanded=true;renderEndpointFlags();if(routeStart&&routeEnd)route();});
  bindHeaderAction('route-go',()=>{if(!routeStart&&currentOrigin)routeStart=currentOrigin.id;if(!routeStart||!routeEnd){status('请选择起点和终点');routeExpanded=true;renderRouteHeader();return;}route();});
  $('pick-selected').onclick=()=>{const p=byId.get(selected);if(p)native?.chooseLocation(JSON.stringify({name:p.name,placeId:p.temporary?null:p.id,coordinate:p.coordinate}));};
  $('app-locate').onclick=()=>{following=true;if(currentOrigin)map.setView(currentOrigin.coordinate,Math.max(map.getZoom(),17),{animate:false});native?.requestLocation();};
  $('fit-route').onclick=()=>{if(routeResult)map.fitBounds(routeResult.points,{padding:[30,30],maxZoom:18,animate:false});};$('campus-clear-route').onclick=clearRoute;
  $('app-external').onclick=()=>{const p=byId.get(routeEnd||selected);if(p)native?.openNavigation(p.name,p.coordinate.lat,p.coordinate.lng);};
  $('import-edits').onclick=()=>native?.importEdits();$('export-edits').onclick=()=>native?.exportEdits(store.exportText());
  let redrawFrame=null;
  function scheduleRender(){if(redrawFrame!==null)return;redrawFrame=requestAnimationFrame(()=>{redrawFrame=null;render();if(positioning)previewPosition();});}
  map.on('dragstart',()=>following=false);map.on('moveend zoomend',scheduleRender);map.on('rotate',()=>{
    rotateArrow();
    scheduleRender();
    // leaflet-rotate changes the map transform outside Leaflet's normal
    // viewreset path. Reset every route path after the transform so Android
    // WebView cannot keep one stale SVG matrix during a gesture.
    routeLayer.eachLayer(layer=>{layer._reset?.();layer.redraw?.();});
  });map.on('resize',()=>{if(positioning)previewPosition();});
  window.CampusApp={status,setLocation,clearLocation,setLocationPending,setHeading(value){heading=Number.isFinite(value)?value:null;rotateArrow();},setDestination,setScheduleMarkers,
    openSettings(){show('settings');},
    setMode(value){
      mode=value;
      const picking=mode==='pick';
      const button=$('settings-open');
      if(button){
        // Browse mode uses the native top-bar action; keep this only in picker/edit modes.
        button.hidden=mode==='view';
        tool(button,picking?'locate-fixed':'pencil',picking?'选取临时位置':'编辑地图');
      }
      // Android WebView can restore focus to the first input after the page is
      // created. Keep the initial map view keyboard-free until the user taps it.
      if(document.activeElement===searchInput)searchInput.blur();
      if(document.activeElement===endSearchInput)endSearchInput.blur();
    },
    setTheme(theme){for(const key of ['accent','ink','muted','surface','on-accent'])if(/^#[a-f0-9]{6}$/i.test(theme[key]||''))document.documentElement.style.setProperty('--'+key,theme[key]);
      if(theme.surface)document.documentElement.style.setProperty('--panel',theme.surface+'ee');},
    importText(text){attempt(()=>{store.importText(text);router=null;routerPromise=null;refresh();close('settings');status('编辑数据已导入');if(routeEnd)route();});},
    setAliases(records){aliases=Array.isArray(records)?records:[];for(const legacy of aliases){const place=places.find(p=>p.name===legacy.name);if(place)place.aliases=[...new Set([...(place.aliases||[]),...legacy.aliases])];}refresh();},
    inspect(){return {visible:[...markers.keys()],route:routeResult,routerLoaded:!!router,preferences:store.preferences(),mode,positioning};}
  };
  window.__mapEditor={store,get route(){return routeResult;},setRoutePoint(kind,id){if(kind==='start')routeStart=id;else routeEnd=id;renderEndpointFlags();route();},destination:()=>byId.get(routeEnd||selected)};
  Object.defineProperty(window.__campusDebug,'route',{get:()=>routeResult});
  refresh();if(store.loadError)status(store.loadError);
})();
