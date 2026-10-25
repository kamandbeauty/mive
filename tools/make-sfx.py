#!/usr/bin/env python3
"""
Rebuild every sound effect used by Mivehchin — pure Python synthesis, no samples.

Design notes (matched to the reference game's audio style: short, bright,
"juicy" rather than musical, with a soft wooden UI layer):

    sfx_click     wood tick for buttons
    sfx_swap      short airy whoosh for a swap
    sfx_pop       the workhorse: a wet pop with a click transient
    sfx_match     bell for a combo (pitch-shifted at play time)
    sfx_special   rising sparkle when a special is created
    sfx_explode   low punch + debris for bombs
    sfx_ice       glassy crack when ice breaks
    sfx_coin      two-note metallic ding for rewards
    sfx_star      bright star ding with a long tail
    sfx_wheel     wooden tick for the wheel
    sfx_booster   magic shimmer when arming a booster
    sfx_win       short fanfare
    sfx_lose      soft descending phrase

Everything is mono 16-bit 22.05 kHz, peak-normalised to about -1 dBFS with a
few milliseconds of fade so no click is audible on device.
"""

import math
import os
import random
import struct
import wave

SR = 22050
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "game-android", "app", "src", "main", "res", "raw")


# ---------------------------------------------------------------- primitives

def n_of(dur):
    return max(1, int(SR * dur))


def add(dst, src, at=0.0, gain=1.0):
    off = int(at * SR)
    need = off + len(src)
    if len(dst) < need:
        dst.extend([0.0] * (need - len(dst)))
    for i, v in enumerate(src):
        dst[off + i] += v * gain


def osc(dur, f0, f1=None, kind="sine", glide="exp", phase=0.0):
    """Oscillator with optional pitch glide."""
    n = n_of(dur)
    out = [0.0] * n
    ph = phase
    if f1 is None or f1 == f0:
        step = 2.0 * math.pi * f0 / SR
        for i in range(n):
            ph += step
            out[i] = _wave(ph, kind)
        return out
    for i in range(n):
        t = i / max(1, n - 1)
        if glide == "exp":
            f = f0 * (f1 / f0) ** t
        else:
            f = f0 + (f1 - f0) * t
        ph += 2.0 * math.pi * f / SR
        out[i] = _wave(ph, kind)
    return out


def _wave(ph, kind):
    if kind == "sine":
        return math.sin(ph)
    if kind == "tri":
        return 2.0 / math.pi * math.asin(math.sin(ph))
    if kind == "saw":
        x = (ph / (2.0 * math.pi)) % 1.0
        return 2.0 * x - 1.0
    if kind == "square":
        return 1.0 if math.sin(ph) >= 0 else -1.0
    return math.sin(ph)


def noise(dur, seed=1, smooth=0.0):
    rnd = random.Random(seed)
    n = n_of(dur)
    out = [0.0] * n
    prev = 0.0
    k = max(0.0, min(0.999, smooth))
    for i in range(n):
        v = rnd.uniform(-1.0, 1.0)
        prev = prev * k + v * (1.0 - k)
        out[i] = prev
    return out


def env(x, attack=0.004, decay=None, power=2.0, release=0.02):
    """Attack / exponential decay / release shaping."""
    n = len(x)
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    for i in range(n):
        if i < a:
            g = i / a
        else:
            t = (i - a) / max(1, n - a)
            g = (1.0 - t) ** power
        if i > n - r:
            g *= (n - i) / r
        x[i] *= g
    return x


def fade(x, ms=4):
    n = len(x)
    f = max(1, int(SR * ms / 1000.0))
    for i in range(min(f, n)):
        x[i] *= i / f
        x[n - 1 - i] *= i / f
    return x


def lowpass(x, cutoff):
    a = 1.0 - math.exp(-2.0 * math.pi * cutoff / SR)
    out = [0.0] * len(x)
    y = 0.0
    for i, v in enumerate(x):
        y += a * (v - y)
        out[i] = y
    return out


def highpass(x, cutoff):
    a = 1.0 - math.exp(-2.0 * math.pi * cutoff / SR)
    out = [0.0] * len(x)
    y = 0.0
    prev = 0.0
    for i, v in enumerate(x):
        y += a * (v - y)
        out[i] = v - y
        prev = v
    return out


def bandpass(x, lo, hi):
    return highpass(lowpass(x, hi), lo)


def tanh_clip(x, drive=1.0):
    return [math.tanh(v * drive) for v in x]


