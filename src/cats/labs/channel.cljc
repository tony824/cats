;; Copyright (c) 2014-2016 Andrey Antukh <niwi@niwi.nz>
;; Copyright (c) 2014-2016 Alejandro Gómez <alejandro@dialelo.com>
;; All rights reserved.
;;
;; Redistribution and use in source and binary forms, with or without
;; modification, are permitted provided that the following conditions
;; are met:
;;
;; 1. Redistributions of source code must retain the above copyright
;;    notice, this list of conditions and the following disclaimer.
;; 2. Redistributions in binary form must reproduce the above copyright
;;    notice, this list of conditions and the following disclaimer in the
;;    documentation and/or other materials provided with the distribution.
;;
;; THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
;; IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
;; OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
;; IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
;; INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
;; NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
;; DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
;; THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
;; (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
;; THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

(ns cats.labs.channel
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]))
  #?(:cljs (:require [cljs.core.async :as a]
                     [cljs.core.async.impl.protocols :as impl]
                     [cats.context :as ctx]
                     [cats.core :as m]
                     [cats.protocols :as p]
                     [cats.util :as util])
     :clj  (:require [clojure.core.async :refer [go go-loop] :as a]
                     [clojure.core.async.impl.protocols :as impl]
                     [cats.context :as ctx]
                     [cats.core :as m]
                     [cats.protocols :as p]
                     [cats.util :as util])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Monad definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn with-value
  "Simple helper that creates a channel and attach
  an value to it."
  ([value] (with-value value (a/chan)))
  ([value ch]
   (a/put! ch value)
   ch))

(defn channel?
  "Return true if a `c` is a channel."
  [c]
  (instance? #?(:clj  clojure.core.async.impl.channels.ManyToManyChannel
                :cljs cljs.core.async.impl.channels.ManyToManyChannel) c))

(def ^{:no-doc true}
  context
  (reify
    p/Context
    p/Functor
    (-fmap [_ f mv]
      (a/pipe mv (a/chan 1 (map f))))

    p/Semigroup
    (-mappend [_ sv sv']
      (a/merge [sv sv']))

    p/Monoid
    (-mempty [_]
      (a/to-chan []))

    p/Applicative
    (-pure [_ v]
      (a/to-chan [(if (nil? v) ::nil v)]))

    (-fapply [mn af av]
      (a/map #(%1 %2) [af av]))

    p/Monad
    (-mreturn [_ v]
      (a/to-chan [(if (nil? v) ::nil v)]))

    (-mbind [it mv f]
      (let [ctx ctx/*context*
            out (a/chan)
            bindf #(binding [ctx/*context* ctx] (f %))]
        (a/pipeline-async 1 out #(a/pipe (bindf %1) %2) mv)
        out))

    p/MonadZero
    (-mzero [it]
      (a/to-chan []))

    p/MonadPlus
    (-mplus [it mv mv']
      (a/merge [mv mv']))

    p/Printable
    (-repr [_]
      "#<Channel>")))

(util/make-printable (type context))

(extend-type #?(:clj  clojure.core.async.impl.channels.ManyToManyChannel
                :cljs cljs.core.async.impl.channels.ManyToManyChannel)
  p/Contextual
  (-get-context [_] context))
