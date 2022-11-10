(ns com.phronemophobic.clj-glfw
  (:require [com.phronemophobic.clj-glfw.raw :as raw])
  (:import com.sun.jna.Platform
           com.sun.jna.Pointer
           java.util.concurrent.Executors))

(def GL_COLOR_BUFFER_BIT (int 0x00004000))
(def GL_STENCIL_BUFFER_BIT (int 0x00000400))

(def ^:no-doc main-class-loader @clojure.lang.Compiler/LOADER)
(def ^:no-doc void Void/TYPE)

(defmacro ^:no-doc defc
  ([fn-name lib ret]
   `(defc ~fn-name ~lib ~ret []))
  ([fn-name lib ret args]
   (let [cfn-sym (with-meta (gensym "cfn") {:tag 'com.sun.jna.Function})]
     `(let [~cfn-sym (delay (.getFunction ~(with-meta `(deref ~lib) {:tag 'com.sun.jna.NativeLibrary})
                                          ~(name fn-name)))]
        (defn- ~fn-name [~@args]
          (.invoke (deref ~cfn-sym)
                   ~ret (to-array [~@args])))))))

(def ^:no-doc
  objlib (delay
           (com.sun.jna.NativeLibrary/getInstance "CoreFoundation")))

(def ^:no-doc
  main-queue (delay
               (.getGlobalVariableAddress ^com.sun.jna.NativeLibrary @objlib "_dispatch_main_q")))

(deftype ^:no-doc DispatchCallback [f]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (.setContextClassLoader (Thread/currentThread) main-class-loader)

    (import 'com.sun.jna.Native)
    ;; https://java-native-access.github.io/jna/4.2.1/com/sun/jna/Native.html#detach-boolean-
    ;; for some other info search https://java-native-access.github.io/jna/4.2.1/ for CallbackThreadInitializer

    ;; turning off detach here might give a performance benefit,
    ;; but more importantly, it prevents jna from spamming stdout
    ;; with "JNA: could not detach thread"
    (com.sun.jna.Native/detach false)
    (f)
    ;; need turn detach back on so that
    ;; we don't prevent the jvm exiting
    ;; now that we're done
    (com.sun.jna.Native/detach true)))

(defonce dispatch-executor (delay
                             (let [thread-factory
                                   (reify
                                     java.util.concurrent.ThreadFactory
                                     (newThread [this r]
                                       (let [thread (.newThread (Executors/defaultThreadFactory)
                                                                r)]
                                         (.setDaemon thread true)
                                         thread)))]
                               (Executors/newSingleThreadExecutor thread-factory))))

(defc dispatch_sync_f objlib void [queue context work])
(defc dispatch_async_f  objlib void [queue context work])

(defn dispatch-async
  "Run `f` on the main thread. Will return immediately."
  [f]
  (if (Platform/isLinux)
    (.submit @dispatch-executor f)
    (let [callback (DispatchCallback. f)]
      (dispatch_async_f @main-queue nil callback)
      ;; please don't garbage collect me :D
      (identity callback)
      nil)))

(defn dispatch-sync
  "Run `f` on the main thread. Waits for `f` to complete before returning."
  [f]
  (if (Platform/isLinux)
    (.get (.submit @dispatch-executor f))
    (let [callback (DispatchCallback. f)]
      (dispatch_sync_f @main-queue nil callback)
      ;; please don't garbage collect me :D
      (identity callback)
      nil)))

(raw/import-structs!)

(def width 640)
(def height 480)

(defn mouse-button-callback [window button action mods]
  (prn button action mods))

(defn run-window []
  (dispatch-sync
   (fn []
     (when (= 1 (raw/glfwInit))
       (let [window (raw/glfwCreateWindow width height "Hello World" nil nil)]
         (raw/glfwMakeContextCurrent window)
         (raw/glViewport 0 0 width height)
         (raw/glfwSetMouseButtonCallback window
                                         mouse-button-callback)
         (raw/glViewport 0 0 width height)
         (while (zero? (raw/glfwWindowShouldClose window))
           (raw/glClearStencil 0)
           (raw/glClear (bit-or GL_COLOR_BUFFER_BIT
                                GL_STENCIL_BUFFER_BIT))
           (raw/glfwSwapBuffers window)
           (raw/glfwWaitEventsTimeout 1.0))
         (raw/glfwTerminate))))))

