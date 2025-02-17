(ns y0lsp.addons.docsync
  (:require [y0lsp.addon-utils :refer [register-addon merge-server-capabilities]]))

(register-addon "docsync"
                (merge-server-capabilities
                 {:text-document-sync {:change 1 :open-close true}}))
