(ns nibblonian.tickets
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure-commons.file-utils :as ft]
            [clojure.string :as string])
  (:use [clj-jargon.jargon]
        [clojure-commons.error-codes]
        [slingshot.slingshot :only [try+ throw+]])
  (:import [org.irods.jargon.ticket TicketAdminServiceImpl 
            Ticket]
           [org.irods.jargon.ticket.packinstr TicketCreateModeEnum]))

(def ticket-read TicketCreateModeEnum/TICKET_CREATE_READ)
(def ticket-write TicketCreateModeEnum/TICKET_CREATE_WRITE)
(def ticket-unknown TicketCreateModeEnum/TICKET_CREATE_UNKNOWN)

(def collection-type
  org.irods.jargon.ticket.Ticket$TicketObjectType/COLLECTION)

(def dataobject-type 
  org.irods.jargon.ticket.Ticket$TicketObjectType/DATA_OBJECT)

(defn ticket-admin
  "Creates an instance of TicketAdminServiceImpl. Assumes that it's being
   run from with in a (with-jargon) call."
  []
  (let [aof           (:accessObjectFactory cm)
        irods-account (:irodsAccount cm)]
    (TicketAdminServiceImpl. aof irods-account)))

(defn new-ticket
  "Creates a new ticket for a file or directory."
  [ticket-type ticket-path ticket-id]
  (with-jargon
    (let [admin (ticket-admin)
          fobj  (file ticket-path)]
      (.createTicket admin ticket-type fobj ticket-id))))

(defn delete-ticket
  "Deletes a ticket."
  [ticket-id]
  (with-jargon
    (let [admin (ticket-admin)]
      (.deleteTicket admin ticket-id))))

(defn ticket
  "Gets an instance of Ticket based on the ticket-id."
  [ticket-id]
  (with-jargon
    (let [admin (ticket-admin)]
      (.getTicketForSpecifiedTicketString admin ticket-id))))

(defn expire-time
  "Gets and sets the expiration date for a ticket."
  ([ticket]
    (.getExpireTime ticket))
  ([ticket new-date]
    (.setExpireTime ticket new-date)))

(defn abs-path
  "Gets and sets the absolute path to the file/directory
   the ticket was created for."
  ([ticket]
    (.getIrodsAbsolutePath ticket))
  ([ticket new-path]
    (.setIrodsAbsolutePath ticket new-path)))

(defn object-type
  "Gets and set the object type the ticket was created for.
   Returns 'COLLECTION', 'DATAOBJECT', or 'UNKNOWN'."
  ([ticket]
    (let [obj-type (.getObjectType ticket)]
      (cond
        (= obj-type collection-type) "COLLECTION"
        (= obj-type dataobject-type) "DATAOBJECT"
        :else "UNKNOWN")))
  ([ticket obj-type]
    (.setObjectType ticket obj-type)))

(defn owner-name
  "Gets and sets the owner name for the ticket."
  ([ticket]
    (.getOwnerName ticket))
  ([ticket oname]
    (.setOwnerName ticket oname)))

(defn owner-zone
  "Gets and sets the owner zone for the ticket."
  ([ticket]
    (.getOwnerZone ticket))
  ([ticket ozone]
    (.setOwnerZone ticket ozone)))

(defn ticket-id
  "Gets and sets the owner id for the ticket."
  ([ticket]
    (.getTicketId ticket))
  ([ticket tckt-id]
    (.setTicketId ticket tckt-id)))

(defn ticket-string
  "Gets and sets the string for a ticket."
  ([ticket]
    (.getTicketString ticket))
  ([ticket tckt-string]
    (.setTicketString ticket tckt-string)))

(defn uses-count
  ([ticket]
    (.getUsesCount ticket))
  ([ticket new-count]
    (.setUsesCount ticket new-count)))

(defn uses-limit
  ([ticket]
    (.getUsesLimit ticket))
  ([ticket new-limit]
    (.setUsesLimit ticket new-limit)))

(defn write-byte-count
  ([ticket]
    (.getWriteByteCount ticket))
  ([ticket new-write-byte-count]
    (.setWriteByteCount ticket new-write-byte-count)))

(defn write-byte-limit
  ([ticket]
    (.getWriteByteLimit ticket))
  ([ticket new-write-byte-limit]
    (.setWriteByteLimit ticket new-write-byte-limit)))

(defn write-file-count
  ([ticket]
    (.getWriteFileCount ticket))
  ([ticket new-write-file-count]
    (.setWriteFileCount ticket new-write-file-count)))

(defn write-file-limit
  ([ticket]
    (.getWriteFileLimit ticket))
  ([ticket new-write-file-limit]
    (.setWriteFileLimit ticket new-write-file-limit)))

(defn ticket-type
  ([ticket]
    (.getType ticket))
  ([ticket new-ticket-type]
    (.setType ticket new-ticket-type)))

(defn valid-keys
  [k]
  (case k
    "expire-time"      expire-time
    "abs-path"         abs-path
    "object-type"      object-type
    "owner-name"       owner-name
    "owner-zone"       owner-zone
    "ticket-id"        ticket-id
    "ticket-string"    ticket-string
    "type"             ticket-type
    "uses-count"       uses-count
    "uses-limit"       uses-limit
    "write-byte-count" write-byte-count
    "write-byte-limit" write-byte-limit
    "write-file-count" write-file-count
    "write-file-limit" write-file-limit
    (throw {:error_code ERR_UNCHECKED_EXCEPTION :key k})))

(defn set-ticket-attrs
  "Sets a tickets attributes based on the map that's passed in. The
   keys in the map are transformed into function calls as follows:
   * Key names are split on dashes.
   * Each part of the name is capitalized.
   * The parts are rejoined together and prepended with 'set'.
   For example: 'write-byte-count' becomes 'setWriteByteCount'.
   This will hopefully allow us to write more idiomatic Clojure when
   setting ticket attrs."
  [ticket attrs]
  (doseq [fn-key (keys attrs)]
    (let [set-fn  (valid-keys fn-key)
          set-val (get attrs fn-key)]
      (set-fn ticket set-val))))

(defn get-ticket-attrs
  "Gets the listed attributes from a ticket. Returns a map of the 
   attributes/values. The keys in the map are transformed into
   function calls as follows:
   * Key names are split on dashes.
   * Each part of the name is capitalized.
   * the aprts are rejoined together and prepended with 'get'.
   For example, 'write-byte-count' becomes 'getWriteByteCount'."
  [ticket attrs]
  (merge {} 
    (for [fn-key attrs]
      (let [get-fn (valid-keys fn-key)]
        {fn-key (get-fn ticket)}))))
