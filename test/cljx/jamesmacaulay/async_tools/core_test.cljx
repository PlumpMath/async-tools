#+clj
(ns jamesmacaulay.async-tools.core-test
  (:require [jamesmacaulay.async-tools.core :as tools]
            [clojure.core.async :as async :refer [go chan to-chan <! >!]]
            [clojure.core.async.impl.protocols :as impl]
            [jamesmacaulay.async-tools.test :refer (deftest-async)]
            [clojure.test :refer (deftest is testing)]))
#+cljs
(ns jamesmacaulay.async-tools.core-test
  (:require [jamesmacaulay.async-tools.core :as tools]
            [cljs.core.async :as async :refer [chan to-chan <! >!]]
            [cljs.core.async.impl.protocols :as impl]
            [jamesmacaulay.async-tools.test :refer-macros (deftest-async)]
            [cemerick.cljs.test :refer-macros (deftest is testing)])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(deftest-async test-concat
  (go
    (is (= [1 2 3 4 5 6]
           (->> [[1 2] [3] [4 5] [6]]
                (map async/to-chan)
                (apply tools/concat)
                (async/into [])
                <!)))))

(deftest-async test-do-effects
  (go
    (let [box (atom 0)
          in (chan)
          out (chan)
          ret (tools/do-effects (partial async/put! out) in)]
      (is (= in ret))
      (>! in 1)
      (is (= 1 (<! out)))
      (>! in 2)
      (is (= 2 (<! out))))))

(deftest readport?-test
  (is (every? tools/readport? [(chan)
                               (async/map< inc (chan))]))
  (is (not-any? tools/readport? [1
                                 "another"
                                 {}])))



(deftest-async test-constant
  (go
    (let [c (tools/constant 1)]
      (is (= [1 1 1]
             [(<! c) (<! c) (<! c)])))))

(deftest-async test-cast-as-readport
  (go
    (let [channel (chan)
          not-a-channel {}]
      (is (identical? channel
                      (tools/cast-as-readport channel)))
      (is (identical? not-a-channel
                      (<! (tools/cast-as-readport not-a-channel)))))))

(deftest-async test-async-future*
  (go
    (let [fut (tools/async-future* (fn [resolve!]
                                     (go (is (nil? (resolve! (<! (to-chan [1]))))))))]
      (is (= [1 1 1]
             [(<! fut) (<! fut) (<! fut)])))))

; thrown-with-msg? isn't catching the error correctly in the cljs go block above,
; so we'll do a synchronous test:
(deftest test-async-future*-throws-on-multi-resolve!
  (is (thrown-with-msg? #+clj clojure.lang.ExceptionInfo #+cljs js/Error
                        #"resolve"
                        (tools/async-future* (fn [resolve!]
                                               (resolve! 1)
                                               (resolve! 2))))))

(deftest-async test-async-future<
  (go
    (let [fut (tools/async-future< (go 1))]
      (is (= [1 1 1]
             [(<! fut) (<! fut) (<! fut)])))))

(deftest-async test-then<
  (go
    (let [fut (tools/then< inc (go 1))]
      (is (= [2 2 2]
             [(<! fut) (<! fut) (<! fut)])))))

(deftest-async test-all<
  (go
    (let [fut (tools/all< [1 (go 2) 3 (go 4)])]
      (is (= [[1 2 3 4] [1 2 3 4] [1 2 3 4]]
             [(<! fut) (<! fut) (<! fut)])))))

(deftest-async test-race<
  (go
    (let [fut (tools/race< [(go 1) 2])]
      (is (= [2 2 2]
             [(<! fut) (<! fut) (<! fut)])))
    (let [t5 (async/timeout 5)
          t50 (async/timeout 50)
          fut (tools/race< [(go (<! t50) 1)
                            (go (<! t5) 2)])]
      (is (= [2 2 2]
             [(<! fut) (<! fut) (<! fut)])))))
