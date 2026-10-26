"""Split one car out of the Sketchfab "Generic passenger car pack" glTF into a standalone GLB.

Bakes node transforms into vertices, centers the car on the origin (wheels on y=0), scales it to a
real-world length, assigns the four nearest wheels, and re-tints the paint texture.

usage: python -I export_car.py <scene.gltf> <body node name> <paint hex> <out.glb> [length_m]
"""
import io
import json
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image

src, body_name, paint_hex, out_path = sys.argv[1:5]
target_length = float(sys.argv[5]) if len(sys.argv) > 5 else 4.6
root = Path(src).parent
g = json.loads(Path(src).read_text(encoding="utf-8"))
bin_data = (root / g["buffers"][0]["uri"]).read_bytes()
nodes = g["nodes"]

COMP = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}


def read_accessor(i):
    a = g["accessors"][i]
    bv = g["bufferViews"][a["bufferView"]]
    dtype = np.dtype(COMP[a["componentType"]])
    n = NCOMP[a["type"]]
    offset = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
    item = dtype.itemsize * n
    stride = bv.get("byteStride", item)
    if stride == item:
        return np.frombuffer(bin_data, dtype=dtype, count=a["count"] * n, offset=offset).reshape(a["count"], n).copy()
    rows = [np.frombuffer(bin_data, dtype=dtype, count=n, offset=offset + k * stride) for k in range(a["count"])]
    return np.array(rows)


def local_matrix(n):
    if "matrix" in n:
        return np.array(n["matrix"], dtype=np.float64).reshape(4, 4).T
    m = np.eye(4)
    if "scale" in n:
        m = np.diag(list(n["scale"]) + [1.0]) @ m
    if "rotation" in n:
        x, y, z, w = n["rotation"]
        r = np.array([
            [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0],
            [0, 0, 0, 1],
        ])
        m = r @ m
    if "translation" in n:
        t = np.eye(4)
        t[:3, 3] = n["translation"]
        m = t @ m
    return m


world = {}


def walk(i, parent):
    w = parent @ local_matrix(nodes[i])
    world[i] = w
    for c in nodes[i].get("children", []):
        walk(c, w)


for r in g["scenes"][g.get("scene", 0)]["nodes"]:
    walk(r, np.eye(4))


def primitives_under(i):
    out = []
    n = nodes[i]
    if "mesh" in n:
        for p in g["meshes"][n["mesh"]]["primitives"]:
            out.append((world[i], p))
    for c in n.get("children", []):
        out.extend(primitives_under(c))
    return out


def center_of(i):
    pts = []
    for m, p in primitives_under(i):
        a = g["accessors"][p["attributes"]["POSITION"]]
        lo, hi = np.array(a["min"]), np.array(a["max"])
        pts.append((m @ np.append((lo + hi) / 2, 1))[:3])
    return np.mean(pts, axis=0)


top = nodes[[i for i, n in enumerate(nodes) if n.get("name") == "RootNode"][0]]["children"]
bodies = [i for i in top if "body" in nodes[i].get("name", "").lower()]
wheels = [i for i in top if nodes[i].get("name", "").startswith("Wheel_")]
body = [i for i in bodies if nodes[i]["name"].lower() == body_name.lower()][0]
bc = center_of(body)
wheels.sort(key=lambda w: np.linalg.norm((center_of(w) - bc)[[0, 2]]))
parts = [body] + wheels[:4]

# ---- gather geometry in world space
prims = []
for part in parts:
    for m, p in primitives_under(part):
        pos = read_accessor(p["attributes"]["POSITION"]).astype(np.float64)
        pos = (np.c_[pos, np.ones(len(pos))] @ m.T)[:, :3]
        nrm = None
        if "NORMAL" in p["attributes"]:
            nrm = read_accessor(p["attributes"]["NORMAL"]).astype(np.float64) @ np.linalg.inv(m[:3, :3])
            nrm /= np.linalg.norm(nrm, axis=1, keepdims=True) + 1e-12
        uv = read_accessor(p["attributes"]["TEXCOORD_0"]) if "TEXCOORD_0" in p["attributes"] else None
        idx = read_accessor(p["indices"]).reshape(-1) if "indices" in p else np.arange(len(pos))
        prims.append({"pos": pos, "nrm": nrm, "uv": uv, "idx": idx, "material": p["material"]})