def reverb(x, mix=0.22, size=1.0):
    """Tiny Schroeder reverb: four combs + two allpasses."""
    out = list(x)
    combs = [(int(0.0297 * SR * size), 0.72), (int(0.0371 * SR * size), 0.7),
             (int(0.0411 * SR * size), 0.68), (int(0.0437 * SR * size), 0.66)]
    tail = [0.0] * (len(x) + int(0.35 * SR))
    for i, v in enumerate(x):
        tail[i] += v
    for delay, fb in combs:
        buf = [0.0] * delay
        p = 0
        for i in range(len(tail)):
            y = buf[p]
            buf[p] = tail[i] + y * fb
            tail[i] += y * 0.25
            p = (p + 1) % delay
    for delay, g in ((int(0.005 * SR), 0.7), (int(0.0017 * SR), 0.7)):
        buf = [0.0] * delay
        p = 0
        for i in range(len(tail)):
            b = buf[p]
            cur = tail[i] + b * g
            buf[p] = cur
            tail[i] = b - cur * g
            p = (p + 1) % delay
    for i in range(len(x)):
        out[i] += (tail[i] - x[i]) * mix
    return out


def note(freq, dur, kind="sine", amp=1.0, decay=1.6, detune=0.0):
    v = osc(dur, freq, kind=kind)
    if detune:
        w = osc(dur, freq * (1.0 + detune), kind=kind)
        v = [a + 0.5 * b for a, b in zip(v, w)]
    env(v, attack=0.005, power=decay)
    return [s * amp for s in v]


# ------------------------------------------------------------------- voices

def click():
    x = [0.0] * n_of(0.07)
    add(x, env(bandpass(noise(0.03, 11), 900, 4200), attack=0.001, power=3.0), 0.0, 0.9)
    add(x, env(osc(0.045, 1180, 760, "sine"), attack=0.001, power=3.2), 0.0, 0.45)
    return x


def swap():
    x = [0.0] * n_of(0.24)
    n = noise(0.2, 21, smooth=0.55)
    # sweeping band: emulate with three stacked bands faded in sequence
    add(x, env(bandpass(n, 420, 1300), attack=0.01, power=1.6), 0.0, 0.55)
    add(x, env(bandpass(noise(0.18, 22, smooth=0.4), 900, 2600), attack=0.02, power=2.0), 0.02, 0.4)
    add(x, env(osc(0.18, 520, 900, "sine"), attack=0.005, power=1.8), 0.0, 0.18)
    return x


def pop():
    x = [0.0] * n_of(0.17)
    # body: fast downward pitch blip, bright enough to cut through music
    add(x, env(osc(0.11, 1180, 460, "sine"), attack=0.0015, power=2.4), 0.0, 0.8)
    add(x, env(osc(0.09, 2360, 940, "tri"), attack=0.0015, power=2.8), 0.0, 0.34)
    # snappy click transient + "wet" air
    add(x, env(highpass(noise(0.025, 31), 3200), attack=0.0008, power=3.2), 0.0, 0.55)
    add(x, env(bandpass(noise(0.085, 32, smooth=0.45), 1100, 3600), attack=0.002, power=2.6), 0.004, 0.28)
    return x


def match():
    x = [0.0] * n_of(0.45)
    base = 1046.5
    for mult, amp, dec in ((1.0, 0.55, 2.1), (2.01, 0.28, 2.8), (3.02, 0.14, 3.4), (4.16, 0.07, 3.8)):
        add(x, note(base * mult, 0.42, "sine", amp, dec), 0.0)
    add(x, env(bandpass(noise(0.12, 41, smooth=0.3), 3000, 8000), attack=0.002, power=2.6), 0.0, 0.16)
    return reverb(x, 0.18)


def special():
    x = [0.0] * n_of(0.55)
    notes = (523.25, 659.25, 783.99, 1046.5)
    for i, f in enumerate(notes):
        add(x, note(f, 0.34, "tri", 0.4, 2.4), i * 0.055)
        add(x, note(f * 2, 0.24, "sine", 0.14, 3.0), i * 0.055 + 0.005)
    shimmer = bandpass(noise(0.42, 51, smooth=0.2), 2600, 9000)
    env(shimmer, attack=0.06, power=1.4)
    add(x, shimmer, 0.02, 0.2)
    return reverb(x, 0.26)


def explode():
    x = [0.0] * n_of(0.7)
    add(x, env(osc(0.45, 150, 44, "sine"), attack=0.002, power=1.7), 0.0, 0.95)
    add(x, env(osc(0.3, 320, 70, "tri"), attack=0.002, power=2.2), 0.0, 0.35)
    rumble = lowpass(noise(0.55, 61, smooth=0.75), 900)
    env(rumble, attack=0.003, power=1.5)
    add(x, rumble, 0.0, 0.65)
    debris = bandpass(noise(0.35, 62), 1600, 7000)
    env(debris, attack=0.004, power=2.4)
    add(x, debris, 0.03, 0.22)
    return tanh_clip(reverb(x, 0.22), 1.25)


def ice():
    x = [0.0] * n_of(0.4)
    for at, amp, seed in ((0.0, 0.7, 71), (0.045, 0.45, 72), (0.09, 0.3, 73)):
        add(x, env(highpass(noise(0.06, seed), 3200), attack=0.001, power=3.0), at, amp)
    for f, amp in ((2489.0, 0.16), (3136.0, 0.12), (4186.0, 0.08)):
        add(x, env(osc(0.26, f, f * 0.985, "sine"), attack=0.002, power=2.6), 0.0, amp)
    return reverb(x, 0.2)


