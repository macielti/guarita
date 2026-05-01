(ns guarita.dataset
  (:require [integrant.core :as ig]
            [taoensso.timbre :as log])
  (:import [java.io RandomAccessFile]
           [java.nio ByteBuffer ByteOrder FloatBuffer]
           [java.nio.channels FileChannel$MapMode]))

(def ^:private dim 14)
(def ^:private bytes-per-vector (* dim 4))
;; "IVF1" little-endian
(def ^:private ivf-magic 0x31465649)
(def ^:private ivf-header-bytes 16)

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
        vectors (.asFloatBuffer v-buf)
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
  (doseq [h handles] (.close h)))

(defn- sq-dist-buf
  ^double [^FloatBuffer vectors ^floats query ^long i ^floats scratch]
  (let [start (int (* i 14))]
    (.get vectors start scratch 0 14)
    (loop [k 0 acc 0.0]
      (if (< k 14)
        (let [diff (- (double (aget scratch k))
                      (double (aget query k)))]
          (recur (inc k) (+ acc (* diff diff))))
        acc))))

(defn- sq-dist-arr
  ^double [^floats centroids ^long c-off ^floats query]
  (loop [k 0 acc 0.0]
    (if (< k 14)
      (let [diff (- (aget centroids (+ c-off k))
                    (aget query k))]
        (recur (inc k) (+ acc (* diff diff))))
      acc)))

(defn- update-worst! [^doubles top-dist ^ints worst ^long k]
  (loop [j 0 max-d Double/NEGATIVE_INFINITY max-pos 0]
    (if (< j k)
      (if (> (aget top-dist j) max-d)
        (recur (inc j) (aget top-dist j) j)
        (recur (inc j) max-d max-pos))
      (aset worst 0 (int max-pos)))))

(defn- topn-clusters
  "Returns int-array of the nprobe cluster ids closest to query."
  ^ints [^floats centroids ^long nlist ^floats query ^long nprobe]
  (let [top-id   (int-array nprobe -1)
        top-dist (double-array nprobe Double/POSITIVE_INFINITY)
        worst    (int-array 1 0)]
    (loop [c 0]
      (when (< c nlist)
        (let [d (sq-dist-arr centroids (* c 14) query)]
          (when (< d (aget top-dist (aget worst 0)))
            (aset top-id   (aget worst 0) (int c))
            (aset top-dist (aget worst 0) d)
            (update-worst! top-dist worst nprobe)))
        (recur (inc c))))
    top-id))

(defn- knn-range!
  [dataset ^floats query ^long k range-and-buffers]
  (let [^FloatBuffer vectors (:vectors dataset)
        scratch (float-array 14)
        ^long start (:start range-and-buffers)
        ^long end (:end range-and-buffers)
        ^longs top-idx (:top-idx range-and-buffers)
        ^doubles top-dist (:top-dist range-and-buffers)
        ^ints worst (:worst range-and-buffers)]
    (loop [i start]
      (when (< i end)
        (let [d (sq-dist-buf vectors query i scratch)]
          (when (< d (aget top-dist (aget worst 0)))
            (aset top-idx  (aget worst 0) (long i))
            (aset top-dist (aget worst 0) d)
            (update-worst! top-dist worst k)))
        (recur (inc i))))))

(defn- finalize-results
  [^ByteBuffer labels ^longs top-idx ^doubles top-dist ^long k]
  (let [k (int k)]
    (->> (range k)
         (keep (fn [i]
                 (let [d (aget top-dist i)]
                   (when (< d Double/POSITIVE_INFINITY)
                     [(aget top-idx i) d]))))
         (sort-by second)
         (mapv (fn [[^long idx ^double sq-dist]]
                 {:index    idx
                  :distance (Math/sqrt sq-dist)
                  :label    (case (long (.get labels (int idx)))
                              0 :legit
                              1 :fraud)})))))

(defn knn
  "Sequential brute force over all n vectors. Returns k nearest neighbors."
  [{:keys [^ByteBuffer labels ^long n] :as dataset} ^floats query ^long k]
  (let [top-idx  (long-array k Long/MAX_VALUE)
        top-dist (double-array k Double/POSITIVE_INFINITY)
        worst    (int-array 1 0)
        buffers  {:start 0 :end n :top-idx top-idx :top-dist top-dist :worst worst}]
    (knn-range! dataset query k buffers)
    (vec (finalize-results labels top-idx top-dist k))))

(defn knn-ivf
  "IVF-based k-NN: scans only the nprobe clusters whose centroids are nearest
  to the query. Vectors are stored cluster-contiguous in vectors.bin so each
  cluster scan is a tight loop over a flat slice of the mmap."
  [{:keys [^ByteBuffer labels ^floats centroids ^ints offsets ^long nlist] :as dataset}
   ^floats query ^long k ^long nprobe]
  (let [^ints clusters (topn-clusters centroids nlist query nprobe)
        top-idx  (long-array k Long/MAX_VALUE)
        top-dist (double-array k Double/POSITIVE_INFINITY)
        worst    (int-array 1 0)]
    (loop [ci 0]
      (when (< ci nprobe)
        (let [cid (aget clusters ci)]
          (when (>= cid 0)
            (let [buffers {:start (aget offsets cid)
                           :end (aget offsets (inc cid))
                           :top-idx top-idx
                           :top-dist top-dist
                           :worst worst}]
              (knn-range! dataset query k buffers))))
        (recur (inc ci))))
    (vec (finalize-results labels top-idx top-dist k))))
