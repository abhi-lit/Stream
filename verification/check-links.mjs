import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const scripts=JSON.parse(fs.readFileSync(new URL('./web-scripts.json', import.meta.url),'utf8'));
scripts.forEach(s=>new Function(s));
function click(href){
 let listener;
 const context={URL,window:{},location:{href:'https://multimovies.wtf/'},document:{addEventListener:(name,fn)=>{listener=fn;}}};
 vm.runInNewContext(scripts[1],context);
 const anchor={href,target:'_blank'};
 listener({target:{closest:()=>anchor}});
 return anchor;
}
assert.equal(click('https://multinime.site/').target,'_self');
assert.equal(click('https://multinime.site/watch/example').href,'https://multinime.site/watch/example');
assert.equal(click('https://m1.multimovies.courses/').target,'_self');
assert.equal(click('https://multimovies.wtf/multinime-logo.svg').href,'https://multinime.site/');
assert.equal(click('https://multinime.site.evil.example/').target,'_blank');
assert.equal(click('https://notmultinime.site/').target,'_blank');
assert.equal(click('http://multinime.site/').target,'_blank');
console.log('7 site routing checks and both injected script syntax checks passed.');
