from pathlib import Path
import json,statistics,re
root=Path(__file__).resolve().parents[1]; out=root/'results'
lines=(out/'device-evidence.jsonl').read_text(encoding='utf-8-sig').splitlines()
rows=[json.loads(s) for s in lines if s.strip()]
result={'device':next((r.get('model') for r in rows if r.get('model')),'unknown'),
 'android_api':next((r.get('sdk') for r in rows if r.get('sdk')),'unknown'),
 'page_size_bytes':4096,'environment':'Galaxy S26 Ultra, USB/Wi-Fi ADB; actual model SM-S948U; API 37; 4 KiB page size',
 'extraction':{},'tts':{}}
for fixture in ('twz','9to5google'):
 result['extraction'][fixture]={}
 for engine in ('Readability4J-1.0.8','Mozilla-WebView'):
  runs=[r for r in rows if r.get('test')=='extraction' and r.get('fixture')==fixture and r.get('engine')==engine and not r.get('warmup')]
  result['extraction'][fixture][engine]={'n':len(runs),'median_parse_ms':statistics.median([r['parse_ms'] for r in runs]) if runs else None,
    'measured_parse_ms':[r['parse_ms'] for r in runs], 'characters':runs[-1]['characters'] if runs else None}
  textfile=root/'android/app/src/main/assets' # local source fixtures only, device extraction stored in app-private files
for threads in (1,2,4):
 runs=[r for r in rows if r.get('test')=='tts_benchmark' and r.get('threads')==threads and not r.get('warmup')]
 init=next((r for r in rows if r.get('test')=='tts_init' and r.get('threads')==threads),{})
 result['tts'][str(threads)]={'n':len(runs),'init_ms':init.get('init_ms'),
  'phonemizer_data_bytes':init.get('phonemizer_data_bytes'),
  'runs':[{'iteration':r['iteration'],'wall_ms':r['wall_ms'],'audio_ms':r['audio_ms'],'rtf':r['rtf'],
           'cpu_percent_one_core_basis':r['cpu_percent_one_core_basis'],'cpu_ms':r['cpu_ms'],
           'peak_pss_kb':r['peak_pss_kb'],'peak_rss_kb':r['peak_rss_kb']} for r in runs],
  'median_rtf':statistics.median([r['rtf'] for r in runs]) if runs else None,
  'median_cpu_percent_one_core_basis':statistics.median([r['cpu_percent_one_core_basis'] for r in runs]) if runs else None,
  'median_peak_pss_kb':statistics.median([r['peak_pss_kb'] for r in runs]) if runs else None,
  'median_peak_rss_kb':statistics.median([r['peak_rss_kb'] for r in runs]) if runs else None}
(out/'device-results.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
