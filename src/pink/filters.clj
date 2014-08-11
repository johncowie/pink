(ns pink.filters
  (:require [pink.config :refer [*sr* *ksmps*]]
            [pink.util :refer :all]))

(defn tone 
  "A first-order recursive low-pass filter with variable frequency response. (based on Csound's tone opcode). 
  
  For further information, see: http://csound.github.io/docs/manual/tone.html"
  [afn cutoff]
  (let [TPIDSR (/ (* 2 Math/PI) *sr*)
        cutoff-fn (arg cutoff)
        out ^doubles (create-buffer)
        last-val (double-array 1 0.0)]
    (fn []
      (let [in ^doubles (afn)
            hps ^doubles (cutoff-fn)] 
        (when (and in hps)
          (loop [i 0 last-value (getd last-val)]
            (if (< i *ksmps*)
              (let [hp (aget hps i)
                    b (- 2.0 (Math/cos (* hp TPIDSR)))
                    c2 (- b (Math/sqrt (- (* b b) 1.0)))
                    c1 (- 1.0 c2)
                    new-val (+ (* c1 (aget in i)) 
                               (* c2 last-value))]
                (aset out i new-val) 
                (recur (unchecked-inc-int i) new-val))
              (aset last-val 0 last-value)))
          out)))))