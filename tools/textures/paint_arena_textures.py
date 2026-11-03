"""Paints the Proving Grounds entity textures (64x64, vanilla humanoid UV layout, original art).

Run: python tools/textures/paint_arena_textures.py
Writes mod-fabric/src/main/resources/assets/veyrath/textures/entity/{duelist,training_dummy}.png.
Only the top half is used: HumanoidModel.createMesh mirrors the right limbs onto the left ones.
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'mod-fabric', 'src', 'main', 'resources',
                   'assets', 'veyrath', 'textures', 'entity')


def hexc(s, a=255):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def box_faces(u, v, w, h, d):
    """Face rectangles (x0, y0, x1, y1) of a model cube's texture, by face name."""
    return {
        'top': (u + d, v, u + d + w, v + d),
        'bottom': (u + d + w, v, u + d + 2 * w, v + d),
        'right': (u, v + d, u + d, v + d + h),
        'front': (u + d, v + d, u + d + w, v + d + h),
        'left': (u + d + w, v + d, u + 2 * d + w, v + d + h),
        'back': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


HEAD = box_faces(0, 0, 8, 8, 8)
HAT = box_faces(32, 0, 8, 8, 8)
BODY = box_faces(16, 16, 8, 12, 4)
ARM = box_faces(40, 16, 4, 12, 4)
LEG = box_faces(0, 16, 4, 12, 4)
SIDES = ('right', 'front', 'left', 'back')


class Painter:
    def __init__(self, seed):
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rnd = random.Random(seed)

    def put(self, x, y, c):
        self.px[x, y] = c

    def fill(self, rect, colors, weights=None):
        """Fills a rectangle with a random mix of colours (texture grain)."""
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, self.rnd.choices(colors, weights)[0])

    def row(self, part, r, colors, faces=SIDES, weights=None):
        """Paints row r (0 = top of the side faces) around a cube."""
        for f in faces:
            x0, y0, x1, _ = part[f]
            for x in range(x0, x1):
                self.put(x, y0 + r, self.rnd.choices(colors, weights)[0])

    def at(self, part, face, cx, cy, c):
        x0, y0, _, _ = part[face]
        self.put(x0 + cx, y0 + cy, c)

    def save(self, name):
        os.makedirs(OUT, exist_ok=True)
        self.img.save(os.path.join(OUT, name))


