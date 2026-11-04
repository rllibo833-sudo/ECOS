import bpy, math, random, sys, os
from mathutils import Vector
from math import sin, cos, pi

# ECOS World cinematic scene builder. Canon-safe visual interpretation:
# Arunika = dense/industrial; Wanasera = research/technology;
# Tirta Nusa = scenic/coastal; Giriwana = frontier/forest/highlands.
# Undocumented architecture, flags, emblems and exact geography are intentionally omitted.

def arg(name, default):
    argv=sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else []
    return argv[argv.index(name)+1] if name in argv else default
OUT=arg("--output","ecos-world-trailer.mp4")
FPS=int(arg("--fps","24")); W=int(arg("--width","1280")); H=int(arg("--height","720"))
random.seed(24)
scene=bpy.context.scene
scene.render.engine='BLENDER_EEVEE'
scene.render.resolution_x=W; scene.render.resolution_y=H; scene.render.resolution_percentage=100
scene.render.fps=FPS; scene.frame_start=1; scene.frame_end=45*FPS
scene.render.image_settings.file_format='FFMPEG'
scene.render.ffmpeg.format='MPEG4'; scene.render.ffmpeg.codec='H264'
scene.render.ffmpeg.constant_rate_factor='MEDIUM'; scene.render.ffmpeg.audio_codec='NONE'
scene.render.filepath=os.path.abspath(OUT)
scene.world.color=(0.025,0.04,0.065)
scene.view_settings.view_transform='AgX'
scene.render.film_transparent=False

def mat(name,color,metal=0,rough=.7,emission=None):
    m=bpy.data.materials.new(name); m.diffuse_color=(*color,1); m.use_nodes=True
    bs=m.node_tree.nodes.get("Principled BSDF")
    bs.inputs["Base Color"].default_value=(*color,1)
    bs.inputs["Metallic"].default_value=metal; bs.inputs["Roughness"].default_value=rough
    if emission:
        bs.inputs["Emission Color"].default_value=(*color,1); bs.inputs["Emission Strength"].default_value=emission
    return m
M={
 "earth":mat("deep green earth",(.075,.16,.12)),
 "grass":mat("meadow",(.16,.29,.17)),
 "forest":mat("forest canopy",(.025,.12,.075)),
 "rock":mat("mountain stone",(.22,.27,.29)),
 "snow":mat("highland snow",(.72,.79,.8)),
 "water":mat("coastal water",(.025,.16,.22),.35,.22),
 "road":mat("asphalt",(.055,.07,.08),.1,.85),
 "concrete":mat("warm concrete",(.48,.48,.42)),
 "glass":mat("blue glass",(.035,.13,.19),.45,.2),
 "metal":mat("brushed metal",(.28,.34,.35),.7,.3),
 "light":mat("warm windows",(.95,.58,.2),.15,.3,2.0),
 "gold":mat("architectural brass",(.62,.43,.18),.65,.25),
 "ivory":mat("mosque limestone",(.78,.74,.62),.1,.42),
 "roof":mat("deep terracotta",(.29,.12,.075),.1,.6),
 "white":mat("clean ceramic",(.75,.79,.75),.1,.3),
 "cyan":mat("interface light",(.08,.65,.72),.2,.25,1.5),
}
def assign(obj, material):
    if material: obj.data.materials.append(material)
    return obj
def cube(name,loc,scale,material,bevel=.08):
    bpy.ops.mesh.primitive_cube_add(size=1,location=loc); o=bpy.context.object; o.name=name
    o.dimensions=scale; bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    assign(o,material)
    if bevel:
        mod=o.modifiers.new("soft architectural edges","BEVEL"); mod.width=bevel; mod.segments=3
        o.modifiers.new("weighted corner normals","WEIGHTED_NORMAL")
    return o
def uv(name,loc,scale,material,segments=24,rings=12):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments,ring_count=rings,radius=1,location=loc)
    o=bpy.context.object; o.name=name; o.scale=scale; assign(o,material)
    bpy.ops.object.shade_smooth(); return o
def cyl(name,loc,radius,depth,material,vertices=24):
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices,radius=radius,depth=depth,location=loc)
    o=bpy.context.object; o.name=name; assign(o,material)
    mod=o.modifiers.new("edge bevel","BEVEL"); mod.width=.035; mod.segments=2
    o.modifiers.new("weighted normals","WEIGHTED_NORMAL"); return o
