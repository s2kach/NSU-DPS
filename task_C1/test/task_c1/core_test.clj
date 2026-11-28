(ns task-c1.core-test
  (:require [clojure.test :refer :all]
            [task-c1.core :refer :all]))

(deftest pairs-of-abc
  (is (= #{"ab" "ac" "ba" "bc" "ca" "cb"}
         (set (strings '("a" "b" "c") 2)))))

(deftest length-one-is-alphabet
  (is (= #{"a" "b" "c"}
         (set (strings '("a" "b" "c") 1)))))

(deftest length-three-of-abc
  (let [res (strings '("a" "b" "c") 3)]
    (is (= 12 (count res)))
    (is (= 12 (count (set res))))
    (is (every? (fn [s]
                  (every? (fn [[a b]] (not= a b))
                          (map vector s (rest s))))
                res))))

(deftest single-letter-alphabet-gives-nothing
  (is (empty? (strings '("a") 2)))
  (is (empty? (strings '("a") 3))))