def duelist():
    p = Painter(7)
    skin = [hexc('#C68E62'), hexc('#BE8659')]
    skin_dark = hexc('#A8724C')
    hair = [hexc('#1A1626'), hexc('#221D33'), hexc('#2C2840')]
    band = [hexc('#B3202A'), hexc('#A31B25')]
    band_dark = hexc('#7E141C')
    gi = [hexc('#2B2F6B'), hexc('#282C64'), hexc('#30357A')]
    gi_dark = hexc('#1F2350')
    gi_light = hexc('#3E4490')
    wrap = [hexc('#E6E0CF'), hexc('#DDD6C3')]
    wrap_line = hexc('#B5AE9C')
    pants = [hexc('#24243A'), hexc('#2A2A44')]
    shoe = hexc('#2A1E18')

    # Head: skin, hair over top, back and the upper sides, fringe on the forehead.
    for f in SIDES + ('bottom',):
        p.fill(HEAD[f], skin)
    p.fill(HEAD['top'], hair)
    p.fill(HEAD['back'], hair)
    for r in range(0, 2):
        p.row(HEAD, r, hair, faces=('right', 'front', 'left'))
    for r in range(2, 5):
        p.row(HEAD, r, hair, faces=('right', 'left'))
    # Spiky fringe: a few hair pixels drop to row 2 on the front.
    for cx in (0, 2, 5, 7):
        p.at(HEAD, 'front', cx, 2, hair[0])
    # Headband at row 2 all around (under the fringe at the front), knot at the back.
    for f in SIDES:
        x0, y0, x1, _ = HEAD[f]
        for x in range(x0, x1):
            if f == 'front' and (x - x0) in (0, 2, 5, 7):
                continue
            p.put(x, y0 + 3, p.rnd.choice(band))
    p.row(HEAD, 4, [skin_dark], faces=('right', 'left'))
    # Face: brows, eyes (white and iris), nose shade, mouth.
    for cx in (1, 2):
        p.at(HEAD, 'front', cx, 4, hair[0])
    for cx in (5, 6):
        p.at(HEAD, 'front', cx, 4, hair[0])
    p.at(HEAD, 'front', 1, 5, hexc('#F2EEE6'))
    p.at(HEAD, 'front', 2, 5, hexc('#3A2A55'))
    p.at(HEAD, 'front', 5, 5, hexc('#3A2A55'))
    p.at(HEAD, 'front', 6, 5, hexc('#F2EEE6'))
    p.at(HEAD, 'front', 3, 6, skin_dark)
    p.at(HEAD, 'front', 4, 6, skin_dark)
    p.at(HEAD, 'front', 3, 7, hexc('#8E5A3E'))
    p.at(HEAD, 'front', 4, 7, hexc('#8E5A3E'))
    # Hat layer: the headband knot and two tails at the back, a few spikes on top.
    x0, y0, _, _ = HAT['back']
    p.put(x0 + 3, y0 + 3, band_dark)
    p.put(x0 + 4, y0 + 3, band[0])
    for i, (cx, cy) in enumerate([(3, 4), (2, 5), (2, 6), (1, 7), (4, 4), (5, 5), (5, 6), (6, 7)]):
        p.put(x0 + cx, y0 + cy, band[i % 2] if cy < 7 else band_dark)
    tx0, ty0, _, _ = HAT['top']
    for cx, cy in [(1, 1), (3, 0), (5, 1), (6, 3), (2, 4), (4, 6)]:
        p.put(tx0 + cx, ty0 + cy, hair[2])

    # Body: indigo gi, skin V at the neck, lighter lapels, crimson sash with a knot.
    for f in ('top', 'bottom') + SIDES:
        p.fill(BODY[f], gi)
    fx, fy, _, _ = BODY['front']
    v_rows = {0: (2, 6), 1: (3, 5), 2: (3, 5), 3: (4, 4)}
    for r, (a, b) in v_rows.items():
        for cx in range(a, b):
            p.put(fx + cx, fy + r, p.rnd.choice(skin))
    for r, (a, b) in v_rows.items():
        p.put(fx + a - 1, fy + r, gi_light)
        if b < 8:
            p.put(fx + b, fy + r, gi_light)
    for r in range(4, 8):
        p.put(fx + 4 + (r - 4) // 2, fy + r, gi_dark)  # the crossed lapel's fold line
    for r in (8, 9):
        p.row(BODY, r, band)
    p.row(BODY, 10, [gi_dark] + gi, weights=[2, 1, 1, 1])
    # Sash knot and its hanging ends on the front.
    p.put(fx + 2, fy + 8, band_dark)
    p.put(fx + 3, fy + 9, band_dark)
    p.put(fx + 2, fy + 10, band[0])
    p.put(fx + 2, fy + 11, band_dark)
    p.put(fx + 4, fy + 10, band[1])
    p.put(fx + 4, fy + 11, band_dark)
    # Shoulder shading on the back.
    bx, by, _, _ = BODY['back']
    for cx in range(8):
        p.put(bx + cx, by, gi_dark)

    # Arms: short gi sleeve, wrapped forearm with diagonal lines, bare hand.
    p.fill(ARM['top'], gi)
    p.fill(ARM['bottom'], skin)
    for r in range(0, 4):
        p.row(ARM, r, gi)
    p.row(ARM, 4, [gi_dark])
    for r in range(5, 10):
        p.row(ARM, r, wrap)
    for f in SIDES:
        x0, y0, x1, _ = ARM[f]
        for r in range(5, 10):
            for x in range(x0, x1):
                if (x - x0 + r) % 3 == 0:
                    p.put(x, y0 + r, wrap_line)
    for r in (10, 11):
        p.row(ARM, r, skin)
    p.row(ARM, 11, [skin_dark], faces=('front',))

    # Legs: dark trousers, wrapped shins, dark shoes.
    p.fill(LEG['top'], pants)
    p.fill(LEG['bottom'], [shoe])
    for r in range(0, 8):
        p.row(LEG, r, pants)
    for f in ('front',):
        x0, y0, x1, _ = LEG[f]
        for r in range(1, 7):
            p.put(x0 + 3, y0 + r, hexc('#1C1C2E'))  # crease
    for r in range(8, 11):
        p.row(LEG, r, wrap)
    for f in SIDES:
        x0, y0, x1, _ = LEG[f]
        for r in range(8, 11):
            for x in range(x0, x1):
                if (x - x0 + r) % 3 == 0:
                    p.put(x, y0 + r, wrap_line)
    p.row(LEG, 11, [shoe])
    p.save('duelist.png')


def dummy():
    p = Painter(11)
    burlap = [hexc('#B08A55'), hexc('#A47F4B'), hexc('#BC9762'), hexc('#9C7745')]
    weights = [4, 3, 2, 1]
    seam = hexc('#5A3E22')
    rope = [hexc('#8A6A3A'), hexc('#A07C47')]
    wood = [hexc('#8A6338'), hexc('#7E5A32')]
    grain = hexc('#6A4A28')
    iron = hexc('#4A4A52')
    red = hexc('#B8322B')
    white = hexc('#E6DECB')

    for part in (HEAD, BODY, ARM):
        for f in ('top', 'bottom') + SIDES:
            p.fill(part[f], burlap, weights)
    # Head: X-stitched eyes, a stitched mouth, a seam up the back, a rope tie at the neck.
    for ox in (0, 4):
        for dx, dy in [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2)]:
            p.at(HEAD, 'front', ox + dx, 2 + dy, seam)
    for cx in range(2, 6):
        p.at(HEAD, 'front', cx, 6 + (cx % 2), seam)
    for r in range(8):
        p.at(HEAD, 'back', 4, r, seam)
    p.row(HEAD, 7, rope)
    # Body: a painted target on the chest, rope bindings at the chest and the waist.
    fx, fy, _, _ = BODY['front']
    target = [
        "..rrrr..",
        ".rwwwwr.",
        "rwwrrwwr",
        "rwrwwrwr",
        "rwrwwrwr",
        "rwwrrwwr",
        ".rwwwwr.",
        "..rrrr..",
    ]
    for r, line in enumerate(target):
        for cx, ch in enumerate(line):
            if ch == 'r':
                p.put(fx + cx, fy + 1 + r, red)
            elif ch == 'w':
                p.put(fx + cx, fy + 1 + r, white)
    p.row(BODY, 0, rope)
    p.row(BODY, 10, rope)
    bx, by, _, _ = BODY['back']
    for r in range(1, 10):
        p.put(bx + 4, by + r, seam)
    # Arms: rope at the shoulder and the wrist, stitched cuffs.
    p.row(ARM, 1, rope)
    p.row(ARM, 9, rope)
    p.row(ARM, 11, [seam])
    # Legs: a wooden stand with iron bands.
    p.fill(LEG['top'], wood)
    p.fill(LEG['bottom'], [grain])
    for r in range(12):
        p.row(LEG, r, wood)
    for f in SIDES:
        x0, y0, x1, _ = LEG[f]
        for r in range(12):
            if r % 4 != 3:
                p.put(x0 + 1 + (r // 6), y0 + r, grain)
    p.row(LEG, 2, [iron])
    p.row(LEG, 9, [iron])
    p.save('training_dummy.png')


if __name__ == '__main__':
    duelist()
    dummy()
    print('wrote', os.path.abspath(OUT))