def cone(name,loc,r1,r2,depth,material,vertices=32):
    bpy.ops.mesh.primitive_cone_add(vertices=vertices,radius1=r1,radius2=r2,depth=depth,location=loc)
    o=bpy.context.object; o.name=name; assign(o,material); return o
def text_obj(body,loc,size,material,rot=(pi/2,0,0),align='CENTER'):
    cu=bpy.data.curves.new("type_"+body[:12],"FONT"); cu.body=body; cu.size=size; cu.align_x=align
    cu.extrude=.001; ob=bpy.data.objects.new(body,cu); scene.collection.objects.link(ob); ob.location=loc; ob.rotation_euler=rot; assign(ob,material); return ob
def look(obj,pt): obj.rotation_euler=(Vector(pt)-obj.location).to_track_quat('-Z','Y').to_euler()
def track(obj, frame, loc, target):
    obj.location=loc; look(obj,target); obj.keyframe_insert(data_path="location",frame=frame); obj.keyframe_insert(data_path="rotation_euler",frame=frame)

# Terrain mesh with smooth rolling relief; broad enough for a cinematic fly-through.
def terrain(name,cx,cy,size,material,seed=0,rough=.5):
    random.seed(seed); n=50; verts=[]; faces=[]
    for j in range(n+1):
        y=(j/n-.5)*size
        for i in range(n+1):
            x=(i/n-.5)*size
            z=.10*sin(x*.22+seed)+.12*cos(y*.19)+.08*sin((x+y)*.33)
            z+=rough*(.22*sin(x*.07)*cos(y*.08)+.12*cos(x*.16+y*.1))
            verts.append((cx+x,cy+y,z))
    for j in range(n):
        for i in range(n):
            a=j*(n+1)+i; faces.append((a,a+1,a+n+2,a+n+1))
    mesh=bpy.data.meshes.new(name); mesh.from_pydata(verts,[],faces); mesh.materials.append(material)
    ob=bpy.data.objects.new(name,mesh); scene.collection.objects.link(ob)
    for p in mesh.polygons:p.use_smooth=True
    sol=ob.modifiers.new("terrain depth","SOLIDIFY"); sol.thickness=.5
    return ob

# Lighting: warm low sun, cool sky fill and atmospheric volume.
world=bpy.data.worlds.new("ECOS atmospheric sky"); scene.world=world; world.use_nodes=True
bg=world.node_tree.nodes.get("Background"); bg.inputs[0].default_value=(.12,.19,.28,1); bg.inputs[1].default_value=.45
bpy.ops.object.light_add(type='SUN',location=(0,0,18)); sun=bpy.context.object; sun.name="low cinematic sun"; sun.data.energy=2.2; sun.rotation_euler=(math.radians(28),math.radians(-22),math.radians(-28))
bpy.ops.object.light_add(type='AREA',location=(0,-12,16)); fill=bpy.context.object; fill.data.energy=1700; fill.data.shape='DISK'; fill.data.size=18; look(fill,(0,0,0))
# visible sun disc
sunmat=mat("sun disc",(1,.63,.31),0,1,3.5); uv("sun",(-20,48,28),(5,5,5),sunmat,48,24)

# Coastal / mountain world strip.
terrain("rolling ECOS terrain",0,10,150,M["earth"],5,.8)
# distant mountain chains
for i in range(15):
    x=-65+i*9; y=48+random.uniform(-4,8); z=random.uniform(7,13)
    o=cone("distant mountain",(x,y,z),random.uniform(6,10),.2,random.uniform(14,24),M["rock"],8)
    o.scale.y=random.uniform(1.2,2.4)
# water bay at left of travel corridor
cube("coastal sea",(-58,18,-1.3),(48,95,.7),M["water"],0)
# trees with varied silhouettes
def tree(x,y,s=1,kind=0):
    cyl("tree trunk",(x,y,1.1*s),.22*s,2.2*s,M["roof"],10)
    if kind%2:
        for k in range(3):
            cone("layered conifer",(x,y,(2.1+k*.75)*s),1.45*s*(1-k*.22),0,1.5*s,M["forest"],9)
    else:
        uv("broadleaf crown",(x,y,3*s),(1.45*s,1.2*s,1.55*s),M["forest"],12,8)
        uv("foliage highlight",(x+.35*s,y-.12*s,3.25*s),(1.0*s,.85*s,1.05*s),M["grass"],12,8)