# The pack arranges cars in a circle, so each car is yawed differently. Align the long axis
# (principal component in the ground plane) with +X.
xz = np.vstack([q["pos"] for q in prims])[:, [0, 2]]
xz = xz - xz.mean(0)
evals, evecs = np.linalg.eigh(np.cov(xz.T))
major = evecs[:, np.argmax(evals)]
yaw = np.arctan2(major[1], major[0])
c, s_ = np.cos(-yaw), np.sin(-yaw)
rot = np.array([[c, 0, -s_], [0, 1, 0], [s_, 0, c]])
for q in prims:
    q["pos"] = q["pos"] @ rot.T
    if q["nrm"] is not None:
        q["nrm"] = q["nrm"] @ rot.T
if len(sys.argv) > 6 and sys.argv[6] == "flip":  # turn the car around so the front faces +X
    for q in prims:
        q["pos"][:, [0, 2]] *= -1
        if q["nrm"] is not None:
            q["nrm"][:, [0, 2]] *= -1

allpos = np.vstack([q["pos"] for q in prims])
lo, hi = allpos.min(0), allpos.max(0)
size = hi - lo
up = int(np.argmin(size))          # the thinnest extent of a car is its height... unless it is the width
length_axis = int(np.argmax(size))
# Height is the axis with the smallest range among non-length axes only if wheels sit at its minimum;
# for this pack Y is up after the root transforms, so trust Y and just report.
print(f"[{body_name}] extents x={size[0]:.3f} y={size[1]:.3f} z={size[2]:.3f} (length axis {length_axis})")
scale = target_length / size[length_axis]
center = (lo + hi) / 2
for q in prims:
    q["pos"] = (q["pos"] - [center[0], lo[1], center[2]]) * scale

# ---- materials and textures
paint = tuple(int(paint_hex.lstrip("#")[k:k + 2], 16) / 255 for k in (0, 2, 4))
used = sorted({q["material"] for q in prims})
mat_map = {m: k for k, m in enumerate(used)}
images, image_index, textures = [], {}, []


def add_image(uri, tint):
    key = (uri, tint)
    if key in image_index:
        return image_index[key]
    im = Image.open(root / uri).convert("RGB")
    im = im.resize((1024, 1024), Image.LANCZOS) if im.width > 1024 else im
    if tint:
        a = np.asarray(im).astype(np.float32) / 255
        lum = a @ np.array([0.299, 0.587, 0.114])
        sat = a.max(-1) - a.min(-1)
        # Chrome, glass trim and lamps are bright and grey; painted panels are either dark (black
        # cars) or saturated (colored cars). Only the paint is replaced.
        chrome = (np.clip((lum - 0.45) / 0.2, 0, 1) * np.clip((0.18 - sat) / 0.1, 0, 1))[..., None]
        paint_mask = chrome[..., 0] < 0.5
        ref = np.median(lum[paint_mask & (lum > 0.03)]) if paint_mask.any() else 0.5
        # Keep the baked panel shading relative to the car's own paint brightness.
        shade = np.clip(0.9 * lum / max(ref, 1e-3), 0.25, 1.15)[..., None]
        tinted = np.clip(np.array(paint) * shade, 0, 1)
        a = tinted * (1 - chrome) + a * chrome
        im = Image.fromarray((a * 255).astype(np.uint8))
    buf = io.BytesIO()
    im.save(buf, format="JPEG", quality=88)
    images.append(buf.getvalue())
    textures.append({"source": len(images) - 1, "sampler": 0})
    image_index[key] = len(textures) - 1
    return image_index[key]


