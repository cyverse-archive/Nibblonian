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

;;;Ticket types.
(def ticket-read TicketCreateModeEnum/TICKET_CREATE_READ)
(def ticket-write TicketCreateModeEnum/TICKET_CREATE_WRITE)
(def ticket-unknown TicketCreateModeEnum/TICKET_CREATE_UNKNOWN)

;;;Object types that a ticket can work on.
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
  [ticket-type ticket-path ticket-string]
  (let [admin (ticket-admin)
        fobj  (file ticket-path)]
    (.createTicket admin ticket-type fobj ticket-string)))

(defn delete-ticket
  "Deletes a ticket."
  [ticket-string]
  (let [admin (ticket-admin)]
    (.deleteTicket admin ticket-string)))

(defn ticket
  "Gets an instance of Ticket based on the ticket-string."
  [ticket-string]
  (let [admin (ticket-admin)]
    (.getTicketForSpecifiedTicketString admin ticket-string)))

(defn ticket-groups
  "Gets and sets the list of groups that can use the ticket.
   Adding new groups is additive, it doesn't reset the whole list."
  ([ticket-string]
    (.listAllGroupRestrictionsForSpecifiedTicket (ticket-admin) ticket-string 0))
  ([ticket-string irods-groups]
    (let [ta (ticket-admin)]
      (doseq [group irods-groups]
        (.addticketGroupRestriction ta ticket-string group)))))

(defn ticket-hosts
  "Gets and sets the list of hosts that can use the ticket. Adding new
   hosts is actually additive it doesn't reset the whole list."
  ([ticket-string]
    (.listAllHostRestrictionsForSpecifiedTicket (ticket-admin) ticket-string 0))
  ([ticket-string ticket-hosts]
    (let [ta (ticket-admin)]
      (doseq [host ticket-hosts]
        (.addTicketHostRestriction ta ticket-string host)))))

(defn ticket-users
  "Gets and sets the list of user that can use the ticket. Adding new
   users is actually additive, it doesn't reset the whole list."
  ([ticket-string]
    (.listAllHostRestrictionsForSpecifiedTicket (ticket-admin) ticket-string 0))
  ([ticket-string ticket-users]
    (let [ta (ticket-admin)]
      (doseq [user ticket-users]
        (.addTicketUserRestriction ta ticket-string user)))))

(defn expire-time
  "Gets and sets the expiration date for a ticket."
  ([ticket-string]
    (.getExpireTime (ticket ticket-string)))
  ([ticket-string new-date]
    (.setExpireTime (ticket ticket-string) new-date)))

(defn abs-path
  "Gets and sets the absolute path to the file/directory
   the ticket was created for."
  ([ticket-string]
    (.getIrodsAbsolutePath (ticket ticket-string)))
  ([ticket-string new-path]
    (.setIrodsAbsolutePath (ticket ticket-string) new-path)))

(defn object-type
  "Gets and set the object type the ticket was created for.
   Returns 'COLLECTION', 'DATAOBJECT', or 'UNKNOWN'."
  ([ticket-string]
    (let [obj-type (.getObjectType ticket)]
      (cond
        (= obj-type collection-type) "COLLECTION"
        (= obj-type dataobject-type) "DATAOBJECT"
        :else "UNKNOWN")))
  ([ticket-string obj-type]
    (.setObjectType (ticket ticket-string) obj-type)))

(defn owner-name
  "Gets and sets the owner name for the ticket."
  ([ticket-string]
    (.getOwnerName (ticket ticket-string)))
  ([ticket-string oname]
    (println "Setting owner name.")
    (.setOwnerName (ticket ticket-string) oname)))

(defn owner-zone
  "Gets and sets the owner zone for the ticket."
  ([ticket-string]
    (.getOwnerZone (ticket ticket-string)))
  ([ticket-string ozone]
    (.setOwnerZone (ticket ticket-string) ozone)))

(defn ticket-id
  "Gets and sets the owner id for the ticket."
  ([ticket-string]
    (.getTicketId (ticket ticket-string)))
  ([ticket-string tckt-id]
    (.setTicketId (ticket ticket-string) tckt-id)))

(defn ticket-string
  "Gets and sets the string for a ticket."
  ([ticket]
    (.getTicketString (ticket ticket-string)))
  ([ticket tckt-string]
    (.setTicketString (ticket ticket-string) tckt-string)))

(defn uses-count
  "Gets and sets the uses count for a ticket."
  ([ticket-string]
    (.getUsesCount (ticket ticket-string)))
  ([ticket-string new-count]
    (.setUsesCount (ticket ticket-string) new-count)))

(defn uses-limit
  "Gets and sets the uses limit for a ticket."
  ([ticket-string]
    (.getUsesLimit (ticket ticket-string)))
  ([ticket-string new-limit]
    (.setUsesLimit (ticket ticket-string) new-limit)))

(defn write-byte-count
  "Gets and sets the write byte count for a ticket."
  ([ticket-string]
    (.getWriteByteCount (ticket ticket-string)))
  ([ticket-string new-write-byte-count]
    (.setWriteByteCount (ticket ticket-string) new-write-byte-count)))

