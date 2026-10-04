"""Create actual Kokoro speech, loop to 900 s, encode with LAME, inspect both ID3 versions.
Desktop fixture generation only: no target-device performance conclusions.
"""
from pathlib import Path
import sys, json, subprocess, hashlib, wave
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools/python-deps'))
import imageio_ffmpeg
import sherpa_onnx
import numpy as np
from mutagen.id3 import ID3, TIT2, TPE1, CHAP, CTOC, CTOCFlags
from mutagen.mp3 import MP3

model = ROOT / 'downloads/kokoro-en-v0_19'
config = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
    kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(model=str(model/'model.onnx'),
        voices=str(model/'voices.bin'), tokens=str(model/'tokens.txt'), data_dir=str(model/'espeak-ng-data')),
    num_threads=2), max_num_sentences=1)
assert config.validate(), 'Invalid Kokoro configuration'
tts=sherpa_onnx.OfflineTts(config)
audio=tts.generate('This is the Yomu chapter compatibility fixture. We are testing a single audio file with three named chapters. The spoken passage repeats to create a fifteen minute recording. Chapter markers occur at zero, five, and ten minutes. This recording is a technical test, not a full document narration.', sid=0, speed=1)
pcm=(np.asarray(audio.samples).clip(-1,1)*32767).astype('<i2')
out=ROOT/'results'; out.mkdir(exist_ok=True)
wav=out/'speech-seed.wav'
with wave.open(str(wav),'wb') as f:
    f.setnchannels(1); f.setsampwidth(2); f.setframerate(audio.sample_rate); f.writeframes(pcm.tobytes())
ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
version=subprocess.check_output([ffmpeg,'-version'],text=True)
(out/'ffmpeg-version.txt').write_text(version,encoding='utf-8')
encoders=subprocess.check_output([ffmpeg,'-hide_banner','-encoders'],text=True,stderr=subprocess.STDOUT)
assert 'libmp3lame' in encoders
subprocess.run([ffmpeg,'-y','-hide_banner','-loglevel','error','-stream_loop','-1','-i',str(wav),'-t','900',
    '-c:a','libmp3lame','-b:a','96k',str(out/'chapters-base.mp3')],check=True)
report={'generator':'Desktop Kokoro speech loop, not target-device benchmark','ffmpeg':version.splitlines()[0], 'files':[]}
for v in (3,4):
    path=out/f'chapters-v2{v}.mp3'; path.write_bytes((out/'chapters-base.mp3').read_bytes())
    tags=ID3(); tags.add(TIT2(encoding=3,text=['Yomu — 15-minute chapter fixture']))
    tags.add(TPE1(encoding=3,text=['Yomu spike']))
    ids=[]
    for i in range(3):
        eid=f'ch{i+1}'; ids.append(eid)
        tags.add(CHAP(element_id=eid,start_time=i*300000,end_time=(i+1)*300000,
            start_offset=0xffffffff,end_offset=0xffffffff,sub_frames=[TIT2(encoding=3,text=[f'Chapter {i+1}'])]))
    tags.add(CTOC(element_id='toc',flags=CTOCFlags.TOP_LEVEL|CTOCFlags.ORDERED,child_element_ids=ids))
    tags.save(path,v2_version=v)
    parsed=ID3(path); chapters=sorted(parsed.getall('CHAP'),key=lambda c:c.start_time)
    assert parsed.version==(2,v,0)
    assert [(c.start_time,c.end_time) for c in chapters]==[(0,300000),(300000,600000),(600000,900000)]
    toc=parsed.getall('CTOC'); assert len(toc)==1 and toc[0].child_element_ids==ids
    duration=MP3(path).info.length; assert abs(duration-900)<0.2
    subprocess.run([ffmpeg,'-v','error','-i',str(path),'-f','null','-'],check=True)
    report['files'].append({'file':path.name,'duration_s':duration,'bytes':path.stat().st_size,
        'id3_version':parsed.version,'chapters':[{'id':c.element_id,'start_ms':c.start_time,'end_ms':c.end_time,'title':str(c.sub_frames['TIT2'])} for c in chapters],
        'toc':ids,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'decode':'passed', 'pocket_casts_android':'not tested'})
(out/'mp3-inspection.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
