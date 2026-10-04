from pathlib import Path
import json,zipfile,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]; out=ROOT/'results'
rows=json.loads((out/'gradle-artifacts.json').read_text()); cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
ns={'m':'http://maven.apache.org/POM/4.0.0'}
for row in rows:
    poms=list((cache/row['group']/row['name']/row['version']).rglob('*.pom'))
    declared=[]
    for pom in poms:
        root=ET.parse(pom).getroot()
        for node in root.findall('m:licenses/m:license',ns):
            declared.append({'name':node.findtext('m:name',default='',namespaces=ns),'url':node.findtext('m:url',default='',namespaces=ns)})
    row['pom_declared_licenses']=declared
    row['audit_status']='declared metadata, transitive native contents need separate audit'
    with zipfile.ZipFile(row['file']) as z:
        names=[n for n in z.namelist() if any(w in n.lower() for w in ['license','notice','copying'])]
        row['archive_license_entries']=names
        if row['group']=='com.microsoft.cognitiveservices.speech':
            for i,name in enumerate(names):
                data=z.read(name)
                if len(data)<2_000_000: (out/'licenses'/f'azure-sdk-{i}.txt').write_bytes(data)
(out/'runtime-bom.json').write_text(json.dumps(rows,indent=2))
print('Audited declared metadata for',len(rows),'resolved runtime artifacts')
for row in rows:
    if row['group']=='com.microsoft.cognitiveservices.speech': print(row['pom_declared_licenses'],row['archive_license_entries'])
