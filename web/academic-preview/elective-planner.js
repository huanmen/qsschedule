'use strict';
// Credits are already earned/recognized. Art credits are a subset of the total.
window.ElectivePlanner = (() => {
  function pack(courses, firstCredits, maxCourses) {
    const ordered = [...courses].sort((a,b)=>b.credits-a.credits || Number(b.art)-Number(a.art));
    for(let future=0;future<=courses.length;future++) {
      const bins=Array.from({length:future+1},(_,index)=>({capacity:index?4:firstCredits,items:[],credits:0}));
      function assign(index) {
        if(index===ordered.length)return true;
        const course=ordered[index], tried=new Set();
        for(const bin of bins) {
          const state=bin.capacity+':'+bin.credits+':'+bin.items.length;
          if(tried.has(state)||bin.credits+course.credits>bin.capacity||bin.items.length>=maxCourses)continue;
          tried.add(state);bin.items.push(course);bin.credits+=course.credits;
          if(assign(index+1))return true;
          bin.items.pop();bin.credits-=course.credits;
        }
        return false;
      }
      if(assign(0))return bins.map((bin,index)=>({...bin,offset:index})).filter(bin=>bin.items.length);
    }
    return [];
  }
  function plan(input) {
    const {total,art,online=0,firstCredits=4,maxCourses=3,artTwo=true,normalTwo=true,allowOnline=false,normalOffline=true,artOffline=true}=input;
    if(![total,art,online,firstCredits,maxCourses].every(Number.isInteger)||total<0||art<0||online<0||art>total||Math.min(online,4)>total||firstCredits<0||firstCredits>4||maxCourses<1||maxCourses>4)
      return {state:'invalid',message:'已获学分与分类不一致，请核对记录。'};
    const artGap=Math.max(0,2-art),gap=Math.max(0,7-total),onlineRoom=Math.max(0,4-online);
    if(total>8)return {state:'review',message:'已计入学分超过 8 分，请先核对学校认定结果。',gap,artGap};
    if(!gap&&!artGap)return {state:'done',message:'选修课要求已满足，无需再补选。',gap,artGap};
    if(total+artGap>8)return {state:'review',message:`艺术类还差 ${artGap} 分，直接补选会超过 8 分。请先向教务确认课程归类或替换认定，不能仅靠继续加课。`,gap,artGap};
    const types=[...(artOffline?[...(artTwo?[{credits:2,art:true,online:false}]:[]),{credits:1,art:true,online:false}]:[]),...(normalOffline?[...(normalTwo?[{credits:2,art:false,online:false}]:[]),{credits:1,art:false,online:false}]:[]),...(allowOnline?[...(normalTwo?[{credits:2,art:false,online:true}]:[]),{credits:1,art:false,online:true}]:[])];
    let best=null,bestRank=null,alternatives=[];
    const rankLess=(a,b)=>a.some((value,index)=>value<b[index]&&a.slice(0,index).every((v,i)=>v===b[i]));
    for(let count=1;count<=8;count++) {
      function enumerate(start,chosen,credits,arts,onlines) {
        if(total+credits>8||onlines>onlineRoom)return;
        if(chosen.length===count) {
          if(credits<gap||arts<artGap)return;
          const semesters=pack(chosen,firstCredits,maxCourses);
          const lastOffset=semesters.at(-1)?.offset||0;
          const rank=[lastOffset,credits,Math.max(0,arts-artGap),onlines];
          const candidate={state:'plan',gap,artGap,onlineRoom,courses:[...chosen],added:credits,finalTotal:total+credits,finalArt:art+arts,semesters,count};
          alternatives.push({rank,candidate});
          if(!bestRank||rankLess(rank,bestRank)) {bestRank=rank;best={state:'plan',gap,artGap,onlineRoom,courses:[...chosen],added:credits,finalTotal:total+credits,finalArt:art+arts,semesters,count};}
          return;
        }
        for(let index=start;index<types.length;index++) {
          const course=types[index];
          enumerate(index,[...chosen,course],credits+course.credits,arts+(course.art?course.credits:0),onlines+(course.online?course.credits:0));
        }
      }
      enumerate(0,[],0,0,0);
      if(best){alternatives.sort((a,b)=>{for(let i=0;i<a.rank.length;i++){if(a.rank[i]!==b.rank[i])return a.rank[i]-b.rank[i];}return 0;});return {...best,alternatives:alternatives.slice(0,3).map(x=>x.candidate)};}
    }
    return {state:'blocked',message:'当前可选课程不足以补齐缺口。等待下一学期合适的线下课程或艺术课，不要用超额线上学分补缺口。',gap,artGap};
  }
  const label=course=>`${course.art?'艺术类':course.online?'线上':'其他线下'} ${course.credits} 分`;
  function combination(courses) {
    const counts=new Map();
    for(const course of courses){const text=label(course);counts.set(text,(counts.get(text)||0)+1);}
    return [...counts].map(([text,count])=>text+(count>1?` × ${count} 门`:'')).join(' + ');
  }
  return {plan,combination};
})();
