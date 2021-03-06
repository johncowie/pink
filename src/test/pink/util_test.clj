(ns pink.util-test
  (:require [pink.util :refer :all]
            [pink.config :refer :all])
  (:require [clojure.test :refer :all]))


(deftest set-get-d  
  (let [a (create-buffer 22.0)]
    (is (= (aget ^doubles a 0) 22.0))
    (is (= (getd a) 22.0))))

(deftest test-shared
  (let [a (atom 0)
        tfn (shared (fn [] (swap! a inc)))]
    (binding [*current-buffer-num* 0]
      (is (= 1 (tfn))) 
      (is (= 1 (tfn))) 
      (is (= 1 (tfn)))) 
    (binding [*current-buffer-num* 1]
      (is (= 2 (tfn))) 
      (is (= 2 (tfn))) 
      (is (= 2 (tfn))))
    ))


(deftest test-reader
  (let [a (atom 1)
        rdr (reader a)]
    (is (= 1.0 (aget ^doubles (rdr) 0))) 
    (reset! a 3.25) 
    (is (= 3.25 (aget ^doubles (rdr) 0))) 
    ))

(deftest test-with-buffer-size
  (testing "with-buffer-size runs sub-code 4 times"
    (let [counter (atom 0)
          afn (with-buffer-size 16
                (fn [] 
                  (swap! counter inc)
                  (double-array *buffer-size*)))]
      (afn)
      (is (= 4 @counter))))


  (testing "with-buffer-size runs sub-code 8 times with shared afn"
    (let [counter (atom 0)
          afn (with-buffer-size 16
                (shared 
                  (fn [] 
                  (swap! counter inc)
                  (double-array *buffer-size*))))]
      (afn)
      (afn)
      (is (= 8 @counter))))

  (testing "with-buffer-size returns nil if afn returns nil in first buffer"
    (let [counter (atom 0)
          afn (with-buffer-size 16
                (fn [] 
                  (swap! counter inc)
                  nil))
          out (afn)]

      (is (= 1 @counter))
      (is (nil? out))
      ))

  (testing "with-buffer-size returns partial buffer when nil is not first buffer,
           then returns nil"
    (let [counter (atom 0)
          afn (with-buffer-size 16
                (fn [] 
                  (swap! counter inc)
                  (if (>= @counter 3) 
                    nil
                    (double-array *buffer-size* 80))))
          out ^doubles (afn)
          out2 (afn)]

      (is (= 3 @counter)) ;; tests short circuits after first nil found
      (is (= 64 (alength out)))
      (is (= 80.0 (aget out 31)))
      (is (= 0.0 (aget out 32)))
      (is (nil? out2))
      ))

  (testing "with-buffer-size throws exception with invalid buffer-size"
    (let [counter (atom 0)] 
      (is (thrown-with-msg? Exception #"Invalid buffer-size: 33"
                            (with-buffer-size 33
                              (fn [] 
                                (swap! counter inc)
                                (if (>= @counter 3) 
                                  nil
                                  (double-array *buffer-size* 80)))))) 
      )))

(defn done-reader
  []
  (let [done-val *done*]
    (fn []
      (aget done-val 0))))

(deftest test-with-duration
  (let [a (with-duration 1.0
            (done-reader))]
    (is (= false (a)))
    (doseq [x (range (long (/ *sr* *buffer-size*)))]
      (a)) 

    (is (= true (a)))
    ))


(deftest test-max-allocator
  (let [a (create-max-allocator 3)]
    (is (= 0 (num-allocs a)))
    (doseq [_ (range 3)]
      (is (= true (acquire-alloc! a)))) 
    (is (= false (acquire-alloc! a)))
    (is (= 3 (num-allocs a))))
  (let [a (create-max-allocator 3)]
    (doseq [_ (range 3)]
      (is (= true (acquire-alloc! a)))
      (let [temp-afn (with-allocator a (fn [] nil))]
        (is (= 1 (num-allocs a)))
        (temp-afn)
        (is (= 0 (num-allocs a)))))
    (let [v (acquire-alloc! a) 
          temp-afn (with-allocator a (fn [] (double-array 64)))]
      (is (= true v)) 

      (is (= 1 (num-allocs a)))
      (temp-afn)
      (is (= 1 (num-allocs a)))
      )))
