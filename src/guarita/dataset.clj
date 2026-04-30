(ns guarita.dataset
  (:require [integrant.core :as ig]
            [taoensso.timbre :as log])
  (:import [java.io RandomAccessFile]
           [java.nio ByteBuffer ByteOrder FloatBuffer]
           [java.nio.channels FileChannel$MapMode]))

(defmethod ig/init-key ::dataset
  [_ {:keys [vectors-path labels-path]}]
  (log/info :starting ::dataset)
  (let [v-file  (RandomAccessFile. ^String vectors-path "r")
        v-ch    (.getChannel v-file)
        v-size  (.size v-ch)
        v-bytes (doto (.map v-ch FileChannel$MapMode/READ_ONLY 0 v-size)
                  (.order ByteOrder/LITTLE_ENDIAN))
        vectors (.asFloatBuffer v-bytes)
        l-file  (RandomAccessFile. ^String labels-path "r")
        l-ch    (.getChannel l-file)
        l-size  (.size l-ch)
        labels  (.map l-ch FileChannel$MapMode/READ_ONLY 0 l-size)
        bytes-per-vector 56
        n       (long (/ v-size bytes-per-vector))]
    (when-not (zero? (rem v-size bytes-per-vector))
      (throw (ex-info "vectors.bin com tamanho inválido"
                      {:size v-size :expected-multiple-of bytes-per-vector})))
    (when-not (= n l-size)
      (throw (ex-info "vectors.bin e labels.bin têm contagens diferentes"
                      {:vectors-count n :labels-count l-size})))
    {:vectors  vectors
     :labels   labels
     :n        n
     ::handles [v-ch v-file l-ch l-file]}))

(defmethod ig/halt-key! ::dataset
  [_ {:keys [::handles]}]
  (log/info :stopping ::dataset)
  (doseq [h handles] (.close h)))

(defn- sq-dist-at
  ^double [^FloatBuffer vectors ^floats query ^long i]
  (let [start (int (* i 14))]
    (loop [k 0 acc 0.0]
      (if (< k 14)
        (let [diff (- (double (.get vectors (+ start k)))
                      (double (aget query k)))]
          (recur (inc k) (+ acc (* diff diff))))
        acc))))

(defn- knn-range
  [dataset query k start end]
  (let [^FloatBuffer vectors (:vectors dataset)
        ^floats query  query
        k     (long k)
        start (long start)
        end   (long end)
        top-idx  (long-array k Long/MAX_VALUE)
        top-dist (double-array k Double/POSITIVE_INFINITY)
        worst    (int-array 1 0)]
    (loop [i start]
      (when (< i end)
        (let [d (sq-dist-at vectors query i)]
          (when (< d (aget top-dist (aget worst 0)))
            (aset top-idx  (aget worst 0) (long i))
            (aset top-dist (aget worst 0) d)
            (loop [j 0 max-d Double/NEGATIVE_INFINITY max-pos 0]
              (if (< j k)
                (if (> (aget top-dist j) max-d)
                  (recur (inc j) (aget top-dist j) j)
                  (recur (inc j) max-d max-pos))
                (aset worst 0 max-pos)))))
        (recur (inc i))))
    (map (fn [idx d] {:index idx :sq-dist d}) top-idx top-dist)))

(defn knn
  "Sequential brute force over all n vectors. Returns k nearest neighbors."
  [{:keys [^ByteBuffer labels ^long n] :as dataset} ^floats query ^long k]
  (->> (knn-range dataset query k 0 n)
       (sort-by :sq-dist)
       (take k)
       (mapv (fn [{:keys [index sq-dist]}]
               {:index    index
                :distance (Math/sqrt sq-dist)
                :label    (case (long (.get labels (int index)))
                            0 :legit
                            1 :fraud)}))))

(defn knn-parallel
  "Parallel KNN: splits dataset across all CPU cores via pmap, merges top-k."
  [{:keys [^ByteBuffer labels ^long n] :as dataset} ^floats query ^long k]
  (let [cores  (.. Runtime getRuntime availableProcessors)
        chunk  (long (Math/ceil (/ n (double cores))))
        ranges (map (fn [s] [s (min n (+ s chunk))]) (range 0 n chunk))]
    (->> ranges
         (pmap (fn [[s e]] (knn-range dataset query k s e)))
         (apply concat)
         (sort-by :sq-dist)
         (take k)
         (mapv (fn [{:keys [index sq-dist]}]
                 {:index    index
                  :distance (Math/sqrt sq-dist)
                  :label    (case (long (.get labels (int index)))
                              0 :legit
                              1 :fraud)})))))
