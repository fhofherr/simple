(ns fhofherr.clj-io.files
  (:refer-clojure :exclude [exists?])
  (:require [clojure.java.io :as io])
  (:import [java.nio.file CopyOption
                          FileVisitResult
                          Files
                          LinkOption
                          SimpleFileVisitor]
           [java.nio.file.attribute FileAttribute PosixFilePermission]))

(defn create-tmp-dir
  []
  (Files/createTempDirectory "tmp-" (make-array FileAttribute 0)))

(defn rm-rf
  [path]
  (Files/walkFileTree path
                      (proxy [SimpleFileVisitor] []
                        (visitFile [file attrs]
                          (Files/delete file)
                          FileVisitResult/CONTINUE)
                        (postVisitDirectory [dir attrs]
                          (Files/delete dir)
                          FileVisitResult/CONTINUE))))

(defn copy-resource
  "TODO: add dedicated tests."
  [resource path]
  (let [is (-> resource
               (io/resource)
               (io/input-stream))]
    (Files/copy is path (make-array CopyOption 0))
    path))

(defmacro with-tmp-dir
  [bnd & body]
  `(let [f# (fn ~bnd ~@body)
         tmp-dir# (create-tmp-dir)]
     (try
       (f# tmp-dir#)
       (finally
         (rm-rf tmp-dir#)))))

(defn exists?
  [path]
  (Files/exists path (make-array LinkOption 0)))

(defn directory?
  [path]
  (Files/isDirectory path (make-array LinkOption 0)))

(defn mkdir
  [path]
  (Files/createDirectory path (make-array FileAttribute 0)))
