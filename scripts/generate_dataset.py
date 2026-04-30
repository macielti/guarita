#!/usr/bin/env python3

import gzip
import json
import struct
import urllib.request

URL = 'https://github.com/zanfranceschi/rinha-de-backend-2026/raw/main/resources/references.json.gz'

with urllib.request.urlopen(URL) as r:
    data = json.loads(gzip.decompress(r.read()))

with open('resources/vectors.bin', 'wb') as f:
    for row in data:
        f.write(struct.pack(f'<{len(row["vector"])}f', *row['vector']))

with open('resources/labels.bin', 'wb') as f:
    for row in data:
        f.write(bytes([1 if row['label'] == 'fraud' else 0]))

print(f'{len(data)} records written')
