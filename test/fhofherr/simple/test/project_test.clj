(ns fhofherr.simple.test.project-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.project :as prj]
            [clojure.java.io :as io])
  (:import [java.nio.file CopyOption Files SimpleFileVisitor FileVisitResult]
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

(deftest execute-a-shell-script
  (with-tmp-dir
    [path]
    (let [script-path (-> "tests/execute-shell-script.sh"
                          (copy-resource (.resolve path "execute-shell-script.sh"))
                          (Files/setPosixFilePermissions
                            #{PosixFilePermission/OWNER_READ PosixFilePermission/OWNER_EXECUTE})
                          (.getFileName)
                          (str))
          result ((prj/execute script-path) {:project-dir path})]
      (is (= 0 (:exit result))))))
