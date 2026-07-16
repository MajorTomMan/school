from pathlib import Path
from pypdf import PdfReader
import fitz, hashlib, json, re, unicodedata
import argparse

parser=argparse.ArgumentParser()
parser.add_argument('--input', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args=parser.parse_args()
ROOT=args.input
OUT=args.output

def clean(s):
    s=unicodedata.normalize('NFKC',s or '')
    s=s.replace('\u2003',' ').replace('\u2002',' ').replace('\u200b',' ')
    s=re.sub(r'\s+',' ',s).strip(' ·\t')
    return s

def slug(s):
    s=clean(s).lower()
    s=re.sub(r'[^0-9a-z\u4e00-\u9fffぁ-んァ-ヶ一-龥]+','-',s).strip('-')
    return s[:80] or hashlib.sha1(s.encode()).hexdigest()[:12]

def metadata(name):
    title=name[:-4]
    normalized=title.replace('（根据2022年版课程标准修订）','')
    subj=None; sid=None
    for a,b in [('语文','chinese'),('数学','math'),('英语','english'),('日语','japanese'),('物理','physics'),('化学','chemistry')]:
        if a in normalized:
            subj,sid=a,b;break
    if not subj: raise ValueError(name)
    if '义务教育' in normalized:
        stage='junior-high'
        m=re.search(r'([七八九])年级',normalized)
        grades={'七':7,'八':8,'九':9}; grade=grades[m.group(1)]
        volume=1 if '上册' in normalized else 2
        course_code=f'{sid}-jh-g{grade}-v{volume}'
    else:
        stage='senior-high'
        if '选择性必修 上册' in normalized: seq=3
        elif '选择性必修 中册' in normalized: seq=4
        elif '选择性必修 下册' in normalized: seq=5
        else:
            m=re.search(r'选择性必修\s*(?:第)?\s*([一二三四1234])\s*册?',normalized)
            if m:
                conv={'一':1,'二':2,'三':3,'四':4}
                n=conv.get(m.group(1), int(m.group(1)) if m.group(1).isdigit() else 1)
                required_count = 2 if sid == 'chinese' else 3
                seq=required_count+n
            elif '必修 上册' in normalized: seq=1
            elif '必修 下册' in normalized: seq=2
            else:
                m=re.search(r'必修\s*(?:第)?\s*([一二三123])\s*册?',normalized)
                conv={'一':1,'二':2,'三':3}
                seq=conv.get(m.group(1), int(m.group(1)) if m and m.group(1).isdigit() else 1) if m else 1
        if seq <= 6:
            grade=10 + (seq-1)//2
            volume=1 if seq%2 else 2
        else:
            grade=12
            volume=3
        course_code=f'{sid}-sh-{seq:02d}'
    return dict(title=normalized, subjectId=sid, subjectTitle=subj, stage=stage, grade=grade, volume=volume, courseCode=course_code)

def outline_nodes(pdf):
    r=PdfReader(str(pdf)); result=[]
    def walk(items, ancestors=()):
        i=0
        while i<len(items):
            item=items[i]
            if isinstance(item,list): walk(item,ancestors)
            else:
                title=clean(getattr(item,'title',str(item)))
                try: page=r.get_destination_page_number(item)+1
                except Exception: page=None
                child=items[i+1] if i+1<len(items) and isinstance(items[i+1],list) else None
                result.append({'title':title,'page':page,'path':list(ancestors),'hasChildren':bool(child)})
                if child:
                    walk(child,ancestors+(title,)); i+=1
            i+=1
    try: walk(r.outline)
    except Exception: pass
    return len(r.pages),result

def japanese_nodes(pdf):
    d=fitz.open(pdf); hits={}; rx=re.compile(r'第\s*(\d{1,2})\s*課\s*([^\n\r]{1,80})')
    for idx,p in enumerate(d):
        for m in rx.finditer(p.get_text('text')):
            n=int(m.group(1)); title=clean(m.group(2)); title=re.sub(r'[ \s]*[0-9]+$','',title).strip()
            if title and n not in hits: hits[n]=(idx+1,title)
    return len(d),[{'title':f'第{n}课 {title}','page':page,'path':[f'单元{(n-1)//4+1}'],'hasChildren':False} for n,(page,title) in sorted(hits.items())]

def choose_lessons(nodes, subject):
    valid=[x for x in nodes if x['page'] and x['title']]
    if subject=='japanese': return valid
    bad=re.compile(r'^(目录|Contents|附录|索引|元素周期表|词汇表|语法与表达一览表|学习评价)')
    return [x for x in valid if not x['hasChildren'] and not bad.search(x['title'])]

books=[]
for pdf in sorted(ROOT.glob('*.pdf')):
    meta=metadata(pdf.name)
    if meta['subjectId']=='math': continue
    pages,nodes=japanese_nodes(pdf) if meta['subjectId']=='japanese' else outline_nodes(pdf)
    lessons=sorted(choose_lessons(nodes,meta['subjectId']),key=lambda x:(x['page'],len(x['path']),x['title']))
    ded=[]; seen=set()
    for x in lessons:
        key=(x['page'],x['title'])
        if key not in seen: seen.add(key); ded.append(x)
    lessons=ded
    for i,x in enumerate(lessons):
        nxt=lessons[i+1]['page'] if i+1<len(lessons) else pages+1
        x['pageStart']=x.pop('page'); x['pageEnd']=max(x['pageStart'],nxt-1); x.pop('hasChildren',None)
        x['id']=f"{meta['courseCode']}:{i+1:03d}:{slug(x['title'])}"
    books.append({**meta,'sha256':hashlib.sha256(pdf.read_bytes()).hexdigest(),'aliases':[meta['title'],pdf.stem],'pageCount':pages,'pageIndexOffset':0,'publisher':'人民教育出版社','edition':'全学科预制课程 v1','lessons':lessons})

root={'schemaVersion':2,'packVersion':'all-subjects-prebuilt-2026-07-v1','books':books}
OUT.parent.mkdir(parents=True,exist_ok=True)
OUT.write_text(json.dumps(root,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
assert len(books)==34, len(books)
assert sum(len(b['lessons']) for b in books)==1027
assert len({b['courseCode'] for b in books})==34
for b in books:
    assert b['lessons']
    assert all(1 <= x['pageStart'] <= x['pageEnd'] <= b['pageCount'] for x in b['lessons'])
print('books',len(books),'lessons',sum(len(b['lessons']) for b in books),'bytes',OUT.stat().st_size)
