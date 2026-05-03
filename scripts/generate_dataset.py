#!/usr/bin/env python3

import gzip
import json
import struct
import time
import urllib.request

import numpy as np
from sklearn.cluster import KMeans

URL = 'https://github.com/zanfranceschi/rinha-de-backend-2026/raw/main/resources/references.json.gz'
NLIST = 1700
DIM = 14
IVF_MAGIC = 0x31465649  # 'IVF1' little-endian

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

t2 = time.time()
with open('resources/vectors.bin', 'wb') as f:
    f.write(vectors.tobytes(order='C'))
with open('resources/labels.bin', 'wb') as f:
    f.write(labels.tobytes(order='C'))
with open('resources/ivf.bin', 'wb') as f:
    f.write(struct.pack('<Iiii', IVF_MAGIC, NLIST, n, DIM))
    f.write(centroids.tobytes(order='C'))
    f.write(offsets.tobytes(order='C'))

print(f'{n} records written in {time.time()-t2:.1f}s; nlist={NLIST}', flush=True)

t3 = time.time()
for name in ('vectors', 'labels', 'ivf'):
    src = f'resources/{name}.bin'
    dst = f'resources/{name}.bin.gz'
    with open(src, 'rb') as f_in, gzip.open(dst, 'wb') as f_out:
        f_out.write(f_in.read())
print(f'compressed in {time.time()-t3:.1f}s; total={time.time()-t0:.1f}s', flush=True)