for i in range(130):
    x=random.uniform(-72,72); y=random.uniform(18,62)
    if abs(x)<17 and y<37: continue
    tree(x,y,random.uniform(.55,1.3),i)

# Region 1 — Arunika / Nawasena: dense urban-industrial skyline.
terrain("Arunika urban plateau",0,-24,82,M["earth"],8,.25)
# road grid
for y in (-49,-35,-21,-7):
    cube("urban avenue",(0,y,.12),(80,2.6,.22),M["road"],.04)
for x in (-32,-16,0,16,32):
    cube("cross street",(x,-28,.13),(2.3,52,.24),M["road"],.04)
# skyscrapers with varied silhouettes and facade rhythm
for i in range(68):
    x=random.uniform(-37,37); y=random.uniform(-48,-5)
    if abs(x)<5 and y<-17: continue
    h=random.uniform(5,22); w=random.uniform(2.2,5.2); dep=random.uniform(2.5,5)
    b=cube("Arunika city tower",(x,y,h/2),(w,dep,h),random.choice([M["concrete"],M["glass"],M["metal"]]),.16)
    if i%5==0:
        cyl("tower crown",(x,y,h+.6),w*.34,1.2,M["metal"],16)
    # visible window bands on camera-facing side
    for z in range(2,int(h),3):
        cube("window strip",(x,y-dep/2-.025,z),(w*.76,.045,.75),M["light"],.01)
# industrial hall, smokestacks
for i in range(4):
    x=-31+i*7
    cube("industrial hall",(x,-2,2.2),(6,8,4.4),M["metal"],.12)
    cyl("factory stack",(x+1,-1,7),.55,5,M["concrete"],16)
# transit viaduct and train
cube("elevated rail deck",(0,-13,5),(70,2.2,.6),M["metal"],.08)
for x in range(-32,34,8): cube("rail support",(x,-13,2.5),(1,1,5),M["concrete"],.08)
for i in range(5):
    car=cube("regional train carriage",(-18+i*7,-13,6.5),(6.5,1.7,1.8),M["white"],.22)
    cube("train window band",(-18+i*7,-13.88,6.7),(5.4,.05,.62),M["glass"],.03)

