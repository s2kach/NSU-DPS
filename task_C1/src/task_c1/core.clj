(ns task-c1.core)

(defn strings
  [alphabet n]
  (reduce (fn [acc _]
            (reduce concat
                    (map (fn [s]
                           (let [lastc (subs s (dec (count s)))]
                             (map (fn [c] (str s c))
                                  (remove #(= % lastc) alphabet))))
                         acc)))
          (map str alphabet)
          (range (dec n))))
