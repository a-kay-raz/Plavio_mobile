#!/usr/bin/env python3
"""Re-lay out libffmpegJNI.so for 16 KiB page sizes WITHOUT rebuilding FFmpeg.

Root cause it fixes: the bundled arm64 libffmpegJNI.so is 4 KiB-aligned.
On Android 15+ devices with 16 KiB pages the dynamic linker cannot load it,
so FfmpegLibrary.isAvailable() is false and every FFmpeg-decoded audio track
(AC-3/E-AC-3/DTS/TrueHD, incl. 5.1) fails with
"FfmpegAudioRenderer error, ..., format_supported=NO_UNSUPPORTED_TYPE".

Why a plain p_align bump is WRONG: the three PT_LOAD segments are congruent
modulo 4 KiB but NOT modulo 16 KiB -- LOAD2 (vaddr - offset) = 0x1000,
LOAD3 (vaddr - offset) = 0x2000. Raising p_align to 0x4000 without moving
the segments would violate the ELF congruence rule (p_vaddr == p_offset
mod p_align) and the dynamic linker would mis-map the library. This script
inserts zero padding before LOAD2 (+0x1000) and before LOAD3 (+0x1000 more,
so LOAD3 moves +0x2000 total), making every LOAD segment's file offset
equal to its virtual address and therefore 16 KiB-congruent. No code or
data byte is changed; only ELF metadata (program/section headers) and file
layout change, and the script verifies that every segment's bytes are
identical before and after.

The script refuses to run on a library that does not match the expected
pre-patch layout, or that is already 16 KiB-aligned (so it can never be
run twice destructively). Usage:

    python3 scripts/patch-ffmpeg-16k.py <input.so> <output.so>

Then verify with the repo's own gates before replacing the original:

    python3 scripts/check-native-alignment.py
    python3 scripts/native-codec-inventory.py
    readelf -lW <output.so>
"""
from __future__ import annotations

import hashlib
import struct
import sys
from pathlib import Path

PAGE_SIZE = 16 * 1024
PT_LOAD = 1
SHT_NOBITS = 8

# Expected pre-patch layout of the bundled library (verified with readelf).
# The script asserts all of these before touching anything.
EXPECTED_LOADS = [
    # (p_offset, p_vaddr, p_filesz, p_memsz)
    (0x000000, 0x0000000000000000, 0x0FF6E0, 0x0FF6E0),  # LOAD1 R E
    (0x0FF6E0, 0x00000000001006E0, 0x01CE00, 0x01D920),  # LOAD2 RW
    (0x11C4E0, 0x000000000011E4E0, 0x000028, 0x12D058),  # LOAD3 RW (bss)
]
EXPECTED_PHNUM = 9
EXPECTED_SHNUM = 35
EXPECTED_SHOFF = 0x48AB38
EXPECTED_SIZE = 4_764_664
EXPECTED_SECTION_NAMES = [
    "", ".note.android.ident", ".note.gnu.build-id", ".dynsym", ".gnu.version",
    ".gnu.version_r", ".gnu.hash", ".dynstr", ".rela.dyn", ".rela.plt",
    ".rodata", ".eh_frame_hdr", ".eh_frame", ".text", ".plt", ".data.rel.ro",
    ".fini_array", ".init_array", ".dynamic", ".got", ".got.plt",
    ".relro_padding", ".data", ".bss", ".comment", ".debug_loc",
    ".debug_abbrev", ".debug_info", ".debug_ranges", ".debug_str",
    ".debug_line", ".debug_aranges", ".symtab", ".shstrtab", ".strtab",
]

ELF64_PHDR = struct.Struct("<IIQQQQQQ")   # type, flags, offset, vaddr, paddr, filesz, memsz, align
ELF64_SHDR = struct.Struct("<IIQQQQIIQQ")  # name, type, flags, addr, offset, size, link, info, addralign, entsize
ELF64_EHDR_TAIL = struct.Struct("<HHIQQQIHHHHHH")  # from e_type (file offset 16)


def fail(msg: str) -> "SystemExit":
    return SystemExit(f"patch-ffmpeg-16k: ERROR: {msg}")


def parse_elf(data: bytes, expect_shoff: int):
    """Return (ehdr_fields, e_phoff, e_shoff, phdrs, shdrs)."""
    if len(data) < 64 or data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
        raise fail("not a 64-bit little-endian ELF file")
    ehdr = ELF64_EHDR_TAIL.unpack_from(data, 16)
    e_machine = ehdr[1]
    e_phoff, e_shoff = ehdr[4], ehdr[5]
    e_phentsize, e_phnum = ehdr[8], ehdr[9]
    e_shentsize, e_shnum, e_shstrndx = ehdr[10], ehdr[11], ehdr[12]
    if e_machine != 183:  # EM_AARCH64
        raise fail(f"unexpected machine {e_machine} (expected 183 = AArch64)")
    if e_phentsize != ELF64_PHDR.size or e_phnum != EXPECTED_PHNUM:
        raise fail(f"unexpected program-header table ({e_phnum} x {e_phentsize})")
    if e_shentsize != ELF64_SHDR.size or e_shnum != EXPECTED_SHNUM:
        raise fail(f"unexpected section-header table ({e_shnum} x {e_shentsize})")
    if e_shoff != expect_shoff:
        raise fail(f"unexpected e_shoff 0x{e_shoff:x} (expected 0x{expect_shoff:x})")
    phdrs = [list(ELF64_PHDR.unpack_from(data, e_phoff + i * e_phentsize))
             for i in range(e_phnum)]
    shdrs = [list(ELF64_SHDR.unpack_from(data, e_shoff + i * e_shentsize))
             for i in range(e_shnum)]
    return ehdr, e_phoff, e_shoff, e_shstrndx, phdrs, shdrs


