(ns guarita.dataset
  (:require [integrant.core :as ig]
            [taoensso.timbre :as log])
  (:import [java.io RandomAccessFile]
           [java.nio ByteBuffer ByteOrder ShortBuffer]
           [java.nio.channels FileChannel$MapMode]))

(def ^:private dim 14)
(def ^:private bytes-per-vector (* dim 2))
(def ^:private scale-inv (/ 1.0 8192.0))
;; "IVF2" little-endian — adds bbox_min/bbox_max sections after offsets
(def ^:private ivf-magic 0x32465649)
(def ^:private ivf-header-bytes 16)

(deftype KnnScratch [^longs top-idx
                     ^doubles top-dist
                     ^ints worst
                     ^ints cl-ids
                     ^doubles cl-dist
                     ^ints cl-worst
                     ^bytes visited])

(defn- make-knn-scratch ^KnnScratch [^long k ^long nprobe ^long nlist]
  (KnnScratch.
   (long-array k Long/MAX_VALUE)
   (double-array k Double/POSITIVE_INFINITY)
   (int-array 1 0)
   (int-array nprobe -1)
   (double-array nprobe Double/POSITIVE_INFINITY)
   (int-array 1 0)
   (byte-array (int (max nlist 1)))))

(def ^:private ^ThreadLocal tl-vec-buf
  (ThreadLocal/withInitial
   (reify java.util.function.Supplier
     (get [_] (short-array 14)))))

;; Default sized for k≤8, nprobe≤16, nlist≤512 — covers all current callers without realloc.
(def ^:private ^ThreadLocal tl-knn-scratch
  (ThreadLocal/withInitial
   (reify java.util.function.Supplier
     (get [_] (atom (make-knn-scratch 8 16 512))))))

