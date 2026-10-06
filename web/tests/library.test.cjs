const test=require('node:test'), assert=require('node:assert/strict'), m=require('../static/library.js');
test('history deduplicates completions and sorts newest first',()=>{
 const s={records:[{kind:'reading_items',data:{id:'a'}},{kind:'reading_items',data:{id:'b'}},{kind:'read_history',data:{itemId:'a',completedAt:3}},{kind:'read_history',data:{itemId:'a',completedAt:1}},{kind:'read_history',data:{itemId:'b',completedAt:2}}]};
 assert.deepEqual(m.selectItems(s,'history').map(i=>i.id),['a','b']);
});
test('unfinished imports and progress are represented accurately',()=>{
 assert.equal(m.canRead({normalizedText:'  '}),false);
 assert.equal(m.importStatus({importState:'failed',warning:'Blocked'}).detail,'Blocked');
 assert.equal(m.importStatus({normalizedText:''}).label,'Waiting for phone');
 assert.equal(m.progressPercent({normalizedText:'hello'},{characterOffset:999}),100);
 assert.equal(m.progressPercent({normalizedText:'hello'},{characterOffset:-1}),0);
 assert.equal(m.emptyState('queue','',false).title,'Up next is clear');
});
test('serialized actions recover after failure',async()=>{
 const q=m.createSerialQueue(), events=[];let release;
 const first=q.run(()=>new Promise(resolve=>{release=()=>{events.push('read');resolve()}}));
 const failure=q.run(()=>{events.push('write');throw Error('offline')});
 const caught=assert.rejects(failure,/offline/), next=q.run(()=>events.push('retry'));
 await Promise.resolve();assert.equal(q.pending,3);assert.deepEqual(events,[]);
 release();await Promise.all([first,caught,next]);assert.deepEqual(events,['read','write','retry']);assert.equal(q.pending,0);
});
