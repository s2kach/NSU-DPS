(ns task-c2.core)

(defn- sieve
  [n table]
  (lazy-seq
    (if-let [ps (table n)]
      (sieve (inc n)
             (reduce (fn [t p] (update t (+ n p) (fnil conj []) p))
                     (dissoc table n)
                     ps))
      (cons n (sieve (inc n) (update table (* n n) (fnil conj []) n))))))

(def primes (sieve 2 {}))
