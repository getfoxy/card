#!/usr/bin/env python3
"""What the chip asks of the built card file, checked after every build.

Two things, both found on the chip (docs/FOXY-CARD-HARDWARE.md):

1. The J3R180 the card is built on fails the INSTALL that both instantiates a
   package for the first time and runs a heavy constructor (6F00, or no answer
   at all). It takes the card's applet once a small applet has been
   instantiated from the package before it. So the file's Applet component
   must list the opener first, and the two are installed in that order
   (docs/HARDWARE_DEPLOYMENT.md).

2. Even then, the card's applet installs only when the load file's size falls
   in a window of each 256 bytes: from WINDOW_LOW to WINDOW_HIGH bytes past a
   multiple of 256, as measured. The same code fails a few dozen bytes to
   either side. `CashuApplet.sizeTheFile` is in the package for its length
   alone, six bytes a line, and this check says how many lines to add or to
   take away. The window was measured with the constructor as it is; a
   constructor that allocates differently may move it, and then it has to be
   found again on the chip.

    python3 tools/refcheck.py <file.cap>
"""
import sys
import zipfile

OPENER = bytes.fromhex('F0464F5859434152444D')
LOADED = ['Header', 'Directory', 'Import', 'Applet', 'Class', 'Method',
          'StaticField', 'Export', 'ConstantPool', 'RefLocation']
# the load file's size, past a multiple of 256, at which the card's applet is known to install (130 to 186) and
# known not to (72 and below, 192 and above); a build is aimed at the middle
PAGE = 256
WINDOW_LOW = 130
WINDOW_HIGH = 186
AIM = (WINDOW_LOW + WINDOW_HIGH) // 2
LINE = 6  # bytes one line of sizeTheFile adds


def components(path):
    out = {}
    with zipfile.ZipFile(path) as z:
        for n in z.namelist():
            if n.endswith('.cap'):
                out[n.rsplit('/', 1)[-1][:-4]] = z.read(n)
    return out


def applets(component):
    """The AIDs the Applet component lists, in order."""
    count = component[3]
    out, at = [], 4
    for _ in range(count):
        n = component[at]
        out.append(component[at + 1:at + 1 + n])
        at += 1 + n + 2
    return out


def load_file_size(parts):
    body = sum(len(parts[k]) for k in LOADED if k in parts)
    return body + (2 if body < 0x80 else 3 if body < 0x100 else 4)


def main(argv):
    if len(argv) < 2:
        raise SystemExit(__doc__)
    parts = components(argv[1])
    size = load_file_size(parts)
    past = size % PAGE
    listed = applets(parts['Applet']) if 'Applet' in parts else []
    print('load file: %d bytes, %d past a multiple of %d (the chip installs at %d to %d); applets, in order: %s'
          % (size, past, PAGE, WINDOW_LOW, WINDOW_HIGH, ', '.join(a.hex().upper() for a in listed)))
    if not listed or listed[0] != OPENER:
        print('REFUSED: the opener (%s) must be the first applet listed, so that it is instantiated first.' % OPENER.hex().upper())
        return 1
    if past < WINDOW_LOW or past > WINDOW_HIGH:
        # the nearest point of the window ahead of or behind this size, and the lines of sizeTheFile that get there
        more = (AIM - past) % PAGE
        fewer = (past - AIM) % PAGE
        print('REFUSED: the chip will not install a file of this size. Add %d lines to CashuApplet.sizeTheFile (about %d bytes), '
              'or take %d away (about %d bytes), to land %d past a multiple of %d.'
              % ((more + LINE - 1) // LINE, more, (fewer + LINE - 1) // LINE, fewer, AIM, PAGE))
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
