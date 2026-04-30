(ns guarita.dataset-test
  (:require [clojure.test :refer [is testing]]
            [guarita.dataset :as dataset]
            [integrant.core :as ig]
            [matcher-combinators.test :refer [match?]]
            [schema.test :as s])
  (:import [java.nio FloatBuffer]))

(def ds
  (ig/init-key :guarita.dataset/dataset
               {:vectors-path "resources/vectors.bin"
                :labels-path  "resources/labels.bin"}))

(defn- vector-at [^FloatBuffer vectors ^long i]
  (let [start (int (* i 14))
        out   (float-array 14)]
    (dotimes [k 14]
      (aset out k (.get vectors (+ start k))))
    out))

(def query (vector-at (:vectors ds) 0))

;; ---- fixtures ----

(def expected-exact-match
  [{:index 0 :distance 0.0 :label keyword?}])

(def expected-result-shape
  {:index integer? :distance number? :label keyword?})

;; ---- knn tests ----

(s/deftest knn-returns-k-results-test
  (testing "it should return exactly k results"
    (let [result (dataset/knn ds query 5)]
      (is (match? 5 (count result))))))

(s/deftest knn-exact-match-has-zero-distance-test
  (testing "it should return index 0 with distance 0.0 as the closest when querying its own vector"
    (let [result (dataset/knn ds query 5)]
      (is (match? expected-exact-match (take 1 result))))))

(s/deftest knn-results-have-expected-shape-test
  (testing "it should return results with :index, :distance and :label keys"
    (let [result (dataset/knn ds query 5)]
      (is (match? (repeat 5 expected-result-shape) result)))))

(s/deftest knn-results-sorted-ascending-test
  (testing "it should return results with non-decreasing distances"
    (let [distances (map :distance (dataset/knn ds query 5))]
      (is (apply <= distances)))))

;; ---- knn-parallel tests ----

(s/deftest knn-parallel-returns-k-results-test
  (testing "it should return exactly k results"
    (let [result (dataset/knn-parallel ds query 5)]
      (is (match? 5 (count result))))))

(s/deftest knn-parallel-exact-match-has-zero-distance-test
  (testing "it should return index 0 with distance 0.0 as the closest when querying its own vector"
    (let [result (dataset/knn-parallel ds query 5)]
      (is (match? expected-exact-match (take 1 result))))))

(s/deftest knn-parallel-matches-sequential-test
  (testing "it should return the same results as the sequential knn"
    (let [sequential (dataset/knn ds query 5)
          parallel   (dataset/knn-parallel ds query 5)]
      (is (match? sequential parallel)))))