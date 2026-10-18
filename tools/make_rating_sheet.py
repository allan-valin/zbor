"""Build a self-contained rating sheet (one HTML file, audio embedded) for native speakers.

    python3 tools/make_rating_sheet.py OUT.html name=eval/audio/espeak [name2=dir2 ...]

Each sentence of eval/mk_eval_sentences.tsv gets one player per system. Systems appear
under neutral labels (А, Б, В…) in a per-sentence shuffled order, so raters cannot tell
which voice is which; the mapping is kept in the exported results, not shown on the page.
Ratings are stored in the browser (localStorage) and exported as TSV text.
"""
import base64
import csv
import html
import json
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LABELS = "АБВГД"


def main() -> None:
    out = Path(sys.argv[1])
    systems = [a.split("=", 1) for a in sys.argv[2:]]
    rows = list(csv.DictReader(open(ROOT / "eval/mk_eval_sentences.tsv"), delimiter="\t"))
    rng = random.Random(20261019)
    items = []
    for r in rows:
        order = systems[:]
        rng.shuffle(order)
        clips = []
        for label, (name, d) in zip(LABELS, order):
            data = (ROOT / d / f"{r['id']}.mp3").read_bytes()
            clips.append({"label": label, "system": name, "src": "data:audio/mpeg;base64," + base64.b64encode(data).decode()})
        items.append({"id": r["id"], "mk": r["mk"], "en": r["en"], "clips": clips})
    page = TEMPLATE.replace("__ITEMS__", json.dumps(items, ensure_ascii=False)).replace(
        "__BATCH__", html.escape(out.stem))
    out.write_text(page, encoding="utf-8")
    print(f"wrote {out} ({out.stat().st_size // 1024} KiB, {len(items)} sentences x {len(systems)} voices)")


TEMPLATE = """<!doctype html>
<html lang="mk"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Zbor — оценување на гласот</title>
<style>
:root{--bg:#151515;--card:#1f1f1f;--fg:#f2f2f2;--mute:#a0a0a0;--acc:#ffc107}
body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.45 system-ui,sans-serif}
main{max-width:760px;margin:0 auto;padding:16px}
h1{font-size:22px;margin:8px 0} .en{color:var(--mute);font-size:14px}
.card{background:var(--card);border-radius:12px;padding:14px;margin:14px 0}
.mk{font-size:20px;font-weight:600}
.clip{border-top:1px solid #333;margin-top:10px;padding-top:10px}
.row{display:flex;flex-wrap:wrap;gap:6px;align-items:center;margin:6px 0}
.row span{flex-basis:100%;color:var(--mute);font-size:14px}
button.s{width:40px;height:40px;border-radius:20px;border:1px solid #555;background:#2a2a2a;color:var(--fg);font-size:16px}
button.s.on{background:var(--acc);color:#111;border-color:var(--acc)}
audio{width:100%} textarea{width:100%;box-sizing:border-box;background:#2a2a2a;color:var(--fg);border:1px solid #444;border-radius:8px;padding:6px}
.big{background:var(--acc);color:#111;border:0;border-radius:24px;padding:12px 20px;font-size:16px;font-weight:600;margin:6px 6px 6px 0}
#done{color:var(--mute)}
</style></head><body><main>
<h1>Zbor — оценување на македонскиот глас</h1>
<p>Слушнете ја секоја реченица и оценете го секој глас од 1 (лошо) до 5 (одлично).<br>
<b>Разбирливост</b>: колку лесно се разбира. <b>Природност</b>: колку звучи како човек.<br>
Во коментарот напишете кој збор звучи погрешно. Кога ќе завршите, притиснете „Копирај резултати“ и испратете ги.</p>
<p class="en">Listen to each sentence and rate every voice from 1 (bad) to 5 (excellent).
<b>Intelligibility</b>: how easy it is to understand. <b>Naturalness</b>: how human it sounds.
Use the comment for words that sound wrong. When finished, tap “Copy results” and send them. Your answers are saved in this browser.</p>
<p id="done"></p>
<div id="list"></div>
<button class="big" id="copy">Копирај резултати / Copy results</button>
<button class="big" id="dl">Преземи / Download</button>
<p class="en" id="msg"></p>
</main>
<script>
const ITEMS=__ITEMS__, KEY="zbor-rating-__BATCH__";
let st={}; try{st=JSON.parse(localStorage.getItem(KEY)||"{}")}catch(e){}
const save=()=>{try{localStorage.setItem(KEY,JSON.stringify(st))}catch(e){};count()};
function count(){let n=0,t=0;for(const it of ITEMS)for(const c of it.clips){t+=2;const k=st[it.id+c.label]||{};if(k.i)n++;if(k.n)n++}
 document.getElementById("done").textContent=`Оценето / rated: ${n} / ${t}`}
const list=document.getElementById("list");
for(const it of ITEMS){const d=document.createElement("div");d.className="card";
 d.innerHTML=`<div class="mk">${it.id}. ${it.mk}</div><div class="en">${it.en}</div>`;
 for(const c of it.clips){const k=it.id+c.label;st[k]=st[k]||{};const e=document.createElement("div");e.className="clip";
  e.innerHTML=`<b>Глас / voice ${c.label}</b><audio controls preload="none" src="${c.src}"></audio>`;
  for(const [f,name] of [["i","Разбирливост"],["n","Природност"]]){const r=document.createElement("div");r.className="row";
   r.innerHTML=`<span>${name}</span>`;for(let v=1;v<=5;v++){const b=document.createElement("button");b.className="s"+(st[k][f]==v?" on":"");b.textContent=v;
    b.onclick=()=>{st[k][f]=v;[...r.querySelectorAll("button")].forEach(x=>x.classList.toggle("on",x===b));save()};r.appendChild(b)}e.appendChild(r)}
  const ta=document.createElement("textarea");ta.rows=2;ta.placeholder="Коментар / comment";ta.value=st[k].c||"";ta.oninput=()=>{st[k].c=ta.value;save()};e.appendChild(ta);d.appendChild(e)}
 list.appendChild(d)}
count();
function tsv(){const L=["batch\\tid\\tlabel\\tsystem\\tintelligibility\\tnaturalness\\tcomment"];
 for(const it of ITEMS)for(const c of it.clips){const k=st[it.id+c.label]||{};L.push(["__BATCH__",it.id,c.label,c.system,k.i||"",k.n||"",(k.c||"").replace(/\\s+/g," ")].join("\\t"))}return L.join("\\n")}
document.getElementById("copy").onclick=async()=>{const m=document.getElementById("msg");
 try{await navigator.clipboard.writeText(tsv());m.textContent="Копирано / copied."}catch(e){m.textContent="Copy failed; use Download."}};
document.getElementById("dl").onclick=()=>{const a=document.createElement("a");a.href=URL.createObjectURL(new Blob([tsv()],{type:"text/tab-separated-values"}));a.download="zbor-rating-__BATCH__.tsv";a.click()};
</script></body></html>
"""

if __name__ == "__main__":
    main()
