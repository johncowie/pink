(ns pink.envelopes
  "Envelope Generator Functions"
  (:require [clojure.pprint :refer [pprint]]
            [pink.config :refer :all]
            [pink.util :refer :all]
            [primitive-math :refer [not==]])
  (:import [java.util Arrays])
  )

(defn- make-env-data 
  "Takes in a list of time tagged pairs (time value) and function for
  calculating the cached value and then creates a list with initial value,
  followed by a list of pairs of (num-samples, cached-value). opfn should
  take in [end start run]"
  [pts opfn]
  {:pre (even? (count pts))}
  (let [pairs (partition 2 pts)
        [x & xs] (if (not== (double (ffirst pairs)) 0.0)
                   (cons [0.0 (second (first pairs))] pairs)
                   pairs)]
     
    (cons (second x) 
          (second (reduce 
              (fn [[[a b] lst] [^double c ^double d :as p]] 
                (let [run (double (* c (double *sr*)))
                      rise (double (opfn d b run))
                      last-pt (last lst) 
                      total-run (if (nil? last-pt) 0.0 ^double (first last-pt))] 
                      [p (conj lst [(+ total-run run) rise])] ))
                         [x []] xs)))))

(defn get-line-pt 
  [^long sample linedata]
  (loop [[x & xs] linedata]
    (when x
      (if (< sample ^double (first x))
        x
        (recur xs)))))

