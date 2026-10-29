const fs=require('fs'),vm=require('vm'),assert=require('assert');
const script=fs.readFileSync(require('path').join(__dirname,'../app/src/main/assets/accessibility.js'),'utf8');
let tests=0;function check(v){assert(v);tests++;}
function fixture(tv=true){
 const listeners={},events=[],head=[],items=[];
 const doc={activeElement:null,head:{appendChild:x=>head.push(x)},documentElement:{classList:{add:()=>{}}},images:[],createElement:()=>({}),addEventListener:(n,f)=>listeners[n]=f,querySelector:()=>null,querySelectorAll:s=>s==='video'?items.filter(x=>x.tagName==='VIDEO'):items};
 function el(id,x,y,tag='BUTTON'){
  const attrs={},a={id,tagName:tag,disabled:false,isContentEditable:false,style:{visibility:'visible',display:'block'},getBoundingClientRect:()=>({left:x,top:y,width:100,height:50}),getAttribute:n=>attrs[n]??null,hasAttribute:n=>n in attrs,closest:()=>null,contains:b=>a===b,focus:()=>doc.activeElement=a,scrollIntoView:()=>a.scrolled=true,matches:s=>s.includes(tag.toLowerCase()),click:()=>a.clicked=true};items.push(a);return a;
 }
 const win={__swTV:tv,addEventListener:(n,f)=>listeners[n]=f,dispatchEvent:e=>events.push(e.type)};
 const ctx={window:win,document:doc,getComputedStyle:e=>e.style,HTMLImageElement:function(){},innerHeight:720,Event:class{constructor(t){this.type=t;}},requestAnimationFrame:f=>f(),console};
 vm.runInNewContext(script,ctx);
 return {doc,win,items,listeners,head,el,ctx,events};
}
let f=fixture(),a=f.el('a',0,0),b=f.el('b',120,0),c=f.el('c',0,80),d=f.el('d',120,80);f.doc.activeElement=a;
check(f.win.__swAccess.move('ArrowRight')&&f.doc.activeElement===b);
check(f.win.__swAccess.move('ArrowDown')&&f.doc.activeElement===d);
check(f.win.__swAccess.move('ArrowLeft')&&f.doc.activeElement===c);
check(f.win.__swAccess.move('ArrowUp')&&f.doc.activeElement===a);
check(!f.win.__swAccess.move('ArrowLeft'));
b.disabled=true;f.win.__swAccess.move('ArrowRight');check(f.doc.activeElement===d);
f.doc.activeElement=f.el('search',0,160,'INPUT');check(!f.win.__swAccess.move('ArrowRight'));
f.doc.activeElement=null;check(f.win.__swAccess.move('ArrowDown')&&f.doc.activeElement===a);
let e={key:'ArrowRight',target:a,defaultPrevented:true};f.listeners.keydown(e);check(f.doc.activeElement===a);
vm.runInNewContext(script,f.ctx);check(f.head.length===1);check(f.events.includes('resize'));
f=fixture(false);a=f.el('a',0,0);b=f.el('b',120,0);f.doc.activeElement=a;f.listeners.keydown({key:'ArrowRight',target:a});check(f.doc.activeElement===a);
f=fixture();a=f.el('v',0,0,'VIDEO');a.paused=true;a.play=()=>{a.paused=false;return Promise.resolve();};a.pause=()=>a.paused=true;a.currentTime=20;a.seekable={length:1,start:()=>0,end:()=>25};
check(f.win.__swAccess.media('play')&&!a.paused);check(f.win.__swAccess.media('toggle')&&a.paused);
check(f.win.__swAccess.media('forward')&&a.currentTime===25);check(f.win.__swAccess.media('rewind')&&a.currentTime===15);
a.seekable.length=0;check(!f.win.__swAccess.media('forward'));
f.items.length=0;check(!f.win.__swAccess.media('play'));
f.listeners.fullscreenchange();check(f.events.includes('resize'));
console.log(tests+' accessibility logic checks passed (mock DOM; not a browser/device test).');