(defn write-byte-limit
  "Gets and sets the write byte limit for a ticket."
  ([ticket-string]
    (.getWriteByteLimit (ticket ticket-string)))
  ([ticket-string new-write-byte-limit]
    (.setWriteByteLimit (ticket ticket-string) new-write-byte-limit)))

(defn write-file-count
  "Gets and set the write file count for a ticket."
  ([ticket-string]
    (.getWriteFileCount (ticket ticket-string)))
  ([ticket-string new-write-file-count]
    (.setWriteFileCount (ticket ticket-string) new-write-file-count)))

(defn write-file-limit
  "Gets and sets the write file limit for a ticket."
  ([ticket-string]
    (.getWriteFileLimit (ticket ticket-string)))
  ([ticket-string new-write-file-limit]
    (.setWriteFileLimit (ticket ticket-string) new-write-file-limit)))

(defn ticket-type
  "Gets and sets the ticket type for a ticket."
  ([ticket-string]
    (.getType (ticket ticket-string)))
  ([ticket-string new-ticket-type]
    (.setType (ticket ticket-string) new-ticket-type)))

(def attr-fns
  "Maps attribute keys to their getter-setter function."
  { :expire-time      expire-time
    :abs-path         abs-path
    :object-type      object-type
    :owner-name       owner-name
    :owner-zone       owner-zone
    :ticket-id        ticket-id
    :ticket-string    ticket-string
    :type             ticket-type
    :uses-count       uses-count
    :uses-limit       uses-limit
    :write-byte-count write-byte-count
    :write-byte-limit write-byte-limit
    :write-file-count write-file-count
    :write-file-limit write-file-limit})

(defn valid-key?
  "Makes sure that an attribute name is actually an attribute
   of a ticket."
  [k]
  (contains? (set (keys attr-fns)) k))

(defn set-get
  "Returns the set-get function for an ticket attribute."
  [k]
  (if (valid-key? k)
    (get attr-fns k)
    (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD :key k})))

(defn set-ticket-attrs
  "Sets a tickets attributes based on the map that's passed in. The
   keys in the map should correspond to the ticket attribute and the
   value should be the new value for the attribute."
  [ticket attrs]
  (when-not (every? valid-key? (keys attrs))
    (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD :attrs attrs}))
  
  (doseq [fn-key (keys attrs)]
    (let [set-fn  (set-get fn-key)
          set-val (get attrs fn-key)]
      (set-fn ticket set-val))))

(defn get-ticket-attrs
  "Gets the current value for a sequence of ticket attributes. Returns
   a map of ticket-attribute ticket-value pairs."
  [ticket attrs]
  (when-not (every? valid-key? attrs)
    (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD :attrs attrs}))
  
  (merge {} 
    (for [fn-key attrs]
      (let [get-fn (set-get fn-key)]
        {fn-key (get-fn ticket)}))))

(defn- type->ttype
  [type]
  (cond
    (= type "read")  ticket-read
    (= type "write") ticket-write
    :else ticket-unknown))

(defn access?
  [user ticket-string]
  (contains? (set (ticket-users ticket-string)) user))

(defn create-ticket
  "Entry point function for creating a new ticket and setting its attributes."
  [owner owner-zone attrs]
  (with-jargon
    (let [ticket-type   (type->ttype (:type attrs))
          ticket-path   (:abs-path attrs)
          ticket-string (:ticket-string attrs)]
      (when-not (exists? ticket-path)
        (throw+ {:error_code ERR_DOES_NOT_EXIST :path ticket-path}))
      
      (log/warn (str "create-ticket: type: " ticket-type))
      (log/warn (str "create-ticket: path: " ticket-path))
      (log/warn (str "create-ticket: string: " ticket-string))
      
      (set-ticket-attrs 
        (new-ticket ticket-type ticket-path ticket-string) 
        (assoc attrs :owner-zone owner-zone)))))

(defn read-ticket
  "Entry point function for reading the attributes of a ticket."
  [user ticket-string]
  (with-jargon
    (when-not (access? user ticket-string)
      (throw+ {:error_code ERR_NOT_AUTHORIZED :user user :ticket ticket-string}))
    
    (get-ticket-attrs (ticket ticket-string) (keys attr-fns))))

(defn update-ticket
  "Entry point function for updating the attributes of a ticket."
  [user attrs]
  (with-jargon
    (when-not (access? user (:ticket-string attrs))
      (throw+ {:error_code ERR_NOT_AUTHORIZED :user user :ticket (:ticket-string attrs)}))
    
    (set-ticket-attrs (ticket ticket-string) attrs)))

(defn delete-ticket
  "Entry point function for deleting a ticket."
  [user ticket-string]
  (with-jargon
    (let [ticket-path (:abs-path (ticket ticket-string))]
      (when-not (owns? user ticket-path)
        (throw+ {:error_code ERR_NOT_OWNER :user user :path ticket-path}))
      
      (delete-ticket ticket-string))))
