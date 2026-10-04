from pathlib import Path
import json,csv,statistics,struct,zipfile,hashlib
ROOT=Path(__file__).resolve().parents[1]; out=ROOT/'results'
mozilla=json.loads((out/'mozilla-host.json').read_text())
java=list(csv.DictReader((out/'readability4j-host.tsv').open(),delimiter='\t'))
result={'environment':'Windows host; JSDOM vs JVM, NOT target WebView benchmark','fixtures':{}}
for name in ('twz','9to5google'):
    engines={}
    for label,rows in [('Mozilla-0.6.0-JSDOM',mozilla),('Readability4J-1.0.8',java)]:
        runs=[r for r in rows if r['fixture']==name and int(r['run'])>0]
        engines[label]={'median_parse_ms':statistics.median(float(r['parse_ms']) for r in runs),'runs':len(runs),'characters':int(runs[-1]['characters'])}
    result['fixtures'][name]=engines
(out/'extraction-host-summary.json').write_text(json.dumps(result,indent=2))
apk=ROOT/'android/app/build/outputs/apk/debug/app-debug.apk'
elf=[]
with zipfile.ZipFile(apk) as z:
    for name in z.namelist():
        if not name.endswith('.so'): continue
        data=z.read(name)
        if data[:4]!=b'\x7fELF' or data[4]!=2 or data[5]!=1: raise ValueError('Expected little-endian ELF64')
        phoff=struct.unpack_from('<Q',data,32)[0]; size,count=struct.unpack_from('<HH',data,54)
        aligns=[]
        for i in range(count):
            entry=phoff+i*size
            if struct.unpack_from('<I',data,entry)[0]==1: aligns.append(struct.unpack_from('<Q',data,entry+48)[0])
        elf.append({'file':name,'load_segment_alignment_bytes':aligns,'all_load_segments_16k_or_higher':all(a>=16384 for a in aligns),'sha256':hashlib.sha256(data).hexdigest()})
(out/'apk-native-alignment.json').write_text(json.dumps(elf,indent=2))
manifest=[]
for path in sorted((ROOT/'android/app/src/main/assets').glob('*')):
    if path.is_file(): manifest.append({'file':path.name,'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
(out/'fixture-manifest.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps(result,indent=2)); print('Native ELF alignment failures:',[r['file'] for r in elf if not r['all_load_segments_16k_or_higher']])
