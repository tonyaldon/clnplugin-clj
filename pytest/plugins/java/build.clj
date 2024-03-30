(ns build
  (:require [clojure.tools.build.api :as b])
  (:require [babashka.fs :as fs]))

(def class-dir "target/classes")
(def p "target/myplugin")
(def uber-file "target/myplugin.jar")
(def p-jar (str (fs/file (fs/cwd) uber-file)))

(defn clean [_]
  (b/delete {:path "target"}))

(defn plugin [_]
  (let [basis (b/create-basis {:project "deps.edn"})]
    (clean nil)
    (b/copy-dir {:src-dirs ["src"]
                 :target-dir class-dir})
    (b/compile-clj {:class-dir class-dir
                    :basis basis
                    :ns-compile '[myplugin]})
    (b/uber {:class-dir class-dir
             :basis basis
             :uber-file uber-file
             :main 'myplugin})
    (spit p (str "#!/usr/bin/env -S java -jar " p-jar))
    (b/process {:command-args ["chmod" "+x" p]})))
