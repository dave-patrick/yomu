const test=require('node:test'), assert=require('node:assert/strict'), s=require('../static/sync.js');

function snapshot(records){return {instance:'inst-1', records};}
const item={id:'a', title:'First', inToRead:true, archived:false, updatedAt:1};
function base(extra=[]){
  return snapshot([
    {kind:'reading_items', id:'a', data:item},
    {kind:'playlists', id:'p1', data:{id:'p1', name:'Reads', createdAt:1, sortOrder:0}},
    {kind:'up_next', id:'a', data:{itemId:'a', position:0, addedAt:1}},
    {kind:'reading_progress', id:'a', data:{itemId:'a', completed:false, sentenceIndex:3}},
    {kind:'read_history', id:'h1', data:{id:'h1', itemId:'a', completedAt:1}},
    {kind:'playlist_memberships', id:'p1:a', data:{playlistId:'p1', itemId:'a', sortOrder:0, addedAt:1}},
    ...extra
  ]);
}

test('every change carries the current server value for conflict detection',()=>{
  const snap=base();
  const read=s.change(snap,'reading_items','a',{...item,title:'Second'});
  assert.equal(read.kind,'reading_items');
  assert.equal(read.id,'a');
  assert.deepEqual(read.base,item);
  assert.equal(read.data.title,'Second');
});

test('marking read completes progress, records history, and leaves the queue',()=>{
  const action=s.toggleRead(base(),'a');
  const kinds=action.changes.map(c=>c.kind);
  assert.ok(kinds.includes('reading_items'));
  assert.ok(kinds.includes('reading_progress'));
  assert.ok(kinds.includes('read_history'));
  assert.ok(kinds.includes('up_next'));
  const progress=action.changes.find(c=>c.kind==='reading_progress');
  assert.equal(progress.data.completed,true);
  assert.equal(progress.data.sentenceIndex,3,'Reading position is kept.');
  assert.match(action.message,/Marked as read/);
});

test('marking unread clears history and returns the item to To read',()=>{
  const snap=base();
  snap.records.find(r=>r.kind==='reading_progress').data.completed=true;
  const action=s.toggleRead(snap,'a');
  const progress=action.changes.find(c=>c.kind==='reading_progress');
  assert.equal(progress.data.completed,false);
  assert.ok(action.changes.some(c=>c.kind==='read_history'&&c.data===null),'History entry is removed.');
  const itemChange=action.changes.find(c=>c.kind==='reading_items');
  assert.equal(itemChange.data.inToRead,true);
  assert.equal(itemChange.data.archived,false);
  assert.match(action.message,/Returned to To read/);
});

test('queue toggle adds the item at the end or removes it',()=>{
  const snap=base();
  const removed=s.toggleQueue(snap,'a');
  assert.equal(removed.changes[0].data,null);
  assert.match(removed.message,/Removed from Up next/);
  // The item is not queued here, so it is appended after the existing entry.
  const snap2=snapshot([
    {kind:'reading_items',id:'a',data:item},
    {kind:'up_next',id:'b',data:{itemId:'b',position:4,addedAt:1}}
  ]);
  const added=s.toggleQueue(snap2,'a');
  assert.equal(added.changes[0].data.itemId,'a');
  assert.equal(added.changes[0].data.position,5);
  assert.match(added.message,/Added to Up next/);
});

test('moving in the queue swaps positions with the neighbour',()=>{
  const snap=snapshot([
    {kind:'reading_items',id:'a',data:item},
    {kind:'up_next',id:'a',data:{itemId:'a',position:0,addedAt:1}},
    {kind:'up_next',id:'b',data:{itemId:'b',position:1,addedAt:1}}
  ]);
  const moved=s.moveInQueue(snap,'a',1);
  assert.ok(moved);
  const positions=Object.fromEntries(moved.changes.map(c=>[c.id,c.data.position]));
  assert.equal(positions.a,1);
  assert.equal(positions.b,0);
  const outOfRange=s.moveInQueue(snap,'a',-1);
  assert.equal(outOfRange,null,'Moving past the start is not offered.');
});

