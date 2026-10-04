"""Evidence inventory; fetched licenses do not prove an upstream binary's entire SBOM."""
from pathlib import Path
import hashlib,json,re,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
vendor=ROOT/'vendor/sherpa-onnx'
out=ROOT/'results'; licenses=out/'licenses'; licenses.mkdir(parents=True,exist_ok=True)
rows=[]
for file in sorted((vendor/'cmake').glob('*.cmake')):
    if file.name not in ['espeak-ng-for-piper.cmake','piper-phonemize.cmake','onnxruntime-android-aarch64.cmake','kaldi-native-fbank.cmake','kaldi-decoder.cmake','simple-sentencepiece.cmake','json.cmake','hclust-cpp.cmake']: continue
    text=file.read_text()
    rows.append({'component_file':file.name,'urls':re.findall(r'set\(\w+_URL\s+"([^"]+)"',text),
        'hashes':re.findall(r'SHA256=([0-9a-f]+)',text),'scope':'upstream source configuration, binary linkage not certified'})
sources={
 'sherpa-onnx':('Apache-2.0','https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v1.13.8/LICENSE'),
 'espeak-ng-pinned':('GPL-3.0-or-later','https://raw.githubusercontent.com/csukuangfj/espeak-ng/ed530aa113046142eb5115cf2fc9157854d0ffe1/COPYING'),
 'piper-phonemize-pinned':('MIT','https://raw.githubusercontent.com/csukuangfj/piper-phonemize/f3ff95afc03640bc1399e113e83361192a2fafb4/LICENSE.md'),
 'piper-phonemize-cmake':('build-evidence','https://raw.githubusercontent.com/csukuangfj/piper-phonemize/f3ff95afc03640bc1399e113e83361192a2fafb4/CMakeLists.txt'),
 'onnxruntime':('MIT','https://raw.githubusercontent.com/microsoft/onnxruntime/v1.28.2/LICENSE'),
 'onnxruntime-thirdparty':('mixed-transitive','https://raw.githubusercontent.com/microsoft/onnxruntime/v1.28.2/ThirdPartyNotices.txt'),
 'kaldi-native-fbank':('Apache-2.0','https://raw.githubusercontent.com/csukuangfj/kaldi-native-fbank/v1.22.3/LICENSE'),
 'kaldi-decoder':('Apache-2.0','https://raw.githubusercontent.com/k2-fsa/kaldi-decoder/v0.3.0/LICENSE'),
 'kaldi-decoder-cmake':('build-evidence','https://raw.githubusercontent.com/k2-fsa/kaldi-decoder/v0.3.0/CMakeLists.txt'),
 'simple-sentencepiece':('Apache-2.0','https://raw.githubusercontent.com/pkufool/simple-sentencepiece/v0.7/LICENSE'),
 'json':('MIT','https://raw.githubusercontent.com/nlohmann/json/v3.12.0/LICENSE.MIT'),
 'hclust':('audit-required','https://raw.githubusercontent.com/csukuangfj/hclust-cpp/2026-02-25/LICENSE'),
 'lame':('LGPL-2.0-or-later-candidate','https://lame.sourceforge.io/license.txt'),
}
fetched=[]
for name,(declared,url) in sources.items():
    row={'name':name,'declared_or_expected_license':declared,'url':url}
    try:
        data=urllib.request.urlopen(url,timeout=40).read(); (licenses/(name+'.txt')).write_bytes(data)
        row.update(bytes=len(data),sha256=hashlib.sha256(data).hexdigest(),fetch='passed')
    except Exception as e: row.update(fetch='failed',error=type(e).__name__)
    fetched.append(row)
model=ROOT/'downloads/kokoro-en-v0_19'; data_dir=model/'espeak-ng-data'
binaries=[]
apk=ROOT/'android/app/build/outputs/apk/debug/app-debug.apk'
with zipfile.ZipFile(apk) as archive:
    for name in sorted(n for n in archive.namelist() if n.startswith('lib/arm64-v8a/') and n.endswith('.so')):
        data=archive.read(name)
        binaries.append({'file':Path(name).name,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
archive_path=ROOT/'downloads/lame-3.100.tar.gz'
archive_hash=hashlib.sha256(archive_path.read_bytes()).hexdigest() if archive_path.exists() else None
report={'sherpa_commit':'11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf','source_dependencies':rows,'license_evidence':fetched,
 'native_artifacts':binaries,'phonemizer_data_bytes':sum(p.stat().st_size for p in data_dir.rglob('*') if p.is_file()),
 'model_bytes':(model/'model.onnx').stat().st_size,'voices_bytes':(model/'voices.bin').stat().st_size,
 'lame_android_encoder':{'version':'3.100','license':'LGPL-2.0-or-later','decoder_included':False,
    'source_archive':'downloads/lame-3.100.tar.gz','source_archive_sha256':archive_hash,
    'device_test':'results/lame-android-inspection.json'},
 'release_compatibility':'NOT CLEARED: user accepts GPL-family components, but sherpa static closure and Azure SDK redistribution/dependency review remain open'}
(out/'native-bom.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps({'phonemizer_data_bytes':report['phonemizer_data_bytes'],'license_fetches':[(r['name'],r['fetch']) for r in fetched]},indent=2))
