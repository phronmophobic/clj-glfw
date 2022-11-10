(ns com.phronemophobic.clj-glfw.raw
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [com.phronemophobic.clong.clang :as clang]
            [clojure.edn :as edn]
            [com.phronemophobic.clong.gen.jna :as gen]
            [com.rpl.specter :as specter])
  (:import java.io.PushbackReader))


(def ^:private lib
  (com.sun.jna.NativeLibrary/getProcess))


(defonce ^:private libs (atom #{}))
(defn ^:private load-libs
  ([lib-names]
   (let [new-libs
         (into []
               (map #(com.sun.jna.NativeLibrary/getInstance %))
               lib-names)]
     
     (swap! libs into new-libs))))
(load-libs ["glfw.3"])



(defn ^:private  parse-glfw-api []
  (clang/easy-api (.getAbsolutePath
                   (io/file "glfw-3.3.8.bin.MACOS/include/GLFW/glfw3.h"))
                  (into clang/default-arguments
                        ["-include" "/Library/Developer/CommandLineTools/SDKs/MacOSX12.3.sdk/System/Library/Frameworks/OpenGL.framework/Versions/A/Headers/gl.h"])))


(defn ^:private dump-glfw-api []
  (let [api (parse-glfw-api)]
    (with-open [w (io/writer (io/file
                              "resources"
                              "glfw-api.edn"))]
      (clang/write-edn w api))))


(def glfw-api
  (with-open [rdr (io/reader (io/file "resources"
                                        "glfw-api.edn"))
                pbr (PushbackReader. rdr)]
      (edn/read pbr)))

(def ^:private generated-api (gen/def-api lib glfw-api "com.phronemophobic.clj_glfw.raw.structs"))
(defmacro import-structs! []
  `(gen/import-structs! glfw-api "com.phronemophobic.clj_glfw.raw.structs"))