test('archiving hides the item from To read and drops it from the queue',()=>{
  const action=s.toggleArchive(base(),'a');
  const itemChange=action.changes.find(c=>c.kind==='reading_items');
  assert.equal(itemChange.data.archived,true);
  assert.equal(itemChange.data.inToRead,false);
  assert.ok(action.changes.some(c=>c.kind==='up_next'&&c.data===null));
  assert.match(action.message,/Moved to Archive/);
});

test('restoring returns an archived item to To read',()=>{
  const snap=base();
  snap.records.find(r=>r.kind==='reading_items').data={...item,archived:true,inToRead:false};
  const action=s.toggleArchive(snap,'a');
  const itemChange=action.changes.find(c=>c.kind==='reading_items');
  assert.equal(itemChange.data.archived,false);
  assert.equal(itemChange.data.inToRead,true);
  assert.match(action.message,/Restored to To read/);
});

test('playlists reject unknown, missing, and duplicate destinations',()=>{
  assert.throws(()=>s.addToPlaylist(base(),'a',''),/Choose a playlist/);
  assert.throws(()=>s.addToPlaylist(base(),'a','nope'),/was removed/);
  assert.throws(()=>s.addToPlaylist(base(),'a','p1'),/Already in that playlist/);
});

test('adding to a playlist appends after the existing members',()=>{
  const snap=base([{kind:'playlist_memberships',id:'p1:b',data:{playlistId:'p1',itemId:'b',sortOrder:7,addedAt:1}}]);
  snap.records=snap.records.filter(r=>!(r.kind==='playlist_memberships'&&r.id==='p1:a'));
  const action=s.addToPlaylist(snap,'a','p1');
  const membership=action.changes[0].data;
  assert.equal(membership.playlistId,'p1');
  assert.equal(membership.itemId,'a');
  assert.equal(membership.sortOrder,8);
});

test('removing from a playlist leaves the saved item alone',()=>{
  const action=s.removeFromPlaylist(base(),'a','p1');
  assert.equal(action.changes[0].kind,'playlist_memberships');
  assert.equal(action.changes[0].id,'p1:a');
  assert.equal(action.changes[0].data,null);
  assert.ok(!action.changes.some(c=>c.kind==='reading_items'),'The item itself is untouched.');
});

test('renaming requires a title and updates the item',()=>{
  assert.throws(()=>s.renameItem(base(),'a','   '),/Enter a title/);
  const action=s.renameItem(base(),'a','  New title  ');
  assert.equal(action.changes[0].data.title,'New title');
  assert.match(action.message,/Title updated/);
});

test('deleting removes the item and everything that points at it',()=>{
  const action=s.deleteItem(base(),'a');
  assert.equal(action.changes[0].kind,'reading_items');
  assert.equal(action.changes[0].data,null);
  const removed=action.changes.filter(c=>c.data===null).map(c=>c.kind);
  for (const kind of ['up_next','reading_progress','playlist_memberships','read_history']) {
    assert.ok(removed.includes(kind),kind + ' is removed with the item.');
  }
  assert.ok(!action.changes.some(c=>c.kind==='playlists'),'Playlists survive a deleted item.');
});

test('acting on a removed item is refused instead of resurrecting it',()=>{
  const snap=snapshot([{kind:'reading_items',id:'b',data:{id:'b',title:'Other'}}]);
  assert.throws(()=>s.toggleArchive(snap,'a'),/removed from your library/);
  assert.throws(()=>s.deleteItem(snap,'a'),/removed from your library/);
});

test('finishing an article can archive it and then still delete it',()=>{
  const snap=base();
  const archived=s.toggleArchive(snap,'a');
  assert.equal(archived.changes.find(c=>c.kind==='reading_items').data.archived,true);
  // An archived article is still deletable, and deleting clears both states.
  const deleted=s.deleteItem(snap,'a');
  assert.equal(deleted.changes[0].data,null);
  assert.ok(!deleted.changes.some(c=>c.kind==='playlists'),'Playlists are never removed by a delete.');
});
