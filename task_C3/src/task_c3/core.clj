(ns task-c3.core
  (:gen-class))

(defn pfilter
  ([pred coll]
   (pfilter pred coll 100 (.availableProcessors (Runtime/getRuntime))))
  ([pred coll block-size lookahead]
   (let [futures (fn futures [[b & more]]
                   (when b
                     (lazy-seq
                       (cons (future (doall (filter pred b)))
                             (futures more)))))
         s (futures (seq (partition-all block-size coll)))
         step (fn step [ss dd]
                (when (seq ss)
                  (lazy-seq
                    (cons [(first ss) (first (seq dd))]
                          (step (rest ss) (rest dd))))))
         join (fn join [ps]
                (when-let [p (seq ps)]
                  (lazy-seq
                    (concat (deref (first (first p)))
                            (join (next p))))))]
     (join (step (seq s) (seq (drop lookahead s)))))))

(defn -main
  [& args]
  (let [pred (fn [x] (Thread/sleep 10) (even? x))
        coll (range 1000)]
    (println "filter:" (count (time (doall (filter pred coll)))))
    (println "pfilter:" (count (time (doall (pfilter pred coll)))))
    (shutdown-agents)))
