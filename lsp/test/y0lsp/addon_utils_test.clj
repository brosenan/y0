(ns y0lsp.addon-utils-test
  (:require
   [midje.sweet :refer [fact]]
   [y0lsp.addon-utils :refer :all]
   [y0lsp.initializer-test :refer [addon-test]]
   [y0lsp.server :refer [register-notification register-req]]))

;; # Addon Utils

;; This module contains helper functions for creating addons.

;; ## Handler Installation Functions

;; The `y0lsp` [server](server.md) allows for
;; [request](server.md#handling-requests) and
;; [notification](server.md#handling-notifications) handlers to be installed to
;; the server context.

;; `add-req-handler` takes a request name and a handler and returns an addon
;; function that installs it to the context.
(register-req "test/foo" any?)
(fact
 (let [my-handler (fn [{:keys [req-handlers]} req]
                    {:got req
                     :handlers req-handlers})
       {:keys [send shutdown]}
       (addon-test (add-req-handler "test/foo" my-handler))]
   (send "test/foo" {:foo :bar}) => {:got {:foo :bar}
                                     :handlers {"test/foo" my-handler}}
   (shutdown)))

;; `add-notification-handler` takes a notification name and handler and returns
;; a addon function that adds the handler as a handler for that notification.
(register-notification "test/didFoo")
(fact
 (let [x (atom nil)
       my-handler (fn [_ctx params]
                    (reset! x params))
       {:keys [notify shutdown]}
       (addon-test (add-notification-handler "test/didFoo" my-handler))]
   (notify "test/didFoo" {:foo :bar})
   @x => {:foo :bar}
   (shutdown)))