def section_names(data: bytes, shoff: int, shstrndx: int, shdrs) -> list[str]:
    shstr = shdrs[shstrndx]
    base, size = shstr[4], shstr[5]  # sh_offset, sh_size
    table = data[base:base + size]
    names = []
    for sh in shdrs:
        start = sh[0]  # sh_name
        end = table.index(b"\x00", start)
        names.append(table[start:end].decode())
    return names


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    data = src.read_bytes()
    if len(data) != EXPECTED_SIZE:
        raise fail(f"unexpected size {len(data)} (expected {EXPECTED_SIZE}); "
                   "refusing to patch an unfamiliar library")

    _ehdr, e_phoff, e_shoff, e_shstrndx, phdrs, shdrs = parse_elf(data, EXPECTED_SHOFF)
    if section_names(data, e_shoff, e_shstrndx, shdrs) != EXPECTED_SECTION_NAMES:
        raise fail("section names do not match the expected layout")
    loads = [(p[2], p[3], p[5], p[6], p[7]) for p in phdrs if p[0] == PT_LOAD]
    if len(loads) != 3:
        raise fail(f"expected 3 PT_LOAD segments, found {len(loads)}")
    for got, want in zip(loads, EXPECTED_LOADS):
        if got[:4] != want or got[4] != 0x1000:
            raise fail(f"unexpected PT_LOAD layout {got}; expected {want} with p_align 0x1000")
    if all((off - va) % PAGE_SIZE == 0 and al >= PAGE_SIZE for off, va, _fs, _ms, al in loads):
        raise fail("library is already 16 KiB-aligned; nothing to do")

    # Shift each LOAD segment so its file offset becomes congruent with its
    # vaddr modulo 16 KiB (offset == vaddr after the shift). Shifts must be
    # non-decreasing in file order so the insertions nest correctly.
    shifts = [((va - off) % PAGE_SIZE) for off, va, _fs, _ms, _al in loads]
    if shifts != sorted(shifts):
        raise fail(f"segment shifts {shifts} are not non-decreasing; cannot re-lay out")
    # Insertion points (original file offsets): before each segment whose
    # shift grows relative to the previous segment.
    insertions = []  # (original_offset, bytes_to_insert)
    prev_shift = 0
    for (off, _va, _fs, _ms, _al), shift in zip(loads, shifts):
        if shift > prev_shift:
            insertions.append((off, shift - prev_shift))
        prev_shift = shift
    total_shift = shifts[-1]

    def shift_for(offset: int) -> int:
        """Total bytes inserted before a given original file offset."""
        total = 0
        for at, size in insertions:
            if offset >= at:
                total += size
        return total

    # Sanity: no LOAD segment's new file range may overlap the previous one.
    new_loads = [(off + shift, va, fsz, msz)
                 for (off, va, fsz, msz, _al), shift in zip(loads, shifts)]
    for (prev_off, _va, prev_fsz, _ms), (off, _va2, _fsz, _ms2) in zip(new_loads, new_loads[1:]):
        if off < prev_off + prev_fsz:
            raise fail("re-layout would overlap PT_LOAD segments")

    # Build the new file: copy the original, inserting zero padding.
    out = bytearray()
    cursor = 0
    for at, size in insertions:
        out += data[cursor:at]
        out += b"\x00" * size
        cursor = at
    out += data[cursor:]

    def put(fmt: str, pos: int, value: int) -> None:
        struct.pack_into(fmt, out, pos, value)

    # Program headers: shift p_offset, raise PT_LOAD p_align to 16 KiB.
    for i, p in enumerate(phdrs):
        ph_off = e_phoff + i * ELF64_PHDR.size
        p_type, _flags, p_offset = p[0], p[1], p[2]
        if p_offset:
            new_offset = p_offset + shift_for(p_offset)
            if new_offset != p_offset:
                put("<Q", ph_off + 8, new_offset)  # p_offset field
        if p_type == PT_LOAD:
            put("<Q", ph_off + 48, PAGE_SIZE)  # p_align field

    # Section headers: shift sh_offset by the insertion total before it.
    # One exception: .relro_padding is a NOBITS tail of LOAD2 whose file
    # offset sits exactly on LOAD3's start; it belongs to LOAD2's shift,
    # not LOAD3's. It is the only section at that boundary with an address
    # below LOAD3's vaddr.
    load3_off, load3_va = loads[2][0], loads[2][1]
    special = 0
    expected_shifts = []
    for sh in shdrs:
        sh_type, sh_addr, sh_offset = sh[1], sh[3], sh[4]
        if sh_offset == 0:
            expected_shifts.append(0)
            continue
        if sh_type == SHT_NOBITS and sh_offset == load3_off and sh_addr < load3_va:
            expected_shifts.append(shifts[1])  # LOAD2's shift
            special += 1
        else:
            expected_shifts.append(shift_for(sh_offset))
    if special != 1:
        raise fail(f"expected exactly one LOAD2-tail NOBITS section, found {special}")
    # NOTE: the section-header table itself moved by total_shift (both
    # insertions are before it), so entries are written at the NEW table
    # position, not at e_shoff.
    shdr_table_new = e_shoff + total_shift
    for i, (sh, want_shift) in enumerate(zip(shdrs, expected_shifts)):
        if want_shift:
            put("<Q", shdr_table_new + i * ELF64_SHDR.size + 24, sh[4] + want_shift)  # sh_offset

    # ELF header: the section-header table moves by the total shift.
    put("<Q", 40, e_shoff + total_shift)  # e_shoff field

    # ---- Verification (fail closed) ----
    new = bytes(out)
    ehdr2, _phoff2, shoff2, shstrndx2, phdrs2, shdrs2 = parse_elf(new, e_shoff + total_shift)
    if ehdr2[:4] != _ehdr[:4] or ehdr2[6:] != _ehdr[6:]:
        raise fail("ELF header fields other than e_shoff changed")
    if shstrndx2 != e_shstrndx:
        raise fail("e_shstrndx changed")
    if section_names(new, shoff2, shstrndx2, shdrs2) != EXPECTED_SECTION_NAMES:
        raise fail("section names no longer resolve after patching")
    loads2 = [(p[2], p[3], p[5], p[6], p[7]) for p in phdrs2 if p[0] == PT_LOAD]
    for i, (p_old, p_new) in enumerate(zip(phdrs, phdrs2)):
        if p_old[0] == PT_LOAD:
            if p_new[7] < PAGE_SIZE or (p_new[2] - p_new[3]) % PAGE_SIZE != 0:
                raise fail(f"PT_LOAD #{i + 1} still not 16 KiB-aligned/congruent")
        # Only p_offset (and PT_LOAD p_align) may change.
        for j, (a, b) in enumerate(zip(p_old, p_new)):
            if j == 2 and a != b and b - a not in (0x1000, 0x2000):
                raise fail(f"program header #{i}: unexpected p_offset change")
            if j == 7 and a != b and not (p_old[0] == PT_LOAD and b == PAGE_SIZE):
                raise fail(f"program header #{i}: unexpected p_align change")
            if j not in (2, 7) and a != b:
                raise fail(f"program header #{i}: field {j} changed unexpectedly")
    for (off, va, _fs, _ms, _al), (o_off, _o_va, o_fsz, _o_msz, _o_al) in zip(loads2, loads):
        # Segment bytes must be identical to the original, except for the
        # ELF header + program-header table (offsets [0, 568)), which live
        # inside LOAD1 and legitimately change (e_shoff, p_offset, p_align
        # -- those are verified field-by-field above instead).
        lo = max(off, 568) if o_off == 0 else off
        if hashlib.sha256(new[lo:off + o_fsz]).hexdigest() != \
                hashlib.sha256(data[max(o_off, 568) if o_off == 0 else o_off:o_off + o_fsz]).hexdigest():
            raise fail(f"PT_LOAD at 0x{off:x}: bytes changed (must be identical)")
    for i, (s_old, s_new, want_shift) in enumerate(zip(shdrs, shdrs2, expected_shifts)):
        for j, (a, b) in enumerate(zip(s_old, s_new)):
            if j == 4 and b - a != want_shift:
                raise fail(f"section #{i}: sh_offset shift {b - a:#x} != expected {want_shift:#x}")
            if j != 4 and a != b:
                raise fail(f"section #{i}: field {j} changed unexpectedly")
    # Non-alloc region after the LOAD segments (debug/symbol data) must be
    # byte-identical; the section-header table itself is excluded (its
    # sh_offset fields were deliberately updated).
    tail_start = 0x11C508  # .comment, first section after the LOAD segments
    if new[tail_start + total_shift:shoff2] != data[tail_start:e_shoff]:
        raise fail("debug/symbol bytes changed (must be identical)")

    dst.write_bytes(new)
    print(f"patched {src} -> {dst}")
    print(f"  size: {len(data)} -> {len(new)} (+{total_shift} bytes of zero padding)")
    for (off, va, fsz, _ms, _al), (n_off, _n_va, _n_fsz, _n_ms) in zip(loads, new_loads):
        print(f"  LOAD 0x{off:08x} -> 0x{n_off:08x} (vaddr 0x{va:08x}), "
              f"p_align 0x1000 -> 0x{PAGE_SIZE:x}")
    print("  segment bytes identical: yes (SHA-256 checked per PT_LOAD)")
    print("  verify with: python3 scripts/check-native-alignment.py && "
          "python3 scripts/native-codec-inventory.py && readelf -lW <patched>")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
