#!/usr/bin/env python3

import gzip
import json
import struct
import urllib.request

import numpy as np
from sklearn.cluster import MiniBatchKMeans

URL = 'https://github.com/zanfranceschi/rinha-de-backend-2026/raw/main/resources/references.json.gz'
NLIST = 1700
DIM = 14
IVF_MAGIC = 0x31465649  # 'IVF1' little-endian

with urllib.request.urlopen(URL) as r:
    data = json.loads(gzip.decompress(r.read()))

n = len(data)
vectors = np.empty((n, DIM), dtype=np.float32)
labels = np.empty(n, dtype=np.uint8)
for i, row in enumerate(data):
    vectors[i] = row['vector']
    labels[i] = 1 if row['label'] == 'fraud' else 0

print(f'fitting MiniBatchKMeans(n_clusters={NLIST}) on {n} vectors')
km = MiniBatchKMeans(n_clusters=NLIST, batch_size=8192, n_init=3, random_state=0)
assignments = km.fit_predict(vectors)
centroids = km.cluster_centers_.astype(np.float32)

order = np.argsort(assignments, kind='stable')
vectors = vectors[order]
labels = labels[order]
sorted_assignments = assignments[order]

counts = np.bincount(sorted_assignments, minlength=NLIST).astype(np.int32)
offsets = np.empty(NLIST + 1, dtype=np.int32)
offsets[0] = 0
np.cumsum(counts, out=offsets[1:])

print(f'cluster sizes: min={counts.min()} max={counts.max()} mean={counts.mean():.1f}')

with open('resources/vectors.bin', 'wb') as f:
    f.write(vectors.tobytes(order='C'))

with open('resources/labels.bin', 'wb') as f:
    f.write(labels.tobytes(order='C'))

with open('resources/ivf.bin', 'wb') as f:
    f.write(struct.pack('<Iiii', IVF_MAGIC, NLIST, n, DIM))
    f.write(centroids.tobytes(order='C'))
    f.write(offsets.tobytes(order='C'))

print(f'{n} records written; nlist={NLIST}; ivf.bin written')
