(ns guarita.dataset
  (:require [integrant.core :as ig]
            [taoensso.timbre :as log])
  (:import [java.io RandomAccessFile]
           [java.nio ByteBuffer ByteOrder]
           [java.nio.channels FileChannel$MapMode]))

(def ^:private dim 14)
(def ^:private bytes-per-vector (* dim 4))
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
        n       (long (/ v-size bytes-per-vector))
        magic   (.getInt i-buf 0)
        nlist   (.getInt i-buf 4)
        ntotal  (.getInt i-buf 8)
        ivf-dim (.getInt i-buf 12)
        vectors (let [fb (.asFloatBuffer v-buf)
                      fa (float-array (* n dim))]
                  (.get ^java.nio.FloatBuffer fb fa)
                  fa)]
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
  (doseq [h handles] (.close h)))

(defn- sq-dist-at
  ^double [^floats vectors ^floats query ^long i]
  (let [start (unchecked-multiply i 14)]
    (loop [k 0 acc 0.0]
      (if (< k 14)
        (let [diff (- (double (aget vectors (unchecked-add start k)))
                      (double (aget query k)))]
          (recur (unchecked-inc k) (+ acc (* diff diff))))
        acc))))

(defn- sq-dist-arr
  ^double [^floats centroids ^long c-off ^floats query]
  (loop [k 0 acc 0.0]
    (if (< k 14)
      (let [diff (- (aget centroids (unchecked-add c-off k))
                    (aget query k))]
        (recur (unchecked-inc k) (+ acc (* diff diff))))
      acc)))

(defn- update-worst! [^doubles top-dist ^ints worst ^long k]
  (loop [j 0 max-d Double/NEGATIVE_INFINITY max-pos 0]
    (if (< j k)
      (if (> (aget top-dist j) max-d)
        (recur (unchecked-inc j) (aget top-dist j) j)
        (recur (unchecked-inc j) max-d max-pos))
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
        (let [d (sq-dist-arr centroids (unchecked-multiply c 14) query)]
          (when (< d (aget cl-dist (aget cl-worst 0)))
            (aset cl-ids (aget cl-worst 0) (int c))
            (aset cl-dist (aget cl-worst 0) d)
            (update-worst! cl-dist cl-worst nprobe)))
        (recur (unchecked-inc c))))))

(defn- knn-range! [vectors query k start end s]
  (let [^floats vectors vectors
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
        (let [d (sq-dist-at vectors query i)]
          (when (< d (aget top-dist (aget worst 0)))
            (aset top-idx (aget worst 0) (long i))
            (aset top-dist (aget worst 0) d)
            (update-worst! top-dist worst k)))
        (recur (unchecked-inc i))))))

(defn- finalize-results
  [^ByteBuffer labels ^longs top-idx ^doubles top-dist ^long k]
  (let [pairs (java.util.ArrayList.)]
    (loop [i 0]
      (when (< i k)
        (let [d (aget top-dist i)]
          (when (< d Double/POSITIVE_INFINITY)
            (.add pairs (object-array [(aget top-idx i) d]))))
        (recur (unchecked-inc i))))
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
  [{:keys [^ByteBuffer labels ^floats vectors ^long n]} ^floats query ^long k]
  (let [^KnnScratch s (acquire-scratch! k 16)]
    (knn-range! vectors query k 0 n s)
    (vec (finalize-results labels (.top-idx s) (.top-dist s) k))))

(defn knn-ivf
  "IVF-based k-NN: scans only the nprobe clusters whose centroids are nearest
  to the query. Vectors are stored cluster-contiguous in vectors.bin so each
  cluster scan is a tight loop over a flat slice of the mmap."
  [{:keys [^ByteBuffer labels ^floats vectors ^floats centroids ^ints offsets ^long nlist]}
   ^floats query ^long k ^long nprobe]
  (let [^KnnScratch s (acquire-scratch! k nprobe)]
    (topn-clusters! centroids nlist query nprobe s)
    (loop [ci 0]
      (when (< ci nprobe)
        (let [cid (aget ^ints (.cl-ids s) ci)]
          (when (>= cid 0)
            (knn-range! vectors query k
                        (aget offsets cid) (aget offsets (unchecked-inc cid)) s)))
        (recur (unchecked-inc ci))))
    (vec (finalize-results labels (.top-idx s) (.top-dist s) k))))
