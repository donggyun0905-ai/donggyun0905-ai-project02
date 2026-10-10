#!/usr/bin/env python3
"""코드 해설서(docs/code-atlas) 데이터를 다시 만든다 — 2026-10-11.

사용: python3 scripts/gen-code-atlas.py            (저장소 루트에서, 결과는 docs/code-atlas/atlas-data.js)
      python3 scripts/gen-code-atlas.py <저장소> <출력 폴더>

파일마다 머리 주석(왜 이렇게 만들었나)·메서드·연결 관계(쓰는 것/쓰는 곳/테스트)·요구사항(feature-coverage.json)·
테이블 사용처를 뽑는다. 원본 코드는 담지 않는다 — 화면(index.html)이 지금 커밋의 파일을 GitHub에서 그대로 불러온다.
그래서 커밋(푸시)한 뒤에 돌려야 화면의 줄 번호와 원본이 맞는다.
"""
import json, os, re, subprocess, sys, collections

REPO = sys.argv[1] if len(sys.argv) > 1 else '.'
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(REPO, 'docs', 'code-atlas')
os.makedirs(OUT, exist_ok=True)

files = subprocess.run(['git', '-c', 'core.quotepath=false', 'ls-files'], cwd=REPO,
                       capture_output=True, text=True).stdout.splitlines()

TEXT_EXT = {'java', 'jsp', 'jspf', 'js', 'css', 'sql', 'xml', 'json', 'properties', 'yml', 'sh', 'py', 'ps1',
            'wxs', 'wxl', 'tsv', 'md', 'example'}
SKIP_PREFIX = ('개발자료/', '2d/', 'src/main/webapp/js/vendor/', 'desktop-companion/dist-setup/', 'docs/code-atlas/')

def keep(p):
    if p.startswith(SKIP_PREFIX):
        return False
    ext = p.rsplit('.', 1)[-1] if '.' in p else ''
    if p.startswith('docs/') and ext == 'html':
        return False
    if p.startswith('개발일지/'):
        return ext == 'md'
    if p.startswith('docs/'):
        return ext == 'md'
    return ext in TEXT_EXT or p in ('.gitattributes', '.gitignore', 'mvnw', '.mvn/wrapper/maven-wrapper.properties')

files = [f for f in files if keep(f)]

def read(p):
    with open(os.path.join(REPO, p), encoding='utf-8', errors='replace') as fh:
        return fh.read()

def area_of(p):
    if p.startswith('src/main/java/com/specodyssey/'):
        rest = p[len('src/main/java/com/specodyssey/'):]
        parts = rest.split('/')
        if parts[0] == 'service' and len(parts) > 2:
            return 'service/' + parts[1]
        return parts[0]
    if p.startswith('src/test/java/'):
        rest = p[len('src/test/java/com/specodyssey/'):]
        parts = rest.split('/')
        return 'test/' + (parts[0] if len(parts) > 1 else 'root')
    if p.startswith('src/test/'):
        return 'test/resources'
    if p.startswith('src/main/webapp/WEB-INF/views'):
        return 'views'
    if p.startswith('src/main/webapp/WEB-INF') or p.startswith('src/main/webapp/META-INF'):
        return 'config'
    if p.startswith('src/main/webapp/css'):
        return 'css'
    if p.startswith('src/main/webapp/js'):
        return 'js'
    if p.startswith('src/main/webapp/'):
        return 'views'
    if p.startswith('src/main/resources'):
        return 'config'
    if p.startswith('desktop-companion/src/test'):
        return 'companion-test'
    if p.startswith('desktop-companion'):
        return 'companion'
    if p.startswith('sql/'):
        return 'sql'
    if p.startswith('개발일지/'):
        return 'journal'
    if p.startswith('docs/'):
        return 'docs'
    return 'config'

# ---------------------------------------------------------------- 설명 주석 뽑기
def clean_block(c):
    lines = []
    for ln in c.splitlines():
        ln = re.sub(r'^\s*/\*\*?', '', ln)
        ln = re.sub(r'\*/\s*$', '', ln)
        ln = re.sub(r'^\s*\*\s?', '', ln)
        lines.append(ln.rstrip())
    txt = '\n'.join(lines).strip()
    return txt

TYPE_RE = re.compile(r'^(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:public\s+|final\s+|abstract\s+|sealed\s+)*(class|interface|record|enum)\s+(\w+)', re.M)

