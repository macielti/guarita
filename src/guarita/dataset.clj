(ns guarita.dataset
  (:require [integrant.core :as ig]
            [taoensso.timbre :as log])
  (:import [java.io RandomAccessFile]
           [java.nio ByteBuffer ByteOrder ShortBuffer]
           [java.nio.channels FileChannel$MapMode]))

(def ^:private dim 14)
(def ^:private bytes-per-vector (* dim 2))
(def ^:private scale-inv (/ 1.0 8192.0))
;; "IVF1" little-endian
(def ^:private ivf-magic 0x31465649)
(def ^:private ivf-header-bytes 16)

(deftype KnnScratch [^longs top-idx
                     ^doubles top-dist
                     ^ints worst
                     ^ints cl-ids
                     ^doubles cl-dist
                     ^ints cl-worst])

(defn- make-knn-scratch ^KnnScratch [^long k ^long nprobe]
  (KnnScratch.
   (long-array k Long/MAX_VALUE)
   (double-array k Double/POSITIVE_INFINITY)
   (int-array 1 0)
   (int-array nprobe -1)
   (double-array nprobe Double/POSITIVE_INFINITY)
   (int-array 1 0)))

(def ^:private ^ThreadLocal tl-vec-buf
  (ThreadLocal/withInitial
   (reify java.util.function.Supplier
     (get [_] (short-array 14)))))

;; Default sized for k≤8, nprobe≤16 — covers all current callers without realloc.
(def ^:private ^ThreadLocal tl-knn-scratch
  (ThreadLocal/withInitial
   (reify java.util.function.Supplier
     (get [_] (atom (make-knn-scratch 8 16))))))

(defn- acquire-scratch! ^KnnScratch [^long k ^long nprobe]
  (let [cell (.get tl-knn-scratch)
        ^KnnScratch s @cell]
    (if (and (>= (alength ^longs (.top-idx s)) k)
             (>= (alength ^ints (.cl-ids s)) nprobe))
      (do (java.util.Arrays/fill ^longs   (.top-idx  s) Long/MAX_VALUE)
          (java.util.Arrays/fill ^doubles (.top-dist s) Double/POSITIVE_INFINITY)
          (aset ^ints (.worst    s) 0 (int 0))
          (java.util.Arrays/fill ^ints    (.cl-ids   s) (int -1))
          (java.util.Arrays/fill ^doubles (.cl-dist  s) Double/POSITIVE_INFINITY)
          (aset ^ints (.cl-worst s) 0 (int 0))
          s)
      (let [ns (make-knn-scratch k nprobe)]
        (reset! cell ns)
        ns))))

(defn- mmap-read-only [^String path]
  (let [f  (RandomAccessFile. path "r")
        ch (.getChannel f)
        sz (.size ch)
        bb (doto (.map ch FileChannel$MapMode/READ_ONLY 0 sz)
             (.order ByteOrder/LITTLE_ENDIAN))]
    {:buffer bb :handles [ch f] :size sz}))

(defn- read-centroids ^floats [^ByteBuffer ivf-buf ^long nlist]
  (let [n   (* nlist dim)
        out (float-array n)]
    (dotimes [i n]
      (aset out i (.getFloat ivf-buf (+ ivf-header-bytes (* i 4)))))
    out))

(defn- read-offsets ^ints [^ByteBuffer ivf-buf ^long nlist]
  (let [out  (int-array (inc nlist))
        base (+ ivf-header-bytes (* nlist dim 4))]
    (dotimes [i (inc nlist)]
      (aset out i (.getInt ivf-buf (+ base (* i 4)))))
    out))