(defn- acquire-scratch! ^KnnScratch [^long k ^long nprobe ^long nlist]
  (let [cell (.get tl-knn-scratch)
        ^KnnScratch s @cell]
    (if (and (>= (alength ^longs   (.top-idx  s)) k)
             (>= (alength ^ints    (.cl-ids   s)) nprobe)
             (>= (alength ^bytes   (.visited  s)) nlist))
      (do (java.util.Arrays/fill ^longs   (.top-idx  s) Long/MAX_VALUE)
          (java.util.Arrays/fill ^doubles (.top-dist s) Double/POSITIVE_INFINITY)
          (aset ^ints (.worst    s) 0 (int 0))
          (java.util.Arrays/fill ^ints    (.cl-ids   s) (int -1))
          (java.util.Arrays/fill ^doubles (.cl-dist  s) Double/POSITIVE_INFINITY)
          (aset ^ints (.cl-worst s) 0 (int 0))
          (java.util.Arrays/fill ^bytes   (.visited  s) (byte 0))
          s)
      (let [ns (make-knn-scratch k nprobe (max nlist 1))]
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

(defn- read-bbox ^shorts [^ByteBuffer ivf-buf ^long nlist ^long byte-offset]
  (let [n   (* nlist dim)
        out (short-array n)]
    (dotimes [i n]
      (aset out i (.getShort ivf-buf (int (+ byte-offset (* i 2))))))
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
        ivf-dim (.getInt i-buf 12)
        centroids-bytes (long (* nlist dim 4))
        offsets-bytes   (long (* (inc nlist) 4))
        bbox-min-base   (long (+ ivf-header-bytes centroids-bytes offsets-bytes))
        bbox-max-base   (long (+ bbox-min-base (* nlist dim 2)))]
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
     :bbox-min  (read-bbox i-buf nlist bbox-min-base)
     :bbox-max  (read-bbox i-buf nlist bbox-max-base)
     ::handles  (concat v-handles l-handles i-handles)}))

(defmethod ig/halt-key! ::dataset
  [_ {:keys [::handles]}]
  (log/info :stopping ::dataset)
  (doseq [^java.io.Closeable h handles] (.close h)))

(defn- sq-dist-buf
  "Bulk-reads 14 i16 values via a single ShortBuffer.get call, then computes
  squared Euclidean distance. Checks partial distance (first 8 dims) against
  `worst` before computing the remaining 6 dims."
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
    (+ t3 t2 s5)))

(defn- bbox-lower-sq
  "Minimum possible squared distance from query to any point in cluster c's bounding box.
  Per dim: 0 if query is inside [bmin,bmax], else squared distance to the nearer bound."
  ^double [^shorts bbox-min ^shorts bbox-max ^long c ^floats query]
  (let [off (int (* c dim))
        q0   (double (aget query  0))
        q1   (double (aget query  1))
        q2   (double (aget query  2))
        q3   (double (aget query  3))
        q4   (double (aget query  4))
        q5   (double (aget query  5))
        q6   (double (aget query  6))
        q7   (double (aget query  7))
        q8   (double (aget query  8))
        q9   (double (aget query  9))
        q10  (double (aget query 10))
        q11  (double (aget query 11))
        q12  (double (aget query 12))
        q13  (double (aget query 13))
        mn0  (* (double (int (aget bbox-min (+ off  0)))) scale-inv)
        mn1  (* (double (int (aget bbox-min (+ off  1)))) scale-inv)
        mn2  (* (double (int (aget bbox-min (+ off  2)))) scale-inv)
        mn3  (* (double (int (aget bbox-min (+ off  3)))) scale-inv)
        mn4  (* (double (int (aget bbox-min (+ off  4)))) scale-inv)
        mn5  (* (double (int (aget bbox-min (+ off  5)))) scale-inv)
        mn6  (* (double (int (aget bbox-min (+ off  6)))) scale-inv)
        mn7  (* (double (int (aget bbox-min (+ off  7)))) scale-inv)
        mn8  (* (double (int (aget bbox-min (+ off  8)))) scale-inv)
        mn9  (* (double (int (aget bbox-min (+ off  9)))) scale-inv)
        mn10 (* (double (int (aget bbox-min (+ off 10)))) scale-inv)
        mn11 (* (double (int (aget bbox-min (+ off 11)))) scale-inv)
        mn12 (* (double (int (aget bbox-min (+ off 12)))) scale-inv)
        mn13 (* (double (int (aget bbox-min (+ off 13)))) scale-inv)
        mx0  (* (double (int (aget bbox-max (+ off  0)))) scale-inv)
        mx1  (* (double (int (aget bbox-max (+ off  1)))) scale-inv)
        mx2  (* (double (int (aget bbox-max (+ off  2)))) scale-inv)
        mx3  (* (double (int (aget bbox-max (+ off  3)))) scale-inv)
        mx4  (* (double (int (aget bbox-max (+ off  4)))) scale-inv)
        mx5  (* (double (int (aget bbox-max (+ off  5)))) scale-inv)
        mx6  (* (double (int (aget bbox-max (+ off  6)))) scale-inv)
        mx7  (* (double (int (aget bbox-max (+ off  7)))) scale-inv)
        mx8  (* (double (int (aget bbox-max (+ off  8)))) scale-inv)
        mx9  (* (double (int (aget bbox-max (+ off  9)))) scale-inv)
        mx10 (* (double (int (aget bbox-max (+ off 10)))) scale-inv)
        mx11 (* (double (int (aget bbox-max (+ off 11)))) scale-inv)
        mx12 (* (double (int (aget bbox-max (+ off 12)))) scale-inv)
        mx13 (* (double (int (aget bbox-max (+ off 13)))) scale-inv)
        d0   (if (< q0  mn0)  (- mn0  q0)  (if (> q0  mx0)  (- q0  mx0)  0.0))
        d1   (if (< q1  mn1)  (- mn1  q1)  (if (> q1  mx1)  (- q1  mx1)  0.0))
        d2   (if (< q2  mn2)  (- mn2  q2)  (if (> q2  mx2)  (- q2  mx2)  0.0))
        d3   (if (< q3  mn3)  (- mn3  q3)  (if (> q3  mx3)  (- q3  mx3)  0.0))
        d4   (if (< q4  mn4)  (- mn4  q4)  (if (> q4  mx4)  (- q4  mx4)  0.0))
        d5   (if (< q5  mn5)  (- mn5  q5)  (if (> q5  mx5)  (- q5  mx5)  0.0))
        d6   (if (< q6  mn6)  (- mn6  q6)  (if (> q6  mx6)  (- q6  mx6)  0.0))
        d7   (if (< q7  mn7)  (- mn7  q7)  (if (> q7  mx7)  (- q7  mx7)  0.0))
        d8   (if (< q8  mn8)  (- mn8  q8)  (if (> q8  mx8)  (- q8  mx8)  0.0))
        d9   (if (< q9  mn9)  (- mn9  q9)  (if (> q9  mx9)  (- q9  mx9)  0.0))
        d10  (if (< q10 mn10) (- mn10 q10) (if (> q10 mx10) (- q10 mx10) 0.0))
        d11  (if (< q11 mn11) (- mn11 q11) (if (> q11 mx11) (- q11 mx11) 0.0))
        d12  (if (< q12 mn12) (- mn12 q12) (if (> q12 mx12) (- q12 mx12) 0.0))
        d13  (if (< q13 mn13) (- mn13 q13) (if (> q13 mx13) (- q13 mx13) 0.0))]
    (+ (* d0 d0) (* d1 d1) (* d2 d2) (* d3 d3)
       (* d4 d4) (* d5 d5) (* d6 d6) (* d7 d7)
       (* d8 d8) (* d9 d9) (* d10 d10) (* d11 d11)
       (* d12 d12) (* d13 d13))))

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
  (let [^KnnScratch s (acquire-scratch! k 16 1)]
    (knn-range! vectors query k 0 n s)
    (vec (finalize-results labels (.top-idx s) (.top-dist s) k))))

