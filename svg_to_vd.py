#!/usr/bin/env python3
"""
svg_to_vd_scaled_paths.py - Convert an SVG into an Android VectorDrawable
with everything scaled into a 108×108 viewport.

Features:
- Reads SVG viewBox, scales all shapes and paths into 108×108
- Supports <rect>, <circle>, <ellipse>, <line>, <polyline>, <polygon>
- Parses <path d="..."> fully, rescales every coordinate (absolute and relative)
- Preserves command structure (upper/lowercase unchanged)
- Cleans fill/stroke: skips fill="none"/stroke="none", and strokeWidth=0

Usage:
    python svg_to_vd_scaled_paths.py input.svg output.xml
"""

import sys, re, math, xml.etree.ElementTree as ET
from copy import deepcopy

# ---------------- VectorDrawable template ----------------
VD_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{paths}
</vector>
"""

DEFAULTS = {"fill": None, "stroke": None, "stroke-width": "0"}

# ---------------- path scaling ----------------
def scale_path(d, sx, sy):
    # Regex to split into commands + numbers
    tokens = re.findall(r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?", d)
    out, i = [], 0
    cmd = None
    while i < len(tokens):
        t = tokens[i]
        if re.match(r"[A-Za-z]", t):
            cmd = t
            out.append(cmd)
            i += 1
        else:
            # numbers depending on cmd
            if cmd in ("M","L","T"):   # x,y
                x,y = float(tokens[i]), float(tokens[i+1])
                out += [fmt(x*sx), fmt(y*sy)]
                i += 2
            elif cmd in ("m","l","t"):
                x,y = float(tokens[i]), float(tokens[i+1])
                out += [fmt(x*sx), fmt(y*sy)]
                i += 2
            elif cmd in ("H",):  # x only
                x = float(tokens[i]); out.append(fmt(x*sx)); i += 1
            elif cmd in ("h",):
                x = float(tokens[i]); out.append(fmt(x*sx)); i += 1
            elif cmd in ("V",):
                y = float(tokens[i]); out.append(fmt(y*sy)); i += 1
            elif cmd in ("v",):
                y = float(tokens[i]); out.append(fmt(y*sy)); i += 1
            elif cmd in ("C",):
                nums = list(map(float, tokens[i:i+6]))
                out += [fmt(nums[0]*sx), fmt(nums[1]*sy),
                        fmt(nums[2]*sx), fmt(nums[3]*sy),
                        fmt(nums[4]*sx), fmt(nums[5]*sy)]
                i += 6
            elif cmd in ("c",):
                nums = list(map(float, tokens[i:i+6]))
                out += [fmt(nums[0]*sx), fmt(nums[1]*sy),
                        fmt(nums[2]*sx), fmt(nums[3]*sy),
                        fmt(nums[4]*sx), fmt(nums[5]*sy)]
                i += 6
            elif cmd in ("S","Q"):
                nums = list(map(float, tokens[i:i+4]))
                out += [fmt(nums[0]*sx), fmt(nums[1]*sy),
                        fmt(nums[2]*sx), fmt(nums[3]*sy)]
                i += 4
            elif cmd in ("s","q"):
                nums = list(map(float, tokens[i:i+4]))
                out += [fmt(nums[0]*sx), fmt(nums[1]*sy),
                        fmt(nums[2]*sx), fmt(nums[3]*sy)]
                i += 4
            elif cmd in ("A",):
                rx,ry,rot,laf,sf,x,y = map(float, tokens[i:i+7])
                out += [fmt(rx*sx), fmt(ry*sy), fmt(rot), str(int(laf)), str(int(sf)),
                        fmt(x*sx), fmt(y*sy)]
                i += 7
            elif cmd in ("a",):
                rx,ry,rot,laf,sf,x,y = map(float, tokens[i:i+7])
                out += [fmt(rx*sx), fmt(ry*sy), fmt(rot), str(int(laf)), str(int(sf)),
                        fmt(x*sx), fmt(y*sy)]
                i += 7
            elif cmd in ("Z","z"):
                # shouldn't have numbers
                i += 1
            else:
                out.append(t); i += 1
    return " ".join(out)

def fmt(f):
    return str(round(f,3)).rstrip("0").rstrip(".")

# ---------------- shape converters ----------------
def rect_to_path(x,y,w,h): return f"M{x},{y} H{x+w} V{y+h} H{x} Z"
def circle_to_path(cx,cy,r): return f"M{cx-r},{cy} A{r},{r} 0 1,0 {cx+r},{cy} A{r},{r} 0 1,0 {cx-r},{cy} Z"
def ellipse_to_path(cx,cy,rx,ry): return f"M{cx-rx},{cy} A{rx},{ry} 0 1,0 {cx+rx},{cy} A{rx},{ry} 0 1,0 {cx-rx},{cy} Z"
def line_to_path(x1,y1,x2,y2): return f"M{x1},{y1} L{x2},{y2}"
def polyline_to_path(pts,close=False):
    d = f"M{pts[0][0]},{pts[0][1]}" + "".join(f" L{x},{y}" for x,y in pts[1:])
    return d + (" Z" if close else "")

# ---------------- attribute helpers ----------------
def style_to_dict(style_str):
    out={}
    if style_str:
        for decl in style_str.split(";"):
            if ":" in decl:
                k,v=decl.split(":",1); out[k.strip()]=v.strip()
    return out

def collect_attributes(el, parent):
    attribs=deepcopy(parent); attribs.update(el.attrib)
    attribs.update(style_to_dict(el.attrib.get("style",""))); return attribs

def make_path_element(d,attribs):
    attrs=[]
    fill=attribs.get("fill",DEFAULTS["fill"])
    stroke=attribs.get("stroke",DEFAULTS["stroke"])
    sw=attribs.get("stroke-width",DEFAULTS["stroke-width"])
    if fill and fill.lower()!="none": attrs.append(f'android:fillColor="{fill}"')
    if stroke and stroke.lower()!="none":
        attrs.append(f'android:strokeColor="{stroke}"')
        if sw!="0": attrs.append(f'android:strokeWidth="{sw}"')
    return f'    <path android:pathData="{d}" {" ".join(attrs)}/>'

# ---------------- main ----------------
def convert_svg_to_vd(svg_file,out_file):
    tree=ET.parse(svg_file); root=tree.getroot()
    vb=root.attrib.get("viewBox")
    if not vb: sys.exit("SVG needs viewBox")
    vx,vy,vw,vh=map(float,vb.strip().split())
    sx,sy=108/vw,108/vh
    paths=[]
    def traverse(el,attribs):
        attribs=collect_attributes(el,attribs)
        tag=el.tag.split("}")[-1]
        if tag=="rect":
            x,y,w,h=[float(el.attrib.get(k,"0")) for k in("x","y","width","height")]
            d=rect_to_path(x*sx,y*sy,w*sx,h*sy); paths.append(make_path_element(d,attribs))
        elif tag=="circle":
            cx,cy,r=map(float,[el.attrib["cx"],el.attrib["cy"],el.attrib["r"]])
            d=circle_to_path(cx*sx,cy*sy,r*sx); paths.append(make_path_element(d,attribs))
        elif tag=="ellipse":
            cx,cy,rx,ry=map(float,[el.attrib["cx"],el.attrib["cy"],el.attrib["rx"],el.attrib["ry"]])
            d=ellipse_to_path(cx*sx,cy*sy,rx*sx,ry*sy); paths.append(make_path_element(d,attribs))
        elif tag=="line":
            x1,y1,x2,y2=map(float,[el.attrib["x1"],el.attrib["y1"],el.attrib["x2"],el.attrib["y2"]])
            d=line_to_path(x1*sx,y1*sy,x2*sx,y2*sy); paths.append(make_path_element(d,attribs))
        elif tag in ("polyline","polygon"):
            nums=list(map(float,re.findall(r"[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?", el.attrib["points"])))
            pts=[(nums[i]*sx,nums[i+1]*sy) for i in range(0,len(nums),2)]
            d=polyline_to_path(pts,close=(tag=="polygon")); paths.append(make_path_element(d,attribs))
        elif tag=="path":
            d=el.attrib.get("d")
            if d: paths.append(make_path_element(scale_path(d,sx,sy),attribs))
        for c in el: traverse(c,attribs)
    traverse(root,DEFAULTS)
    with open(out_file,"w") as f: f.write(VD_TEMPLATE.format(paths="\n".join(paths)))

if __name__=="__main__":
    if len(sys.argv)!=3:
        print("Usage: python svg_to_vd_scaled_paths.py input.svg output.xml"); sys.exit(1)
    convert_svg_to_vd(sys.argv[1],sys.argv[2])
    print("Wrote",sys.argv[2])