(defmethod ig/init-key ::dataset
  [_ {:keys [vectors-path labels-path ivf-path]}]
  (log/info :starting ::dataset)
  (let [{v-buf :buffer v-handles :handles v-size :size} (mmap-read-only vectors-path)
        {l-buf :buffer l-handles :handles l-size :size} (mmap-read-only labels-path)
        {i-buf :buffer i-handles :handles}              (mmap-read-only ivf-path)
        ^ByteBuffer v-buf v-buf
        ^ByteBuffer l-buf l-buf
        ^ByteBuffer i-buf i-buf
        vectors (.asShortBuffer v-buf)
        n       (long (/ v-size bytes-per-vector))
        magic   (.getInt i-buf 0)
        nlist   (.getInt i-buf 4)
        ntotal  (.getInt i-buf 8)
        ivf-dim (.getInt i-buf 12)]
    (when-not (zero? (rem v-size bytes-per-vector))
      (throw (ex-info "vectors.bin com tamanho inválido"
                      {:size v-size :expected-multiple-of bytes-per-vector})))
    (when-not (= n l-size)
      (throw (ex-info "vectors.bin e labels.bin têm contagens diferentes"
                      {:vectors-count n :labels-count l-size})))
    (when-not (= magic ivf-magic)
      (throw (ex-info "ivf.bin: magic inválido"
                      {:expected (Integer/toHexString ivf-magic)
                       :got      (Integer/toHexString magic)})))
    (when-not (and (= ntotal n) (= ivf-dim dim))
      (throw (ex-info "ivf.bin: cabeçalho não confere com vectors.bin"
                      {:ivf-ntotal ntotal :n n :ivf-dim ivf-dim :dim dim})))
    {:vectors   vectors
     :labels    l-buf
     :n         n
     :nlist     nlist
     :centroids (read-centroids i-buf nlist)
     :offsets   (read-offsets   i-buf nlist)
     ::handles  (concat v-handles l-handles i-handles)}))

(defmethod ig/halt-key! ::dataset
  [_ {:keys [::handles]}]
  (log/info :stopping ::dataset)
  (doseq [^java.io.Closeable h handles] (.close h)))

(defn- sq-dist-buf
  "Bulk-reads 14 i16 values via a single ShortBuffer.get call, then computes
  squared Euclidean distance. Checks partial distance (first 8 dims) against
  `worst` before computing the remaining 6 dims — skips the rest when the
  partial already exceeds the current k-NN threshold."
  ^double [^ShortBuffer vectors ^floats query ^long i ^double worst]
  (let [^shorts s (.get tl-vec-buf)
        _         (.get vectors (int (* i 14)) s 0 14)
        d0  (- (* (double (int (aget s  0))) scale-inv) (double (aget query  0)))
        d1  (- (* (double (int (aget s  1))) scale-inv) (double (aget query  1)))
        d2  (- (* (double (int (aget s  2))) scale-inv) (double (aget query  2)))
        d3  (- (* (double (int (aget s  3))) scale-inv) (double (aget query  3)))
        d4  (- (* (double (int (aget s  4))) scale-inv) (double (aget query  4)))
        d5  (- (* (double (int (aget s  5))) scale-inv) (double (aget query  5)))
        d6  (- (* (double (int (aget s  6))) scale-inv) (double (aget query  6)))
        d7  (- (* (double (int (aget s  7))) scale-inv) (double (aget query  7)))
        partial (+ (* d0 d0) (* d1 d1) (* d2 d2) (* d3 d3)
                   (* d4 d4) (* d5 d5) (* d6 d6) (* d7 d7))]
    (if (>= partial worst)
      Double/POSITIVE_INFINITY
      (let [d8  (- (* (double (int (aget s  8))) scale-inv) (double (aget query  8)))
            d9  (- (* (double (int (aget s  9))) scale-inv) (double (aget query  9)))
            d10 (- (* (double (int (aget s 10))) scale-inv) (double (aget query 10)))
            d11 (- (* (double (int (aget s 11))) scale-inv) (double (aget query 11)))
            d12 (- (* (double (int (aget s 12))) scale-inv) (double (aget query 12)))
            d13 (- (* (double (int (aget s 13))) scale-inv) (double (aget query 13)))]
        (+ partial (* d8 d8) (* d9 d9) (* d10 d10) (* d11 d11) (* d12 d12) (* d13 d13))))))

(defn- sq-dist-arr
  ^double [^floats centroids ^long c-off ^floats query]
  (let [d0  (- (double (aget centroids (+ c-off  0))) (double (aget query  0)))
        d1  (- (double (aget centroids (+ c-off  1))) (double (aget query  1)))
        d2  (- (double (aget centroids (+ c-off  2))) (double (aget query  2)))
        d3  (- (double (aget centroids (+ c-off  3))) (double (aget query  3)))
        d4  (- (double (aget centroids (+ c-off  4))) (double (aget query  4)))
        d5  (- (double (aget centroids (+ c-off  5))) (double (aget query  5)))
        d6  (- (double (aget centroids (+ c-off  6))) (double (aget query  6)))
        d7  (- (double (aget centroids (+ c-off  7))) (double (aget query  7)))
        d8  (- (double (aget centroids (+ c-off  8))) (double (aget query  8)))
        d9  (- (double (aget centroids (+ c-off  9))) (double (aget query  9)))
        d10 (- (double (aget centroids (+ c-off 10))) (double (aget query 10)))
        d11 (- (double (aget centroids (+ c-off 11))) (double (aget query 11)))
        d12 (- (double (aget centroids (+ c-off 12))) (double (aget query 12)))
        d13 (- (double (aget centroids (+ c-off 13))) (double (aget query 13)))
        s0  (+ (* d0  d0)  (* d1  d1))
        s1  (+ (* d2  d2)  (* d3  d3))
        s2  (+ (* d4  d4)  (* d5  d5))
        s3  (+ (* d6  d6)  (* d7  d7))
        s4  (+ (* d8  d8)  (* d9  d9))
        s5  (+ (* d10 d10) (* d11 d11))
        s6  (+ (* d12 d12) (* d13 d13))
        t0  (+ s0 s1)
        t1  (+ s2 s3)
        t2  (+ s4 s6)
        t3  (+ t0 t1)]
    (+ t3 (+ t2 s5))))

