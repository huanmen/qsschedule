(function () {
  'use strict';
  function create(data) {
    const scale=256*2**data.meta.zoom;
    const mpp=156543.033928*Math.cos(24.869*Math.PI/180)/2**data.meta.zoom;
    const pixel=c=>[(c.lng+180)/360*scale-data.meta.origin[0]-.5,
      (1-Math.asinh(Math.tan(c.lat*Math.PI/180))/Math.PI)/2*scale-data.meta.origin[1]-.5];
    const latlng=p=>[180/Math.PI*Math.atan(Math.sinh(Math.PI*(1-2*(data.meta.origin[1]+p[1]+.5)/scale))),
      (data.meta.origin[0]+p[0]+.5)/scale*360-180];
    const edges=[],byKey=new Map(),splits=new Map();
    function addEdge(key,a,b,points,meters){
      const cumulative=[0];
      for(let i=1;i<points.length;i++)cumulative.push(cumulative.at(-1)+Math.hypot(points[i][0]-points[i-1][0],points[i][1]-points[i-1][1]));
      const edge={key,a,b,points,meters,cumulative,total:cumulative.at(-1),order:edges.length};
      edges.push(edge);byKey.set(key,edge);
    }
    const blockedJunction=(data.regions||[]).some(r=>r.id==='region-mu52hgpr-fu5y');
    const closedNodes=new Set([49389,49429,49430]);
    data.edges.forEach((e,id)=>{
      const node=n=>blockedJunction&&closedNodes.has(n)?'closed:'+n+':'+id:'n'+n;
      addEdge('base:'+id,node(e.a),node(e.b),e.points,e.meters);
    });
    // Existing white road below the building-label mask. Screenshot alignment
    // used six stored marker centers (<2px residual); no basemap is repainted.
    // Guard the endpoints so a future graph revision cannot apply this blindly.
    const north=byKey.get('base:2460'),south=byKey.get('base:2670');
    if(data.repairExistingCorridor!==false && north?.b==='n76993' && south?.a==='n78512' &&
        north.points.at(-1)[0]===1729 && north.points.at(-1)[1]===2548 &&
        south.points[0][0]===1701 && south.points[0][1]===2583){
      const points=[north.points.at(-1),south.points[0]];
      const length=points.slice(1).reduce((sum,p,i)=>sum+Math.hypot(p[0]-points[i][0],p[1]-points[i][1]),0);
      addEdge('repair:existing-building-corridor',north.b,south.a,points,length*mpp);
    }
    function at(edge,offset){
      if(offset<0||offset>edge.total+1e-4)throw Error('新增路线的接入点超出道路范围');
      let low=1,high=edge.cumulative.length-1;
      while(low<high){const mid=(low+high)>>1;if(edge.cumulative[mid]<offset-1e-7)low=mid+1;else high=mid;}
      const i=low;
      if(!edge.points[i])return edge.points[0];
      const t=Math.max(0,Math.min(1,(offset-edge.cumulative[i-1])/(edge.cumulative[i]-edge.cumulative[i-1])));
      return edge.points[i-1].map((v,j)=>v+(edge.points[i][j]-v)*t);
    }
    function junction(anchor){
      const edge=byKey.get(anchor.edge);
      if(!edge)throw Error('新增路线依赖的道路不存在');
      if(anchor.offset<1e-5)return {node:edge.a,point:edge.points[0]};
      if(edge.total-anchor.offset<1e-5)return {node:edge.b,point:edge.points.at(-1)};
      const point=at(edge,anchor.offset),node='j:'+edge.key+':'+anchor.offset.toFixed(4);
      const list=splits.get(edge.key)||[];
      if(!list.some(c=>c.node===node))list.push({node,point,offset:anchor.offset});
      splits.set(edge.key,list);return {node,point};
    }
    for(const road of data.roads||[]){
      const start=junction(road.start),end=road.end?junction(road.end):null;
      const points=road.points.map(p=>pixel({lat:p[0],lng:p[1]}));
      if(Math.hypot(points[0][0]-start.point[0],points[0][1]-start.point[1])>1||
          end&&Math.hypot(points.at(-1)[0]-end.point[0],points.at(-1)[1]-end.point[1])>1)throw Error('新增路线的端点与接入点不符');
      points[0]=start.point;if(end)points[points.length-1]=end.point;
      const length=points.slice(1).reduce((sum,p,i)=>sum+Math.hypot(p[0]-points[i][0],p[1]-points[i][1]),0);
      if(length<1)throw Error('新增路线长度不足');
      addEdge('user:'+road.id,start.node,end?.node||'u:'+road.id+':end',points,length*mpp);
    }

    const cellSize=64,grid=new Map(),segments=[];
    let minCellX=Infinity,minCellY=Infinity,maxCellX=-Infinity,maxCellY=-Infinity;
    for(const edge of edges)for(let i=1;i<edge.points.length;i++){
      const a=edge.points[i-1],b=edge.points[i],dx=b[0]-a[0],dy=b[1]-a[1],len2=dx*dx+dy*dy;
      if(!len2)continue;
      const segment={edge,i,a,dx,dy,len2,length:Math.sqrt(len2),id:segments.length};segments.push(segment);
      const x0=Math.floor(Math.min(a[0],b[0])/cellSize),x1=Math.floor(Math.max(a[0],b[0])/cellSize);
      const y0=Math.floor(Math.min(a[1],b[1])/cellSize),y1=Math.floor(Math.max(a[1],b[1])/cellSize);
      minCellX=Math.min(minCellX,x0);maxCellX=Math.max(maxCellX,x1);minCellY=Math.min(minCellY,y0);maxCellY=Math.max(maxCellY,y1);
      for(let x=x0;x<=x1;x++)for(let y=y0;y<=y1;y++){const key=x+','+y;if(!grid.has(key))grid.set(key,[]);grid.get(key).push(segment);}
    }
    function snap(c){
      const p=pixel(c);let best=null;
      function consider(s){
        const t=Math.max(0,Math.min(1,((p[0]-s.a[0])*s.dx+(p[1]-s.a[1])*s.dy)/s.len2));
        const point=[s.a[0]+t*s.dx,s.a[1]+t*s.dy],distance=Math.hypot(point[0]-p[0],point[1]-p[1]);
        if(!best||distance<best.distance||(distance===best.distance&&s.id<best.id))
          best={edge:s.edge.key,offset:s.edge.cumulative[s.i-1]+t*s.length,point,distance,id:s.id};
      }
      const cx=Math.floor(p[0]/cellSize),cy=Math.floor(p[1]/cellSize);
      if(cx<minCellX||cx>maxCellX||cy<minCellY||cy>maxCellY){for(const s of segments)consider(s);}
      else{
        const visited=new Set(),limit=Math.max(cx-minCellX,maxCellX-cx,cy-minCellY,maxCellY-cy);
        function visit(x,y){for(const s of grid.get(x+','+y)||[])if(!visited.has(s.id)){visited.add(s.id);consider(s);}}
        for(let r=0;r<=limit;r++){
          for(let x=cx-r;x<=cx+r;x++){visit(x,cy-r);if(r)visit(x,cy+r);}
          for(let y=cy-r+1;y<cy+r;y++){visit(cx-r,y);if(r)visit(cx+r,y);}
          // No unvisited cell can contain a closer point once the nearest point is inside this radius.
          const boundary=Math.min(p[0]-(cx-r)*cellSize,(cx+r+1)*cellSize-p[0],p[1]-(cy-r)*cellSize,(cy+r+1)*cellSize-p[1]);
          if(best&&best.distance<boundary)break;
        }
      }
      return best&&{edge:best.edge,offset:+best.offset.toFixed(4),coordinate:latlng(best.point),distanceMeters:best.distance*mpp};
    }

    function pieces(edge,extra=[]){
      if(edge.total<=0)return [];
      const cuts=[{node:edge.a,point:edge.points[0],offset:0},
        ...[...(splits.get(edge.key)||[]),...extra].sort((a,b)=>a.offset-b.offset),
        {node:edge.b,point:edge.points.at(-1),offset:edge.total}];
      const result=[];
      for(let i=1;i<cuts.length;i++){
        const left=cuts[i-1],right=cuts[i],meters=(right.offset-left.offset)/edge.total*edge.meters;
        if(left.node!==right.node&&meters>0)result.push({edge,left,right,u:left.node,v:right.node,meters,forward:true});
      }
      return result;
    }
    const baseGraph=Object.create(null),baseGeometry=new Map(),alternatives=new Map(),edgePairs=new Map();
    const preferred=(a,b)=>!b||a.meters<b.meters||(a.meters===b.meters&&a.edge.order<b.edge.order);
    function cache(piece){
      const key=piece.u+'|'+piece.v,list=alternatives.get(key)||[];list.push(piece);alternatives.set(key,list);
      if(preferred(piece,baseGeometry.get(key))){baseGraph[piece.u] ||= {};baseGraph[piece.u][piece.v]=piece.meters;baseGeometry.set(key,piece);}
      const pairs=edgePairs.get(piece.edge.key)||new Set();pairs.add(key);edgePairs.set(piece.edge.key,pairs);
    }
    for(const edge of edges)for(const piece of pieces(edge)){
      cache(piece);cache({...piece,u:piece.v,v:piece.u,forward:false});
    }
    function pointsFor(piece){
      const {edge,left,right}=piece,points=[left.point];
      for(let i=1;i<edge.points.length-1;i++)if(edge.cumulative[i]>left.offset&&edge.cumulative[i]<right.offset)points.push(edge.points[i]);
      points.push(right.point);return piece.forward?points:points.reverse();
    }
    function snapCandidates(c){
      const nearest=snap(c);
      if(!nearest)return [];
      const p=pixel(c),limit=Math.min(80,nearest.distanceMeters+3)/mpp,found=new Map();
      for(const segment of segments){
        const t=Math.max(0,Math.min(1,((p[0]-segment.a[0])*segment.dx+(p[1]-segment.a[1])*segment.dy)/segment.len2));
        const point=[segment.a[0]+t*segment.dx,segment.a[1]+t*segment.dy];
        const distance=Math.hypot(point[0]-p[0],point[1]-p[1]);
        if(distance>limit)continue;
        const candidate={edge:segment.edge.key,offset:Math.min(segment.edge.total,segment.edge.cumulative[segment.i-1]+t*segment.length),coordinate:latlng(point),distanceMeters:distance*mpp};
        const previous=found.get(candidate.edge);
        if(!previous||candidate.distanceMeters<previous.distanceMeters)found.set(candidate.edge,candidate);
      }
      return [...found.values()].sort((a,b)=>a.distanceMeters-b.distanceMeters||a.edge.localeCompare(b.edge)).slice(0,4);
    }
    function plan(from,to){
      const pair=coordinate=>[coordinate.lat,coordinate.lng];
      const samePlace=from?.id&&to?.id&&from.id===to.id;
      const sameCoordinate=from?.coordinate&&to?.coordinate&&
        Math.abs(from.coordinate.lat-to.coordinate.lat)<1e-9&&
        Math.abs(from.coordinate.lng-to.coordinate.lng)<1e-9;
      if(samePlace||sameCoordinate){
        const point=pair(from.coordinate);
        return {meters:0,points:[point],displayPoints:[point],roadPoints:[],edgeKeys:[],method:'edited-road-network',startDistance:0,endDistance:0};
      }
      const starts=snapCandidates(from.coordinate),ends=snapCandidates(to.coordinate);
      if(!starts.length||!ends.length)return {error:'地点距离现有道路超过 80 米，暂不能规划路线'};
      const extraCuts=new Map();
      function anchor(s,label){
        const edge=byKey.get(s.edge);
        if(s.offset<1e-5)return edge.a;
        if(edge.total-s.offset<1e-5)return edge.b;
        const list=extraCuts.get(s.edge)||[];
        const existing=[...(splits.get(s.edge)||[]),...list].find(x=>Math.abs(x.offset-s.offset)<1e-4);
        if(existing)return existing.node;
        list.push({node:label,point:at(edge,s.offset),offset:s.offset});extraCuts.set(s.edge,list);return label;
      }
      const origins=starts.map((snap,i)=>({snap,node:anchor(snap,'origin-'+i)}));
      const destinations=ends.map((snap,i)=>({snap,node:anchor(snap,'destination-'+i)}));
      const source='virtual-origin',destination='virtual-destination';
      // Share the immutable road graph; only the two endpoint edges need temporary splits.
      const graph=Object.create(baseGraph),geometry=new Map(),affected=new Set(extraCuts.keys());
      function neighbors(node){if(!Object.hasOwn(graph,node))graph[node]={...baseGraph[node]};return graph[node];}
      const changedPairs=new Set();
      for(const edge of affected)for(const key of edgePairs.get(edge)||[])changedPairs.add(key);
      for(const key of changedPairs){
        const [u,v]=key.split('|');let replacement=null;
        for(const piece of alternatives.get(key)||[])if(!affected.has(piece.edge.key)&&preferred(piece,replacement))replacement=piece;
        if(replacement)neighbors(u)[v]=replacement.meters;else delete neighbors(u)[v];
        geometry.set(key,replacement);
      }
      function connect(piece){
        const key=piece.u+'|'+piece.v,old=geometry.has(key)?geometry.get(key):baseGeometry.get(key);
        if(preferred(piece,old)){neighbors(piece.u)[piece.v]=piece.meters;geometry.set(key,piece);}
      }
      for(const [key,cuts] of extraCuts)for(const piece of pieces(byKey.get(key),cuts)){
        connect(piece);connect({...piece,u:piece.v,v:piece.u,forward:false});
      }
      // One search with endpoint access costs, not a search for every pair.
      graph[source]={};graph[destination]={};
      for(const entry of origins)graph[source][entry.node]=entry.snap.distanceMeters;
      for(const entry of destinations)neighbors(entry.node)[destination]=entry.snap.distanceMeters;
      let path;
      try{path=window.dijkstra.find_path(graph,source,destination);}catch{return {error:'这两个地点的道路尚未连通'};}
      const a=origins.find(e=>e.node===path[1]).snap,b=destinations.find(e=>e.node===path.at(-2)).snap;
      path=path.slice(1,-1);
      // Keep the walking access from each named place to the nearest road in
      // the rendered geometry. Previously the line started/ended at the
      // snapped road points, so it could visibly miss the two endpoint pins.
      if(path.length===1){
        const roadPoints=[a.coordinate,b.coordinate];
        const routePoints=[pair(from.coordinate),...roadPoints,pair(to.coordinate)];
        return {meters:Math.round(a.distanceMeters+b.distanceMeters),points:routePoints,displayPoints:roadPoints,roadPoints,method:'edited-road-network',startDistance:a.distanceMeters,endDistance:b.distanceMeters};
      }
      const pixels=[],displayPixels=[],edgeKeys=[];let meters=0;
      for(let i=1;i<path.length;i++){
        const key=path[i-1]+'|'+path[i],piece=geometry.has(key)?geometry.get(key):baseGeometry.get(key);
        edgeKeys.push(piece.edge.key);
        const points=pointsFor(piece);pixels.push(...(pixels.length?points.slice(1):points));meters+=piece.meters;
        // Keep every graph junction; remove sub-metre raster jitter only within a road piece.
        const simplified=window.L?L.LineUtil.simplify(points.map(p=>L.point(p)),.6/mpp).map(p=>[p.x,p.y]):points;
        displayPixels.push(...(displayPixels.length?simplified.slice(1):simplified));
      }
      const roadPoints=displayPixels.map(latlng),routePoints=[pair(from.coordinate),...roadPoints,pair(to.coordinate)];
      return {meters:Math.round(meters),points:routePoints,displayPoints:roadPoints,roadPoints,pixels,edgeKeys,method:'edited-road-network',
        startDistance:+a.distanceMeters.toFixed(1),endDistance:+b.distanceMeters.toFixed(1)};
    }
    return {plan,snap,metersPerPixel:mpp};
  }
  window.CampusEditedRouter={create};
})();
