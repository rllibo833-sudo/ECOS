#!/usr/bin/env python3
"""Render a 45-second procedural ECOS World teaser with Pillow, NumPy and FFmpeg."""
from __future__ import annotations
import argparse, math, os, subprocess, tempfile, wave
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

DURATION = 45.0
W, H = 1280, 720
FPS = 24

def font(size, bold=False):
    paths = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
    ]
    for p in paths:
        if os.path.exists(p): return ImageFont.truetype(p, size)
    return ImageFont.load_default()

def clamp(x, a=0.0, b=1.0): return max(a, min(b, x))
def smooth(x): x=clamp(x); return x*x*(3-2*x)

def gradient(top, bottom, w, h):
    a=np.array(top,dtype=np.float32); b=np.array(bottom,dtype=np.float32)
    t=np.linspace(0,1,h,dtype=np.float32)[:,None,None]
    arr=np.broadcast_to(a[None,None,:]*(1-t)+b[None,None,:]*t,(h,w,3)).astype(np.uint8)
    return Image.fromarray(arr,"RGB")

def poly(draw, pts, fill, outline=None, width=1):
    draw.polygon(pts, fill=fill)
    if outline: draw.line(pts+[pts[0]], fill=outline, width=width, joint="curve")

def centered(draw, xy, text, fnt, fill, anchor="mm", stroke=0):
    draw.text(xy,text,font=fnt,fill=fill,anchor=anchor,stroke_width=stroke,stroke_fill=(8,16,25))

