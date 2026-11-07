"""Tiny XTEST driver for the screenshot client: move, click, press keys and type on an X display.

Usage: python3 xinput.py move X Y | click X Y [button] | shiftclick X Y | button N | key NAME | type TEXT
Needs python-xlib (pip install python-xlib). DISPLAY selects the Xvfb display.
"""
import sys
import time

from Xlib import X, XK, display
from Xlib.ext import xtest

d = display.Display()
SHIFT = d.keysym_to_keycode(XK.string_to_keysym("Shift_L"))


def move(x, y):
    xtest.fake_input(d, X.MotionNotify, x=int(x), y=int(y))
    d.sync()


def click(x, y, button=1, shift=False):
    move(x, y)
    time.sleep(0.08)
    if shift:
        xtest.fake_input(d, X.KeyPress, SHIFT)
        d.sync()
        time.sleep(0.05)
    xtest.fake_input(d, X.ButtonPress, int(button))
    d.sync()
    time.sleep(0.06)
    xtest.fake_input(d, X.ButtonRelease, int(button))
    d.sync()
    if shift:
        time.sleep(0.05)
        xtest.fake_input(d, X.KeyRelease, SHIFT)
        d.sync()


def button(number):
    xtest.fake_input(d, X.ButtonPress, int(number))
    d.sync()
    time.sleep(0.06)
    xtest.fake_input(d, X.ButtonRelease, int(number))
    d.sync()


def press(code, shift=False):
    if shift:
        xtest.fake_input(d, X.KeyPress, SHIFT)
    xtest.fake_input(d, X.KeyPress, code)
    d.sync()
    time.sleep(0.04)
    xtest.fake_input(d, X.KeyRelease, code)
    if shift:
        xtest.fake_input(d, X.KeyRelease, SHIFT)
    d.sync()
    time.sleep(0.035)


def key(name):
    keysym = XK.string_to_keysym(name)
    if keysym == 0 and len(name) == 1:
        keysym = ord(name)
    press(d.keysym_to_keycode(keysym))


# Characters whose keysym name differs from the character itself.
NAMES = {" ": "space", "/": "slash", ".": "period", "-": "minus", "_": "underscore", ":": "colon", ",": "comma",
         "!": "exclam", "'": "apostrophe", "$": "dollar", "?": "question", "&": "ampersand", "#": "numbersign",
         "(": "parenleft", ")": "parenright", "%": "percent", "+": "plus", "=": "equal", ";": "semicolon"}


def type_char(char):
    keysym = XK.string_to_keysym(NAMES.get(char, char))
    if keysym == 0:
        keysym = ord(char)
    code = d.keysym_to_keycode(keysym)
    # The shift level is whatever the keymap says: index 0 is the plain key, index 1 the shifted one.
    shift = d.keycode_to_keysym(code, 0) != keysym
    press(code, shift)


def type_text(text):
    for char in text:
        type_char(char)


if __name__ == "__main__":
    command = sys.argv[1]
    if command == "move":
        move(sys.argv[2], sys.argv[3])
    elif command == "click":
        click(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else 1)
    elif command == "shiftclick":
        click(sys.argv[2], sys.argv[3], 1, shift=True)
    elif command == "button":
        button(sys.argv[2])
    elif command == "key":
        key(sys.argv[2])
    elif command == "type":
        type_text(" ".join(sys.argv[2:]))