(defn- update-worst! [^doubles top-dist ^ints worst ^long k]
  (loop [j 0 max-d Double/NEGATIVE_INFINITY max-pos 0]
    (if (< j k)
      (if (> (aget top-dist j) max-d)
        (recur (inc j) (aget top-dist j) j)
        (recur (inc j) max-d max-pos))
      (aset worst 0 (int max-pos)))))

(defn- topn-clusters! [centroids nlist query nprobe s]
  (let [^floats centroids centroids
        ^long nlist nlist
        ^floats query query
        ^long nprobe nprobe
        ^KnnScratch s s
        ^doubles cl-dist (.cl-dist s)
        ^ints cl-worst (.cl-worst s)
        ^ints cl-ids (.cl-ids s)]
    (loop [c 0]
      (when (< c nlist)
        (let [d (sq-dist-arr centroids (* c 14) query)]
          (when (< d (aget cl-dist (aget cl-worst 0)))
            (aset cl-ids (aget cl-worst 0) (int c))
            (aset cl-dist (aget cl-worst 0) d)
            (update-worst! cl-dist cl-worst nprobe)))
        (recur (inc c))))))

(defn- sort-cl-ids-by-dist! [^ints cl-ids ^doubles cl-dist ^long nprobe]
  (loop [i 1]
    (when (< i nprobe)
      (let [ki (aget cl-ids i)
            kd (aget cl-dist i)]
        (loop [j (dec i)]
          (if (and (>= j 0) (> (aget cl-dist j) kd))
            (do (aset cl-ids  (inc j) (aget cl-ids j))
                (aset cl-dist (inc j) (aget cl-dist j))
                (recur (dec j)))
            (do (aset cl-ids  (inc j) ki)
                (aset cl-dist (inc j) kd)))))
      (recur (inc i)))))

(defn- knn-range! [vectors query k start end s]
  (let [^ShortBuffer vectors vectors
        ^floats query query
        ^long k k
        ^long start start
        ^long end end
        ^KnnScratch s s
        ^longs top-idx (.top-idx s)
        ^doubles top-dist (.top-dist s)
        ^ints worst (.worst s)]
    (loop [i start]
      (when (< i end)
        (let [w (aget top-dist (aget worst 0))
              d (sq-dist-buf vectors query i w)]
          (when (< d w)
            (aset top-idx (aget worst 0) (long i))
            (aset top-dist (aget worst 0) d)
            (update-worst! top-dist worst k)))
        (recur (inc i))))))

(defn- finalize-results
  [^ByteBuffer labels ^longs top-idx ^doubles top-dist ^long k]
  (let [pairs (java.util.ArrayList.)]
    (dotimes [i k]
      (let [d (aget top-dist i)]
        (when (< d Double/POSITIVE_INFINITY)
          (.add pairs (object-array [(aget top-idx i) d])))))
    (.sort pairs (java.util.Comparator/comparingDouble (fn [^objects p] (aget p 1))))
    (mapv (fn [^objects p]
            (let [idx (long (aget p 0))]
              {:index    idx
               :distance (Math/sqrt (double (aget p 1)))
               :label    (case (long (.get labels (int idx)))
                           0 :legit
                           1 :fraud)}))
          pairs)))

(defn knn
  "Sequential brute force over all n vectors. Returns k nearest neighbors."
  [{:keys [^ByteBuffer labels ^ShortBuffer vectors ^long n]} ^floats query ^long k]
  (let [^KnnScratch s (acquire-scratch! k 16)]
    (knn-range! vectors query k 0 n s)
    (vec (finalize-results labels (.top-idx s) (.top-dist s) k))))

