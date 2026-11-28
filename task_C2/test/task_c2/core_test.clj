(ns task-c2.core-test
  (:require [clojure.test :refer :all]
            [task-c2.core :refer :all]))

(defn- prime-by-division?
  [n]
  (and (< 1 n)
       (every? #(pos? (mod n %)) (range 2 n))))

(deftest first-ten
  (is (= '(2 3 5 7 11 13 17 19 23 29)
         (take 10 primes))))

(deftest hundredth-and-thousandth
  (is (= 541 (nth primes 99)))
  (is (= 7919 (nth primes 999))))

(deftest below-100
  (is (= 25 (count (take-while #(< % 100) primes)))))

(deftest thousand-strictly-increasing-and-prime
  (let [ps (take 1000 primes)]
    (is (apply < ps))
    (is (every? prime-by-division? ps))))

(deftest lazy-head
  (is (= 2 (first primes))))