# Region 2 — Wanasera: research campus and knowledge infrastructure.
terrain("Wanasera green research grounds",0,83,82,M["grass"],13,.25)
for i in range(9):
    x=-30+(i%3)*30; y=68+(i//3)*14
    cube("research institute",(x,y,3),(17,10,6),M["white"],.4)
    cube("glass facade",(x,y-5.08,3),(12,.08,4.2),M["glass"],.04)
    # rooftop solar / antenna equipment
    for k in range(3): cube("solar panel",(x-5+k*4,y,6.25),(3,3,.12),M["metal"],.03)
    cyl("research antenna",(x+6,y,8),.12,4,M["metal"],12)
# data/knowledge visualization sculpture (abstract network, not magic)
nodes=[(-13,90,7),(-5,96,9),(4,89,8),(13,96,10),(0,105,12)]
for p in nodes: uv("knowledge node",p,(.75,.75,.75),M["cyan"],16,8)
for a,b in ((0,1),(1,2),(2,3),(1,4),(4,3),(0,4)):
    bpy.ops.mesh.primitive_cylinder_add(vertices=8,radius=.07,depth=(Vector(nodes[a])-Vector(nodes[b])).length,location=(Vector(nodes[a])+Vector(nodes[b]))/2)
    o=bpy.context.object; o.name="knowledge graph link"; o.rotation_euler=(Vector(nodes[b])-Vector(nodes[a])).to_track_quat('Z','Y').to_euler(); assign(o,M["metal"])

# Region 3 — Tirta Nusa: coast, islands, tourism and scenic landscape.
terrain("Tirta Nusa coastal island",0,168,92,M["grass"],18,.65)
cube("Tirta Nusa lagoon",(0,167,-.6),(90,65,.45),M["water"],0)
# coast villas / low-rise waterfront, winding promenade
for i in range(12):
    x=-38+i*7; y=145+random.uniform(-5,5)
    cube("coastal lodging",(x,y,2.1),(5,5,4.2),M["ivory"],.2)
    cone("terracotta roof",(x,y,4.8),3.5,0,1.6,M["roof"],4)
# palms
def palm(x,y,s):
    cyl("palm trunk",(x,y,2*s),.18*s,4*s,M["roof"],10)
    for a in range(7):
        ang=a*2*pi/7
        leaf=uv("palm frond",(x+cos(ang)*1.3*s,y+sin(ang)*1.3*s,4.15*s),(1.5*s,.25*s,.16*s),M["grass"],12,6)
        leaf.rotation_euler[2]=ang
for i in range(30): palm(random.uniform(-43,43),random.uniform(145,188),random.uniform(.7,1.2))

# Region 4 — Giriwana: frontier highlands and deep forest.
terrain("Giriwana frontier highlands",0,248,100,M["earth"],22,1.1)
for i in range(25):
    x=random.uniform(-47,47); y=random.uniform(211,282)
    tree(x,y,random.uniform(.9,1.8),i+1)
for i in range(9):
    x=-43+i*11; y=272+random.uniform(-4,5)
    cone("Giriwana volcanic ridge",(x,y,random.uniform(7,12)),random.uniform(6,10),.2,random.uniform(15,23),M["rock"],9)
# snow-capped peak cluster
for x,y,z in [(-25,287,16),(0,292,21),(24,286,17)]:
    cone("snow mountain",(x,y,z),9,.2,z*1.8,M["rock"],9)
    cone("snow cap",(x,y,z+z*.35),5,.05,z*.85,M["snow"],9)

# Juailin: rural settlement and working fields, attached to connected transport.
terrain("Juailin rural land",0,-106,100,M["grass"],30,.3)
for i in range(12):
    x=-42+(i%6)*16; y=-126+(i//6)*15
    cube("Juailin farmhouse",(x,y,1.6),(6,5,3.2),M["ivory"],.16)
    cone("farmhouse roof",(x,y,3.7),4.3,0,1.8,M["roof"],4)
# agricultural rows
for k in range(18):
    x=-43+k*5
    cube("cultivated field row",(x,-99,.16),(.6,27,.3),M["forest"],.04)
# livestock as modest silhouettes
for i in range(9):
    x=-26+(i%5)*3.4; y=-115+(i//5)*5
    uv("livestock body",(x,y,.8),(1.05,.48,.55),M["white"],12,8)
    uv("livestock head",(x+.78,y,.93),(.36,.35,.42),M["white"],12,8)

# Mosque complex — one central mosque within a broad campus, not a 1000-ha single building.
terrain("mosque district campus",0,-183,116,M["grass"],38,.15)
# campus roads and neighborhood blocks
for y in (-225,-205,-160,-141): cube("mosque district boulevard",(0,y,.13),(110,2.8,.24),M["road"],.04)
for x in (-47,-29,29,47): cube("district access road",(x,-183,.14),(2.5,90,.24),M["road"],.04)
# mosque central building
cube("mosque prayer hall",(0,-183,5),(31,22,10),M["ivory"],.4)
# arched facade recesses
for x in (-11,-5.5,0,5.5,11):
    cube("mosque entrance shadow",(x,-194.15,4.5),(2.5,.08,5.8),M["glass"],.05)
    # arch above door
    uv("arch crown",(x,-194.12,7.4),(1.25,.08,1.25),M["gold"],20,10)
# large central dome
uv("main mosque dome",(0,-183,12),(10,10,6),M["ivory"],40,20)
cone("dome finial",(0,-183,18.7),.55,0,1.8,M["gold"],16)
# four smaller domes
for x in (-13,13):
    uv("side dome",(x,-183,10),(3.3,3.3,2.3),M["gold"],24,12)
# minarets
for x in (-20,20):
    cyl("mosque minaret shaft",(x,-183,9),1.25,18,M["ivory"],24)
    cyl("minaret balcony",(x,-183,16),1.8,.55,M["gold"],24)
    cone("minaret spire",(x,-183,19),1.5,0,4,M["gold"],24)
# campus blocks, gardens, services
for i in range(24):
    x=random.uniform(-49,49); y=random.uniform(-222,-144)
    if abs(x)<25 and -202<y<-164: continue
    cube("district home and service",(x,y,1.5),(random.uniform(4,8),random.uniform(4,7),3),random.choice([M["ivory"],M["concrete"],M["white"]]),.14)
for i in range(40):
    x=random.uniform(-53,53); y=random.uniform(-226,-140)
    if abs(x)<22 and -201<y<-164: continue
    uv("campus tree",(x,y,1.7),(1.1,1.1,1.6),M["forest"],12,8)
# eight village markers as neutral low-key neighborhood clusters, no fabricated architectural style
for i,name in enumerate(["JUAILIN","MERAVIN","TALVEN","ORDEL","SENVARA","KALVEN","DARSEN","ELVARA"]):
    x=-42+(i%4)*28; y=-235-(i//4)*9
    text_obj(name,(x,y,1.1),1.0,M["ivory"])

# Subtle living infrastructure: streetlights, public info displays, moving vehicles.
for x in range(-40,45,10):
    cyl("streetlight pole",(x,-204,3),.09,6,M["metal"],10)
    uv("streetlight lamp",(x,-204,6.1),(.35,.35,.2),M["light"],12,8)
for x in (-18,18):
    cube("public information display",(x,-198,3.8),(3.5,.35,2.4),M["glass"],.08)
    cube("display glow",(x,-198.2,3.8),(2.8,.05,1.7),M["cyan"],.02)

# Camera rig travels through a continuous route: Juailin -> Arunika -> Wanasera -> Tirta Nusa -> Giriwana -> mosque -> world reveal.
bpy.ops.object.camera_add(location=(0,-145,19)); cam=bpy.context.object; cam.name="ECOS cinematic camera"; scene.camera=cam; cam.data.lens=38
cam.data.dof.use_dof=True; cam.data.dof.aperture_fstop=5.6
focus=bpy.data.objects.new("camera focus",None); scene.collection.objects.link(focus); cam.data.dof.focus_object=focus
def shot(frame,loc,target):
    cam.location=loc; look(cam,target); cam.keyframe_insert(data_path="location",frame=frame); cam.keyframe_insert(data_path="rotation_euler",frame=frame)
    focus.location=target; focus.keyframe_insert(data_path="location",frame=frame)
# 0-7 rural morning and connected train route
shot(1,(4,-142,13),(0,-115,2)); shot(168,(-7,-105,16),(0,-30,8))
# 7-15 city reveal and Cuk-like tech presence without inventing syntax
shot(169,(-17,-18,18),(0,-25,8)); shot(360,(13,-8,22),(0,-28,8))
# 15-25 fly through four regions
shot(361,(-25,44,23),(0,78,4)); shot(600,(0,284,35),(0,245,5))
# 25-35 mosque complex / eight settlements
shot(601,(0,-255,42),(0,-183,4)); shot(840,(36,-234,34),(0,-183,6))
# 35-45 pullback to horizon/world scale
shot(841,(0,-208,24),(0,-160,3)); shot(1080,(0,-330,145),(0,25,0))
for obj in (cam,focus):
    if obj.animation_data and obj.animation_data.action:
        for fc in obj.animation_data.action.fcurves:
            for kp in fc.keyframe_points: kp.interpolation='BEZIER'

# Title cards are camera-facing planes parented to camera, timed for final reveal.
def camera_text(body, frame_start, frame_end, y, size, material):
    cu=bpy.data.curves.new("title_"+body[:10],"FONT"); cu.body=body; cu.size=size; cu.align_x='CENTER'; cu.extrude=.001
    ob=bpy.data.objects.new(body,cu); scene.collection.objects.link(ob); ob.parent=cam; ob.location=(0,y,-12); ob.rotation_euler=(0,0,0); assign(ob,material)
    ob.hide_render=True; ob.keyframe_insert(data_path="hide_render",frame=frame_start-1)
    ob.hide_render=False; ob.keyframe_insert(data_path="hide_render",frame=frame_start)
    ob.hide_render=False; ob.keyframe_insert(data_path="hide_render",frame=frame_end)
    ob.hide_render=True; ob.keyframe_insert(data_path="hide_render",frame=frame_end+1)
camera_text("ECOS WORLD",900,1080,-.8,1.25,M["ivory"])
camera_text("ONE WORLD. COUNTLESS PLACES TO DISCOVER.",930,1080,-1.65,.28,M["white"])

# Gentle cinematic contrast and bloom.
scene.use_nodes=True; nt=scene.node_tree; nt.nodes.clear()
rl=nt.nodes.new("CompositorNodeRLayers"); glare=nt.nodes.new("CompositorNodeGlare"); glare.glare_type='FOG_GLOW'; glare.quality='MEDIUM'; glare.threshold=1.5
comp=nt.nodes.new("CompositorNodeComposite"); nt.links.new(rl.outputs["Image"],glare.inputs["Image"]); nt.links.new(glare.outputs["Image"],comp.inputs["Image"])
# Slightly soft focus at distance; motion remains smooth from camera animation.
scene.camera.data.lens=38
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath("ecos-world.blend"))
bpy.ops.render.render(animation=True)
