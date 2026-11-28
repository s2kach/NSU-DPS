(ns task-c3.core-test
  (:require [clojure.test :refer :all]
            [task-c3.core :refer :all]))

(defn- slow-pred
  [x]
  (Thread/sleep 5)
  (even? x))

(deftest matches-filter-for-various-windows
  (doseq [block-size [1 7 100 5000]
          lookahead [1 4]]
    (is (= (filter even? (range 1000))
           (pfilter even? (range 1000) block-size lookahead))
        (str "block-size=" block-size " lookahead=" lookahead))))

(deftest default-args-match-filter
  (is (= (filter even? (range 1000))
         (pfilter even? (range 1000)))))

(deftest empty-inputs-and-empty-results
  (is (empty? (pfilter even? [])))
  (is (empty? (pfilter even? [] 10 4)))
  (is (empty? (pfilter odd? (range 0 1000 2) 37 3))))

(deftest infinite-input
  (is (= (range 0 20 2)
         (take 10 (pfilter even? (range))))))

(deftest bounded-lookahead-on-infinite-input
  (let [cnt (atom 0)
        pred (fn [x] (swap! cnt inc) (even? x))]
    (is (= 0 (first (pfilter pred (range) 10 2))))
    (Thread/sleep 200)
    (is (<= @cnt 40))))

(deftest lookahead-window-is-bounded-by-lookahead
  (let [procs (.availableProcessors (Runtime/getRuntime))
        cnt (atom 0)
        pred (fn [x] (swap! cnt inc) true)]
    (is (= 500 (count (take 500 (pfilter pred (range) 100 procs)))))
    (Thread/sleep 200)
    (is (<= @cnt (* (+ procs 12) 100)))))

(deftest faster-than-sequential
  (let [coll (range 200)
        t0 (System/nanoTime)
        _ (doall (filter slow-pred coll))
        t1 (System/nanoTime)
        _ (doall (pfilter slow-pred coll 10 8))
        t2 (System/nanoTime)]
    (is (<= (- t2 t1) (* 0.5 (- t1 t0))))))
