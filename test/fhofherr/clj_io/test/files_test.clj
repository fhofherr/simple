(ns fhofherr.clj-io.test.files-test
  (:require [clojure.test :refer :all]
            [fhofherr.clj-io.files :as files])
  (:import [java.time Instant]
           [java.nio.file.attribute PosixFilePermission]))

(deftest with-tmp-dir
  (testing "creates and deletes a temporary directory"
    (let [tmp-dir (atom nil)]
      (files/with-tmp-dir [path]
        (reset! tmp-dir path)
        (is (true? (files/directory? path))))
      (is (false? (files/exists? @tmp-dir))))))

(deftest with-tmp-file
  (testing "creates and deletes a temporary file"
    (let [tmp-file (atom nil)]
      (files/with-tmp-file [path]
        (reset! tmp-file path)
        (is (true? (files/exists? path))))
      (is (false? (files/exists? @tmp-file))))))

(deftest posix-permissions
  (files/with-tmp-dir [tmp-dir]
    (let [file (files/touch (.resolve tmp-dir "some-file"))
          perms (files/posix-permissions file)]
      (is (not-empty perms))
      (is (every? #(instance? PosixFilePermission %) perms)))))

(deftest set-posix-permissions

  (testing "clear all permissions"
    (files/with-tmp-file [file]
      (files/set-posix-permissions file #{})
      (is (empty? (files/posix-permissions file)))))

  (testing "set a new set of permissions"
    (files/with-tmp-file [file]
      (files/set-posix-permissions file #{PosixFilePermission/OWNER_EXECUTE})
      (is (= #{PosixFilePermission/OWNER_EXECUTE}
             (files/posix-permissions file))))))

(deftest readable?

  (testing "readable by owner"
    (files/with-tmp-file [file]
      (files/set-posix-permissions file #{PosixFilePermission/OWNER_READ})
      (is (true? (files/readable? file :by :owner)))
      (is (true? (files/readable? file)))))

  (testing "readable by group"
    (files/with-tmp-file [file]
      (files/set-posix-permissions file #{PosixFilePermission/GROUP_READ})
      (is (true? (files/readable? file :by :group)))
      (is (false? (files/readable? file)))))

  (testing "readable by others"
    (files/with-tmp-file [file]
      (files/set-posix-permissions file #{PosixFilePermission/OTHERS_READ})
      (is (true? (files/readable? file :by :others)))
      (is (false? (files/readable? file))))))

(deftest writable?

  (testing "writable by owner"
    (files/with-tmp-file [file]
      (files/set-posix-permissions file #{PosixFilePermission/OWNER_WRITE})
      (is (true? (files/writable? file :by :owner)))
      (is (true? (files/writable? file)))))

  (testing "writable by group"
    (files/with-tmp-file [file]
      (files/set-posix-permissions file #{PosixFilePermission/GROUP_WRITE})
      (is (true? (files/writable? file :by :group)))
      (is (false? (files/writable? file)))))

  (testing "writable by others"
    (files/with-tmp-file [file]
      (files/set-posix-permissions file #{PosixFilePermission/OTHERS_WRITE})
      (is (true? (files/writable? file :by :others)))
      (is (false? (files/writable? file))))))

(deftest executable?

  (testing "executable by owner"
    (files/with-tmp-file [file]
      (files/set-posix-permissions file #{PosixFilePermission/OWNER_EXECUTE})
      (is (true? (files/executable? file :by :owner)))
      (is (true? (files/executable? file)))))

  (testing "executable by group"
    (files/with-tmp-file [file]
      (files/set-posix-permissions file #{PosixFilePermission/GROUP_EXECUTE})
      (is (true? (files/executable? file :by :group)))
      (is (false? (files/executable? file)))))

  (testing "executable by others"
    (files/with-tmp-file [file]
      (files/set-posix-permissions file #{PosixFilePermission/OTHERS_EXECUTE})
      (is (true? (files/executable? file :by :others)))
      (is (false? (files/executable? file))))))

(deftest chmod

  (testing "set the file permissions for the owner"
    (files/with-tmp-file [file]
      (-> file
          (files/set-posix-permissions #{})
          (files/chmod "rwx"))
      (is (files/readable? file :by :owner))
      (is (files/writable? file :by :owner))
      (is (files/executable? file :by :owner)))

    (files/with-tmp-file [file]
      (-> file
          (files/set-posix-permissions #{})
          (files/chmod :owner "rwx"))
      (is (files/readable? file :by :owner))
      (is (files/writable? file :by :owner))
      (is (files/executable? file :by :owner))))

  (testing "set the file permissions for group and others"
    (files/with-tmp-file [file]
      (-> file
          (files/set-posix-permissions #{})
          (files/chmod :group "r" :others "x"))
      (is (files/readable? file :by :group))
      (is (not (files/readable? file :by :others)))
      (is (files/executable? file :by :others))
      (is (not (files/executable? file :by :group))))))

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

(deftest touch

  (testing "creates a not yet existing file"
    (files/with-tmp-dir [tmp-dir]
      (let [new-file (.resolve tmp-dir "new_file")]
        (files/touch new-file)
        (is (files/exists? new-file)))))

  (testing "it sets the mtime and atime of existing files to the given Instant"
    (files/with-tmp-dir [tmp-dir]
      (let [file (files/touch (.resolve tmp-dir "some-file"))
            epoch (Instant/EPOCH)]
        (files/touch file epoch)
        (is (= epoch (files/mtime file)))
        (is (= epoch (files/atime file))))))

  (testing "it sets the mtime and atime of existing files to now"
    (files/with-tmp-dir [tmp-dir]
      (let [file (files/touch (.resolve tmp-dir "some-file") Instant/EPOCH)
            now (Instant/now)
            max-delta-millis 1000]
        (files/touch file)
        (is (<= (- (.toEpochMilli now)
                   (.toEpochMilli (files/mtime file)))
                max-delta-millis))
        (is (<= (- (.toEpochMilli now)
                   (.toEpochMilli (files/atime file)))
                max-delta-millis))))))
