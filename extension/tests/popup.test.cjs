const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
async function scenario(failPlaylist=false,already=false){
 const elements=new Map();const el=id=>elements.get(id)||elements.set(id,{value:'',textContent:'',disabled:false,open:false,options:[],selectedOptions:[{textContent:'Weekend'}],classList:{toggle(){}},replaceChildren(...o){this.options=o},add(o){this.options.push(o)}}).get(id);
 const state={instance:'test-library',records:[{kind:'playlists',id:'p1',data:{name:'Weekend',sortOrder:0}}]};let calls=[];
 const context={document:{getElementById:el,documentElement:{dataset:{}}},URL,TypeError,Error,Date,Math,JSON,AbortSignal,Option:function(text,value){this.textContent=text;this.value=value},chrome:{storage:{local:{async setAccessLevel(){},async get(){return{server:'https://example.org',token:'x'.repeat(32)}},async set(){},async remove(){}}},tabs:{async query(){return[{url:'https://article.example/story#paragraph',title:'Story'}]},create(){}},permissions:{async request(){return true},async remove(){}}},fetch:async(url,opts)=>{calls.push({url,opts});let data=state;if(url.endsWith('/articles'))data={status:already?'already_saved':'saved',id:'item1'};if(url.endsWith('/sync')){if(failPlaylist)return{ok:false,status:500};const c=JSON.parse(opts.body).changes[0];data={conflicts:[],records:[{kind:c.kind,id:c.id,data:c.data}]};}return{ok:true,status:200,json:async()=>data}}};
 vm.createContext(context);vm.runInContext(fs.readFileSync('extension/popup.js','utf8'),context);await vm.runInContext('start()',context);
 el('playlist').value='p1';await el('save').onclick();
 const article=calls.find(c=>c.url.endsWith('/articles'));assert.equal(JSON.parse(article.opts.body).url,'https://article.example/story');
 const membership=JSON.parse(calls.find(c=>c.url.endsWith('/sync')).opts.body).changes[0];assert.equal(membership.data.itemId,'item1');assert.equal(membership.data.playlistId,'p1');assert.equal(membership.data.sortOrder,0);
 assert.equal(el('save').disabled,false);assert.match(el('status').textContent,failPlaylist?/Article saved, but playlist addition failed/:/Saved to Weekend/);
 assert.throws(()=>vm.runInContext("serverOrigin('https://example.org/private')",context));assert.throws(()=>vm.runInContext("serverOrigin('http://example.org')",context));
}
(async()=>{await scenario();await scenario(false,true);await scenario(true);console.log('PASS: new save, duplicate save, partial playlist failure, URL normalization, invalid server addresses');})().catch(e=>{console.error(e);process.exitCode=1});