def java_doc(src):
    m = TYPE_RE.search(src)
    if not m:
        return '', None, None
    head = src[:m.start()]
    # 타입 선언 바로 위 주석 블록(어노테이션 사이 허용)
    blocks = list(re.finditer(r'/\*[\s\S]*?\*/', head))
    doc = ''
    if blocks:
        last = blocks[-1]
        between = re.sub(r'//[^\n]*', '', head[last.end():])
        if re.fullmatch(r'(\s|@[\w.]+(\((?:[^()]|\([^()]*\))*\))?)*', between):
            doc = clean_block(last.group(0))
    if not doc:
        # 줄 주석 묶음
        lc = re.findall(r'((?:^\s*//.*\n)+)\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*$', head, re.M)
        if lc:
            doc = '\n'.join(l.strip()[2:].strip() for l in lc[-1].splitlines())
    return doc, m.group(1), m.group(2)

def leading_comment(src, kind):
    if kind in ('jsp', 'jspf'):
        m = re.search(r'<%--([\s\S]*?)--%>', src[:4000])
        return m.group(1).strip() if m else ''
    if kind == 'sql':
        out = []
        for ln in src.splitlines():
            s = ln.strip()
            if s.startswith('--'):
                out.append(s[2:].strip())
            elif s == '' and not out:
                continue
            elif s == '':
                if out:
                    out.append('')
            else:
                break
        return '\n'.join(out).strip()
    if kind in ('js', 'css'):
        m = re.match(r'\s*/\*([\s\S]*?)\*/', src)
        if m:
            return clean_block(m.group(0))
        lc = re.match(r'((?:\s*//.*\n)+)', src)
        return '\n'.join(l.strip()[2:].strip() for l in lc.group(1).splitlines()) if lc else ''
    if kind in ('sh', 'py', 'yml', 'ps1'):
        out = []
        for ln in src.splitlines()[:40]:
            s = ln.strip()
            if s.startswith('#!'):
                continue
            if s.startswith('#'):
                out.append(s.lstrip('#').strip())
            elif s.startswith('"""'):
                continue
            elif out:
                break
        return '\n'.join(out).strip()
    if kind == 'md':
        return ''
    return ''

def strip_comments_java(src):
    src = re.sub(r'/\*[\s\S]*?\*/', ' ', src)
    src = re.sub(r'//[^\n]*', ' ', src)
    return src

def first_para(doc, n=320):
    if not doc:
        return ''
    para = re.split(r'\n\s*\n', doc.strip())[0]
    para = re.sub(r'\s*\n\s*', ' ', para)
    para = re.sub(r'<[^>]+>', '', para)
    para = re.sub(r'\{@(?:code|link) ([^}]*)\}', r'\1', para)
    return para[:n] + ('…' if len(para) > n else '')

METHOD_RE = re.compile(r'^ {4}(?:@\w+(?:\([^)]*\))?[ \t]*\n? {4})*((?:(?:public|protected|private|static|final|synchronized|default|abstract)[ \t]+)*)((?:<[^>]+>[ \t]+)?[\w.]+(?:<[^()]*?>)?(?:\[\])*)[ \t]+(\w+)[ \t]*\(([^)]*)\)\s*(?:throws\s+[\w.,\s]+?)?\s*[{;]', re.M)
SKIP_NAMES = {'if', 'for', 'while', 'switch', 'return', 'new', 'catch', 'else', 'try', 'throw'}

def methods(src, kind):
    out = []
    for m in METHOD_RE.finditer(src):
        mods = m.group(1).split()
        ret = m.group(2).strip()
        name = m.group(3)
        if name in SKIP_NAMES or ret in ('return', 'new', 'throw', 'else'):
            continue
        if ret in ('public', 'protected', 'private'):
            mods = mods + [ret]
            ret = '(생성자)'
        vis = 'public' if 'public' in mods else 'protected' if 'protected' in mods else 'private' if 'private' in mods else 'package'
        before = src[:m.start()]
        cm = re.search(r'(/\*\*?[\s\S]*?\*/|(?:\s*//[^\n]*\n)+)\s*(?:@\w+(?:\([^)]*\))?\s*)*$', before[-1500:])
        note = ''
        if cm:
            raw = cm.group(1)
            note = first_para(clean_block(raw) if raw.strip().startswith('/*') else '\n'.join(l.strip()[2:].strip() for l in raw.strip().splitlines()), 200)
        params = re.sub(r'\s+', ' ', m.group(4).strip())
        out.append({'n': name, 'v': vis, 'st': 'static' in mods, 'sig': f"{ret} {name}({params})", 'note': note,
                    'line': src[:m.start(3)].count('\n') + 1})
    return out

