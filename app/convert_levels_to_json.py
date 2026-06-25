#!/usr/bin/env python3
"""
Converts Replica Island .bin level files to .json format.

Level binary format (signature=96):
  - 1 byte: signature (96)
  - 1 byte: layer count
  - 1 byte: background index
  - Per layer:
    - 1 byte: type (0=background, 1=collision, 2=objects, 3=hotspots)
    - 1 byte: tile index
    - 4 bytes: scroll speed (float, little-endian)
    - TiledWorld data:
      - 1 byte: signature (42)
      - 4 bytes: width (int, little-endian)
      - 4 bytes: height (int, little-endian)
      - width*height bytes: tile data (row-major, each tile is a signed byte)

Collision binary format (signature=52):
  - 1 byte: signature (52)
  - 1 byte: tile count
  - Per tile:
    - 1 byte: tile index
    - 1 byte: segment count
    - Per segment:
      - 6x 4 bytes: startX, startY, endX, endY, normalX, normalY (floats, little-endian)
"""

import os
import sys
import json
import struct
import glob


def read_byte(f, default=None):
    b = f.read(1)
    if not b:
        if default is not None:
            return default
        raise EOFError("Unexpected end of file")
    return struct.unpack('b', b)[0]


def read_ubyte(f, default=None):
    b = f.read(1)
    if not b:
        if default is not None:
            return default
        raise EOFError("Unexpected end of file")
    return struct.unpack('B', b)[0]


def read_int_le(f):
    data = f.read(4)
    if len(data) < 4:
        raise EOFError("Unexpected end of file")
    return struct.unpack('<i', data)[0]


def read_float_le(f):
    data = f.read(4)
    if len(data) < 4:
        raise EOFError("Unexpected end of file")
    return struct.unpack('<f', data)[0]


def convert_level_file(bin_path):
    """Convert a level .bin file (signature=96) to JSON."""
    with open(bin_path, 'rb') as f:
        signature = read_byte(f)

        if signature == 96:
            return convert_level_96(f)
        elif signature == 52:
            return convert_collision_52(f)
        elif signature == 42:
            # Standalone TiledWorld (unlikely as a level file, but handle it)
            f.seek(0)
            return convert_tiledworld_standalone(f)
        else:
            print(f"  WARNING: Unknown signature {signature}, skipping")
            return None


def convert_level_96(f):
    """Convert level data with signature 96."""
    layer_count = read_byte(f)
    background_index = read_byte(f)

    result = {
        "signature": 96,
        "layerCount": layer_count,
        "backgroundIndex": background_index,
        "layers": []
    }

    for i in range(layer_count):
        layer_type = read_byte(f)
        tile_index = read_byte(f)
        scroll_speed = read_float_le(f)

        # Read embedded TiledWorld
        tw_signature = read_byte(f)
        if tw_signature != 42:
            print(f"  WARNING: Expected TiledWorld signature 42, got {tw_signature}")
            return None

        width = read_int_le(f)
        height = read_int_le(f)

        tiles = []
        for y in range(height):
            row = []
            for x in range(width):
                row.append(read_byte(f, default=-1))
            tiles.append(row)

        type_names = {0: "background", 1: "collision", 2: "objects", 3: "hotspots"}

        layer = {
            "type": layer_type,
            "typeName": type_names.get(layer_type, f"unknown_{layer_type}"),
            "tileIndex": tile_index,
            "scrollSpeed": round(scroll_speed, 6),
            "world": {
                "width": width,
                "height": height,
                "tiles": tiles
            }
        }
        result["layers"].append(layer)

    return result


def convert_collision_52(f):
    """Convert collision data with signature 52."""
    tile_count = read_ubyte(f)

    result = {
        "signature": 52,
        "tileCount": tile_count,
        "tiles": []
    }

    for i in range(tile_count):
        tile_index = read_ubyte(f)
        segment_count = read_ubyte(f)

        tile = {
            "tileIndex": tile_index,
            "segmentCount": segment_count,
            "segments": []
        }

        for j in range(segment_count):
            start_x = read_float_le(f)
            start_y = read_float_le(f)
            end_x = read_float_le(f)
            end_y = read_float_le(f)
            normal_x = read_float_le(f)
            normal_y = read_float_le(f)

            segment = {
                "startX": round(start_x, 6),
                "startY": round(start_y, 6),
                "endX": round(end_x, 6),
                "endY": round(end_y, 6),
                "normalX": round(normal_x, 6),
                "normalY": round(normal_y, 6)
            }
            tile["segments"].append(segment)

        result["tiles"].append(tile)

    return result


def convert_tiledworld_standalone(f):
    """Convert a standalone TiledWorld file."""
    signature = read_byte(f)
    if signature != 42:
        return None

    width = read_int_le(f)
    height = read_int_le(f)

    tiles = []
    for y in range(height):
        row = []
        for x in range(width):
            row.append(read_byte(f, default=-1))
        tiles.append(row)

    return {
        "signature": 42,
        "width": width,
        "height": height,
        "tiles": tiles
    }


def main():
    raw_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                           'src', 'main', 'res', 'raw')

    if not os.path.isdir(raw_dir):
        print(f"ERROR: raw directory not found: {raw_dir}")
        sys.exit(1)

    bin_files = glob.glob(os.path.join(raw_dir, '*.bin'))
    print(f"Found {len(bin_files)} .bin files in {raw_dir}")

    converted = 0
    failed = 0
    for bin_path in sorted(bin_files):
        filename = os.path.basename(bin_path)
        json_filename = filename.replace('.bin', '.json')
        json_path = os.path.join(raw_dir, json_filename)

        print(f"Converting: {filename} -> {json_filename}")
        try:
            data = convert_level_file(bin_path)
            if data is not None:
                with open(json_path, 'w', encoding='utf-8') as jf:
                    json.dump(data, jf, indent=2)
                converted += 1
            else:
                failed += 1
        except Exception as e:
            print(f"  ERROR: {e}")
            failed += 1

    print(f"\nDone! Converted: {converted}, Failed: {failed}")


if __name__ == '__main__':
    main()
