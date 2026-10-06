(function () {
  'use strict';
  const data=window.CALIBRATED_MAP, manifest=window.TILE_MANIFEST;
  if(!window.L||!window.CampusLayout){window.dispatchEvent(new CustomEvent('campus-map-error',{detail:'地图运行库未加载'}));return;}
  if(!data||!manifest){window.dispatchEvent(new CustomEvent('campus-map-error',{detail:'地图底图数据缺失'}));return;}
  const $=id=>document.getElementById(id);
  const taskAnnotations=window.CAMPUS_TASK_ANNOTATIONS;
  const layout=window.CampusLayout;
  const map=L.map('map',{
    center:layout.crop.center,zoom:16,minZoom:15.5,maxZoom:19,zoomSnap:.25,
    rotate:true,bearing:layout.bearing,rotateControl:false,touchRotate:document.body.classList.contains('mobile-map'),shiftKeyRotate:false,
    zoomAnimation:false,fadeAnimation:false,markerZoomAnimation:false
  });
  window.campusMap=map;
  try{layout.attach(map);}catch(error){window.dispatchEvent(new CustomEvent('campus-map-error',{detail:'地图裁切层加载失败'}));}
  setTimeout(()=>{map.invalidateSize({pan:false});layout.fit(map);},250);
  const tileConfig=window.CAMPUS_TILE_CONFIG||{clean:'amap-no-label-cropped/{z}/{x}/{y}.png',original:'amap-cropped/{z}/{x}/{y}.png'};
  const Tiles=L.TileLayer.extend({
    _isValidTile(coords){
      const level=manifest[String(coords.z)];
      return !!level&&coords.x>=level.minX&&coords.x<=level.maxX&&coords.y>=level.minY&&coords.y<=level.maxY&&layout.tileIntersects(coords);
    }
  });
  const tiles=new Tiles(tileConfig.clean,{
    minZoom:15.5,maxZoom:19,maxNativeZoom:19,noWrap:true,keepBuffer:1,
    attribution:'底图 © 高德地图 · 离线副本'
  }).addTo(map);
  L.control.scale({metric:true,imperial:false}).addTo(map);
  for(const [name,index] of [['surfaces',390],['roads',410],['road-edits',425],['routes',450]]){
    const pane=map.createPane(name,map.getPane('rotatePane'));
    pane.style.zIndex=index;pane.style.pointerEvents='none';
  }
  // leaflet-rotate needs the renderer transform reset during a live gesture.
  // Keep one renderer per rotated pane so the Android WebView does not carry
  // a stale SVG matrix from another geometry layer.
  const GestureSvg=L.SVG.extend({
    getEvents(){
      const events=L.Renderer.prototype.getEvents.call(this);
      events.rotate=this._reset;
      return events;
    },
    _reset(){
      if(this._gestureResetFrame!==undefined)return;
      this._gestureResetFrame=requestAnimationFrame(()=>{
        this._gestureResetFrame=undefined;
        L.Renderer.prototype._reset.call(this);
      });
    }
  });
  const roadsRenderer=document.body.classList.contains('mobile-map')?new GestureSvg({pane:'roads',padding:.2}):null;
  const geometryRenderer=document.body.classList.contains('mobile-map')?new GestureSvg({pane:'routes',padding:.2}):null;
  const roads=L.layerGroup();
  if(!document.body.classList.contains('mobile-map'))roads.addTo(map);
  function latlng(pixel){
    const x=data.meta.origin[0]+pixel[0]+.5,y=data.meta.origin[1]+pixel[1]+.5,scale=256*2**data.meta.zoom;
    return [180/Math.PI*Math.atan(Math.sinh(Math.PI*(1-2*y/scale))),x/scale*360-180];
  }
  const lines=[];
  for(const edge of data.edges)lines.push(...layout.clipPixels(edge.points,data.meta));
  // Multi-polylines preserve every original road vertex without thousands of DOM paths.
  for(let i=0;i<lines.length;i+=128){
    L.polyline(lines.slice(i,i+128),{pane:'roads',renderer:roadsRenderer,color:'#279590',weight:1.25,opacity:.65,interactive:false,smoothFactor:0}).addTo(roads);
  }
  const taskRoadLayer=L.layerGroup().addTo(map);
  // Render the complete exported edit set. The router already consumes these
  // roads, but showing only one legacy annotation made the app look as if the
  // latest local export had not been bundled.
  const bundledEditedRoads=[...(taskAnnotations?.roads||[]),...(window.CAMPUS_INITIAL_EDITS?.roads||[])];
  const visibleEditedRoads=[...new Map(bundledEditedRoads.map(road=>[road.id,road])).values()];
  for(const road of visibleEditedRoads)
    for(const line of layout.clipLine(road.points))L.polyline(line,{pane:'roads',renderer:roadsRenderer,color:'#fff',weight:3,opacity:.95,interactive:false,smoothFactor:0}).addTo(taskRoadLayer);
  for(const field of layout.fields)new layout.AthleticsLayer(field).addTo(map);
  $('original')?.addEventListener('change',()=>{
    // Reattach so fractional view zooms still request integer XYZ tile levels.
    map.removeLayer(tiles);
    tiles.setUrl($('original').checked?tileConfig.original:tileConfig.clean);
    tiles.addTo(map);
  });
  $('fit-campus')?.addEventListener('click',()=>layout.fit(map));
  function updateLevel(){if($('level-status'))$('level-status').textContent=map.getZoom()+' 级';}
  map.on('zoomend',updateLevel);
  let resizeTimer=null,lastSize='';
  new ResizeObserver(()=>{
    clearTimeout(resizeTimer);
    resizeTimer=setTimeout(()=>{
      const rect=$('map').getBoundingClientRect(),size=rect.width+'x'+rect.height;
      if(size===lastSize)return;
      lastSize=size;map.invalidateSize({pan:false,animate:false});
    },90);
  }).observe($('map'));
  updateLevel();
  if($('data-count'))$('data-count').textContent=data.places.filter(p=>p.status==='verified').length+' 个地点已对照原图，'+data.places.filter(p=>p.status==='area').length+' 个区域地点，'+data.places.filter(p=>p.status==='regional').length+' 个扩展区域地点，'+data.pendingPlaces.length+' 条旧记录待核对。';
  window.__campusDebug={data,map,roads,taskRoadLayer,roadsRenderer,geometryRenderer,layout,bounds:L.latLngBounds(layout.crop.corners),tiles,latlng};
})();