(defn knn-ivf
  "IVF-based k-NN: scans only the nprobe clusters whose centroids are nearest
  to the query. Vectors are stored cluster-contiguous in vectors.bin so each
  cluster scan is a tight loop over a flat slice of the mmap."
  [{:keys [^ByteBuffer labels ^ShortBuffer vectors ^floats centroids ^ints offsets ^long nlist]}
   ^floats query ^long k ^long nprobe]
  (let [^KnnScratch s (acquire-scratch! k nprobe 1)]
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
  "IVF k-NN fraud count with bounding-box repair.
  Scans the nprobe nearest clusters first (fast path), then checks every
  remaining cluster via its axis-aligned bounding box: if the minimum possible
  squared distance from the query to the bbox is ≤ current worst neighbor
  distance, that cluster is also scanned. This guarantees no neighbor closer
  than the current worst is skipped, giving near-exact recall with adaptive
  cost."
  [{:keys [^ByteBuffer labels ^ShortBuffer vectors ^floats centroids ^ints offsets
           ^shorts bbox-min ^shorts bbox-max ^long nlist]}
   ^floats query k nprobe]
  (let [k      (long k)
        nprobe (long nprobe)
        ^KnnScratch s  (acquire-scratch! k nprobe nlist)
        ^ints cl-ids   (.cl-ids s)
        ^ints offsets  offsets
        ^bytes visited (.visited s)]
    (topn-clusters! centroids nlist query nprobe s)
    (sort-cl-ids-by-dist! cl-ids (.cl-dist s) nprobe)
    ;; Scan top-nprobe clusters and mark them visited
    (loop [ci 0]
      (when (< ci nprobe)
        (let [cid (aget cl-ids ci)]
          (when (>= cid 0)
            (aset visited cid (byte 1))
            (knn-range! vectors query k
                        (aget offsets cid) (aget offsets (inc cid)) s)))
        (recur (inc ci))))
    ;; Bbox repair: scan any unvisited cluster whose bbox lower bound
    ;; is ≤ current worst neighbor distance (could contain a better neighbor)
    (let [^doubles top-dist (.top-dist s)
          ^ints worst       (.worst s)]
      (loop [c 0]
        (when (< c nlist)
          (when (zero? (aget visited c))
            (let [w  (aget top-dist (aget worst 0))
                  lb (bbox-lower-sq bbox-min bbox-max c query)]
              (when (<= lb w)
                (knn-range! vectors query k
                            (aget offsets c) (aget offsets (inc c)) s))))
          (recur (inc c)))))
    (count-fraud labels (.top-idx s) (.top-dist s) k)))

(defn knn-ivf-weighted-fraud-score
  "Returns inverse-distance-weighted fraud probability in [0.0, 1.0].
  Closer fraud neighbors contribute more weight than distant ones."
  [{:keys [^ByteBuffer labels ^ShortBuffer vectors ^floats centroids ^ints offsets ^long nlist]}
   ^floats query ^long k ^long nprobe]
  (let [^KnnScratch s (acquire-scratch! k nprobe 1)]
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
