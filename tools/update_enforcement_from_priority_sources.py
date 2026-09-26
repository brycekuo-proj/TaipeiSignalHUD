#!/usr/bin/env python3
import csv, math, re, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TP = ROOT / "data/raw/taipei_speed_cameras.csv"

TP_PDF = "https://www-ws.gov.taipei/Download.ashx?icon=..pdf&n=6Ie65YyX5biC5pS%2F5bqc6K2m5a%2Bf5bGA5Zu65a6a5byP6YGV6KaP54Wn55u46Kit5YKZ5Y%2BK5Y2A6ZaT5ris6YCf6KOd572u6Kit572u5Zyw6bue5LiA6Ka96KGoLnBkZg%3D%3D&u=LzAwMS9VcGxvYWQvNDY3L3JlbGZpbGUvMTg5ODUvMjQ1NDEvMmJhZjY2MTQtZWEyOS00MmNmLWFlOTEtMDQyZTNjYWFiMjFlLnBkZg%3D%3D"

def hav(lat1, lon1, lat2, lon2):
    r=6371000
    p1,p2=map(math.radians,(lat1,lat2))
    dp=math.radians(lat2-lat1); dl=math.radians(lon2-lon1)
    a=math.sin(dp/2)**2+math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*r*math.asin(math.sqrt(a))

# authoritative snapshot transcribed from current Taipei Police PDF text retrieved 2026-09-26.
# Only rows whose coordinates differ materially from the existing open-data CSV are listed here.
CORR = {
7:(25.033098,121.496790),8:(25.030560,121.489610),9:(25.043744,121.500396),
11:(25.044512,121.501140),12:(25.019234,121.497120),20:(25.052633,121.536780),
21:(25.066557,121.527700),22:(25.071774,121.544340),23:(25.076668,121.566376),
24:(25.077658,121.540924),26:(25.077879,121.536270),27:(25.048206,121.519770),
38:(25.018555,121.545944),39:(25.017244,121.548830),40:(25.030283,121.533680),
41:(25.026731,121.526738),42:(25.027754,121.537917),43:(25.044518,121.539940),
44:(25.016639,121.543838),45:(25.026205,121.537620),46:(25.020033,121.534060),
52:(25.043074,121.508580),53:(25.038351,121.530170),54:(25.042990,121.518695),
55:(25.028524,121.515686),63:(25.057970,121.552055),64:(25.066849,121.544380),
65:(25.054245,121.565600),66:(25.061950,121.568436),67:(25.062050,121.557490),
72:(25.041310,121.559980),79:(25.082521,121.507430),80:(25.108170,121.546036),
81:(25.132162,121.546520),82:(25.086670,121.523060),83:(25.104001,121.542523),
84:(25.079073,121.508220),85:(25.080551,121.522160)
}

with TP.open("r",encoding="big5",newline="") as f:
    rows=list(csv.DictReader(f))
    fields=rows[0].keys()

changes=[]
for r in rows:
    try: idx=int(r["編號"])
    except: continue
    if idx not in CORR: continue
    old=(float(r["緯度"]),float(r["經度"])); new=CORR[idx]
    d=hav(*old,*new)
    if d >= 2:
        changes.append((idx,d,old,new,r.get("設置路段",""),r.get("設置地點","")))
        r["緯度"]=f"{new[0]:.6f}"; r["經度"]=f"{new[1]:.6f}"

backup=TP.with_suffix(".csv.bak_2026-09-26")
if not backup.exists():
    backup.write_bytes(TP.read_bytes())
with TP.open("w",encoding="big5",newline="") as f:
    w=csv.DictWriter(f,fieldnames=fields); w.writeheader(); w.writerows(rows)

report=ROOT/"data/derived/enforcement_corrections_2026-09-26.csv"
report.parent.mkdir(parents=True,exist_ok=True)
with report.open("w",encoding="utf-8-sig",newline="") as f:
    w=csv.writer(f); w.writerow(["city","id","offset_m","old_lat","old_lon","new_lat","new_lon","road","location","source","source_date"])
    for idx,d,old,new,road,loc in changes:
        w.writerow(["Taipei",idx,f"{d:.1f}",*old,*new,road,loc,"Taipei Police current fixed-camera PDF","2026-09-09 page update / PDF current as retrieved 2026-09-26"])

print("corrected",len(changes),"Taipei camera coordinates")
for x in sorted(changes,key=lambda z:z[1],reverse=True)[:20]:
    print(x[0],f"{x[1]:.1f}m",x[4],x[5])
print("report",report)
