const fs = require('fs'); const path = require('path'); const crypto = require('crypto');
const {JSDOM}=require('jsdom'); const {Readability}=require('@mozilla/readability');
const root=path.resolve(__dirname,'../..'); const output=path.join(root,'results');
const fixtures=[['twz','https://www.twz.com/air/full-f-47-production-line-not-too-far-away-usaf-general'],['9to5google','https://9to5google.com/2026/09/30/facer-wear-os-library-update-timex/']];
const rows=[];
for(const [name,url] of fixtures){
 const html=fs.readFileSync(path.join(root,'android/app/src/main/assets',name+'.html'),'utf8');
 for(let run=0;run<11;run++){
  const dom=new JSDOM(html,{url}); const t=performance.now(); const article=new Readability(dom.window.document).parse();
  const ms=performance.now()-t; if(!article || article.textContent.trim().length<100) throw Error('Empty fixture extraction');
  const text=article.textContent; fs.writeFileSync(path.join(output,name+'-mozilla-host.txt'),text);
  rows.push({engine:'Mozilla-0.6.0-JSDOM',environment:'Windows-host-not-Android',fixture:name,run,warmup:run===0,parse_ms:ms,characters:text.length,sha256_html:crypto.createHash('sha256').update(html).digest('hex')});
  dom.window.close();
 }
}
fs.writeFileSync(path.join(output,'mozilla-host.json'),JSON.stringify(rows,null,2));
console.log('Mozilla host baseline: '+rows.length+' runs');