materials = []
for m in used:
    mat = json.loads(json.dumps(g["materials"][m]))
    pbr = mat.setdefault("pbrMetallicRoughness", {})
    is_paint = mat.get("name", "").startswith("Body")
    for slot in ("baseColorTexture",):
        if slot in pbr:
            uri = g["images"][g["textures"][pbr[slot]["index"]]["source"]]["uri"]
            pbr[slot] = {"index": add_image(uri, is_paint)}
    # Metallic/roughness maps are dropped; a uniform glossy paint reads better at this size.
    pbr.pop("metallicRoughnessTexture", None)
    if is_paint:
        pbr["metallicFactor"], pbr["roughnessFactor"] = 0.35, 0.35
    for k in ("normalTexture", "occlusionTexture", "emissiveTexture"):
        mat.pop(k, None)
    materials.append(mat)

# ---- write GLB
blob = bytearray()
views, accessors = [], []


def push(arr, target=None):
    while len(blob) % 4:
        blob.append(0)
    off = len(blob)
    blob.extend(arr.tobytes())
    v = {"buffer": 0, "byteOffset": off, "byteLength": arr.nbytes}
    if target:
        v["target"] = target
    views.append(v)
    return len(views) - 1


def accessor(arr, ctype, atype, target, minmax=False):
    v = push(arr, target)
    acc = {"bufferView": v, "componentType": ctype, "count": len(arr), "type": atype}
    if minmax:
        acc["min"], acc["max"] = arr.min(0).tolist(), arr.max(0).tolist()
    accessors.append(acc)
    return len(accessors) - 1


meshes = []
for q in prims:
    attrs = {"POSITION": accessor(q["pos"].astype(np.float32), 5126, "VEC3", 34962, True)}
    if q["nrm"] is not None:
        attrs["NORMAL"] = accessor(q["nrm"].astype(np.float32), 5126, "VEC3", 34962)
    if q["uv"] is not None:
        attrs["TEXCOORD_0"] = accessor(q["uv"].astype(np.float32), 5126, "VEC2", 34962)
    idx = q["idx"].astype(np.uint32 if q["idx"].max() > 65535 else np.uint16)
    ind = accessor(idx, 5125 if idx.dtype == np.uint32 else 5123, "SCALAR", 34963)
    meshes.append({"primitives": [{"attributes": attrs, "indices": ind, "material": mat_map[q["material"]]}]})

image_entries = []
for data in images:
    image_entries.append({"bufferView": push(np.frombuffer(data, dtype=np.uint8)), "mimeType": "image/jpeg"})

gltf = {
    "asset": {
        "version": "2.0",
        "generator": "drivecare export_car.py",
        "copyright": 'Based on "Generic passenger car pack" by Comrade1280, CC-BY-4.0',
    },
    "scene": 0,
    "scenes": [{"nodes": [0]}],
    "nodes": [{"name": body_name, "children": list(range(1, len(meshes) + 1))}]
    + [{"mesh": k} for k in range(len(meshes))],
    "meshes": meshes,
    "materials": materials,
    "textures": textures,
    "images": image_entries,
    "samplers": [{"magFilter": 9729, "minFilter": 9987, "wrapS": 10497, "wrapT": 10497}],
    "accessors": accessors,
    "bufferViews": views,
    "buffers": [{"byteLength": len(blob)}],
}
while len(blob) % 4:
    blob.append(0)
gltf["buffers"][0]["byteLength"] = len(blob)
js = json.dumps(gltf, separators=(",", ":")).encode()
js += b" " * ((4 - len(js) % 4) % 4)
glb = struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(blob))
glb += struct.pack("<II", len(js), 0x4E4F534A) + js + struct.pack("<II", len(blob), 0x004E4942) + bytes(blob)
Path(out_path).write_bytes(glb)
print(f"wrote {out_path} {len(glb) / 1e6:.2f} MB, {len(meshes)} meshes, {len(materials)} materials, wheels={[nodes[w]['name'] for w in wheels[:4]]}")
