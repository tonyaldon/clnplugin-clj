(ns rpc
  (:require [clnrpc-clj :as rpc])
  (:require [clojure.data.json :as json])
  (:require [clojure.java.io :as io])
  (:require [clojure.core.async :refer [<!! go chan]]))

;; only to test at the terminal this script work correctly
(defn getinfo [{:keys [socket-file]}]
  (let [rpc-info {:socket-file socket-file}]
    (-> (rpc/getinfo rpc-info)
        (json/write *out* :escape-slash false))))

(defn call-send-message-notifications
  [{:keys [socket-file]}]
  (let [notifs (chan)
        rpc-info {:socket-file socket-file :notifs notifs}
        resp (go (rpc/call rpc-info "send-message-notifications"))
        notifs-and-resp (atom [])]
    (loop [notif (<!! notifs)]
      (if (= notif :no-more)
        (swap! notifs-and-resp conj (<!! resp))
        (do
          (swap! notifs-and-resp conj (get-in notif [:params :message]))
          (recur (<!! notifs)))))
    (json/write @notifs-and-resp *out* :escape-slash false)))

(defn call-send-progress-notifications-with-enable-notifications
  [{:keys [socket-file]}]
  (let [notifs (chan)
        rpc-info {:socket-file socket-file
                  :notifs notifs}
        resp (go (rpc/call rpc-info "send-progress-notifications"))
        notifs-and-resp (atom [])]
    (loop [notif (<!! notifs)]
      (if (= notif :no-more)
        (swap! notifs-and-resp conj (<!! resp))
        (do
          (swap! notifs-and-resp conj (get-in notif [:params :num]))
          (recur (<!! notifs)))))
    (json/write @notifs-and-resp *out* :escape-slash false)))
