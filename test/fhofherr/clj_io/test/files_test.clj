(ns fhofherr.clj-io.test.files-test
  (:require [clojure.test :refer :all]
            [fhofherr.clj-io.files :as files]))

(deftest with-tmp-dir
  (testing "creates and deletes a temporary directory"
    (let [tmp-dir (atom nil)]
      (files/with-tmp-dir [path]
        (reset! tmp-dir path)
        (is (true? (files/directory? path))))
      (is (false? (files/exists? @tmp-dir))))))

(deftest mkdir
  (testing "create a single directory"
    (files/with-tmp-dir [tmp-dir]
      (let [new-dir (files/mkdir (.resolve tmp-dir "directory"))]
        (is (files/directory? new-dir))))))

(deftest rm-rf
  (testing "deletes non-empty directories"
    (files/with-tmp-dir [tmp-dir]
      (let [dir1 (files/mkdir (.resolve tmp-dir "directory"))
            dir2 (files/mkdir (.resolve dir1 "subdirectory"))]
        (files/rm-rf dir1)
        (is (false? (files/exists? dir2)))
        (is (false? (files/exists? dir1)))))))
