"""Normalize the supplied sheets offline; Android decodes only two small atlases."""
import argparse
import json
from pathlib import Path
from PIL import Image
from PIL import ImageFilter
import numpy as np

def clean_cell(cell):
    pixels=np.array(cell); todo=pixels[:,:,3]>32; height,width=todo.shape; components=[]
    for yy,xx in zip(*np.nonzero(todo)):
        if not todo[yy,xx]: continue
        stack=[(int(xx),int(yy))];todo[yy,xx]=False;points=[]
        while stack:
            x,y=stack.pop();points.append((x,y))
            for nx,ny in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
                if 0<=nx<width and 0<=ny<height and todo[ny,nx]:
                    todo[ny,nx]=False;stack.append((nx,ny))
        components.append(points)
    main=max(components,key=len);keep=np.zeros((height,width),dtype=np.uint8)
    for points in components:
        x,y=np.array(points).T;rgb=pixels[y,x,:3].astype(int)
        decoration=((rgb[:,0]>150)&(rgb[:,0]>rgb[:,2]*1.3)).mean()>0.3
        if points is main or (len(points)>=45 and decoration and x.min()>1 and x.max()<width-2):keep[y,x]=255
    mask=Image.fromarray(keep).filter(ImageFilter.MaxFilter(3))
    pixels[:,:,3]=np.minimum(pixels[:,:,3],np.array(mask))
    return Image.fromarray(pixels)

parser = argparse.ArgumentParser()
parser.add_argument("idle", type=Path)
parser.add_argument("tap", type=Path)
parser.add_argument("output", type=Path)
parser.add_argument("preview", type=Path)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
args.preview.mkdir(parents=True, exist_ok=True)
size = 192
report = {}
for name, path, columns in (("idle", args.idle, 6), ("tap", args.tap, 8)):
    sheet = Image.open(path).convert("RGBA")
    assert sheet.size == (2172, 724), sheet.size
    # TAP art is unevenly spaced, so uniform 1/8 crops would cut the first two sprites.
    edges = [[0,298,566,830,1083,1364,1635,1898,2172],
             [0,300,575,847,1117,1380,1645,1900,2172]] if name == "tap" else [list(range(0,2173,362))] * 2
    atlas = Image.new("RGBA", (columns * size, 2 * size))
    bounds = []
    for row in range(2):
        for col in range(columns):
            cell = clean_cell(sheet.crop((edges[row][col], row*362, edges[row][col+1], (row+1)*362)))
            # A central body band excludes floating hearts/top artifacts from the scale anchor.
            alpha = cell.getchannel("A")
            anchor = alpha.crop((0, 28 if name == "tap" else 7, cell.width, 350 if name == "tap" else 355))
            box = anchor.point(lambda a: 255 if a > 100 else 0).getbbox()
            if box is None: raise ValueError("Empty sprite")
            x0,y0,x1,y1=box
            y0 += 28 if name == "tap" else 7
            y1 += 28 if name == "tap" else 7
            # Main sitting baseline is constant within each row; decorative fragments don't move it.
            baseline = (344 if row == 0 else 328) if name == "tap" else 354
            scale = 164 / (baseline-y0)
            if scale * cell.width > 188: scale = 188 / cell.width
            resized = cell.resize((round(cell.width*scale),round(cell.height*scale)),Image.Resampling.LANCZOS)
            frame = Image.new("RGBA", (size,size))
            x = round(size/2 - (x0+x1)/2*scale)
            y = round(184-baseline*scale)
            frame.alpha_composite(resized,(x,y))
            # Keep a fully clear gutter, including resampling's nearly invisible edge pixels.
            pixels=np.array(frame);pixels[:4,:,:]=0;pixels[-4:,:,:]=0;pixels[:,:4,:]=0;pixels[:,-4:,:]=0
            frame=Image.fromarray(pixels)
            atlas.alpha_composite(frame,(col*size,row*size))
            frame.save(args.preview / f"{name}_{row*columns+col:02d}.png")
            bounds.append({"frame":row*columns+col,"anchor":box,"scale":round(scale,4),"offset":[x,y],"bounds":frame.getbbox()})
    atlas.save(args.output / f"wattson_{name}_atlas.png",optimize=True)
    check = Image.new("RGBA", atlas.size, (240,240,240,255))
    check.alpha_composite(atlas)
    check.convert("RGB").save(args.preview / f"{name}_contact.jpg",quality=95)
    report[name]={"frames":columns*2,"frameSize":size,"bounds":bounds}
(args.preview/"normalization.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
print("Prepared 12 idle and 16 tap frames, transparent 192px atlases.")