# ---------------------------------------------------------------- 1차 수집
meta = {}
class_index = {}   # 클래스 이름 -> 경로
for p in files:
    src = read(p)
    ext = p.rsplit('.', 1)[-1] if '.' in p else ''
    e = {'p': p, 'a': area_of(p), 'ext': ext, 'lines': src.count('\n') + (0 if src.endswith('\n') else 1),
         'bytes': len(src.encode('utf-8'))}
    if ext == 'java':
        doc, kind, name = java_doc(src)
        e['doc'] = doc
        e['kind'] = kind
        e['cls'] = name
        if name:
            class_index.setdefault(name, p)
        e['tests'] = len(re.findall(r'@(?:Test|ParameterizedTest)\b', src)) if '/test/' in p else 0
        e['methods'] = methods(src, 'test' if '/test/' in p else 'main')
        ws = re.findall(r'@WebServlet\(([^)]*)\)', src)
        e['urls'] = re.findall(r'"([^"]+)"', ' '.join(ws))
        wf = re.findall(r'@WebFilter\(([^)]*)\)', src)
        e['filter'] = re.findall(r'"([^"]+)"', ' '.join(wf))
        e['listener'] = '@WebListener' in src
        e['views'] = sorted(set(re.findall(r'/WEB-INF/views/([\w/\-]+\.jspf?)', src)))
        flds = re.findall(r'^\s{4}private\s+(?:final\s+)?([\w<>\[\], ?.]+?)\s+(\w+)\s*(?:=[^;]*)?;\s*(?://\s*(.*))?$', src, re.M)
        e['fields'] = [{'t': t.strip(), 'n': n, 'c': (c or '').strip()} for t, n, c in flds
                       if not n.isupper() and 'Dao' not in t and 'Service' not in t][:60]
        rc = re.search(r'record\s+\w+\s*\(([^)]*)\)', src)
        if rc and not e['fields']:
            e['fields'] = [{'t': ' '.join(x.split()[:-1]), 'n': x.split()[-1], 'c': ''} for x in rc.group(1).split(',') if x.strip()]
    else:
        e['doc'] = leading_comment(src, ext)
        if ext in ('jsp', 'jspf'):
            inc = re.findall(r'<jsp:include\s+page="([^"]+)"', src) + re.findall(r'<%@\s*include\s+file="([^"]+)"', src)
            e['includes'] = sorted(set(inc))
            e['posts'] = sorted(set(re.findall(r'action="\$\{[^}]*\}([^"?]*)', src)))
            e['links'] = sorted(set(re.findall(r'href="\$\{[^}]*contextPath\}([^"?#$]+)', src)))
    e['sum'] = first_para(e.get('doc', ''))
    meta[p] = e

# ---------------------------------------------------------------- 참조 관계
code_cache = {}
for p, e in meta.items():
    if e['ext'] == 'java':
        code_cache[p] = strip_comments_java(read(p))

names = set(class_index)
uses = collections.defaultdict(set)
for p, code in code_cache.items():
    own = meta[p].get('cls')
    toks = set(re.findall(r'\b([A-Z][A-Za-z0-9]+)\b', code))
    for t in toks & names:
        if t != own:
            q = class_index[t]
            # 테스트가 아닌 파일은 테스트를 '사용'하지 않는다
            if '/test/' in q and '/test/' not in p:
                continue
            uses[p].add(q)
used_by = collections.defaultdict(set)
for p, qs in uses.items():
    for q in qs:
        used_by[q].add(p)
for p, e in meta.items():
    if e['ext'] == 'java':
        e['uses'] = sorted(q for q in uses[p] if '/test/' not in q)
        ub = used_by[p]
        e['usedBy'] = sorted(q for q in ub if '/test/' not in q)
        e['testedBy'] = sorted(q for q in ub if '/test/' in q)

# JSP <- 서블릿, JSP include
view_path = lambda v: 'src/main/webapp/WEB-INF/views/' + v
for p, e in meta.items():
    if e['ext'] == 'java' and e.get('views'):
        for v in e['views']:
            vp = view_path(v)
            if vp in meta:
                meta[vp].setdefault('renderedBy', []).append(p)
    if e['ext'] in ('jsp', 'jspf'):
        res = []
        for inc in e.get('includes', []):
            if '${' in inc:
                continue
            if inc.startswith('/'):
                cand = 'src/main/webapp' + inc
            else:
                cand = os.path.normpath(os.path.join(os.path.dirname(p), inc))
            if cand in meta:
                res.append(cand)
                meta[cand].setdefault('includedBy', []).append(p)
        e['includes'] = res