def skyline(draw, horizon, scale=1.0, shift=0, night=False):
    base=(26,49,64) if not night else (12,27,42)
    x=-40+shift
    i=0
    while x < W+80:
        bw=int((42+(i*37)%67)*scale); bh=int((70+(i*53)%160)*scale)
        y=horizon-bh
        draw.rectangle((x,y,x+bw,horizon),fill=base)
        if i%4==0:
            draw.polygon([(x,y),(x+bw//2,y-28*scale),(x+bw,y)],fill=base)
        for wx in range(x+8,x+bw-5,13):
            for wy in range(y+12,horizon-8,19):
                if (wx+wy+i)%4:
                    draw.rectangle((wx,wy,wx+4,wy+7),fill=(207,184,125,180))
        x += bw+8; i+=1

def draw_train(draw, x, y, s=1):
    draw.rectangle((x,y,x+205*s,y+34*s),fill=(198,211,208),outline=(34,57,67),width=max(1,int(2*s)))
    draw.polygon([(x+170*s,y),(x+205*s,y+9*s),(x+205*s,y+34*s),(x+180*s,y+34*s)],fill=(105,147,151))
    for k in range(5):
        xx=x+(12+k*34)*s
        draw.rectangle((xx,y+7*s,xx+22*s,y+19*s),fill=(41,77,91))
    for xx in (x+34*s,x+165*s): draw.ellipse((xx,y+26*s,xx+49*s,y+57*s),fill=(20,30,36))
    draw.line((x-15*s,y+49*s,x+230*s,y+49*s),fill=(28,34,38),width=max(2,int(3*s)))

def mosque(draw, cx, ground, scale=1):
    ivory=(220,215,194); shadow=(142,157,151); gold=(193,158,91)
    # broad courtyard and colonnades
    draw.rectangle((cx-245*scale,ground-22*scale,cx+245*scale,ground),fill=(188,190,173))
    for side in (-1,1):
        xx=cx+side*170*scale
        draw.rectangle((xx-65*scale,ground-100*scale,xx+65*scale,ground-20*scale),fill=shadow)
        for j in range(5):
            x=xx-50*scale+j*25*scale
            draw.rectangle((x,ground-78*scale,x+8*scale,ground-20*scale),fill=ivory)
    draw.rectangle((cx-115*scale,ground-150*scale,cx+115*scale,ground-20*scale),fill=ivory)
    draw.rectangle((cx-135*scale,ground-38*scale,cx+135*scale,ground-20*scale),fill=gold)
    draw.ellipse((cx-92*scale,ground-270*scale,cx+92*scale,ground-100*scale),fill=(184,190,174),outline=gold,width=max(1,int(3*scale)))
    draw.rectangle((cx-92*scale,ground-190*scale,cx+92*scale,ground-20*scale),fill=ivory)
    draw.pieslice((cx-92*scale,ground-280*scale,cx+92*scale,ground-100*scale),180,360,fill=(179,190,172),outline=gold,width=max(1,int(3*scale)))
    for side in (-1,1):
        x=cx+side*215*scale
        draw.rectangle((x-13*scale,ground-215*scale,x+13*scale,ground-20*scale),fill=ivory)
        draw.polygon([(x-19*scale,ground-215*scale),(x,ground-250*scale),(x+19*scale,ground-215*scale)],fill=gold)
        draw.ellipse((x-9*scale,ground-257*scale,x+9*scale,ground-239*scale),fill=gold)
    for j in range(7):
        x=cx-72*scale+j*24*scale
        draw.arc((x,ground-125*scale,x+15*scale,ground-82*scale),180,360,fill=shadow,width=max(1,int(3*scale)))

def world_scene(t, w, h):
    local=t
    # Segment-specific palette and scene
    if t<7: kind="dawn"
    elif t<15: kind="city"
    elif t<25: kind="regions"
    elif t<35: kind="mosque"
    else: kind="final"
    palettes={
      "dawn":((15,37,58),(239,163,103)),
      "city":((17,36,55),(91,139,151)),
      "regions":((25,47,67),(120,151,151)),
      "mosque":((27,49,65),(217,167,111)),
      "final":((9,25,42),(70,111,130))}
    top,bottom=palettes[kind]
    im=gradient(top,bottom,w,h).convert("RGBA")
    d=ImageDraw.Draw(im,"RGBA")
    p=(t%7)/7
    # sun and atmospheric haze
    if kind in ("dawn","mosque"):
        sx=int(w*(.76-.12*p)); sy=int(h*.36)
        d.ellipse((sx-64,sy-64,sx+64,sy+64),fill=(255,215,158,175))
        d.ellipse((sx-95,sy-95,sx+95,sy+95),outline=(255,218,171,50),width=5)
    # terrain silhouettes with parallax
    horizon=int(h*(.69 if kind!="city" else .77))
    d.polygon([(0,horizon),(w*.13,h*.53),(w*.29,h*.66),(w*.48,h*.48),(w*.7,h*.65),(w*.87,h*.52),(w,horizon)],fill=(55,83,85,255))
    d.polygon([(0,horizon+35),(w*.22,h*.66),(w*.42,h*.72),(w*.66,h*.62),(w,horizon+15),(w,h)],fill=(31,61,65,255))
    if kind in ("dawn","final","city"):
        skyline(d,horizon,0.95,int((p-.5)*38),night=kind=="final")
    if kind=="dawn":
        # elevated rail and a moving train
        d.line((0,h*.78,w,h*.78),fill=(27,37,42),width=9)
        d.line((0,h*.80,w,h*.80),fill=(170,158,132),width=3)
        draw_train(d,int((p*1.35-.15)*w),int(h*.69),.78)
    elif kind=="city":
        # street perspective
        poly(d,[(w*.35,h),(w*.65,h),(w*.56,h*.77),(w*.46,h*.77)],(43,53,56,255))
        for k in range(7):
            y=h*.8+k*27
            d.line((w*.5-(k*17),y,w*.5+(k*17),y),fill=(184,169,130,100),width=2)
        # traffic and active street
        for i in range(5):
            x=int((i*.23+(p*.22))%1.15*w)-80
            d.rounded_rectangle((x,h*.79-i*17,x+82,h*.79-i*17+27),radius=6,fill=(174+i*8,178,165,255))
            d.rectangle((x+12,h*.79-i*17+4,x+67,h*.79-i*17+13),fill=(39,66,77,255))
        # technology office with Cuk easter egg
        d.rectangle((w*.72,h*.47,w*.94,h*.68),fill=(17,34,46,255),outline=(185,174,137,255),width=4)
        d.rectangle((w*.74,h*.49,w*.92,h*.65),fill=(6,19,29,255))
        for j,line in enumerate(("CUK // BUILD 06","fn world.tick() {","  move(camera, 1);","  render(ecos);","}")):
            d.text((w*.755,h*.505+j*22),line,font=font(max(12,int(w/95))),fill=(119,210,174,255))
        centered(d,(w*.5,h*.14),"A WORLD THAT MOVES",font(int(w*.026),True),(242,231,207,235))
    elif kind=="regions":
        idx=int((t-15)//(10/6))
        # each quick vignette gets its own recognizable landmark
        if idx%4==0: # port
            for i in range(5):
                x=w*.12+i*w*.17
                d.rectangle((x,h*.43,x+75,h*.69),fill=(174,126,82,255))
                d.line((x+15,h*.43,x+15,h*.23),fill=(31,49,57),width=5)
                d.line((x+15,h*.25,x+110,h*.25),fill=(31,49,57),width=5)
            d.rectangle((0,h*.72,w,h),fill=(33,83,103,255))
            for i in range(20): d.line((i*w/20,h*.75,(i*w/20)+40,h*.75),fill=(161,192,187,90),width=2)
            label="PORT DISTRICT"
        elif idx%4==1: # airport
            d.polygon([(w*.1,h*.7),(w*.9,h*.7),(w*.7,h*.57),(w*.3,h*.57)],fill=(61,68,70,255))
            d.line((w*.5,h*.62,w*.5,h*.7),fill=(225,215,183),width=4)
            d.polygon([(w*.43,h*.39),(w*.58,h*.39),(w*.53,h*.44),(w*.48,h*.44)],fill=(225,228,215))
            d.line((w*.5,h*.4,w*.5,h*.22),fill=(225,228,215),width=4)
            label="INTERNATIONAL AIRPORT"
        elif idx%4==2: # industrial
            for i in range(4):
                x=w*.15+i*w*.18
                d.rectangle((x,h*.48,x+110,h*.7),fill=(86,103,99,255))
                d.rectangle((x+18,h*.36,x+38,h*.48),fill=(61,77,75,255))
                d.ellipse((x+10,h*.32,x+46,h*.39),fill=(181,185,169,90))
            label="INDUSTRIAL BELT"
        else:
            for i in range(9):
                x=w*.12+(i%3)*w*.25; y=h*.53+(i//3)*48
                d.polygon([(x,y),(x+42,y-35),(x+84,y)],fill=(177,125,87,255))
                d.rectangle((x+5,y,x+79,y+52),fill=(207,183,143,255))
            label="LIVING SETTLEMENTS"
        centered(d,(w*.5,h*.18),label,font(int(w*.03),True),(243,232,210,245))
        if idx>=4:
            # snow / desert overlay
            if idx%2==0:
                d.polygon([(0,h*.66),(w*.2,h*.34),(w*.38,h*.62),(w*.6,h*.29),(w*.8,h*.61),(w,h*.4),(w,h),(0,h)],fill=(190,207,207,255))
                for i in range(90):
                    x=(i*97)%w; y=(i*53)%int(h*.65)
                    d.ellipse((x,y,x+2,y+2),fill=(255,255,255,190))
                centered(d,(w*.5,h*.13),"THE NORTHERN REACH",font(int(w*.026),True),(255,255,245,255))
            else:
                d.polygon([(0,h*.7),(w*.22,h*.51),(w*.42,h*.69),(w*.66,h*.48),(w,h*.68),(w,h),(0,h)],fill=(181,126,76,255))
                centered(d,(w*.5,h*.13),"THE GREAT DESERT",font(int(w*.026),True),(255,233,199,255))
    elif kind=="mosque":
        mosque(d,w*.5,int(h*.79),.86)
        # people as small scale cues
        for i in range(30):
            x=int(w*.16+(i*71)%(w*.68)); y=int(h*.79+(i%3)*5)
            d.ellipse((x,y-11,x+7,y-4),fill=(29,43,47,255))
            d.line((x+3,y-4,x+3,y+10),fill=(29,43,47,255),width=3)
        centered(d,(w*.5,h*.12),"A PLACE FOR A MILLION STORIES",font(int(w*.026),True),(245,232,208,255))
    else:
        # traveler silhouette in foreground
        x=int(w*(.48+.04*math.sin(t)))
        d.ellipse((x-13,h*.68,x+13,h*.72),fill=(9,17,24,255))
        d.polygon([(x-19,h*.72),(x+19,h*.72),(x+31,h*.92),(x-30,h*.92)],fill=(9,17,24,255))
        d.line((x-12,h*.91,x-20,h),fill=(9,17,24,255),width=11)
        d.line((x+12,h*.91,x+20,h),fill=(9,17,24,255),width=11)
        # road vanishing into the horizon
        poly(d,[(w*.31,h),(w*.69,h),(w*.54,h*.69),(w*.46,h*.69)],(37,48,52,255))
        for i in range(6):
            y=int(h*.72+i*47)
            d.line((w*.5-i*15,y,w*.5+i*15,y),fill=(205,188,148,150),width=3)
        centered(d,(w*.5,h*.18),"",font(20),(255,255,255))
        # end title fades in during final seconds
        fade=smooth((t-38.2)/2.0)
        if fade>0:
            overlay=Image.new("RGBA",(w,h),(0,0,0,0)); od=ImageDraw.Draw(overlay)
            centered(od,(w*.5,h*.27),"ECOS WORLD",font(int(w*.075),True),(248,239,218,int(255*fade)))
            centered(od,(w*.5,h*.37),"One world. Countless places to discover.",font(int(w*.025)),(227,222,207,int(255*fade)))
            im=Image.alpha_composite(im,overlay)
    # subtle cinematic vignette and grain
    arr=np.asarray(im.convert("RGB")).astype(np.float32)
    yy,xx=np.mgrid[0:h,0:w]
    vign=1-0.22*np.clip(((xx-w/2)**2/(w/2)**2+(yy-h/2)**2/(h/2)**2)/2,0,1)
    arr*=vign[...,None]
    rng=np.random.default_rng(int(t*FPS)+41)
    arr+=rng.normal(0,1.6,(h,w,1))
    im=Image.fromarray(np.uint8(np.clip(arr,0,255)),"RGB")
    # scene transitions: 0.45s crossfade-like dip
    edge=min(t%7,7-(t%7))
    if t>0 and edge<.22:
        shade=Image.new("RGB",(w,h),(3,9,16))
        im=Image.blend(im,shade,(1-smooth(edge/.22))*.45)
    return im

def make_audio(path, sample_rate=48000):
    n=int(DURATION*sample_rate)
    t=np.arange(n,dtype=np.float64)/sample_rate
    # Original ambient cinematic pulse: no copyrighted recording or external asset.
    slow=np.sin(2*np.pi*(55+4*np.sin(2*np.pi*.035*t))*t)
    pad=.20*np.sin(2*np.pi*110*t)+.13*np.sin(2*np.pi*164.81*t)+.10*np.sin(2*np.pi*220*t)
    pulse=(np.sin(2*np.pi*82.4*t)+.5*np.sin(2*np.pi*123.5*t))*((np.sin(2*np.pi*.8*t)>0)*.5+.5)
    rise=np.clip(t/8,0,1)*np.clip((45-t)/4,0,1)
    swell=.10*np.sin(2*np.pi*(220+22*np.sin(2*np.pi*.025*t))*t)
    audio=(pad+0.09*pulse+0.07*slow+swell)*rise
    # restrained low-frequency impacts at scene changes
    for hit in (7,15,25,35,42):
        q=t-hit
        audio+=.18*np.exp(-np.maximum(q,0)*8)*np.sin(2*np.pi*48*np.maximum(q,0))*(q>=0)
    audio=np.tanh(audio*1.5)
    stereo=np.stack([audio, np.roll(audio,180)],axis=1)
    pcm=np.int16(np.clip(stereo,-1,1)*32767)
    with wave.open(str(path),"wb") as wf:
        wf.setnchannels(2); wf.setsampwidth(2); wf.setframerate(sample_rate); wf.writeframes(pcm.tobytes())

def render(args):
    out=Path(args.output); out.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory() as td:
        raw=Path(td)/"video.mp4"; audio=Path(td)/"score.wav"
        cmd=["ffmpeg","-y","-loglevel","error","-f","rawvideo","-vcodec","rawvideo","-pix_fmt","rgb24",
             "-s",f"{args.width}x{args.height}","-r",str(args.fps),"-i","-","-an","-c:v","libx264","-preset","ultrafast","-crf","19","-pix_fmt","yuv420p",str(raw)]
        proc=subprocess.Popen(cmd,stdin=subprocess.PIPE)
        try:
            total=int(DURATION*args.fps)
            for frame in range(total):
                t=frame/args.fps
                im=world_scene(t,args.width,args.height)
                proc.stdin.write(im.tobytes())
                if frame%max(1,args.fps*5)==0: print(f"Rendering {t:05.1f}s / {DURATION:.0f}s",flush=True)
            proc.stdin.close()
            if proc.wait()!=0: raise RuntimeError("FFmpeg video encoding failed")
        except Exception:
            proc.kill(); raise
        make_audio(audio)
        subprocess.run(["ffmpeg","-y","-loglevel","error","-i",str(raw),"-i",str(audio),"-c:v","copy","-c:a","aac","-b:a","192k","-shortest","-movflags","+faststart",str(out)],check=True)
    print(f"Created {out} ({DURATION:.0f}s)")

if __name__=="__main__":
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--output",default="ecos-world-trailer.mp4")
    ap.add_argument("--fps",type=int,default=FPS)
    ap.add_argument("--width",type=int,default=W)
    ap.add_argument("--height",type=int,default=H)
    a=ap.parse_args()
    if a.fps<1 or a.width<320 or a.height<180: ap.error("Use fps >= 1 and dimensions >= 320x180")
    render(a)