(defn knn-ivf
  "IVF-based k-NN: scans only the nprobe clusters whose centroids are nearest
  to the query. Vectors are stored cluster-contiguous in vectors.bin so each
  cluster scan is a tight loop over a flat slice of the mmap."
  [{:keys [^ByteBuffer labels ^ShortBuffer vectors ^floats centroids ^ints offsets ^long nlist]}
   ^floats query ^long k ^long nprobe]
  (let [^KnnScratch s (acquire-scratch! k nprobe)]
    (topn-clusters! centroids nlist query nprobe s)
    (loop [ci 0]
      (when (< ci nprobe)
        (let [cid (aget ^ints (.cl-ids s) ci)]
          (when (>= cid 0)
            (knn-range! vectors query k
                        (aget offsets cid) (aget offsets (inc cid)) s)))
        (recur (inc ci))))
    (vec (finalize-results labels (.top-idx s) (.top-dist s) k))))

(defn- count-fraud [^ByteBuffer labels ^longs top-idx ^doubles top-dist ^long k]
  (loop [i 0 n 0]
    (if (< i k)
      (let [d (aget top-dist i)]
        (if (< d Double/POSITIVE_INFINITY)
          (recur (inc i)
                 (if (= 1 (long (.get labels (int (aget top-idx i)))))
                   (inc n)
                   n))
          (recur (inc i) n)))
      n)))

(defn knn-ivf-fraud-count
  "Staged IVF k-NN fraud count.
  Fast probe (nprobe-fast clusters); if result is borderline — where one
  more or fewer fraud neighbor would flip the approve/deny decision — runs
  additional clusters up to nprobe-full for higher recall on edge cases."
  [{:keys [^ByteBuffer labels ^ShortBuffer vectors ^floats centroids ^ints offsets ^long nlist]}
   ^floats query k nprobe-fast nprobe-full]
  (let [k           (long k)
        nprobe-fast (long nprobe-fast)
        nprobe-full (long nprobe-full)
        ^KnnScratch s (acquire-scratch! k nprobe-full)
        ^ints cl-ids  (.cl-ids s)
        ^ints offsets offsets
        threshold     (int (Math/ceil (* 0.6 (double k))))]
    (topn-clusters! centroids nlist query nprobe-full s)
    (sort-cl-ids-by-dist! cl-ids (.cl-dist s) nprobe-full)
    (loop [ci 0]
      (when (< ci nprobe-fast)
        (let [cid (aget cl-ids ci)]
          (when (>= cid 0)
            (knn-range! vectors query k
                        (aget offsets cid) (aget offsets (inc cid)) s)))
        (recur (inc ci))))
    (let [fast-count (count-fraud labels (.top-idx s) (.top-dist s) k)]
      (if (or (= fast-count (dec threshold)) (= fast-count threshold))
        (do
          (loop [ci nprobe-fast]
            (when (< ci nprobe-full)
              (let [cid (aget cl-ids ci)]
                (when (>= cid 0)
                  (knn-range! vectors query k
                              (aget offsets cid) (aget offsets (inc cid)) s)))
              (recur (inc ci))))
          (count-fraud labels (.top-idx s) (.top-dist s) k))
        fast-count))))

(defn knn-ivf-weighted-fraud-score
  "Returns inverse-distance-weighted fraud probability in [0.0, 1.0].
  Closer fraud neighbors contribute more weight than distant ones."
  [{:keys [^ByteBuffer labels ^ShortBuffer vectors ^floats centroids ^ints offsets ^long nlist]}
   ^floats query ^long k ^long nprobe]
  (let [^KnnScratch s (acquire-scratch! k nprobe)]
    (topn-clusters! centroids nlist query nprobe s)
    (loop [ci 0]
      (when (< ci nprobe)
        (let [cid (aget ^ints (.cl-ids s) ci)]
          (when (>= cid 0)
            (knn-range! vectors query k
                        (aget offsets cid) (aget offsets (inc cid)) s)))
        (recur (inc ci))))
    (let [^longs top-idx   (.top-idx s)
          ^doubles top-dist (.top-dist s)]
      (loop [i 0 fraud-w 0.0 total-w 0.0]
        (if (< i k)
          (let [d (aget top-dist i)]
            (if (< d Double/POSITIVE_INFINITY)
              (let [w (/ 1.0 (+ d 1.0e-6))]
                (recur (inc i)
                       (if (= 1 (long (.get ^ByteBuffer labels (int (aget top-idx i)))))
                         (+ fraud-w w)
                         fraud-w)
                       (+ total-w w)))
              (recur (inc i) fraud-w total-w)))
          (if (> total-w 0.0) (/ fraud-w total-w) 0.5))))))
