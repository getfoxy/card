#!/usr/bin/env python3
"""What the chip asks of the built card file, checked after every build.

The J3R180 the card is built on fails the INSTALL that both instantiates a
package for the first time and runs a heavy constructor (6F00, or no answer
at all). It takes the card's applet once a small applet has been
instantiated from the package before it (docs/FOXY-CARD-HARDWARE.md). So
the file's Applet component must list the opener first, and the two are
installed in that order (docs/HARDWARE_DEPLOYMENT.md). The load file's size
is printed for the record: 14,760 bytes is the largest known to install
this way, and a file over 13,362 bytes is known NOT to install without the
opener.

    python3 tools/refcheck.py <file.cap>
"""
import sys
import zipfile

OPENER = bytes.fromhex('F0464F5859434152444D')
LOADED = ['Header', 'Directory', 'Import', 'Applet', 'Class', 'Method',
          'StaticField', 'Export', 'ConstantPool', 'RefLocation']
LARGEST_KNOWN = 14760


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


def main(argv):
    if len(argv) < 2:
        raise SystemExit(__doc__)
    parts = components(argv[1])
    body = sum(len(parts[k]) for k in LOADED if k in parts)
    size = body + (2 if body < 0x80 else 3 if body < 0x100 else 4)
    listed = applets(parts['Applet']) if 'Applet' in parts else []
    print('load file: %d bytes (%d is the largest known to install); applets, in order: %s'
          % (size, LARGEST_KNOWN, ', '.join(a.hex().upper() for a in listed)))
    if not listed or listed[0] != OPENER:
        print('REFUSED: the opener (%s) must be the first applet listed, so that it is instantiated first.' % OPENER.hex().upper())
        return 1
    if size > LARGEST_KNOWN:
        print('NOTE: larger than any file known to install on the chip; try it on the chip before trusting it.')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
