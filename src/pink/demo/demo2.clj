(ns pink.demo.demo2
  (:require [pink.engine :refer :all]
            [pink.config :refer :all]
            [pink.envelopes :refer [env]]
            [pink.oscillators :refer [sine sine2]]
            [pink.util :refer [mul const create-buffer getd setd!]]))


(defn fm-synth [freq]
  (mul
    (sine2 (mul
             freq
             (mul
               (env [0.0 0.0 0.05 2 0.02 1.5 0.2 1.5 0.2 0])
               (sine (* 1 freq)))))
    (mul
      0.5
      (env [0.0 0.0 0.02 1 0.02 0.9 0.2 0.9 0.2 0]))))


(defn demo [e]
  (let [melody (take (* 4 8) (cycle [220 330 440 330]))
        dur 0.25]
    (loop [[x & xs] melody]
      (when x
        (engine-add-afunc e (fm-synth x))
        (engine-add-afunc e (fm-synth (* 2 x)))
        (recur xs)))))



(defn demo-afunc [e]
  (let [melody (ref (take (* 4 8) (cycle [220 330 440 330])))
        dur 0.25 
        cur-time (double-array 1 0.0)
        time-incr (/ *buffer-size* 44100.0)
        out (create-buffer)]
    (engine-add-afunc e (fm-synth 440))
    (fn ^doubles []
      (let [t (+ (getd cur-time) time-incr)]
        (when (>= t dur)
          (engine-add-afunc e (fm-synth 440)))
        (setd! cur-time (rem t dur)))
      out
      )))


;;

(comment


  (def e (engine-create))
  (engine-start e)
  (engine-add-afunc e (demo-afunc e))
  (engine-stop e)

  (engine-clear e)
  e

  (let [e (engine-create)]
    (engine-start e)
    (demo e)
    (Thread/sleep 2000)
    (engine-stop e)))