(defn env
  "Generates an envelope given pairs of values (t0, v0, t1, v1 ...) where tx is duration of segment."
  [pts] 
  {:pre (even? (count pts))}
  (let [[start & linedata] (make-env-data pts #(/ (- ^double %1 ^double %2) ^double %3))
        cur-val (double-array 1 start)
        counter (long-array 1 0)
        ^doubles out (create-buffer)
        len (alength out)]
    (fn ^doubles[]
      (let [cnt (getl counter)
            [^double last-sam ^double increment] (get-line-pt cnt linedata)]
        (if (and last-sam increment) 
          (loop [end last-sam
                 incr increment 
                 v (getd cur-val)
                 c cnt
                 i 0]
            (if (< i len)
              (if (and end incr)
                (if (< c end)
                  (let [new-v (+ v incr)]
                    (aset out i new-v)
                    (recur end incr new-v (unchecked-inc c) (unchecked-inc i)))
                  (let [[new-end new-incr] (get-line-pt c linedata)]
                    (recur new-end new-incr v c i)))
                (do 
                  (aset out i 0.0)
                  (recur nil nil 0.0 (unchecked-inc c) (unchecked-inc i))))
              (do
                (aset cur-val 0 v)
                (aset counter 0 c)
                out)))
          nil)))))


;; EXPONENTIAL ENVELOPE

(defn- adjust-for-zero
  "for exponential envelopes, can not have zero"
  ^double [^double x]
  (if (zero? x)
    0.0000000001
    x))

(defn- make-exp-env-data [pts]
  {:pre (even? (count pts))}
  (let [adjusted-pts 
        (map #(if (even? %2) %1 (adjust-for-zero %1)) pts (range))]
   (make-env-data adjusted-pts #(Math/pow (/ ^double %1 ^double %2) (/ 1.0 ^double %3)))))


;; TODO - this is almost exactly the same as env except
;; the calculation for new-v.  Should probably make a 
;; macro of the body and share between env and exp-env
(defn exp-env
  "Generates an exponential envelope given pairs of values (t0, v0, t1, v1 ...) where tx is duration of segment."
  [pts] 
  {:pre (even? (count pts))}
  (let [[start & linedata] (make-exp-env-data pts)
        cur-val (double-array 1 start)
        counter (long-array 1 0)
        ^doubles out (create-buffer)
        len (alength out)]
    ;(generator
    ;  [counter 0]

    ;  )
    (fn ^doubles[]
      (let [cnt (getl counter)
            [^double last-sam ^double increment] (get-line-pt cnt linedata)]
        (if (and last-sam increment) 
          (loop [end last-sam
                 incr increment 
                 v (getd cur-val)
                 c cnt
                 i 0]
            (if (< i len)
               (if (and end incr)
                (if (< c end)
                  (let [new-v (* v incr)]
                    (aset out i new-v)
                    (recur end incr new-v (unchecked-inc c) (unchecked-inc i)))
                  (let [[new-end new-incr] (get-line-pt c linedata)]
                    (recur new-end new-incr v c i)))
                (do 
                  (aset out i 0.0)
                  (recur nil nil 0.0 (unchecked-inc c) (unchecked-inc i))))
              (do
                (aset cur-val 0 v)
                (aset counter 0 c)
                out)))
          nil)))))

;; Transeg

(defn transeg 
  "Line segment generator using formula for each segment:
  
  ibeg +  (ivalue - ibeg) * (1 - exp( i*itype/(n-1) )) / 
      (1 - exp(itype))
  
  for n steps. Points are given in format of:

  ia, idur, itype, ib, idur2, itype2, ic...

  where i_ is a value, dur is time in seconds, and itype affects the curve of the segment (0 = straight line, < 0 = conve, > 0 = concave))"
  [[start & segments]] 
  {:pre (zero? (mod (count segments) 3))}
  (let [out (create-buffer)]
    (fn []

      )  
    ))


;; Simple Envs

(defn- adsr-calc-coef
 ^double [^double target-ratio ^double samps] 
 (Math/exp (/ (- (Math/log (/ (+ 1.0 target-ratio) target-ratio))) 
              samps)))

(defn- adsr-impl
  "ADSR that checks for *done* flag before doing release."
  [^double a ^double d ^double s ^double r]
  (let [^doubles out (create-buffer)
        done *done*
        sr (double *sr*)
        buffer-size (long *buffer-size*)
        attack-ratio 0.3
        decay-ratio 0.0001
        attack-samps (* a sr) 
        attack-coef (adsr-calc-coef attack-ratio attack-samps) 
        attack-base (* (+ 1.0 attack-ratio) (- 1.0 attack-coef))
        decay-samps (* d sr) 
        decay-coef (adsr-calc-coef decay-ratio decay-samps)
        decay-base (* (- s decay-ratio) (- 1.0 decay-coef))
        release-samps (* r sr) 
        release-coef (adsr-calc-coef decay-ratio release-samps)
        release-base (* (- decay-ratio) (- 1.0 release-coef))
        ^doubles last-val (double-array 1 0.0)
        stage (atom :attack)]
    (fn []
      (if (= @stage :complete)
        nil

        (do
          (when (and (is-done? done) (not= :release @stage))
            (reset! stage :release))
          (loop [indx 0 
                 last-v (aget last-val 0)
                 cur-stage @stage
                 ]
            (if (< indx buffer-size) 
              (do 
                (aset out indx last-v)
                (case cur-stage

                  :attack
                  (let [v (+ attack-base (* last-v attack-coef))]
                    (if (>= v 1.0)
                      (do
                        (recur (unchecked-inc indx) 1.0 :decay)) 
                      (recur (unchecked-inc indx) v cur-stage)
                      ))

                  :decay
                  (let [v (+ decay-base (* last-v decay-coef))]
                    (if (<= v s)
                      (do
                        (recur (unchecked-inc indx) s :sustain)) 
                      (recur (unchecked-inc indx) v cur-stage)
                      ))

                  :sustain
                  (do 
                    (Arrays/fill out indx buffer-size s)
                    (recur buffer-size s cur-stage))

                  :release 

                  (let [v (+ release-base (* last-v release-coef))]
                    (if (<= v 0.0)
                      (do
                        (Arrays/fill out indx buffer-size 0.0)
                        (recur buffer-size 0.0 :complete)) 
                      (recur (unchecked-inc indx) v cur-stage)
                      ))
                  ))
              (do 
                (reset! stage cur-stage)
                (aset last-val 0 last-v)
                out))))
        ))))   

(defn adsr 
  "Attack-Decay-Sustain-Release Envelope. If *done* boolean array flag is used,
  will await until done is set to true before performing release stage.  Otherwise, 
  defaults to *duration* or 1.0 for total time of envelope.
  
  Based on code by Nigel Redmon at http://www.earlevel.com/main/2013/06/03/envelope-generators-adsr-code/
  "
  [^double a ^double d ^double s ^double r ]
  (cond 
    *done* 
    (adsr-impl a d s r)     

    *duration* 
    (with-duration (double *duration*)
      (adsr-impl a d s r)) 

    :else
    (with-duration 1.0 
      (adsr-impl a d s r)) 

    ))

(defn adsr140
  "ADSR Envelope generator based on Doepfer A-140. Takes in gate audio
  function, as well as retrigger audio function (pass in 0 if not using
  retrigger). 
 
  Based on code by Nigel Redmon at
  http://www.earlevel.com/main/2013/06/03/envelope-generators-adsr-code/"
  [gatefn retrigger attack decay sustain release]
  (let [^doubles out (create-buffer)
        a (double attack)
        d (double decay)
        s (double sustain)
        r (double release)
        sr (double *sr*)
        buffer-size (long *buffer-size*)
        attack-ratio 0.3
        decay-ratio 0.0001
        attack-samps (* a sr) 
        attack-coef (adsr-calc-coef attack-ratio attack-samps) 
        attack-base (* (+ 1.0 attack-ratio) (- 1.0 attack-coef))
        decay-samps (* d sr) 
        decay-coef (adsr-calc-coef decay-ratio decay-samps)
        decay-base (* (- s decay-ratio) (- 1.0 decay-coef))
        release-samps (* r sr) 
        release-coef (adsr-calc-coef decay-ratio release-samps)
        release-base (* (- decay-ratio) (- 1.0 release-coef))
        ^doubles last-val (double-array 1 0.0)
        retriggerfn (if (number? retrigger) (const 0.0) retrigger)
        stage (atom :attack)]
    (fn []
      (let [^doubles gate (gatefn)
            ^doubles retrigger (retriggerfn)]
        (when (and gate retrigger)
          (loop [indx 0 
                 last-v (aget last-val 0)
                 cur-stage @stage]
            (if (< indx buffer-size) 
              (let [ gate-val (aget gate indx)
                    retrigger-val (aget retrigger indx)] 
                (aset out indx last-v)
                (if (pos? gate-val)
                  (let [state (if (pos? retrigger-val) :attack cur-stage)]
                    (case state 

                      :attack
                      (let [v (+ attack-base (* last-v attack-coef))]
                        (if (>= v 1.0)
                          (do
                            (recur (unchecked-inc indx) 1.0 :decay)) 
                          (recur (unchecked-inc indx) v state)
                          ))

                      :decay
                      (let [v (+ decay-base (* last-v decay-coef))]
                        (if (<= v s)
                          (do
                            (recur (unchecked-inc indx) s :sustain)) 
                          (recur (unchecked-inc indx) v state)
                          ))

                      :sustain
                      (recur (unchecked-inc indx) s :sustain)))  
                  (if (zero? last-v) 
                    (recur (unchecked-inc indx) 0.0 :attack)
                    (let [v (+ release-base (* last-v release-coef))]
                      (if (<= v 0.0)
                        (recur (unchecked-inc indx) 0.0 :attack)
                        (recur (unchecked-inc indx) v :attack))))
                  ))
              (do 
                (reset! stage cur-stage)
                (aset last-val 0 last-v)
                out)
              )))))))   


(defn xar 
  "Exponential Attack-Release Envelope"
  [a r]
  (exp-env [0.0 0.00001 a 1.0 r 0.00001]))