def coin():
    x = [0.0] * n_of(0.55)
    for f, at, amp in ((1318.5, 0.0, 0.5), (1975.5, 0.07, 0.42)):
        for mult, a, dec in ((1.0, 1.0, 1.9), (2.01, 0.35, 2.6), (2.98, 0.16, 3.2)):
            add(x, note(f * mult, 0.4, "sine", amp * a, dec), at)
    add(x, env(highpass(noise(0.05, 81), 5000), attack=0.001, power=3.0), 0.0, 0.25)
    return reverb(x, 0.24)


def star():
    x = [0.0] * n_of(0.7)
    for mult, amp, dec in ((1.0, 0.5, 1.7), (1.5, 0.22, 2.2), (2.0, 0.18, 2.6), (3.0, 0.09, 3.0)):
        add(x, note(1568.0 * mult, 0.6, "sine", amp, dec), 0.0)
    sparkle = bandpass(noise(0.5, 91, smooth=0.15), 4000, 10000)
    env(sparkle, attack=0.03, power=1.3)
    add(x, sparkle, 0.0, 0.18)
    return reverb(x, 0.3)


def wheel():
    x = [0.0] * n_of(0.09)
    add(x, env(bandpass(noise(0.04, 101), 700, 3200), attack=0.001, power=3.2), 0.0, 0.6)
    add(x, env(osc(0.07, 620, 420, "sine"), attack=0.001, power=3.0), 0.0, 0.5)
    add(x, env(osc(0.05, 1240, 900, "sine"), attack=0.001, power=3.4), 0.0, 0.2)
    return x


def booster():
    x = [0.0] * n_of(0.6)
    add(x, env(osc(0.5, 300, 1500, "sine"), attack=0.05, power=1.3), 0.0, 0.35)
    for i, f in enumerate((784.0, 987.77, 1174.66, 1567.98)):
        add(x, note(f, 0.3, "tri", 0.26, 2.4), 0.03 + i * 0.05)
    sh = bandpass(noise(0.5, 111, smooth=0.35), 1800, 8000)
    env(sh, attack=0.08, power=1.5)
    add(x, sh, 0.0, 0.2)
    return reverb(x, 0.24)


def win():
    x = [0.0] * n_of(1.7)
    melody = ((523.25, 0.0, 0.16), (659.25, 0.15, 0.16), (783.99, 0.30, 0.16),
              (1046.5, 0.45, 0.55))
    for f, at, dur in melody:
        add(x, note(f, dur + 0.5, "tri", 0.42, 1.7), at)
        add(x, note(f * 2, dur + 0.3, "sine", 0.16, 2.3), at)
    # sustained chord under the tail
    for f in (523.25, 659.25, 783.99, 1046.5):
        add(x, note(f, 0.9, "sine", 0.14, 1.4), 0.45)
    sh = bandpass(noise(1.0, 121, smooth=0.25), 3000, 9000)
    env(sh, attack=0.25, power=1.2)
    add(x, sh, 0.35, 0.14)
    return reverb(x, 0.32)


def lose():
    x = [0.0] * n_of(1.25)
    for i, f in enumerate((392.0, 329.63, 261.63)):
        v = note(f, 0.5, "tri", 0.4, 1.8)
        vib = osc(0.5, 5.2, 4.4, "sine")
        v = [a * (1.0 + 0.06 * b) for a, b in zip(v, vib)]
        add(x, v, i * 0.2)
    add(x, note(130.81, 0.9, "sine", 0.28, 1.3), 0.4)
    return reverb(x, 0.3)


VOICES = {
    "sfx_click": click,
    "sfx_swap": swap,
    "sfx_pop": pop,
    "sfx_match": match,
    "sfx_special": special,
    "sfx_explode": explode,
    "sfx_ice": ice,
    "sfx_coin": coin,
    "sfx_star": star,
    "sfx_wheel": wheel,
    "sfx_booster": booster,
    "sfx_win": win,
    "sfx_lose": lose,
}


def write_wav(path, samples, peak=0.89):
    dur_ms = 1000.0 * len(samples) / SR
    shaped = fade(list(samples), max(1.0, min(5.0, dur_ms * 0.06)))
    top = max(0.0001, max(abs(v) for v in shaped))
    k = peak / top
    data = b"".join(
        struct.pack("<h", int(max(-1.0, min(1.0, v * k)) * 32000)) for v in shaped
    )
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)


def main():
    os.makedirs(OUT, exist_ok=True)
    total = 0
    for name, fn in VOICES.items():
        path = os.path.join(OUT, name + ".wav")
        samples = fn()
        write_wav(path, samples)
        size = os.path.getsize(path)
        total += size
        print(f"{name:14s} {len(samples) / SR:5.2f}s {size / 1024:7.1f} KB")
    print(f"total {total / 1024:.0f} KB in {OUT}")


if __name__ == "__main__":
    main()
