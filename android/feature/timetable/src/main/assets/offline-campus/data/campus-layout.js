(function () {
  'use strict';
  const zoom = 18;
  const tasks = window.CAMPUS_TASK_ANNOTATIONS;
  const school = tasks.regions.find(r => r.name === '学校区域');
  const project = ll => L.CRS.EPSG3857.latLngToPoint(L.latLng(ll), zoom);
  const unproject = p => L.CRS.EPSG3857.pointToLatLng(L.point(p), zoom);
  const origin = project(school.points[0]);
  const rotate = (p, angle) => L.point(p.x*Math.cos(angle)-p.y*Math.sin(angle), p.x*Math.sin(angle)+p.y*Math.cos(angle));
  const toLocal = ll => project(ll).subtract(origin);
  const toLatLng = p => unproject(p.add(origin));

  function rectangle(points, angle, padding = 0) {
    const bounds = L.bounds(points.map(p => rotate(p, angle)));
    const min = bounds.min.subtract([padding,padding]), max = bounds.max.add([padding,padding]);
    const corners = [min,L.point(max.x,min.y),max,L.point(min.x,max.y)].map(p => toLatLng(rotate(p,-angle)));
    return {angle,min,max,corners,width:max.x-min.x,height:max.y-min.y,center:toLatLng(rotate(min.add(max).divideBy(2),-angle))};
  }
  // The southeast frontage is the edge from the south corner to the east corner.
  // Align the frontage, then turn the view 90 degrees counterclockwise.
  const front = toLocal(school.points[3]).subtract(toLocal(school.points[2]));
  const bearing = -Math.atan2(front.y,front.x)-Math.PI/2;
  const crop = rectangle(school.points.map(toLocal),bearing,12);

  function fieldRectangle(region) {
    const points = region.points.map(toLocal);
    let best;
    for(let i=0;i<points.length;i++) {
      const edge = points[(i+1)%points.length].subtract(points[i]);
      if(edge.distanceTo([0,0])<8)continue;
      const frame = rectangle(points,-Math.atan2(edge.y,edge.x));
      if(!best||frame.width*frame.height<best.width*best.height)best=frame;
    }
    if(best.width>best.height)best=rectangle(points,best.angle+Math.PI/2);
    const center=best.min.add(best.max).divideBy(2);
    const width=Math.min(best.width,best.height*661/1218)*.94*1.12, height=width*1218/661;
    const min=center.subtract([width/2,height/2]),max=center.add([width/2,height/2]);
    const corners=[min,L.point(max.x,min.y),max,L.point(min.x,max.y)].map(p=>toLatLng(rotate(p,-best.angle)));
    return {...best,min,max,width,height,corners,id:region.id,name:region.name.replace('范围','')};
  }
  const fields=tasks.regions.filter(r=>r.name==='一操范围'||r.name==='二操范围').map(fieldRectangle);
  function contains(ll) {
    const p=rotate(toLocal(ll),bearing);
    return p.x>=crop.min.x-1e-5&&p.x<=crop.max.x+1e-5&&p.y>=crop.min.y-1e-5&&p.y<=crop.max.y+1e-5;
  }
  function clipProjected(line) {
    const result=[];let part=[];
    const bounds=L.bounds(crop.min,crop.max);
    for(let i=1;i<line.length;i++) {
      const pair=L.LineUtil.clipSegment(line[i-1],line[i],bounds,false,false);
      if(!pair){if(part.length>1)result.push(part);part=[];continue;}
      const segment=pair.map(p=>toLatLng(rotate(p,-bearing)));
      if(part.length&&!part.at(-1).equals(segment[0],1e-9)){result.push(part);part=[];}
      if(!part.length)part.push(segment[0]);
      part.push(segment[1]);
    }
    if(part.length>1)result.push(part);
    return result;
  }
  function clipLine(line){return clipProjected(line.map(ll=>rotate(toLocal(ll),bearing)));}
  function clipPixels(points,meta){
    const scale=2**(zoom-meta.zoom);
    const offset=L.point(meta.origin).add([.5,.5]).multiplyBy(scale).subtract(origin);
    return clipProjected(points.map(p=>rotate(L.point(p).multiplyBy(scale).add(offset),bearing)));
  }
  function tileIntersects(coords) {
    const step=256*2**(zoom-coords.z),x=coords.x*step,y=coords.y*step;
    const bounds=L.bounds([[x,y],[x+step,y],[x+step,y+step],[x,y+step]].map(p=>rotate(L.point(p).subtract(origin),bearing)));
    return bounds.intersects(L.bounds(crop.min,crop.max));
  }
  function fit(map) {
    const size=map.getSize().subtract([28,28]);
    if(size.x<=0||size.y<=0)return;
    const level=Math.max(map.getMinZoom(),Math.min(map.getMaxZoom(),Math.floor((zoom+Math.log2(Math.min(size.x/crop.width,size.y/crop.height)))*4)/4));
    if(level>map.getMinZoom())map.setMinZoom(level);
    map.setView(crop.center,level,{animate:false});
  }
  function attach(map) {
    const ns='http://www.w3.org/2000/svg';
    const svg=document.createElementNS(ns,'svg');svg.classList.add('campus-crop-mask');svg.setAttribute('aria-hidden','true');
    const path=document.createElementNS(ns,'path');path.setAttribute('fill','#fff');path.setAttribute('fill-rule','evenodd');svg.append(path);map.getContainer().append(svg);
    const compass=L.control({position:'topright'});
    compass.onAdd=()=>{const el=L.DomUtil.create('button','campus-compass');el.type='button';el.title='恢复默认方向';el.setAttribute('aria-label','恢复默认方向');const arrow=document.createElement('span');arrow.textContent='↑';const update=()=>arrow.style.transform='rotate('+map.getBearing()+'deg)';update();map.on('rotate',update);el.append(arrow,document.createTextNode('北'));L.DomEvent.disableClickPropagation(el);el.addEventListener('click',()=>{map.setBearing(bearing*180/Math.PI);map.fire('viewreset');});return el;};
    compass.addTo(map);
    // Do not use Leaflet's hard maxBounds here. It intercepts the same drag
    // gesture used by location picking and makes the map appear stuck near the
    // crop edge. The crop mask and tile validity still keep the useful map
    // area bounded, while the minimum zoom prevents exposing a large outside
    // region.
    function updateMask() {
      const size=map.getSize();svg.setAttribute('viewBox','0 0 '+size.x+' '+size.y);
      const points=crop.corners.map(ll=>map.layerPointToContainerPoint(map.project(ll,map.getZoom()).subtract(map.getPixelOrigin())));
      path.setAttribute('d',`M0 0H${size.x}V${size.y}H0Z M${points.map(p=>p.x+' '+p.y).join('L')}Z`);
    }
    let correcting=false;
    function constrain() {
      if(correcting||map._campusFreePan)return;
      const point=rotate(toLocal(map.getCenter()),bearing), size=map.getSize().divideBy(2*2**(map.getZoom()-zoom));
      const delta=map.getBearing()*Math.PI/180-bearing,c=Math.abs(Math.cos(delta)),s=Math.abs(Math.sin(delta));
      const half=L.point(c*size.x+s*size.y,s*size.x+c*size.y);
      const clamp=(v,min,max,h)=>max-min<=h*2?(min+max)/2:Math.max(min+h,Math.min(max-h,v));
      const next=L.point(clamp(point.x,crop.min.x,crop.max.x,half.x),clamp(point.y,crop.min.y,crop.max.y,half.y));
      if(next.distanceTo(point)*2**(map.getZoom()-zoom)>1){correcting=true;map.setView(toLatLng(rotate(next,-bearing)),map.getZoom(),{animate:false});correcting=false;}
    }
    // Batch mask work and constrain the center after the touch gesture ends.
    let maskFrame=null;
    const scheduleMask=()=>{
      if(maskFrame!==null)return;
      maskFrame=requestAnimationFrame(()=>{maskFrame=null;updateMask();});
    };
    map.on('move zoom resize rotate',scheduleMask);
    // Expose the optional correction for callers that deliberately want to
    // restore the crop center, but never run it on ordinary map gestures.
    map._campusConstrain=constrain;
    fit(map);updateMask();
  }
  const AthleticsLayer=L.Layer.extend({
    initialize(frame){this.frame=frame;},
    onAdd(map){
      this.image=L.DomUtil.create('img','athletics-svg');this.image.src='./assets/athletics.svg';this.image.alt=this.frame.name;this.image.draggable=false;
      map.getPane('surfaces').append(this.image);this.update();
    },
    onRemove(){this.image.remove();},
    getEvents(){return {zoom:this.update,viewreset:this.update,moveend:this.update};},
    update(){
      const map=this._map,origin=map.getPixelOrigin();
      const [tl,tr,,bl]=this.frame.corners.map(ll=>map.project(ll,map.getZoom()).subtract(origin));
      const u=tr.subtract(tl).divideBy(661),v=bl.subtract(tl).divideBy(1218);
      this.image.style.transform=`matrix(${u.x},${u.y},${v.x},${v.y},${tl.x},${tl.y})`;
    }
  });
  function displayRegion(region){return region.id===school.id?crop.corners:fields.find(f=>f.id===region.id)?.corners||region.points;}
  window.CampusLayout={bearing:bearing*180/Math.PI,crop,fields,contains,clipLine,clipPixels,tileIntersects,fit,attach,displayRegion,AthleticsLayer};
})();
