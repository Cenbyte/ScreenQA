"""Prepare immersive assets from FIVE standalone posters, preserving every label and subject.

Usage: python tools/prepare_reasoning_art.py SOURCE_DIRECTORY [PREVIEW_DIRECTORY]
No five-panel input, subject cutout, title removal or character reconstruction is used.
"""
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageOps, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/main/res/drawable-nodpi'
FILES = ['e4118885e51481dea33e85c2fc4ad7a3.jpg',
         '5844aa43f1d1678fa995a558fdb9418b.png',
         '59f9ed2e84e59f6421543cf7216d6f2f.jpg',
         'ca830899c8bd15ea4b6555dd7f7edad0.jpg',
         '4b7e78f9c194c539a5e7b28104a20611.png']
ART_SIZE = (416, 832)
AMBIENT_SIZE = (512, 320)

def smooth(x):
    x = np.clip(x, 0, 1)
    return x*x*(3-2*x)

def main():
    origin = Path(sys.argv[1]); OUT.mkdir(parents=True, exist_ok=True)
    previews = []
    for index, name in enumerate(FILES):
        source = Image.open(origin/name).convert('RGB')
        poster = ImageOps.contain(source, ART_SIZE, Image.Resampling.LANCZOS)
        art = Image.new('RGBA', ART_SIZE)
        art.paste(poster, ((ART_SIZE[0]-poster.width)//2,(ART_SIZE[1]-poster.height)//2))
        data = np.array(art)
        yy,xx = np.mgrid[:ART_SIZE[1],:ART_SIZE[0]]
        # Horizontal scene-to-scene blend only: no white fill or oval silhouette.
        data[:,:,3] = (data[:,:,3]*smooth(xx/145)).astype(np.uint8)
        art = Image.fromarray(data)
        art.save(OUT/f'reasoning_wattson_{index+1}.webp', lossless=True)
        # Extend environmental side bands, rather than an enlarged face behind the controls.
        w,h=source.size
        left=source.crop((0,round(h*.18),round(w*.21),round(h*.96))).resize((128,80),Image.Resampling.LANCZOS)
        right=source.crop((round(w*.79),round(h*.18),w,round(h*.96))).resize((128,80),Image.Resampling.LANCZOS)
        mix=np.linspace(.12,.88,128)[None,:,None]
        field=np.asarray(left)*(1-mix)+np.asarray(right)*mix
        ambient=Image.fromarray(field.astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.8))
        ambient=ambient.resize(AMBIENT_SIZE,Image.Resampling.BICUBIC)
        ambient.save(OUT/f'reasoning_ambient_{index+1}.webp', quality=88)
        if index == 4:
            rgb = np.asarray(source).astype(float)
            h,w = rgb.shape[:2]; sy,sx=np.mgrid[:h,:w]
            # Bright red halo above the helmet: never the header or fifth character.
            ring = smooth((rgb[:,:,0]-np.maximum(rgb[:,:,1],rgb[:,:,2])*.7-45)/75)
            ring *= smooth((rgb[:,:,0]-145)/95)
            ring *= smooth((sy/h-.188)/.016)*(1-smooth((sy/h-.355)/.018))
            aura = source.convert('RGBA');aura.putalpha(Image.fromarray((ring*255).astype(np.uint8)))
            aura = ImageOps.contain(aura,ART_SIZE,Image.Resampling.LANCZOS)
            framed = Image.new('RGBA',ART_SIZE)
            framed.alpha_composite(aura,((ART_SIZE[0]-aura.width)//2,(ART_SIZE[1]-aura.height)//2))
            aura_data=np.array(framed);aura_data[:,:,3]=(aura_data[:,:,3]*smooth(xx/145)).astype(np.uint8)
            Image.fromarray(aura_data).save(OUT/'reasoning_wattson_aura.webp',lossless=True)
        previews.append(art)
    if len(sys.argv)>2:
        target=Path(sys.argv[2]);target.mkdir(parents=True,exist_ok=True)
        sheet=Image.new('RGB',(ART_SIZE[0]*5,ART_SIZE[1]),'#1e2636')
        for i,art in enumerate(previews):sheet.paste(art,(ART_SIZE[0]*i,0),art)
        sheet.save(target/'new-posters-preview.jpg')

if __name__ == '__main__': main()
