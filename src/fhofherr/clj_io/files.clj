(ns fhofherr.clj-io.files
  (:refer-clojure :exclude [exists?])
  (:require [clojure.java.io :as io]
            [clojure.set :as s])
  (:import [java.nio.file CopyOption
            FileVisitResult
            Files
            LinkOption
            SimpleFileVisitor]
           [java.time Instant]
           [java.nio.file.attribute FileAttribute
            FileTime
            PosixFileAttributes
            PosixFileAttributeView
            PosixFilePermission]))

(def ^:private no-follow-links (into-array LinkOption
                                           [LinkOption/NOFOLLOW_LINKS]))
(def ^:private follow-links (make-array LinkOption 0))

;; TODO: do we really need this?
(defn- posix-file-attribute-view
  [path]
  (Files/getFileAttributeView path PosixFileAttributeView no-follow-links))

(defn- posix-file-attributes
  [path]
  (Files/readAttributes path PosixFileAttributes no-follow-links))

(defn posix-permissions
  [path]
  (->> path
       (posix-file-attributes)
       (.permissions)
       (into #{})))

(defn set-posix-permissions
  [path posix-permissions]
  (let [fav (posix-file-attribute-view path)]
    (Files/setPosixFilePermissions path posix-permissions)))

(defn has-posix-permissions?
  [path expected-perms]
  (->> path
       (posix-permissions)
       (s/intersection expected-perms)
       (not-empty)
       (boolean)))

(defn readable?
  [path & {:keys [by] :or {by :owner}}]
  {:pre [(#{:owner :group :others} by)]}
  (case by
    :owner (has-posix-permissions? path #{PosixFilePermission/OWNER_READ})
    :group (has-posix-permissions? path #{PosixFilePermission/GROUP_READ})
    :others (has-posix-permissions? path #{PosixFilePermission/OTHERS_READ})))

(defn writable?
  [path & {:keys [by] :or {by :owner}}]
  {:pre [(#{:owner :group :others} by)]}
  (case by
    :owner (has-posix-permissions? path #{PosixFilePermission/OWNER_WRITE})
    :group (has-posix-permissions? path #{PosixFilePermission/GROUP_WRITE})
    :others (has-posix-permissions? path #{PosixFilePermission/OTHERS_WRITE})))

(defn executable?
  [path & {:keys [by] :or {by :owner}}]
  {:pre [(#{:owner :group :others} by)]}
  (case by
    :owner (has-posix-permissions? path #{PosixFilePermission/OWNER_EXECUTE})
    :group (has-posix-permissions? path #{PosixFilePermission/GROUP_EXECUTE})
    :others (has-posix-permissions? path #{PosixFilePermission/OTHERS_EXECUTE})))

(def ^:private available-perms)

(defn- parse-perms
  [permstr perm-type]
  {:pre [(#{:owner :group :others} perm-type)]}
  (let [available-perms {:owner {\r PosixFilePermission/OWNER_READ
                                 \w PosixFilePermission/OWNER_WRITE
                                 \x PosixFilePermission/OWNER_EXECUTE}
                         :group {\r PosixFilePermission/GROUP_READ
                                 \w PosixFilePermission/GROUP_WRITE
                                 \x PosixFilePermission/GROUP_EXECUTE}
                         :others {\r PosixFilePermission/OTHERS_READ
                                  \w PosixFilePermission/OTHERS_WRITE
                                  \x PosixFilePermission/OTHERS_EXECUTE}}
        lookup-perm (fn [c]
                      {:pre [(#{\r \w \x} c)]}
                      (get-in available-perms [perm-type c]))]
    (->> permstr
         (map lookup-perm)
         (into #{}))))

(defn chmod
  [path arg & args]
  {:pre [(or (string? arg) (keyword? arg))]}
  (let [perms (if (string? arg)
                (parse-perms arg :owner)
                (as-> [arg] $
                      (into $ args)
                      (partition 2 $)
                      (map (fn [[t p]] (parse-perms p t)) $)
                      (apply s/union $)))]
    (set-posix-permissions path perms))
  path)

(defn ctime
  [path]
  (-> path
      (posix-file-attributes)
      (.creationTime)
      (.toInstant)))

(defn mtime
  [path]
  (-> path
      (posix-file-attributes)
      (.lastModifiedTime)
      (.toInstant)))

(defn atime
  [path]
  (-> path
      (posix-file-attributes)
      (.lastAccessTime)
      (.toInstant)))

(defn create-tmp-dir
  []
  (Files/createTempDirectory "tmp-" (make-array FileAttribute 0)))

(defn create-tmp-file
  []
  (Files/createTempFile "tmp-" "" (make-array FileAttribute 0)))

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

(defmacro with-tmp-file
  [bnd & body]
  `(let [f# (fn ~bnd ~@body)
         tmp-file# (create-tmp-file)]
     (try
       (f# tmp-file#)
       (finally
         (rm-rf tmp-file#)))))

(defn exists?
  [path]
  (Files/exists path follow-links))

(defn directory?
  [path]
  (Files/isDirectory path follow-links))

(defn mkdir
  [path]
  (Files/createDirectory path (make-array FileAttribute 0)))

(defn touch
  ([path]
   (touch path nil))
  ([path ^Instant date-time]
   (when-not (exists? path)
     (Files/createFile path (make-array FileAttribute 0)))
   (let [mt (or date-time (Instant/now))
         at (or date-time (Instant/now))
         ct (ctime path)
         attr-view (posix-file-attribute-view path)]
     (.setTimes attr-view
                (FileTime/from mt)
                (FileTime/from at)
                (FileTime/from ct)))
   path))
