"""Generate the public roadmap page from the repository's canonical document."""
from pathlib import Path
from html import escape
import re
root = Path(__file__).resolve().parent
lines = (root.parent / 'ROADMAP.md').read_text(encoding='utf-8-sig').splitlines()
def text(s):
    s = re.sub(r'\[([^]]+)\]\([^)]+\)', r'\1', s)
    return escape(s)
out=[]; paragraph=[]; items=[]; table=[]
def flush():
    if paragraph: out.append('<p>'+text(' '.join(paragraph))+'</p>');paragraph.clear()
    if items: out.append('<ul>'+''.join('<li>'+text(s)+'</li>' for s in items)+'</ul>');items.clear()
    if table:
        rows=[r for r in table if not re.fullmatch(r'[| :\-]+',r)]
        out.append('<div class="table-scroll"><table>')
        for i,row in enumerate(rows):
            tag='th' if i==0 else 'td'
            out.append('<tr>'+''.join(f'<{tag}>'+text(c.strip())+f'</{tag}>' for c in row.strip('|').split('|'))+'</tr>')
        out.append('</table></div>');table.clear()
for line in lines:
    if not line.strip(): flush();continue
    if line.startswith('#'):
        flush();level=len(line)-len(line.lstrip('#'));out.append(f'<h{level}>'+text(line.lstrip('#').strip())+f'</h{level}>')
    elif line.startswith('|'): table.append(line)
    elif line.startswith('- '):
        if paragraph:flush()
        items.append(line[2:].replace('[ ]','Open:').replace('[x]','Complete:'))
    elif items: items[-1]+=' '+line.strip()
    else: paragraph.append(line.strip())
flush()
page='''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Roadmap · Yomu</title><link rel="icon" href="/icon.png"><link rel="stylesheet" href="/style.css"><script src="/theme.js"></script></head><body class="document-page"><main><a class="back-link" href="/settings.html">← Settings</a><article class="roadmap-content">'''+''.join(out)+'''</article></main></body></html>'''
(root/'static'/'roadmap.html').write_text(page,encoding='utf-8')
print('Generated roadmap.html from ROADMAP.md')