# ---------------------------------------------------------------- 테이블
tables = {}
for p in sorted(f for f in files if f.startswith('sql/') and f.endswith('.sql') and '/do-not-run/' not in f and '/dev/' not in f):
    src = read(p)
    for m in re.finditer(r'CREATE TABLE(?: IF NOT EXISTS)?\s+`?(\w+)`?\s*\(([\s\S]*?)\n\)\s*ENGINE', src, re.I):
        name = m.group(1)
        body = m.group(2)
        # 바로 위 주석
        pre = src[:m.start()].rstrip().splitlines()[-12:]
        note = []
        for ln in reversed(pre):
            s = ln.strip()
            if s.startswith('--'):
                note.insert(0, s[2:].strip().strip('-').strip())
            else:
                break
        cols = []
        for ln in body.splitlines():
            s = ln.strip().rstrip(',')
            cm = re.match(r'`?(\w+)`?\s+([A-Z]+(?:\([^)]*\))?(?:\s+UNSIGNED)?)(.*)', s)
            if not cm or cm.group(1).upper() in ('PRIMARY', 'KEY', 'UNIQUE', 'CONSTRAINT', 'INDEX', 'FOREIGN', 'FULLTEXT', 'CHECK', 'ON', 'REFERENCES'):
                continue
            cmt = re.search(r"COMMENT\s+'([^']*)'", cm.group(3))
            cols.append({'n': cm.group(1), 't': cm.group(2), 'c': cmt.group(1) if cmt else '',
                         'nn': 'NOT NULL' in cm.group(3).upper()})
        keys = [re.sub(r'\s+', ' ', ln.strip().rstrip(',')) for ln in body.splitlines()
                if re.match(r'\s*(PRIMARY KEY|UNIQUE|KEY|INDEX|CONSTRAINT|FULLTEXT)', ln, re.I)]
        tables.setdefault(name, {'file': p, 'note': ' '.join(x for x in note if x)[:400], 'cols': cols, 'keys': keys})
# ALTER ADD COLUMN 보충
for p in sorted(f for f in files if f.startswith('sql/') and f.endswith('.sql') and '/do-not-run/' not in f):
    src = read(p)
    for m in re.finditer(r'ALTER TABLE\s+`?(\w+)`?([\s\S]*?);', src, re.I):
        t = m.group(1)
        if t not in tables:
            continue
        for am in re.finditer(r'ADD COLUMN\s+(?:IF NOT EXISTS\s+)?`?(\w+)`?\s+([A-Z]+(?:\([^)]*\))?)([^,;]*)', m.group(2), re.I):
            if not any(c['n'] == am.group(1) for c in tables[t]['cols']):
                cmt = re.search(r"COMMENT\s+'([^']*)'", am.group(3))
                tables[t]['cols'].append({'n': am.group(1), 't': am.group(2), 'c': cmt.group(1) if cmt else '',
                                          'nn': 'NOT NULL' in am.group(3).upper(), 'alter': p})
tnames = set(tables)
for p, e in meta.items():
    if e['ext'] == 'java' and '/test/' not in p:
        src = read(p)
        lits = ' '.join(re.findall(r'"((?:[^"\\]|\\.)*)"', src))
        used = sorted(t for t in tnames if re.search(r'\b' + t + r'\b', lits))
        if used:
            e['tables'] = used
            for t in used:
                tables[t].setdefault('by', []).append(p)

# ---------------------------------------------------------------- 요구사항
fr = json.loads(read('src/main/resources/feature-coverage.json'))
by_name = collections.defaultdict(list)
for p in meta:
    by_name[os.path.basename(p)].append(p)
for item in fr:
    for k in ('controllers', 'services', 'daos', 'views', 'tests'):
        for fn in item.get(k, []):
            for p in by_name.get(fn, []):
                meta[p].setdefault('fr', []).append(item['id'])

# ---------------------------------------------------------------- 출력
# 원본 코드는 담지 않는다 — 화면이 같은 커밋의 파일을 GitHub에서 그대로 불러온다(내용이 어긋나지 않게).
def git(*args):
    return subprocess.run(['git', *args], cwd=REPO, capture_output=True, text=True).stdout.strip()

out_meta = {'files': list(meta.values()), 'tables': tables, 'fr': fr,
            'sha': git('rev-parse', 'HEAD'), 'commit': git('log', '-1', '--format=%h %ad', '--date=short'),
            'repo': os.environ.get('ATLAS_REPO', 'donggyun0905-ai/donggyun0905-ai-project02')}
for f in out_meta['files']:
    f.pop('g', None)
body = json.dumps(out_meta, ensure_ascii=False, separators=(',', ':'))
with open(os.path.join(OUT, 'atlas-data.js'), 'w', encoding='utf-8') as fh:
    fh.write('// 자동 생성 — scripts/gen-code-atlas.py. 손으로 고치지 말 것.\n')
    fh.write('window.ATLAS = ' + body + ';\n')
print('files', len(meta), 'tables', len(tables), 'fr', len(fr), 'bytes', os.path.getsize(os.path.join(OUT, 'atlas-data.js')))
