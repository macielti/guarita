#!/usr/bin/env python3

import gzip
import json
import struct
import time
import urllib.request

import numpy as np
from sklearn.cluster import KMeans

URL = 'https://github.com/zanfranceschi/rinha-de-backend-2026/raw/main/resources/references.json.gz'
NLIST = 256
DIM = 14
IVF_MAGIC = 0x32465649  # 'IVF2' little-endian

t0 = time.time()
print('downloading dataset...', flush=True)
with urllib.request.urlopen(URL) as r:
    data = json.loads(gzip.decompress(r.read()))
print(f'downloaded {len(data)} records in {time.time()-t0:.1f}s', flush=True)

n = len(data)
vectors = np.empty((n, DIM), dtype=np.float32)
labels = np.empty(n, dtype=np.uint8)
for i, row in enumerate(data):
    vectors[i] = row['vector']
    labels[i] = 1 if row['label'] == 'fraud' else 0

t1 = time.time()
print(f'fitting KMeans(n_clusters={NLIST}) on {n} vectors...', flush=True)
km = KMeans(n_clusters=NLIST, n_init=1, random_state=0, verbose=1)
assignments = km.fit_predict(vectors)
centroids = km.cluster_centers_.astype(np.float32)
print(f'KMeans done in {time.time()-t1:.1f}s ({km.n_iter_} iterations)', flush=True)

order = np.argsort(assignments, kind='stable')
vectors = vectors[order]
labels = labels[order]
sorted_assignments = assignments[order]

counts = np.bincount(sorted_assignments, minlength=NLIST).astype(np.int32)
offsets = np.empty(NLIST + 1, dtype=np.int32)
offsets[0] = 0
np.cumsum(counts, out=offsets[1:])

print(f'cluster sizes: min={counts.min()} max={counts.max()} mean={counts.mean():.1f}', flush=True)

SCALE = 8192  # 2^13 fractional bits
vectors_i16 = np.clip(np.round(vectors * SCALE), np.iinfo(np.int16).min, np.iinfo(np.int16).max).astype(np.int16)
print(f'quantized to int16 (scale={SCALE}); max_err={np.abs(vectors - vectors_i16 / SCALE).max():.6f}', flush=True)

# Compute per-cluster bounding boxes in quantized space
bbox_min = np.full((NLIST, DIM), np.iinfo(np.int16).max, dtype=np.int16)
bbox_max = np.full((NLIST, DIM), np.iinfo(np.int16).min, dtype=np.int16)
for c in range(NLIST):
    start = int(offsets[c])
    end = int(offsets[c + 1])
    if start < end:
        cv = vectors_i16[start:end]
        bbox_min[c] = cv.min(axis=0)
        bbox_max[c] = cv.max(axis=0)

print('bounding boxes computed', flush=True)

t2 = time.time()
with open('resources/vectors.bin', 'wb') as f:
    f.write(vectors_i16.tobytes(order='C'))
with open('resources/labels.bin', 'wb') as f:
    f.write(labels.tobytes(order='C'))
# IVF2 format: header + centroids + offsets + bbox_min + bbox_max
with open('resources/ivf.bin', 'wb') as f:
    f.write(struct.pack('<Iiii', IVF_MAGIC, NLIST, n, DIM))
    f.write(centroids.tobytes(order='C'))
    f.write(offsets.tobytes(order='C'))
    f.write(bbox_min.tobytes(order='C'))
    f.write(bbox_max.tobytes(order='C'))

print(f'{n} records written in {time.time()-t2:.1f}s; nlist={NLIST}', flush=True)

t3 = time.time()
for name in ('vectors', 'labels', 'ivf'):
    src = f'resources/{name}.bin'
    dst = f'resources/{name}.bin.gz'
    with open(src, 'rb') as f_in, gzip.open(dst, 'wb') as f_out:
        f_out.write(f_in.read())
print(f'compressed in {time.time()-t3:.1f}s; total={time.time()-t0:.1f}s', flush=True)
