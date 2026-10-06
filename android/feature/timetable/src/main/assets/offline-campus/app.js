(function () {
  'use strict';
  const map=window.campusMap,editor=window.__mapEditor,$=id=>document.getElementById(id);
  let locationMarker=null,following=true;
  let aliases=[];
  const status=text=>{$('app-status').textContent=text;};
  const normalize=text=>String(text||'').replace(/泉州师范学院|东海校区|主校区|\s/g,'').toLowerCase();
  function setDestination(query){
    if(!query)return;
    query=aliases.find(p=>p.aliases.some(alias=>normalize(alias)===normalize(query)))?.name||query;
    const key=normalize(query),building=key.replace(/[a-z]?\d{2,4}(?:教室)?$/,'');
    const places=editor.store.list().filter(p=>p.status!=='unverified'&&CampusLayout.contains(p.coordinate));
    const exact=places.find(p=>[p.name,p.label,...(p.aliases||[])].some(t=>normalize(t)===key||normalize(t)===building));
    const matched=exact||places.find(p=>[p.name,p.label,...(p.aliases||[])].some(t=>normalize(t).length>1&&key.includes(normalize(t))));
    $('campus-search').value=matched?(matched.label||matched.name):query;
    $('campus-search').dispatchEvent(new Event('input',{bubbles:true}));
    if(matched){editor.setRoutePoint('end',matched.id);map.setView(matched.coordinate,Math.max(map.getZoom(),17),{animate:false});following=false;}
    else status('没有找到对应校内地点，可继续搜索或点击地图选择');
  }
  function setLocation(point){
    if(!Number.isFinite(point.lat)||!Number.isFinite(point.lng))return;
    if(!CampusLayout.contains(point)){status('当前位置在校区范围外，请手动选择校内起点');return;}
    if(!locationMarker)locationMarker=L.circleMarker(point,{radius:7,color:'#fff',weight:2,fillColor:'#087fe5',fillOpacity:1,interactive:false}).addTo(map);
    else locationMarker.setLatLng(point);
    editor.setCurrentOrigin({lat:point.lat,lng:point.lng});
    if(following)map.setView(point,Math.max(map.getZoom(),17),{animate:false});
    status(point.accuracy>100?'定位精度较低，可手动选择起点':'');
  }
  map.on('dragstart',()=>{following=false;});
  map.on('click',event=>{if(event.originalEvent?.pointerType==='touch'||event.originalEvent?.sourceCapabilities?.firesTouchEvents)editor.confirmPosition(event.latlng);});
  window.__campusDebug.tiles.on('tileerror',()=>status('本地地图资源加载失败，请返回后重试'));
  $('app-locate').addEventListener('click',()=>{following=true;status('正在定位');window.CampusNative?.requestLocation();});
  $('app-editor').addEventListener('click',()=>{following=false;document.body.classList.toggle('editing');$('app-editor').textContent=document.body.classList.contains('editing')?'结束编辑':'地图编辑';});
  $('app-external').addEventListener('click',()=>{
    const place=editor.destination();
    if(!place){status('请先选择目的地');return;}
    window.CampusNative?.openNavigation(place.name,place.coordinate.lat,place.coordinate.lng);
  });
  function replaceAction(id,callback){const old=$(id),button=old.cloneNode(true);old.replaceWith(button);button.addEventListener('click',callback);}
  replaceAction('import-edits',()=>window.CampusNative?.importEdits());
  replaceAction('export-edits',()=>window.CampusNative?.exportEdits(editor.store.exportText()));
  window.CampusApp={status,setDestination,setLocation,setAliases(value){aliases=Array.isArray(value)?value:[];},importText(text){try{editor.store.importText(text);location.reload();}catch(error){status('导入失败：'+error.message);}}};
})();
